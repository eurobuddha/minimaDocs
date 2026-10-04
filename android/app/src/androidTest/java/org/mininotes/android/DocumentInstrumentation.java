package org.mininotes.android;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Device checks against real SQLCipher and encrypted files, isolated from the user's notebook. */
public final class DocumentInstrumentation extends Instrumentation {
    private String mode="";
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)mode=args.getString("mode","");start();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private Context isolated(String name) {
        Context target=getTargetContext();File root=new File(target.getCacheDir(),"document-test-"+name+"-"+UUID.randomUUID());root.mkdirs();
        return new ContextWrapper(target) {
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){File dir=new File(root,"files");dir.mkdirs();return dir;}
            @Override public File getDatabasePath(String name){return new File(root,name);}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences(root.getName()+name,mode);}
        };
    }
    private static void receive(NoteStore from,NoteStore to,String id,String sender) throws Exception {
        NoteStore.Note n=from.get(id);
        to.landed(id,sender,n.revision,n.title,n.body,Things.HOME);
        List<Enclosure.Listed> listed=new ArrayList<>();
        for(Enclosure.Listed f:from.enclosed(id))listed.add(new Enclosure.Listed(f.id,f.name,f.kind,f.bytes,"test-manifest"));
        to.listed(id,sender,System.currentTimeMillis(),listed);
        for(NoteStore.Incoming f:to.toFetch(System.currentTimeMillis())) {
            NoteStore.Held source=from.file(f.id);
            if(source!=null)check(to.fileArrived(f,from.bytesOf(source)),"file arrival refused");
        }
    }
    @Override public void onStart() {
        Bundle result=new Bundle();byte[] old=NoteStore.key();
        try {
            if(mode.equals("editors")){editors();result.putString("stream","PASS: packaged Android DOCX/XLSX imports, round trips, PDF and layered PNG export\n");finish(-1,result);return;}
            if(mode.startsWith("parlons")){parlons();result.putString("stream","PASS: real Parlons registration and contacts IPC\n");finish(-1,result);return;}
            NoteStore.unlock(null);
            Context a=isolated("a"),b=isolated("b");
            try(NoteStore one=new NoteStore(a);NoteStore two=new NoteStore(b)) {
                String id=UUID.randomUUID().toString();
                DocumentStore.Saved first=DocumentStore.save(a,one,id,"Report",RichDocument.empty("docx"),new byte[]{1,2,3});
                receive(one,two,id,"sender-a");
                check(Arrays.equals(new byte[]{1,2,3},two.bytesOf(two.file(first.file.id))),"initial bytes changed");
                NoteStore.Note writable=two.get(id);writable.theirs=false;two.save(writable);
                DocumentStore.Saved left=DocumentStore.save(a,one,id,"Report",first.viewed,new byte[]{4,5});
                DocumentStore.Saved right=DocumentStore.save(b,two,id,"Report",first.viewed,new byte[]{6,7});
                receive(one,two,id,"sender-a");receive(two,one,id,"sender-b");
                RichDocument joined=RichDocument.read(one.get(id).body);
                check(joined.heads.size()==2,"concurrent branch lost");
                check(joined.text().equals(two.get(id).body),"replicas diverged");
                check(one.file(left.file.id)!=null&&one.file(right.file.id)!=null,"concurrent bytes lost");
                DocumentStore.Saved resolved=DocumentStore.save(a,one,id,"Report",joined,new byte[]{8,9});
                receive(one,two,id,"sender-a");
                check(RichDocument.read(two.get(id).body).heads.size()==1,"resolution failed");
                check(Arrays.equals(new byte[]{8,9},two.bytesOf(two.file(resolved.file.id))),"resolved bytes changed");
                check(one.filesOf(NoteStore.Branch.Kind.PAGE,id).size()==1,"old snapshots accumulated");
                NoteStore.Note read=one.get(id);read.theirs=true;read.writes=false;one.save(read);
                boolean refused=false;try{DocumentStore.save(a,one,id,"Report",resolved.viewed,new byte[]{0});}catch(IOException expected){refused=true;}
                check(refused,"read-only document was written");
                check(Arrays.equals(new byte[]{8,9},one.bytesOf(one.file(resolved.file.id))),"refused save damaged current bytes");
            }
            Context secure=isolated("encrypted");byte[] key=new byte[32];Arrays.fill(key,(byte)7);NoteStore.unlock(key);
            String id=UUID.randomUUID().toString(),file;
            try(NoteStore store=new NoteStore(secure,key)) {
                DocumentStore.Saved saved=DocumentStore.save(secure,store,id,"Private",RichDocument.empty("xlsx"),new byte[]{10,11,12});file=saved.file.id;
                check(Sealed.is(Files.readAllBytes(store.fileFor(file).toPath())),"snapshot was written in plaintext");
            }
            try(NoteStore reopened=new NoteStore(secure,key)) {
                check(Arrays.equals(new byte[]{10,11,12},reopened.bytesOf(reopened.file(file))),"snapshot did not survive closing the database");
                new File(secure.getFilesDir(),"vault.key").createNewFile();NoteStore.unlock(null);
                boolean refused=false;try{DocumentStore.save(secure,reopened,id,"Private",RichDocument.read(reopened.get(id).body),new byte[]{0});}catch(IOException expected){refused=true;}
                check(refused,"locked notebook accepted a snapshot");
            }
            NoteStore.unlock(null);
            Context inbox=isolated("parlons-inbox");Keys keys=Keys.of(inbox);
            String line=keys.line("Friend","Mx12345678@host:9001");String from="0x"+String.join("",Collections.nCopies(32,"ab"));
            check(ParlonsInbox.keep(inbox,from,line),"new invitation did not notify");
            check(!ParlonsInbox.keep(inbox,from,line),"duplicate invitation notified again");
            List<ParlonsInbox.Invitation> invitations=ParlonsInbox.list(inbox);
            check(invitations.size()==1,"invitation duplicate was kept");check(line.equals(invitations.get(0).line),"invitation did not reopen");
            byte[] sealed=Files.readAllBytes(new File(inbox.getFilesDir(),"parlons-invitations/"+invitations.get(0).id).toPath());
            check(!new String(sealed,java.nio.charset.StandardCharsets.UTF_8).contains("Friend"),"invitation saved in plaintext");
            new File(inbox.getFilesDir(),"vault.key").createNewFile();check(ParlonsInbox.list(inbox).isEmpty(),"locked notebook exposed invitations");
            new File(inbox.getFilesDir(),"vault.key").delete();ParlonsInbox.dismiss(inbox,invitations.get(0).id);ParlonsInbox.keep(inbox,from,line);
            check(ParlonsInbox.list(inbox).isEmpty(),"dismissed invitation replayed");
            result.putString("stream","PASS: same-note saves, concurrent replicas, conflict resolution, bounded snapshots, read-only refusal, encrypted recovery, locked refusal, encrypted invitation recovery and deduplication\n");
            finish(-1,result);
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n"+android.util.Log.getStackTraceString(failure));finish(1,result);}
        finally{NoteStore.unlock(old);}
    }
    private void parlons()throws Exception {
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
        String[] error={null};MaximaConnection[] client={null};
        runOnMainSync(()->{client[0]=new MaximaConnection(getTargetContext());
            check(client[0].installed(),"Parlons not installed");check(client[0].familySigned(),"Parlons signing key differs");
            client[0].connect(()->client[0].contacts(list->latch.countDown(),why->{error[0]=why;latch.countDown();}),why->{error[0]=why;latch.countDown();});
        });
        boolean completed=latch.await(30,java.util.concurrent.TimeUnit.SECONDS);
        runOnMainSync(()->client[0].close());check(completed,"Parlons IPC timeout");if(mode.equals("parlons-pending"))check(error[0]!=null&&error[0].startsWith("Approve minimaDocs"),"Expected Parlons approval gate: "+error[0]);
        else check(error[0]==null,"Parlons IPC: "+error[0]);
    }
    private void editors()throws Exception {
        for(String kind:new String[]{"docx","xlsx","image"}) {
            org.json.JSONObject config=new org.json.JSONObject().put("kind",kind);
            if(!kind.equals("image"))try(InputStream in=getContext().getAssets().open("compatibility."+kind)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                config.put("name","compatibility."+kind).put("base64",android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
            }
            EditorTestActivity.Session session=new EditorTestActivity.Session(config.toString());EditorTestActivity.session=session;
            EditorTestActivity activity=(EditorTestActivity)startActivitySync(new Intent(getTargetContext(),EditorTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                check(session.ready.await(240,java.util.concurrent.TimeUnit.SECONDS),kind+" editor opening timeout");check(session.error==null,kind+": "+session.error);
                runOnMainSync(()->activity.web.evaluateJavascript("minimaDocsSave()",null));
                check(session.saved.await(240,java.util.concurrent.TimeUnit.SECONDS),kind+" save timeout");check(session.error==null,kind+": "+session.error);
                check(session.bytes!=null&&session.bytes.length>0,"No saved bytes");
                if(kind.equals("docx")){
                    String xml=zipText(session.bytes,"word/document.xml");check(xml.contains("Body text: alpha, beta, gamma.")&&xml.contains("Apples")&&xml.contains("w:tbl"),"DOCX content/table lost");
                    check(zipText(session.bytes,"word/header1.xml").contains("minimaDocs header"),"DOCX header lost");
                    check(zipText(session.bytes,"word/footer1.xml").contains("minimaDocs footer"),"DOCX footer lost");
                }else if(kind.equals("xlsx")){
                    String xml=zipText(session.bytes,"xl/worksheets/sheet1.xml");check(xml.contains("SUM(B2:B3)")&&xml.contains("<v>20</v>"),"XLSX formula/result lost");
                    check(xml.contains("pane")&&xml.contains("autoFilter"),"XLSX formatting lost");
                }else check(new org.json.JSONObject(new String(session.bytes,java.nio.charset.StandardCharsets.UTF_8)).has("layers"),"Image layers missing");
                session.saved=new java.util.concurrent.CountDownLatch(1);session.bytes=null;
                runOnMainSync(()->activity.web.evaluateJavascript("minimaDocsExport('"+(kind.equals("image")?"PNG":"PDF")+"')",null));
                check(session.saved.await(240,java.util.concurrent.TimeUnit.SECONDS),kind+" export timeout");check(session.error==null,kind+": "+session.error);
                check(session.bytes!=null&&session.bytes.length>8,"No exported bytes");
                check(kind.equals("image")?session.bytes[0]==(byte)137&&session.bytes[1]==80:new String(session.bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"),"Wrong export format");
            }finally{runOnMainSync(activity::finish);waitForIdleSync();}
        }
    }
    private static String zipText(byte[] bytes,String path)throws Exception {
        try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals(path)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");
            }
        }throw new AssertionError("Missing "+path);
    }

}
