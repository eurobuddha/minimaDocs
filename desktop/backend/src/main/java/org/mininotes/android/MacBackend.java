package org.mininotes.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.mininotes.desktop.platform.content.Context;
import org.mininotes.desktop.platform.database.Cursor;

/** A private stdin/stdout bridge. No HTTP port, shell commands, or renderer-selected disk paths. */
public final class MacBackend implements AutoCloseable {
    static final int MAX_FRAME=24*1024*1024;
    final Context context;
    final NoteStore store;
    final Keys keys;
    final boolean offline;
    private final Map<String,View> views=new HashMap<>();
    private final Map<String,Offer> offers=new ConcurrentHashMap<>();
    private final ScheduledExecutorService network=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"minimaDocs-sharing");t.setDaemon(true);return t;});
    private volatile boolean stopped;
    private volatile String connection="Connecting to Maxima…";
    private static class View {
        String note,title;RichDocument viewed;boolean readOnly;
        View(String note,String title,RichDocument viewed,boolean readOnly){this.note=note;this.title=title;this.viewed=viewed;this.readOnly=readOnly;}
    }
    private record Offer(Sharing.Level level,long expires){}

    MacBackend(Path directory,byte[] key,boolean offline) throws Exception {
        this.offline=offline;context=new Context(directory.toFile());context.unlock(key);NoteStore.unlock(key);
        store=new NoteStore(context);store.getWritableDatabase();keys=new Keys(context);
        store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        store.myAgreement=Point.shorten(keys.agreement().getPublic());store.myName=Node.nameHere(context);
        connection=offline?"Offline session":"Connecting to Maxima…";
        if(!offline)network.execute(this::startNetwork);
    }
    private void startNetwork() {
        try {
            Node.near(true);
            if(!Node.listen(context,bytes->network.execute(()->{try {received(Post.arrived(context,store,keys,bytes));}catch(Exception ignored){connection="A shared update could not be opened";}})))throw new IOException("Could not start Maxima");
            Node.everyBeat(()->network.execute(this::sync));
            Node.tellEverybody(context);sync();
        } catch(Exception e){connection="Maxima is offline. Documents still work.";}
    }
    private void sync() {
        if(stopped)return;
        try {
            List<String> addresses=Node.addresses(context);store.myAddress=addresses.isEmpty()?"":addresses.get(0);
            connection=Node.attached()>0?"Maxima connected":"Maxima reconnecting…";
            Post.again(context,store,keys);Post.leftAgain(context,store,keys);Post.removedAgain(context,store,keys);
            for(NoteStore.Accepting a:store.waitingToAccept())try {Post.sayAgain(context,store,keys,a,Node.nameHere(context),address());store.triedAgain(a.address);}catch(Exception ignored){}
        }catch(Exception e){connection="Maxima reconnecting…";}
    }
    private String address() throws Exception {
        if(offline)throw new IOException("Sharing is unavailable in this offline test session.");
        List<String> all=Node.addresses(context);if(all.isEmpty())throw new IOException("Maxima is connecting. Try again in a moment.");
        store.myAddress=all.get(0);return store.myAddress;
    }
    private void introduce(String address) {
        network.execute(()->{try {String key=Node.introduce(context,address);if(!key.isEmpty())store.knownAs(address,key);}catch(Exception ignored){}});
    }
    private void received(Post.Landed landed) throws Exception {
        if(landed==null||landed.accepted==null)return;
        Hello.Said said=landed.accepted;
        String scope=Pairing.scopeIn(said.scope);
        Offer offer=offers.get(said.target.isEmpty()?"personal":scope+":"+said.target);
        // A code shown by this Mac is consent for 15 minutes, with no permission escalation.
        if(offer==null||System.currentTimeMillis()>offer.expires||said.level.ordinal()>offer.level.ordinal())return;
        if(!said.target.isEmpty()&&(!scope.equals("PAGE")||!store.mayGive(Sharing.Scope.PAGE,said.target).contains(said.level)))return;
        store.pairedWith(said.address,said.name,false,said.agreement,said.signing);introduce(said.address);
        if(said.target.isEmpty())Post.helloBack(context,keys,said,Node.nameHere(context),address(),null);
        else {store.give(Sharing.Scope.PAGE,said.target,said.address,said.level,null);Post.send(context,store,keys,NoteStore.Branch.Kind.PAGE,said.target);}
    }
    private void changed(String id) {
        if(!offline)network.execute(()->{try{Post.changed(context,store,keys,NoteStore.Branch.Kind.PAGE,id);Post.filesChanged(context,store,keys,id);}catch(Exception ignored){connection="Saved here · updates waiting to send";}});
    }
    JSONObject call(String method,JSONObject p) throws Exception {
        return switch(method) {
            case "list" -> list();
            case "versions" -> versions(p.getString("id"));
            case "open" -> open(p);
            case "save" -> save(p);
            case "release" -> {views.remove(p.getString("session"));yield new JSONObject();}
            case "copy" -> {View v=view(p);v.note=UUID.randomUUID().toString();v.viewed=RichDocument.empty(v.viewed.kind);v.readOnly=false;yield new JSONObject().put("id",v.note);}
            case "people" -> people();
            case "invitation" -> invitation(p);
            case "preview" -> preview(p.getString("text"));
            case "accept" -> accept(p);
            case "share" -> share(p);
            case "access" -> access(p.getString("id"));
            case "manage" -> manage(p);
            case "sync" -> {String id=p.getString("id");requireNote(id);changed(id);yield new JSONObject().put("message","Updates queued; delivery is confirmed by the recipient.");}
            case "name" -> {String name=p.getString("name").strip();if(name.isEmpty()||name.length()>80)throw new IOException("Use a name between 1 and 80 characters.");Node.chooseName(context,name);store.myName=name;yield new JSONObject();}
            case "qrRead" -> {byte[] raw=decode(p.getString("base64"));try(var in=javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(raw))){var readers=javax.imageio.ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IOException("Choose a PNG or JPEG QR image.");var r=readers.next();try{r.setInput(in);if((long)r.getWidth(0)*r.getHeight(0)>16000000)throw new IOException("Choose a QR image smaller than 16 megapixels.");yield new JSONObject().put("text",DesktopQr.read(r.read(0)));}finally{r.dispose();}}}
            default -> throw new IOException("Unknown desktop operation");
        };
    }
    private NoteStore.Note requireNote(String id) throws IOException {
        if(!RichDocument.id(id))throw new IOException("Invalid document identifier");
        NoteStore.Note n=store.get(id);if(n==null||n.deleted||n.archived||RichDocument.read(n.body)==null)throw new IOException("This document is unavailable");return n;
    }
    private JSONObject list() {
        JSONArray rows=new JSONArray();
        try(Cursor c=store.getReadableDatabase().query("notes",new String[]{"id"},"deleted=0 AND archived=0",null,null,null,"updated DESC","1000")) {
            while(c.moveToNext()) {NoteStore.Note n=store.get(c.getString(0));RichDocument doc=RichDocument.read(n.body);if(doc==null)continue;
                rows.put(new JSONObject().put("id",n.id).put("title",n.title).put("kind",doc.kind).put("updated",n.updated).put("shared",n.theirs||!store.sharesOn(Sharing.Scope.PAGE,n.id).isEmpty()).put("readonly",store.onlyReads(n.id)).put("versions",doc.heads.size()).put("pending",store.owed(NoteStore.Branch.Kind.PAGE,n.id).size()));
            }
        }
        return new JSONObject().put("documents",rows).put("connection",connection).put("name",Node.nameHere(context));
    }
    private JSONObject open(JSONObject p) throws Exception {
        String id=p.optString("id",UUID.randomUUID().toString()),title=p.optString("title","Untitled document");
        for(View active:views.values())if(active.note.equals(id))throw new IOException("This document is already open. Close its editor before opening another version.");
        RichDocument doc;boolean readonly=false;JSONObject config=new JSONObject();
        if(p.has("id")) {
            NoteStore.Note n=requireNote(id);doc=RichDocument.read(n.body);title=n.title;
            String file=p.optString("file",doc.heads.values().iterator().next());if(!doc.heads.containsValue(file))throw new IOException("This version is unavailable");
            NoteStore.Held held=store.keptFile(file);if(held==null)throw new IOException("This document is still arriving. Try opening it again shortly.");
            config.put("base64",Base64.getEncoder().encodeToString(store.bytesOf(held))).put("name",held.name);
            readonly=store.onlyReads(id)||doc.heads.size()>1;
        } else {
            doc=RichDocument.empty(p.getString("kind"));
            if(p.has("base64")){decode(p.getString("base64"));config.put("base64",p.getString("base64")).put("name",p.getString("name"));}
        }
        String session=UUID.randomUUID().toString();views.put(session,new View(id,title,doc,readonly));
        JSONArray versions=new JSONArray();for(String file:doc.heads.values())versions.put(file);
        return config.put("session",session).put("id",id).put("title",title).put("kind",doc.kind).put("readonly",readonly).put("conflict",doc.heads.size()>1).put("versions",versions);
    }
    private View view(JSONObject p) throws IOException {View v=views.get(p.getString("session"));if(v==null)throw new IOException("This editor has closed");return v;}
    private JSONObject versions(String id) throws IOException {
        NoteStore.Note n=requireNote(id);RichDocument doc=RichDocument.read(n.body);JSONArray versions=new JSONArray();
        for(String file:doc.heads.values()){NoteStore.Held held=store.keptFile(file);versions.put(new JSONObject().put("file",file).put("available",held!=null));}
        return new JSONObject().put("title",n.title).put("versions",versions);
    }
    static byte[] decode(String base64) throws IOException {
        if(base64.length()>((Enclosure.MOST+2)/3)*4)throw new IOException("Files must be 16 MiB or smaller.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(base64);}catch(IllegalArgumentException e){throw new IOException("Invalid file data");}
        if(bytes.length==0||bytes.length>Enclosure.MOST)throw new IOException("Files must be between 1 byte and 16 MiB.");return bytes;
    }
    private JSONObject save(JSONObject p) throws Exception {
        View v=view(p);if(v.readOnly)throw new IOException("This document is read-only. Save a separate copy to edit.");
        String title=p.optString("title",v.title).strip();if(title.length()>100)throw new IOException("The title is too long");
        DocumentStore.Saved saved=DocumentStore.save(context,store,v.note,title,v.viewed,decode(p.getString("base64")));
        v.viewed=saved.viewed;v.title=title;changed(v.note);
        return new JSONObject().put("id",v.note).put("conflict",saved.current.heads.size()>1).put("savedAt",System.currentTimeMillis());
    }
    private JSONObject people() throws Exception {
        JSONArray people=new JSONArray();for(NoteStore.Contact c:store.addresses())if(c.paired())people.put(new JSONObject().put("name",c.name).put("address",c.address).put("code",Envelope.code(keys.signing().getPublic(),Keys.publicKey(c.signing))));
        return new JSONObject().put("people",people).put("connection",connection).put("name",Node.nameHere(context));
    }
    private JSONObject invitation(JSONObject p) throws Exception {
        String id=p.optString("id","");Sharing.Level level=p.optString("level","READ").equals("WRITE")?Sharing.Level.WRITE:Sharing.Level.READ;
        String title="",scope="";if(!id.isEmpty()){NoteStore.Note n=requireNote(id);title=n.title;scope="PAGE";if(!store.mayGive(Sharing.Scope.PAGE,id).contains(level))throw new IOException("Only the owner or an administrator can invite people.");}
        String line=keys.line(Node.nameHere(context),address(),title.isEmpty()?"":Sharing.travelling(Sharing.Scope.PAGE,title),level,scope,id);
        offers.put(id.isEmpty()?"personal":"PAGE:"+id,new Offer(level,System.currentTimeMillis()+15*60_000));
        String link=Pairing.docsLink(line);ByteArrayOutputStream out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(DesktopQr.draw(link,480),"png",out);
        return new JSONObject().put("line",line).put("link",link).put("qr","data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()));
    }
    private JSONObject preview(String text) throws Exception {
        if(text.length()>Pairing.MOST*3)throw new IOException("The invitation is too long");
        Pairing.Said p=Pairing.read(Pairing.line(text.strip()));
        return new JSONObject().put("name",p.name).put("offer",p.offer).put("level",p.level.name()).put("code",Envelope.code(keys.signing().getPublic(),Keys.publicKey(p.signing)));
    }
    private JSONObject accept(JSONObject p) throws Exception {
        String text=p.getString("text");preview(text);Pairing.Said said=Pairing.read(Pairing.line(text.strip()));
        // Enter recipient pairs their identity; an offer embedded in that recipient's code is not accepted.
        if(p.optBoolean("recipient"))said=new Pairing.Said(said.name,said.address,said.agreement,said.signing);
        store.pairedWith(said.address,said.name,false,said.agreement,said.signing);introduce(said.address);
        store.accepting(said.address,said.name,said.scope,said.target,said.level);
        if(!said.target.isEmpty())store.acceptedBack(said.address,said.target);
        Pairing.Said them=said;
        if(!offline)network.execute(()->{try{Post.accept(context,keys,them,Node.nameHere(context),address());}catch(Exception ignored){}});
        return new JSONObject().put("address",said.address).put("message","Paired here. Waiting for the other device to respond.");
    }
    private JSONObject share(JSONObject p) throws Exception {
        String id=p.getString("id");requireNote(id);String address=p.getString("address");
        NoteStore.Contact c=store.address(address);if(c==null||!c.paired())throw new IOException("Exchange an invitation or QR code first so both devices have encryption keys.");
        Sharing.Level level=p.getString("level").equals("WRITE")?Sharing.Level.WRITE:Sharing.Level.READ;
        if(!store.mayGive(Sharing.Scope.PAGE,id).contains(level))throw new IOException("You cannot grant this permission.");
        Sharing.Rule existing=null;for(Sharing.Rule rule:store.sharesOn(Sharing.Scope.PAGE,id))if(rule.address.equals(address))existing=rule;
        if(existing!=null)store.decide(existing,level,System.currentTimeMillis());else store.give(Sharing.Scope.PAGE,id,address,level,null);changed(id);
        return new JSONObject().put("message","Access saved. Waiting for delivery confirmation.");
    }
    private JSONObject access(String id) throws Exception {
        requireNote(id);JSONArray rows=new JSONArray();Set<String> included=new HashSet<>();
        for(Sharing.Rule rule:store.sharesOn(Sharing.Scope.PAGE,id))if(rule.level!=Sharing.Level.GONE){
            NoteStore.Contact c=store.address(rule.address);included.add(rule.address);
            rows.put(new JSONObject().put("address",rule.address).put("name",c==null?rule.address:c.name).put("level",rule.level.name()).put("manage",store.mayChange(rule)));
        }
        for(String address:store.reaches(Sharing.Scope.PAGE,id).keySet())if(!included.contains(address)){
            NoteStore.Contact c=store.address(address);rows.put(new JSONObject().put("address",address).put("name",c==null?address:c.name).put("level","INHERITED").put("manage",false));
        }
        Sharing.Level mine=store.myLevel(Sharing.Scope.PAGE,id);
        return new JSONObject().put("people",rows).put("mine",store.owns(Sharing.Scope.PAGE,id)?"OWNER":mine==null?"GONE":mine.name()).put("pending",store.owed(NoteStore.Branch.Kind.PAGE,id).size());
    }
    private JSONObject manage(JSONObject p) throws Exception {
        String id=p.getString("id"),address=p.getString("address"),level=p.getString("level");requireNote(id);
        if(!Set.of("READ","WRITE","GONE").contains(level))throw new IOException("Choose Can view, Can edit or Remove access.");
        Sharing.Rule current=null;for(Sharing.Rule candidate:store.sharesOn(Sharing.Scope.PAGE,id))if(candidate.address.equals(address))current=candidate;
        if(current==null||!store.mayChange(current))throw new IOException("Your permission to manage this person has changed.");
        store.decide(current,Sharing.Level.valueOf(level),System.currentTimeMillis());changed(id);
        return new JSONObject().put("message",level.equals("GONE")?"Access removed. Copies already received stay on their device.":"Access updated. Waiting for delivery confirmation.");
    }
    @Override public void close(){stopped=true;Node.everyBeat(null);network.shutdownNow();if(Node.running())Node.node(context).stop();store.close();}
    public static void main(String[] args) throws Exception {
        PrintStream replies=System.out;System.setOut(System.err);
        DataInputStream in=new DataInputStream(new BufferedInputStream(System.in));DataOutputStream out=new DataOutputStream(new BufferedOutputStream(replies));
        MacBackend backend=null;
        try {
            while(true) {
                int size;try{size=in.readInt();}catch(EOFException end){break;}if(size<1||size>MAX_FRAME)throw new IOException("Invalid bridge frame");
                byte[] raw=in.readNBytes(size);if(raw.length!=size)throw new EOFException();JSONObject req=new JSONObject(new String(raw,StandardCharsets.UTF_8));
                JSONObject response=new JSONObject().put("id",req.getLong("id"));
                try {
                    String method=req.getString("method");JSONObject p=req.optJSONObject("params");if(p==null)p=new JSONObject();
                    if(backend==null){if(!method.equals("init"))throw new IOException("Initialize the backend first");byte[] key=Base64.getDecoder().decode(p.getString("key"));SecretBox.unlock(key);backend=new MacBackend(Path.of(args[0]),key,p.optBoolean("offline"));Arrays.fill(key,(byte)0);response.put("result",new JSONObject());}
                    else response.put("result",backend.call(method,p));
                }catch(Exception e){response.put("error",e.getMessage()==null?"The operation could not finish":e.getMessage());}
                byte[] result=response.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(result.length);out.write(result);out.flush();
            }
        } finally {if(backend!=null)backend.close();}
        System.exit(0);
    }
}
