package org.mininotes.android;

import android.content.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Small invitations only. The existing device Keystore sealer protects them even while the notebook is locked. */
public final class ParlonsInbox extends BroadcastReceiver {
    private static final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final long WEEK=7L*24*60*60*1000;
    static volatile Runnable changed;
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!(MaximaConnection.TRANSPORT+".DELIVER").equals(intent.getAction())
                ||!MaximaConnection.APPLICATION.equals(intent.getStringExtra("application"))
                ||!context.getSharedPreferences("parlons",Context.MODE_PRIVATE).getBoolean("connected",false))return;
        final String wire=intent.getStringExtra("data"),from=intent.getStringExtra("from");
        if(!ParlonsContact.key(from)||wire==null||wire.length()>Pairing.MOST*8+2)return;
        final PendingResult pending=goAsync();final Context app=context.getApplicationContext();
        worker.execute(()->{try{keep(app,from,ParlonsContact.invitation(wire));Runnable listener=changed;if(listener!=null)listener.run();}
            catch(Exception invalid){/* Untrusted or unwritable invitations do not alter the notebook. */}finally{pending.finish();}});
    }
    static final class Invitation {
        final String id,from,line;
        Invitation(String id,String from,String line){this.id=id;this.from=from;this.line=line;}
    }
    private static File dir(Context c)throws IOException{
        File dir=new File(c.getFilesDir(),"parlons-invitations");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot save invitation");return dir;
    }
    static synchronized void keep(Context c,String from,String line)throws Exception {
        Keys.publicKey(Pairing.read(line).agreement);Keys.publicKey(Pairing.read(line).signing);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((from+"\n"+line).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));String id=hex.toString();
        File root=dir(c),target=new File(root,id);if(target.exists())return;
        File[] files=root.listFiles();int count=0;
        if(files!=null)for(File f:files){if(System.currentTimeMillis()-f.lastModified()>WEEK)f.delete();else count++;}
        if(count>=64)return;
        byte[] raw=new JSONObject().put("from",from).put("line",line).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sealed=Keys.of(c).seal(raw);
        File temp=new File(root,id+".new");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(sealed);out.getFD().sync();}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Cannot save invitation");}
    }
    static synchronized List<Invitation> list(Context c)throws Exception {
        List<Invitation> out=new ArrayList<>();if(!PhoneLock.open(c))return out;
        File[] files=dir(c).listFiles();if(files==null)return out;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
        for(File file:files){
            if(System.currentTimeMillis()-file.lastModified()>WEEK){file.delete();continue;}
            if(!file.getName().matches("[0-9a-f]{64}")||file.length()==0||file.length()>32768)continue;
            try{JSONObject json=new JSONObject(new String(Keys.of(c).unseal(Files.readAllBytes(file.toPath())),java.nio.charset.StandardCharsets.UTF_8));
                out.add(new Invitation(file.getName(),json.getString("from"),ParlonsContact.invitation(json.getString("line"))));}
            catch(Exception invalid){/* A damaged item never becomes a pairing. */}
        }return out;
    }
    static synchronized void dismiss(Context c,String id)throws Exception {
        if(!id.matches("[0-9a-f]{64}"))return;
        // Keep a bounded, empty tombstone for the replay window.
        try(FileOutputStream out=new FileOutputStream(new File(dir(c),id))){out.getFD().sync();}
    }
}
