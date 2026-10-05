package org.mininotes.android;

import android.app.Dialog;
import android.view.Gravity;
import android.view.Window;
import android.widget.*;
import java.util.List;

/** Select a real Parlons contact first, then explicitly grant document access. */
final class DocumentSharingPane extends Dialog {
    private final MainActivity app;private final String id,name;private final LinearLayout root,body;
    private PeoplePane people;private Sharing.Level selected=Sharing.Level.WRITE;private boolean closed;
    DocumentSharingPane(MainActivity app,String id,String name){
        super(app);this.app=app;this.id=id;this.name=name;requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=WorkspaceUi.column(app);root.setBackgroundColor(Design.PAPER());root.setPadding(app.dp(20),app.dp(16),app.dp(20),app.dp(16));
        LinearLayout header=new LinearLayout(app);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(WorkspaceUi.text(app,"Sharing",21,true),new LinearLayout.LayoutParams(0,-2,1));header.addView(WorkspaceUi.button(app,"Close",false,this::dismiss));root.addView(header);WorkspaceUi.gap(root,16);
        LinearLayout tabs=new LinearLayout(app);tabs.addView(WorkspaceUi.button(app,"Invite people",false,this::invitePeople),new LinearLayout.LayoutParams(0,app.dp(48),1));tabs.addView(WorkspaceUi.button(app,"Access & updates",false,this::showAccess),new LinearLayout.LayoutParams(0,app.dp(48),1));root.addView(tabs);WorkspaceUi.gap(root,14);
        body=WorkspaceUi.column(app);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        people=new PeoplePane(app,app.parlonsConnection(),name,this::choose);body.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));
    }
    @Override public void show(){super.show();getWindow().setLayout(-1,-1);getWindow().setBackgroundDrawable(Design.rect(Design.PAPER()));if(PhoneLock.locked(app))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);people.refresh();}
    private void invitePeople(){accessRequest++;if(people!=null)people.close();body.removeAllViews();people=new PeoplePane(app,app.parlonsConnection(),name,this::choose);body.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));people.refresh();}
    private static final class Access {
        java.util.List<Sharing.Rule> rules;java.util.List<NoteStore.Contact> contacts;java.util.Map<String,Boolean> reaches;java.util.Set<String> change=new java.util.HashSet<>();boolean owner;Sharing.Level level;int pending;RichDocument document;
    }
    private int accessRequest;
    private void showAccess(){
        if(closed)return;
        if(people!=null)people.close();body.removeAllViews();body.addView(WorkspaceUi.note(app,"Reading document access…"));final int request=++accessRequest;
        app.background.submit(()->{Access a=new Access();a.rules=app.store.sharesOn(Sharing.Scope.PAGE,id);a.contacts=app.store.addresses();a.reaches=app.store.reaches(Sharing.Scope.PAGE,id);a.owner=app.store.owns(Sharing.Scope.PAGE,id);a.level=app.store.myLevel(Sharing.Scope.PAGE,id);a.pending=app.store.owed(NoteStore.Branch.Kind.PAGE,id).size();NoteStore.Note note=app.store.get(id);a.document=note==null?null:RichDocument.read(note.body);for(Sharing.Rule rule:a.rules)if(app.store.mayChange(rule))a.change.add(rule.address);return a;},a->{
            if(closed||request!=accessRequest)return;body.removeAllViews();ScrollView scroll=new ScrollView(app);LinearLayout items=WorkspaceUi.column(app);scroll.addView(items);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            items.addView(WorkspaceUi.text(app,name,22,true));WorkspaceUi.gap(items,18);items.addView(WorkspaceUi.text(app,"People with access",18,true));WorkspaceUi.gap(items,12);
            items.addView(WorkspaceUi.note(app,"You · "+(a.owner?"Owner":role(a.level))));
            java.util.Map<String,Sharing.Rule> rules=new java.util.HashMap<>();for(Sharing.Rule rule:a.rules)if(rule.level!=Sharing.Level.GONE)rules.put(rule.address,rule);
            for(NoteStore.Contact contact:a.contacts){Sharing.Rule rule=rules.get(contact.address);if(rule==null&&!a.reaches.containsKey(contact.address))continue;WorkspaceUi.gap(items,12);
                String description=contact.name+" · "+(rule==null?"Access through a folder":role(rule.level));
                if(rule!=null&&a.change.contains(contact.address))items.addView(WorkspaceUi.button(app,description+" · Manage",false,()->manage(contact,rule)));
                else items.addView(WorkspaceUi.note(app,description));
            }
            WorkspaceUi.gap(items,28);items.addView(WorkspaceUi.text(app,"Updates",18,true));WorkspaceUi.gap(items,10);items.addView(WorkspaceUi.note(app,"Saved on this device"));WorkspaceUi.gap(items,8);
            items.addView(WorkspaceUi.note(app,a.pending>0?"Updates are waiting to send. Your saved copy is available here.":"No queued document notices. File delivery may still be in progress."));WorkspaceUi.gap(items,12);
            items.addView(WorkspaceUi.button(app,"Send updates now",false,()->app.sendAfterSharing(Sharing.Scope.PAGE,id)));WorkspaceUi.gap(items,10);items.addView(WorkspaceUi.button(app,"Refresh status",false,this::showAccess));
            if(a.document!=null&&a.document.heads.size()>1){WorkspaceUi.gap(items,24);items.addView(WorkspaceUi.text(app,"Versions need review",18,true));items.addView(WorkspaceUi.note(app,"Concurrent edits have been kept separately. Close the editor and reopen this document to choose a version."));}
        },e->{if(!closed&&request==accessRequest){body.removeAllViews();body.addView(WorkspaceUi.note(app,"Could not read permissions. Close and try again."));}});
    }
    private static String role(Sharing.Level level){return level==Sharing.Level.WRITE?"Can edit":level==Sharing.Level.ADMIN?"Administrator":level==Sharing.Level.READ?"Can view":"No access";}
    private void manage(NoteStore.Contact contact,Sharing.Rule rule){new android.app.AlertDialog.Builder(app).setTitle(contact.name).setItems(new String[]{"Can view","Can edit","Remove access"},(d,which)->{
        Sharing.Level level=which==0?Sharing.Level.READ:which==1?Sharing.Level.WRITE:Sharing.Level.GONE;
        Runnable change=()->app.background.submit(()->{Sharing.Rule current=null;for(Sharing.Rule candidate:app.store.sharesOn(rule.scope,rule.target))if(candidate.address.equals(rule.address))current=candidate;if(current==null||!app.store.mayChange(current))throw new IllegalStateException("Your permission to manage this person has changed.");app.store.decide(current,level,System.currentTimeMillis());return null;},done->{showAccess();app.sendAfterSharing(Sharing.Scope.PAGE,id);},e->app.alert(e.getMessage()));
        if(level==Sharing.Level.GONE)new android.app.AlertDialog.Builder(app).setTitle("Remove access for "+contact.name+"?").setMessage("They will stop receiving updates. Copies already received stay on their device.").setNegativeButton("Cancel",null).setPositiveButton("Remove access",(box,w)->change.run()).show();else change.run();
    }).setNegativeButton("Cancel",null).show();}
    private void choose(ParlonsContact contact){
        app.background.submit(()->app.store.mayGive(Sharing.Scope.PAGE,id),levels->{if(closed)return;if(levels.isEmpty()){app.alert("Only the document owner or an administrator can invite people.");return;}if(!levels.contains(selected))selected=levels.get(0);access(contact,levels);},e->app.alert("Could not read document permissions."));
    }
    private void access(ParlonsContact contact,List<Sharing.Level> levels){
        accessRequest++;
        people.close();body.removeAllViews();body.addView(WorkspaceUi.note(app,name));WorkspaceUi.gap(body,24);body.addView(WorkspaceUi.text(app,"Share with "+contact.name,26,true));WorkspaceUi.gap(body,24);
        for(Sharing.Level level:levels){if(level==Sharing.Level.ADMIN)continue;String label=level==Sharing.Level.READ?"Can view":"Can edit";
            LinearLayout option=WorkspaceUi.column(app);option.setPadding(app.dp(16),app.dp(16),app.dp(16),app.dp(16));option.setBackground(WorkspaceUi.surface(app,selected==level?0xFFFBE8E1:Design.CARD(),selected==level?Design.ACCENT():Design.SOFT()));option.addView(WorkspaceUi.text(app,label,17,true));option.addView(WorkspaceUi.note(app,level==Sharing.Level.READ?"Read and download":"Change this document"));option.setOnClickListener(v->{selected=level;access(contact,levels);});option.setFocusable(true);option.setContentDescription(label+(selected==level?", selected":""));body.addView(option);WorkspaceUi.gap(body,12);
        }
        body.addView(WorkspaceUi.note(app,contact.name+" will receive an invitation in minimaDocs. They need minimaDocs connected to Parlons."));
        TextView state=WorkspaceUi.note(app,"");body.addView(state,new LinearLayout.LayoutParams(-1,0,1));
        TextView send=WorkspaceUi.button(app,"Send invitation",true,()->{});send.setOnClickListener(v->{send.setEnabled(false);state.setText("Preparing invitation…");
            app.prepareDocumentInvitation(id,name,selected,line->{if(closed)return;app.parlonsConnection().invite(contact,line,()->{if(!closed){state.setText("Invitation queued for "+contact.name+". Waiting for them to accept.");send.setText("Invitation queued");}},why->{if(!closed){state.setText(why);send.setEnabled(true);}});},why->{if(!closed){state.setText(why);send.setEnabled(true);}});
        });body.addView(send);WorkspaceUi.gap(body,10);body.addView(WorkspaceUi.button(app,"Choose another person",false,()->{body.removeAllViews();people=new PeoplePane(app,app.parlonsConnection(),name,this::choose);body.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));people.refresh();}));
    }
    @Override public void dismiss(){closed=true;if(people!=null)people.close();super.dismiss();}
}
