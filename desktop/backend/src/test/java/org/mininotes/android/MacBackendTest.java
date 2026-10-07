package org.mininotes.android;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class MacBackendTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 static final byte[] KEY=new byte[32];
 @BeforeClass public static void key(){Arrays.fill(KEY,(byte)7);SecretBox.unlock(KEY);}
 private JSONObject fresh(MacBackend b,String kind)throws Exception{return b.call("open",new JSONObject().put("kind",kind).put("title","Synthetic test"));}
 private JSONObject save(MacBackend b,JSONObject view,String text)throws Exception{return b.call("save",new JSONObject().put("session",view.getString("session")).put("title","Synthetic test").put("base64",Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8))));}
 @Test public void documentBytesReopenExactlyAndAreEncryptedOnDisk()throws Exception {
  Path dir=temp.newFolder().toPath();String id;
  try(MacBackend b=new MacBackend(dir,KEY,true)){var v=fresh(b,"docx");id=save(b,v,"Synthetic document bytes 日本語").getString("id");assertEquals(1,b.call("list",new JSONObject()).getJSONArray("documents").length());}
  try(MacBackend b=new MacBackend(dir,KEY,true)){var v=b.call("open",new JSONObject().put("id",id));assertEquals("Synthetic document bytes 日本語",new String(Base64.getDecoder().decode(v.getString("base64")),StandardCharsets.UTF_8));}
  try(var files=Files.walk(dir)){for(Path p:files.filter(Files::isRegularFile).toList())assertFalse(new String(Files.readAllBytes(p),StandardCharsets.ISO_8859_1).contains("Synthetic document bytes"));}
 }
 @Test public void failedSaveKeepsThePreviousSnapshot()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"xlsx");String id=save(b,v,"first").getString("id");assertThrows(Exception.class,()->save(b,v,""));var doc=RichDocument.read(b.store.get(id).body);assertEquals(1,doc.heads.size());assertEquals("first",new String(b.store.bytesOf(b.store.keptFile(doc.heads.values().iterator().next()))));}
 }
 @Test public void archivedOrDeletedDocumentsCannotBeOverwritten()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"docx");String id=save(b,v,"kept").getString("id");var n=b.store.get(id);n.archived=true;b.store.save(n);assertThrows(Exception.class,()->save(b,v,"replace"));}
 }
 @Test public void staleSessionCannotSaveAfterRelease()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"docx");b.call("release",new JSONObject().put("session",v.getString("session")));assertThrows(Exception.class,()->save(b,v,"late"));}
 }
 @Test public void oneActiveEditorPerDocumentPreventsLocalStaleWrites()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"docx");String id=save(b,v,"kept").getString("id");assertThrows(Exception.class,()->b.call("open",new JSONObject().put("id",id)));b.call("release",new JSONObject().put("session",v.getString("session")));assertEquals(id,b.call("open",new JSONObject().put("id",id)).getString("id"));}
 }
 @Test public void separateCopyPreservesTheOriginal()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"image");String first=save(b,v,"original").getString("id");b.call("copy",new JSONObject().put("session",v.getString("session")));String second=save(b,v,"copy").getString("id");assertNotEquals(first,second);assertEquals(2,b.call("list",new JSONObject()).getJSONArray("documents").length());}
 }
 @Test public void accessChangesReusePermissionChecksAndKeepRevocations()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){
   String id=save(b,fresh(b,"docx"),"kept").getString("id"),peer="synthetic-peer";
   b.store.give(Sharing.Scope.PAGE,id,peer,Sharing.Level.WRITE,null);
   JSONObject request=new JSONObject().put("id",id).put("address",peer).put("level","READ");
   b.call("manage",request);assertEquals("READ",b.call("access",new JSONObject().put("id",id)).getJSONArray("people").getJSONObject(0).getString("level"));
   var n=b.store.get(id);n.theirs=true;n.origin="synthetic-owner";b.store.save(n);
   assertThrows(Exception.class,()->b.call("manage",request.put("level","GONE")));
   n.theirs=false;n.origin="";b.store.save(n);b.call("manage",request);
   assertEquals(Sharing.Level.GONE,b.store.membership(Sharing.Scope.PAGE,id).get(0).level);
   assertEquals(0,b.call("access",new JSONObject().put("id",id)).getJSONArray("people").length());
  }
 }
 @Test public void concurrentRemoteHeadsSurviveAndOpenReadOnly()throws Exception {
  try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var v=fresh(b,"docx");String id=save(b,v,"first").getString("id");var n=b.store.get(id);var initial=RichDocument.read(n.body);String actor=UUID.randomUUID().toString(),file=UUID.randomUUID().toString();var other=RichDocument.empty("docx").write(actor,file,0);n.body=initial.merge(other).text();b.store.save(n);b.call("release",new JSONObject().put("session",v.getString("session")));var opened=b.call("open",new JSONObject().put("id",id).put("file",initial.heads.values().iterator().next()));assertTrue(opened.getBoolean("readonly"));assertTrue(opened.getBoolean("conflict"));assertThrows(Exception.class,()->save(b,opened,"must not merge blindly"));assertEquals(2,RichDocument.read(b.store.get(id).body).heads.size());}
 }
 @Test public void malformedAndOversizedFileDataAreRejected(){assertThrows(Exception.class,()->MacBackend.decode("!!"));assertThrows(Exception.class,()->MacBackend.decode(""));assertThrows(Exception.class,()->MacBackend.decode("A".repeat(24*1024*1024)));}
 @Test public void unsupportedDocumentKindsAreRejected()throws Exception {try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){assertThrows(Exception.class,()->fresh(b,"html"));}}
 @Test public void protectionAuthenticatesCiphertext() {byte[] protectedBytes=SecretBox.cryptProtectData("synthetic secret".getBytes());assertArrayEquals("synthetic secret".getBytes(),SecretBox.cryptUnprotectData(protectedBytes));protectedBytes[protectedBytes.length-1]^=1;assertThrows(IllegalStateException.class,()->SecretBox.cryptUnprotectData(protectedBytes));}
 @Test public void storageTransactionStateTracksCommitAndRollback()throws Exception {try(MacBackend b=new MacBackend(temp.newFolder().toPath(),KEY,true)){var db=b.store.getWritableDatabase();assertFalse(db.inTransaction());db.beginTransaction();assertTrue(db.inTransaction());db.endTransaction();assertFalse(db.inTransaction());}}
}
