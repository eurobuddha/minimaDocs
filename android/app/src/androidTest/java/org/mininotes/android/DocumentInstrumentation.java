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
            if(mode.equals("sharing")){sharing();result.putString("stream","PASS: direct sharing without Parlons, QR and copied invitation permissions, recipient entry, invitation review\n");finish(-1,result);return;}
            if(mode.equals("workspace")){workspace();result.putString("stream","PASS: document workspace, Word controls, Manrope DOCX save and native screenshots\n");finish(-1,result);return;}
            if(mode.equals("photo-import")){photoImports();result.putString("stream","PASS: native PNG/JPEG/WebP photo imports contain visible pixels at phone-photo sizes\n");finish(-1,result);return;}
            if(mode.equals("photocraft")){mobilePhoto();result.putString("stream","PASS: PhotoCraft touch adjustments, Look undo, cancel, mask stroke, crop, PCraft reopen and PNG export\n");finish(-1,result);return;}
            if(mode.equals("image-studio")){imageStudio();result.putString("stream","PASS: Android Image Studio touch masks, brush, layers, retouch undo, 15 effects, 14 presets, layered reopen and PNG export\n");finish(-1,result);return;}
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
    private String javascript(android.webkit.WebView web,String code)throws Exception {
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] answer={null};
        runOnMainSync(()->web.evaluateJavascript(code,value->{answer[0]=value;latch.countDown();}));
        check(latch.await(15,java.util.concurrent.TimeUnit.SECONDS),"JavaScript inspection timed out");return answer[0];
    }
    private void painted(android.webkit.WebView web)throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        runOnMainSync(()->web.postVisualStateCallback(System.nanoTime(),new android.webkit.WebView.VisualStateCallback(){@Override public void onComplete(long id){web.postOnAnimation(()->web.postOnAnimation(done::countDown));}}));check(done.await(30,java.util.concurrent.TimeUnit.SECONDS),"Editor frame did not render");
    }
    private static Object field(Object object,String name)throws Exception {java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void screen(String name)throws Exception {waitForIdleSync();Thread.sleep(300);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"No screenshot");try(ByteArrayOutputStream out=new ByteArrayOutputStream()){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);artifact(name,out.toByteArray());}finally{b.recycle();}}
    private static android.view.View textView(android.view.View root,String text){
        if(root instanceof android.widget.TextView&&text.equals(((android.widget.TextView)root).getText().toString()))return root;
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.view.View found=textView(group.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private void tapText(android.view.View root,String text)throws Exception {
        long until=System.currentTimeMillis()+120000;boolean[] clicked={false};
        while(!clicked[0]&&System.currentTimeMillis()<until){runOnMainSync(()->{android.view.View v=textView(root,text);if(v!=null&&v.isShown()&&v.isEnabled())clicked[0]=v.performClick();});if(!clicked[0])Thread.sleep(100);}
        check(clicked[0],"Missing sharing action: "+text);waitForIdleSync();
    }
    private void accessibilityClick(String text)throws Exception {
        long until=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<until){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()==null?"":node.getText())&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return;Thread.sleep(100);}
        throw new AssertionError("Missing dialog action: "+text);
    }
    private void sharing()throws Exception {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Sharing fixtures require a disposable emulator");
        MainActivity app=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            Object before=field(app,"transport");
            NoteStore.Note note=new NoteStore.Note();note.title="Direct sharing review";note.body="Synthetic sharing fixture";app.store.save(note);
            app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("address","MxDIRECTTEST@127.0.0.1:9501").commit();
            runOnMainSync(()->app.shareDocument(note.id,note.title));DocumentSharingPane pane=(DocumentSharingPane)field(app,"documentSharing");android.view.View root=pane.getWindow().getDecorView();
            check(textView(root,"Enter recipient")!=null&&textView(root,"Scan recipient QR")!=null&&textView(root,"Parlons contacts")!=null,"Sharing methods missing");screen("sharing-methods-screen.png");
            tapText(root,"Link or QR code");tapText(root,"Can view");tapText(root,"Create invitation");tapText(root,"Copy invitation");
            String[] copied={null};runOnMainSync(()->{android.content.ClipboardManager board=(android.content.ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE);copied[0]=board.getPrimaryClip().getItemAt(0).coerceToText(app).toString();});
            Pairing.Said offer=Pairing.read(Pairing.line(copied[0]));check(offer.level==Sharing.Level.READ&&offer.target.equals(note.id)&&offer.scope.equals("PAGE"),"Copied invitation lost access or document");
            check(field(app,"transport")==before,"Direct sharing connected to Parlons");
            android.graphics.Bitmap qr=Qr.of(copied[0],900);int w=qr.getWidth(),h=qr.getHeight();byte[] luminance=new byte[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)luminance[y*w+x]=(byte)(qr.getPixel(x,y)&255);check(copied[0].equals(Qr.inside(luminance,w,h)),"Invitation QR failed round trip");qr.recycle();screen("sharing-qr-screen.png");
            tapText(root,"Invite people");tapText(root,"Enter recipient");accessibilityClick("Cancel");
            runOnMainSync(pane::dismiss);runOnMainSync(app::openDirectInvitation);accessibilityClick("Paste invitation");
            android.view.accessibility.AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();boolean entered=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:active.findAccessibilityNodeInfosByText("Invitation link or code")){Bundle args=new Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,copied[0]);entered=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args);if(entered)break;}
            check(entered,"Invitation input unavailable");accessibilityClick("Review invitation");
            long until=System.currentTimeMillis()+10000;boolean reviewed=false;while(!reviewed&&System.currentTimeMillis()<until){active=getUiAutomation().getRootInActiveWindow();reviewed=active!=null&&!active.findAccessibilityNodeInfosByText("It will appear in Shared").isEmpty();if(!reviewed)Thread.sleep(100);}
            check(reviewed,"Direct invitation did not reach acceptance review");screen("sharing-review-screen.png");getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        }finally{runOnMainSync(app::finish);waitForIdleSync();}
    }
    private void workspace()throws Exception {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Workspace fixtures require a disposable emulator");
        MainActivity app=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            check(field(app,"workspace")!=null,"Legacy launcher opened");screen("workspace-screen.png");
            runOnMainSync(()->{NoteStore.Note text=new NoteStore.Note();text.title="Manrope review";text.body="Manrope document proof.\nA second paragraph.";app.openWorkspaceDocument(text);});
            Object pane=field(app,"editor");android.webkit.WebView web=(android.webkit.WebView)field(pane,"web");
            long until=System.currentTimeMillis()+240000;while(System.currentTimeMillis()<until){if("true".equals(javascript(web,"!!document.getElementById('word-tools')")))break;Thread.sleep(500);}
            check("true".equals(javascript(web,"!!document.getElementById('word-tools')")),"Word toolbar never opened");
            check("true".equals(javascript(web,"Array.from(document.querySelectorAll('.word-tabs button')).map(b=>b.textContent).join(',')==='Home,Insert,Layout,Review,View'")),"Word sections missing");
            javascript(web,"document.querySelectorAll('.word-tabs button')[1].click();Array.from(document.querySelectorAll('.word-ribbon button')).find(b=>b.textContent==='Table').click();document.querySelector('#word-panel .primary').click();true");
            javascript(web,"document.querySelectorAll('.word-tabs button')[0].click();true");
            painted(web);screen("workspace-word-screen.png");
            javascript(web,"Array.from(document.querySelectorAll('.word-mobile button')).find(b=>b.textContent==='Format').click();true");
            painted(web);screen("workspace-format-screen.png");
            javascript(web,"document.querySelector('#word-panel .done').click();document.querySelectorAll('.word-tabs button')[0].click();Array.from(document.querySelectorAll('.word-ribbon button')).find(b=>b.textContent==='Find').click();document.querySelectorAll('#word-panel input')[0].value='proof';document.querySelectorAll('#word-panel input')[1].value='verified';Array.from(document.querySelectorAll('#word-panel button')).find(b=>b.textContent==='Replace all').click();document.querySelector('#word-panel .done').click();true");

            // The production bridge accepts a save only after the native save action.
            java.lang.reflect.Method save=pane.getClass().getDeclaredMethod("requestSave");save.setAccessible(true);runOnMainSync(()->{try{save.invoke(pane);}catch(Exception e){throw new RuntimeException(e);}});
            NoteStore.Held source=null;until=System.currentTimeMillis()+240000;
            while(System.currentTimeMillis()<until){source=(NoteStore.Held)field(pane,"source");if(source!=null)break;Thread.sleep(500);}
            check(source!=null,"Native document save failed");byte[] bytes=app.store.bytesOf(source);String xml=zipText(bytes,"word/document.xml");
            check(xml.contains("Manrope document verified.")&&xml.contains("A second paragraph."),"Text conversion lost content");check(xml.contains("Manrope"),"Default document font is not Manrope");check(xml.contains("w:tbl"),"Toolbar table command did not edit document");artifact("workspace-manrope.docx",bytes);
            runOnMainSync(()->((EditorPane)pane).dismiss());
            app.background.flush(15000);waitForIdleSync();Thread.sleep(300);screen("workspace-files-screen.png");
            org.json.JSONObject config=new org.json.JSONObject().put("kind","docx").put("name","Manrope.docx").put("base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
            EditorTestActivity.Session session=new EditorTestActivity.Session(config.toString());EditorTestActivity.session=session;
            EditorTestActivity proof=(EditorTestActivity)startActivitySync(new Intent(getTargetContext(),EditorTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try{check(session.ready.await(240,java.util.concurrent.TimeUnit.SECONDS),"Manrope reopen timeout");check(session.error==null,session.error);runOnMainSync(()->proof.web.evaluateJavascript("minimaDocsExport('PDF')",null));check(session.saved.await(240,java.util.concurrent.TimeUnit.SECONDS),"Manrope PDF timeout");check(session.error==null,session.error);check(session.bytes!=null&&session.bytes.length>8,"No Manrope PDF");artifact("workspace-manrope.pdf",session.bytes);}finally{runOnMainSync(proof::finish);}

        }finally{runOnMainSync(app::finish);waitForIdleSync();}
    }
    private static boolean sameJson(Object a,Object b)throws org.json.JSONException {
        if(a instanceof org.json.JSONObject&&b instanceof org.json.JSONObject){
            org.json.JSONObject x=(org.json.JSONObject)a,y=(org.json.JSONObject)b;if(x.length()!=y.length())return false;
            Iterator<String> keys=x.keys();while(keys.hasNext()){String k=keys.next();if(!y.has(k)||!sameJson(x.get(k),y.get(k)))return false;}return true;
        }
        if(a instanceof org.json.JSONArray&&b instanceof org.json.JSONArray){org.json.JSONArray x=(org.json.JSONArray)a,y=(org.json.JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!sameJson(x.get(i),y.get(i)))return false;return true;}
        if(a instanceof Number&&b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;
        return Objects.equals(a,b);
    }
    private String imageCheck(android.webkit.WebView web,String expression)throws Exception {
        javascript(web,"window.imageCheckResult=null;Promise.resolve().then(()=>{const w=document.getElementById('editor').contentWindow;return w.eval("+org.json.JSONObject.quote(expression)+");}).then(value=>{window.imageCheckResult={ok:true,value};},e=>{window.imageCheckResult={ok:false,error:String(e.stack||e)};});true");
        long until=System.currentTimeMillis()+120000;
        while(System.currentTimeMillis()<until){
            String result=javascript(web,"window.imageCheckResult");
            if(!"null".equals(result)){org.json.JSONObject parsed=new org.json.JSONObject(result);check(parsed.getBoolean("ok"),expression.substring(0,Math.min(80,expression.length()))+": "+parsed.optString("error"));return parsed.optString("value","");}
            Thread.sleep(100);
        }
        throw new AssertionError("Image check timed out: "+expression);
    }
    private String mobileCheck(android.webkit.WebView web,String expression)throws Exception {
        javascript(web,"window.mobileCheckResult=null;Promise.resolve().then(()=>eval("+org.json.JSONObject.quote(expression)+")).then(value=>{window.mobileCheckResult={ok:true,value};},e=>{window.mobileCheckResult={ok:false,error:String(e.stack||e)};});true");
        long until=System.currentTimeMillis()+180000;
        while(System.currentTimeMillis()<until){String result=javascript(web,"window.mobileCheckResult");if(!"null".equals(result)){org.json.JSONObject parsed=new org.json.JSONObject(result);check(parsed.getBoolean("ok"),parsed.optString("error"));return String.valueOf(parsed.opt("value"));}Thread.sleep(100);}
        throw new AssertionError("Mobile check timed out: "+expression);
    }
    /** Exercise the native import, not only a bootstrap mock or an already-saved PCraft file. */
    private void photoImports()throws Exception {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Photo fixtures require a disposable emulator");
        MainActivity app=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            int[][] sizes={{320,240},{1791,1083},{4032,3024},{1200,900}};
            String[] extensions={"png","jpg","jpg","webp"};
            for(int i=0;i<sizes.length;i++) {
                int width=sizes[i][0],height=sizes[i][1];String name="import-"+width+"."+extensions[i];
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(60,100,140));
                android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.rgb(230,180,100));canvas.drawRect(width/4f,height/4f,width*3/4f,height*3/4f,paint);
                File input=new File(app.getCacheDir(),name);
                try(FileOutputStream output=new FileOutputStream(input)) {bitmap.compress(i==0?android.graphics.Bitmap.CompressFormat.PNG:i==3?android.graphics.Bitmap.CompressFormat.WEBP:android.graphics.Bitmap.CompressFormat.JPEG,95,output);}finally{bitmap.recycle();}
                java.lang.reflect.Field kind=MainActivity.class.getDeclaredField("importingKind");kind.setAccessible(true);kind.set(app,"image");
                java.lang.reflect.Method importing=MainActivity.class.getDeclaredMethod("importDocument",android.net.Uri.class);importing.setAccessible(true);
                runOnMainSync(()->{try{importing.invoke(app,android.net.Uri.fromFile(input));}catch(Exception e){throw new RuntimeException(e);}});
                EditorPane pane=null;long deadline=System.currentTimeMillis()+180000;
                while(System.currentTimeMillis()<deadline){pane=(EditorPane)field(app,"editor");if(pane!=null&&Boolean.TRUE.equals(field(pane,"ready")))break;Thread.sleep(100);}
                check(pane!=null&&Boolean.TRUE.equals(field(pane,"ready")),name+": native import not ready");
                android.webkit.WebView web=(android.webkit.WebView)field(pane,"web");
                String source="document.querySelector('#editor').contentWindow.photoCraft";
                String state=mobileCheck(web,"(async()=>{const s=(await "+source+".request('mobile.state')).value;return {width:s.width,height:s.height};})()");
                org.json.JSONObject dimensions=new org.json.JSONObject(state);check(dimensions.getInt("width")==width&&dimensions.getInt("height")==height,name+": import size changed");
                // Wait for a completed frame after the final UI layout and the queued Look previews.
                mobileCheck(web,"(async()=>{await "+source+".request('mobile.state');await new Promise(r=>setTimeout(r,1000));return true;})()");
                painted(web);screen(name+"-screen.png");
                org.json.JSONObject point=new org.json.JSONObject(mobileCheck(web,"(()=>{const r=document.querySelector('#stage').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()"));
                int[] location=new int[2];int[] webWidth={0};runOnMainSync(()->{web.getLocationOnScreen(location);webWidth[0]=web.getWidth();});
                double scale=webWidth[0]/Double.parseDouble(javascript(web,"innerWidth"));
                android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();check(screenshot!=null,"No photo screenshot");
                int color;try{color=screenshot.getPixel(location[0]+(int)(point.getDouble("x")*scale),location[1]+(int)(point.getDouble("y")*scale));}finally{screenshot.recycle();}
                check(Math.abs(android.graphics.Color.red(color)-230)<35&&Math.abs(android.graphics.Color.green(color)-180)<35&&Math.abs(android.graphics.Color.blue(color)-100)<35,name+": imported photo missing from canvas, center pixel="+Integer.toHexString(color));
                EditorPane closing=pane;runOnMainSync(closing::dismiss);waitForIdleSync();input.delete();
            }
        }finally{runOnMainSync(app::finish);waitForIdleSync();}
    }
    private void mobilePhoto()throws Exception {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Photo fixtures require a disposable emulator");
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(320,240,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(60,100,140));android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.rgb(230,180,100));canvas.drawCircle(80,60,30,paint);
        ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
        String script;try(InputStream in=getContext().getAssets().open("photocraft-mobile-check.js")){script=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        byte[] project=null;String expected=null;
        for(int round=0;round<2;round++){
            org.json.JSONObject config=new org.json.JSONObject().put("kind","image").put("photocraft",true).put("name",round==0?"photo.png":"photo.pcraft").put("base64",android.util.Base64.encodeToString(round==0?out.toByteArray():project,android.util.Base64.NO_WRAP));
            EditorTestActivity.Session session=new EditorTestActivity.Session(config.toString());EditorTestActivity.session=session;
            EditorTestActivity activity=(EditorTestActivity)startActivitySync(new Intent(getTargetContext(),EditorTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try{
                check(session.ready.await(180,java.util.concurrent.TimeUnit.SECONDS),"PhotoCraft open timed out");check(session.error==null,session.error);mobileCheck(activity.web,script);
                if(round==0){
                    mobileCheck(activity.web,"MobilePhotoCheck.prepare()");
                    org.json.JSONObject point=new org.json.JSONObject(mobileCheck(activity.web,"MobilePhotoCheck.point()"));strokeAt(activity.web,point);
                    expected=mobileCheck(activity.web,"MobilePhotoCheck.afterMask()");mobileCheck(activity.web,"MobilePhotoCheck.layout()");painted(activity.web);screen("photocraft-mobile-screen.png");
                    javascript(activity.web,"minimaDocsSave();true");check(session.saved.await(180,java.util.concurrent.TimeUnit.SECONDS),"PhotoCraft save timeout");check(session.error==null,session.error);project=session.bytes;check(DocumentStore.photocraft("image",project),"Missing native PCraft snapshot");artifact("photocraft-mobile.pcraft",project);
                    Context context=isolated("mobile-photo");try(NoteStore store=new NoteStore(context)){DocumentStore.Saved saved=DocumentStore.save(context,store,UUID.randomUUID().toString(),"Mobile photo",RichDocument.empty("image"),project);check(saved.file.name.endsWith(".pcraft"),"Stored project extension wrong");check(Arrays.equals(project,store.bytesOf(saved.file)),"Native storage changed project bytes");}
                }else{
                    mobileCheck(activity.web,"MobilePhotoCheck.reopened("+expected+")");javascript(activity.web,"minimaDocsExport('PNG');true");check(session.saved.await(180,java.util.concurrent.TimeUnit.SECONDS),"PhotoCraft PNG timeout");check(session.error==null,session.error);android.graphics.Bitmap png=android.graphics.BitmapFactory.decodeByteArray(session.bytes,0,session.bytes.length);check(png!=null&&png.getWidth()==240&&png.getHeight()==240,"Wrong PNG export");png.recycle();artifact("photocraft-mobile.png",session.bytes);
                }
            }finally{runOnMainSync(activity::finish);waitForIdleSync();}
        }
        MainActivity app=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{DocumentStore.Saved nativeInput=DocumentStore.save(app,app.store,UUID.randomUUID().toString(),"Mobile photo",RichDocument.empty("image"),project);
            runOnMainSync(()->app.editDocument("image",nativeInput.file));EditorPane pane=(EditorPane)field(app,"editor");android.webkit.WebView web=(android.webkit.WebView)field(pane,"web");
            long deadline=System.currentTimeMillis()+180000;while(System.currentTimeMillis()<deadline){if("true".equals(javascript(web,"!!document.querySelector('.tool-grid')")))break;Thread.sleep(100);}
            mobileCheck(web,script);mobileCheck(web,"(async()=>{MobilePhotoCheck.click('Tune image');await MobilePhotoCheck.wait(()=>document.querySelector('#range-brightness'),'No native tool sheet');await MobilePhotoCheck.input('brightness',12);await MobilePhotoCheck.apply();return true;})()");mobileCheck(web,"MobilePhotoCheck.layout()");painted(web);screen("photocraft-native-screen.png");check(textView(pane.getWindow().getDecorView(),"Save")!=null,"Native Save missing");
            runOnMainSync(()->textView(pane.getWindow().getDecorView(),"Save").performClick());
            long savedBy=System.currentTimeMillis()+180000;while(System.currentTimeMillis()<savedBy){if(field(pane,"source")!=null&&!((NoteStore.Held)field(pane,"source")).id.equals(nativeInput.file.id)&&!Boolean.TRUE.equals(field(pane,"saving")))break;Thread.sleep(100);}
            NoteStore.Held nativeSaved=(NoteStore.Held)field(pane,"source");check(nativeSaved!=null&&!nativeSaved.id.equals(nativeInput.file.id)&&nativeSaved.name.endsWith(".pcraft"),"Native save/ack did not persist PCraft");check(DocumentStore.photocraft("image",app.store.bytesOf(nativeSaved)),"Native saved image invalid");
            runOnMainSync(pane::dismiss);
        }finally{runOnMainSync(app::finish);waitForIdleSync();}
    }
    private void strokeAt(android.webkit.WebView web,org.json.JSONObject point)throws Exception {
        int[] location=new int[2];int[] width={0};runOnMainSync(()->{web.getLocationOnScreen(location);width[0]=web.getWidth();});
        float scale=(float)(width[0]/Double.parseDouble(javascript(web,"innerWidth"))),x=location[0]+(float)point.getDouble("x")*scale,y=location[1]+(float)point.getDouble("y")*scale;
        long down=android.os.SystemClock.uptimeMillis();for(int i=0;i<8;i++){int action=i==0?0:i==7?1:2;android.view.MotionEvent e=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x+i*3,y+i,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);try{sendPointerSync(e);}finally{e.recycle();}Thread.sleep(50);}waitForIdleSync();
    }
    private void imageStroke(android.webkit.WebView web)throws Exception {
        org.json.JSONObject point=new org.json.JSONObject(imageCheck(web,"ImageStudioCheck.point()"));int[] location=new int[2];int[] width={0};
        runOnMainSync(()->{web.getLocationOnScreen(location);width[0]=web.getWidth();});
        double cssWidth=Double.parseDouble(javascript(web,"innerWidth"));float scale=(float)(width[0]/cssWidth);
        float x=location[0]+(float)point.getDouble("x")*scale,y=location[1]+(float)point.getDouble("y")*scale;
        long down=android.os.SystemClock.uptimeMillis();
        for(int i=0;i<6;i++){
            int action=i==0?android.view.MotionEvent.ACTION_DOWN:i==5?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x+i*2,y+i,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try{sendPointerSync(event);}finally{event.recycle();}Thread.sleep(40);
        }
        waitForIdleSync();
    }
    private void imageStudio()throws Exception {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Image fixtures require a disposable emulator");
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(320,240,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(60,100,140));android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.rgb(230,180,100));canvas.drawCircle(80,60,30,paint);
        ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
        String script;try(InputStream in=getContext().getAssets().open("image-studio-check.js")){script=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        byte[] project=null;org.json.JSONObject expected=null;
        for(int round=0;round<2;round++){
            org.json.JSONObject config=new org.json.JSONObject().put("kind","image").put("name",round==0?"phone.png":"phone.minimadocs-image.json").put("base64",android.util.Base64.encodeToString(round==0?out.toByteArray():project,android.util.Base64.NO_WRAP));
            EditorTestActivity.Session session=new EditorTestActivity.Session(config.toString());EditorTestActivity.session=session;
            EditorTestActivity activity=(EditorTestActivity)startActivitySync(new Intent(getTargetContext(),EditorTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try{
                check(session.ready.await(120,java.util.concurrent.TimeUnit.SECONDS),"Image Studio open timed out");check(session.error==null,session.error);imageCheck(activity.web,script);
                if(round==0){
                    imageCheck(activity.web,"ImageStudioCheck.prepare()");imageStroke(activity.web);imageCheck(activity.web,"ImageStudioCheck.afterMask()");imageStroke(activity.web);imageCheck(activity.web,"ImageStudioCheck.afterBrush()");painted(activity.web);screen("image-studio-effects-screen.png");
                    imageCheck(activity.web,"ImageStudioCheck.apply()");painted(activity.web);screen("image-studio-phone-screen.png");imageCheck(activity.web,"document.querySelector('.studio-catbar button[aria-controls=studio-slot-layers]').click();true");painted(activity.web);screen("image-studio-layers-screen.png");
                    javascript(activity.web,"minimaDocsSave();true");check(session.saved.await(120,java.util.concurrent.TimeUnit.SECONDS),"Image project save timeout");check(session.error==null,session.error);project=session.bytes;check(project!=null,"No image project");expected=new org.json.JSONObject(new String(project,java.nio.charset.StandardCharsets.UTF_8));artifact("image-studio.minimadocs-image.json",project);
                }else{
                    org.json.JSONObject reopened=new org.json.JSONObject(imageCheck(activity.web,"ImageStudioCheck.reopened()"));artifact("image-studio-reopened.json",reopened.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));check(sameJson(reopened.getJSONArray("data"),expected.getJSONArray("data")),"Image bytes changed after reopen");check(sameJson(reopened.getJSONArray("layers"),expected.getJSONArray("layers")),"Layer properties changed after reopen");
                    javascript(activity.web,"minimaDocsExport('PNG');true");check(session.saved.await(120,java.util.concurrent.TimeUnit.SECONDS),"PNG export timeout");check(session.error==null,session.error);android.graphics.Bitmap png=android.graphics.BitmapFactory.decodeByteArray(session.bytes,0,session.bytes.length);check(png!=null&&png.getWidth()==320&&png.getHeight()==240,"PNG export dimensions wrong");png.recycle();artifact("image-studio.png",session.bytes);
                }
            }finally{runOnMainSync(activity::finish);waitForIdleSync();}
        }
        MainActivity app=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            DocumentStore.Saved legacy=DocumentStore.save(app,app.store,UUID.randomUUID().toString(),"Legacy image fixture",RichDocument.empty("image"),project);
            runOnMainSync(()->app.editDocument("image",legacy.file));EditorPane pane=(EditorPane)field(app,"editor");android.webkit.WebView web=(android.webkit.WebView)field(pane,"web");
            long until=System.currentTimeMillis()+120000;while(System.currentTimeMillis()<until){if("true".equals(javascript(web,"!!document.getElementById('editor')?.contentWindow.document.querySelector('.studio-catbar')")))break;Thread.sleep(100);}
            imageCheck(web,"ImageStudio.ready.then(()=>{document.querySelector('.studio-catbar button[aria-controls=studio-slot-layers]').click();return true;})");painted(web);screen("image-studio-native-screen.png");
            check(textView(pane.getWindow().getDecorView(),"Save")!=null,"Native Save unavailable");
            imageCheck(web,"(()=>{const c=document.querySelector('#canvas_minipaint').getBoundingClientRect(),bar=document.querySelector('.studio-catbar').getBoundingClientRect();if(c.height<30||bar.bottom>innerHeight+1)throw Error('Native host clips phone workspace');return true;})()");runOnMainSync(pane::dismiss);
        }finally{runOnMainSync(app::finish);waitForIdleSync();}
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
                artifact("saved."+(kind.equals("image")?"minimadocs-image.json":kind),session.bytes);
                java.util.concurrent.CountDownLatch painted=new java.util.concurrent.CountDownLatch(1);
                runOnMainSync(()->activity.web.postVisualStateCallback(1,new android.webkit.WebView.VisualStateCallback(){
                    @Override public void onComplete(long id){activity.web.postOnAnimation(()->activity.web.postOnAnimation(painted::countDown));}
                }));
                check(painted.await(30,java.util.concurrent.TimeUnit.SECONDS),"Editor did not render a frame");
                waitForIdleSync();
                if(!kind.equals("image")){
                    java.util.concurrent.CountDownLatch inspected=new java.util.concurrent.CountDownLatch(1);String[] theme={null};
                    runOnMainSync(()->activity.web.evaluateJavascript("JSON.stringify(Array.from(document.getElementById('editor').contentWindow.frames).map(w=>({url:w.location.pathname,classes:w.document.documentElement.className+' '+w.document.body.className,links:Array.from(w.document.querySelectorAll('link[rel=stylesheet]')).map(x=>({href:x.getAttribute('href'),loaded:!!x.sheet})),color:w.getComputedStyle(w.document.documentElement).getPropertyValue('--toolbar-header-document'),header:w.document.querySelector('.header-background-color')&&w.getComputedStyle(w.document.querySelector('.header-background-color')).backgroundColor})))",value->{theme[0]=value;inspected.countDown();}));
                    check(inspected.await(10,java.util.concurrent.TimeUnit.SECONDS),"Could not inspect editor theme");
                    artifact(kind+"-theme.json",theme[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                android.graphics.Bitmap screen=getUiAutomation().takeScreenshot();
                if(screen!=null){try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);artifact(kind+"-screen.png",out.toByteArray());
                }finally{screen.recycle();}}
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
                artifact(kind+(kind.equals("image")?".png":".pdf"),session.bytes);
            }finally{runOnMainSync(activity::finish);waitForIdleSync();}
        }
    }
    private void artifact(String name,byte[] bytes)throws IOException {
        File root=new File(getTargetContext().getCacheDir(),"editor-checks");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot keep device check output");
        Files.write(new File(root,name).toPath(),bytes);
    }
    private static String zipText(byte[] bytes,String path)throws Exception {
        try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals(path)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");
            }
        }throw new AssertionError("Missing "+path);
    }

}
