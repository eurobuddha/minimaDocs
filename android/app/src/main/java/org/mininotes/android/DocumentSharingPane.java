package org.mininotes.android;

import android.app.Dialog;
import android.view.Gravity;
import android.view.Window;
import android.widget.*;
import java.util.List;

/** Direct Maxima invitations and Parlons contacts share the same permission and transport paths. */
final class DocumentSharingPane extends Dialog {
    private final MainActivity app;private final String id,name;private final LinearLayout root,body;
    private PeoplePane people;private Sharing.Level selected=Sharing.Level.WRITE;private boolean closed;
    DocumentSharingPane(MainActivity app,String id,String name){
        super(app);this.app=app;this.id=id;this.name=name;requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=WorkspaceUi.column(app);root.setBackgroundColor(Design.PAPER());root.setPadding(app.dp(20),app.dp(16),app.dp(20),app.dp(16));
        LinearLayout header=new LinearLayout(app);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(WorkspaceUi.text(app,"Sharing",21,true),new LinearLayout.LayoutParams(0,-2,1));header.addView(WorkspaceUi.button(app,"Close",false,this::dismiss));root.addView(header);WorkspaceUi.gap(root,16);
        LinearLayout tabs=new LinearLayout(app);tabs.addView(WorkspaceUi.button(app,"Invite people",false,this::invitePeople),new LinearLayout.LayoutParams(0,app.dp(48),1));tabs.addView(WorkspaceUi.button(app,"Access & updates",false,this::showAccess),new LinearLayout.LayoutParams(0,app.dp(48),1));root.addView(tabs);WorkspaceUi.gap(root,14);
        body=WorkspaceUi.column(app);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        invitePeople();
    }
    @Override public void show(){super.show();getWindow().setLayout(-1,-1);getWindow().setBackgroundDrawable(Design.rect(Design.PAPER()));if(PhoneLock.locked(app))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);}
    private LinearLayout page(){
        if(people!=null){people.close();people=null;}body.removeAllViews();
        ScrollView scroll=new ScrollView(app);LinearLayout items=WorkspaceUi.column(app);scroll.addView(items);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return items;
    }
    private void invitePeople(){
        final int request=++accessRequest;LinearLayout items=page();
        items.addView(WorkspaceUi.text(app,"Share with anyone",26,true));WorkspaceUi.gap(items,8);
        items.addView(WorkspaceUi.note(app,"They need minimaDocs. Parlons is optional."));WorkspaceUi.gap(items,20);
        items.addView(WorkspaceUi.button(app,"Link or QR code",true,this::directInvitation));WorkspaceUi.gap(items,8);
        items.addView(WorkspaceUi.note(app,"Send an invitation in any messaging app, or let them scan your screen."));WorkspaceUi.gap(items,24);
        items.addView(WorkspaceUi.button(app,"Enter recipient",false,()->app.enterDocumentRecipient(id,name,this::showAccess,this::directInvitation)));WorkspaceUi.gap(items,8);
        items.addView(WorkspaceUi.button(app,"Scan recipient QR",false,()->app.scanDocumentRecipient(id,name,this::showAccess,this::directInvitation)));WorkspaceUi.gap(items,24);
        items.addView(WorkspaceUi.button(app,"Parlons contacts",false,this::parlonsPeople));WorkspaceUi.gap(items,24);
        LinearLayout known=WorkspaceUi.column(app);items.addView(known);
        app.background.submit(()->app.store.addresses(),contacts->{
            if(closed||request!=accessRequest)return;boolean heading=false;
            for(NoteStore.Contact contact:contacts)if(contact.paired()){
                if(!heading){known.addView(WorkspaceUi.text(app,"Previously paired",18,true));WorkspaceUi.gap(known,8);heading=true;}
                known.addView(WorkspaceUi.button(app,contact.name,false,()->app.shareWithPairedDocument(id,contact,this::showAccess)));WorkspaceUi.gap(known,8);
            }
        },e->{if(!closed&&request==accessRequest)known.addView(WorkspaceUi.note(app,"Saved contacts could not be loaded. You can still use an invitation."));});
    }
    private void parlonsPeople(){accessRequest++;if(people!=null)people.close();body.removeAllViews();people=new PeoplePane(app,app.parlonsConnection(),name,this::choose);body.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));people.refresh();}
    private void directInvitation(){
        final int request=++accessRequest;LinearLayout items=page();items.addView(WorkspaceUi.note(app,"Reading document permissions…"));
        app.background.submit(()->app.store.mayGive(Sharing.Scope.PAGE,id),levels->{
            if(closed||request!=accessRequest)return;
            if(!levels.contains(Sharing.Level.READ)&&!levels.contains(Sharing.Level.WRITE)){items.removeAllViews();items.addView(WorkspaceUi.note(app,"Only the document owner or an administrator can invite people."));return;}
            if(!levels.contains(selected)||selected==Sharing.Level.ADMIN)selected=levels.contains(Sharing.Level.WRITE)?Sharing.Level.WRITE:Sharing.Level.READ;
            directAccess(levels);
        },e->{if(!closed&&request==accessRequest){items.removeAllViews();items.addView(WorkspaceUi.note(app,"Could not read document permissions. Try again."));}});
    }
    private void directAccess(List<Sharing.Level> levels){
        final int request=++accessRequest;LinearLayout items=page();
        items.addView(WorkspaceUi.text(app,"Link or QR code",26,true));WorkspaceUi.gap(items,8);items.addView(WorkspaceUi.note(app,name));WorkspaceUi.gap(items,16);
        items.addView(WorkspaceUi.note(app,"Choose what the person you invite can do. They can accept in minimaDocs without Parlons."));WorkspaceUi.gap(items,16);
        for(Sharing.Level level:new Sharing.Level[]{Sharing.Level.READ,Sharing.Level.WRITE})if(levels.contains(level)){
            TextView option=WorkspaceUi.button(app,role(level)+(selected==level?" · Selected":""),selected==level,()->{selected=level;directAccess(levels);});items.addView(option);WorkspaceUi.gap(items,8);
        }
        WorkspaceUi.gap(items,8);TextView state=WorkspaceUi.note(app,"");items.addView(state);
        TextView create=WorkspaceUi.button(app,"Create invitation",true,()->{});items.addView(create);
        LinearLayout result=WorkspaceUi.column(app);items.addView(result);
        create.setOnClickListener(v->{
            create.setEnabled(false);state.setText("Preparing your Maxima invitation…");final Sharing.Level level=selected;
            app.prepareDocumentInvitation(id,name,level,line->{
                if(closed||request!=accessRequest)return;
                items.removeAllViews();items.addView(WorkspaceUi.text(app,"Your invitation",26,true));WorkspaceUi.gap(items,8);items.addView(WorkspaceUi.note(app,name+" · "+role(level)));WorkspaceUi.gap(items,16);items.addView(result);
                LinearLayout actions=new LinearLayout(app);actions.addView(WorkspaceUi.button(app,"Share invitation",true,()->app.sendDirectInvitation(line)),new LinearLayout.LayoutParams(0,-2,1));actions.addView(WorkspaceUi.button(app,"Copy invitation",false,()->app.copyDirectInvitation(line)),new LinearLayout.LayoutParams(0,-2,1));result.addView(actions);WorkspaceUi.gap(result,16);
                android.view.View qr=app.codeView(line);LinearLayout.LayoutParams square=new LinearLayout.LayoutParams(Math.min(app.dp(320),Math.max(app.dp(200),body.getWidth())), -2);square.gravity=Gravity.CENTER_HORIZONTAL;result.addView(qr,square);WorkspaceUi.gap(result,12);
                result.addView(WorkspaceUi.note(app,"On their phone: Shared → Open invitation → Scan QR code or Paste invitation.\n\nAnyone with this invitation can join at the selected access level for 15 minutes. After that, you are asked to approve them."));
                WorkspaceUi.gap(result,12);result.addView(WorkspaceUi.button(app,"Change access",false,()->directAccess(levels)));
                if(Node.onlyMine(app))result.addView(WorkspaceUi.note(app,"Your connection is limited to the same Wi-Fi. Open Maxima connection settings to enable relays for sharing over the internet."));
            },why->{if(!closed&&request==accessRequest){state.setText(why);create.setEnabled(true);}});
        });
    }
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
        final int request=++accessRequest;
        app.background.submit(()->app.store.mayGive(Sharing.Scope.PAGE,id),levels->{if(closed||request!=accessRequest)return;if(!levels.contains(Sharing.Level.READ)&&!levels.contains(Sharing.Level.WRITE)){app.alert("Only the document owner or an administrator can invite people.");return;}if(!levels.contains(selected)||selected==Sharing.Level.ADMIN)selected=levels.contains(Sharing.Level.WRITE)?Sharing.Level.WRITE:Sharing.Level.READ;access(contact,levels);},e->{if(!closed&&request==accessRequest)app.alert("Could not read document permissions.");});
    }
    private void access(ParlonsContact contact,List<Sharing.Level> levels){
        accessRequest++;
        if(people!=null)people.close();body.removeAllViews();body.addView(WorkspaceUi.note(app,name));WorkspaceUi.gap(body,24);body.addView(WorkspaceUi.text(app,"Share with "+contact.name,26,true));WorkspaceUi.gap(body,24);
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
