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
        body=WorkspaceUi.column(app);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        people=new PeoplePane(app,app.parlonsConnection(),name,this::choose);body.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));
    }
    @Override public void show(){super.show();getWindow().setLayout(-1,-1);getWindow().setBackgroundDrawable(Design.rect(Design.PAPER()));if(PhoneLock.locked(app))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);people.refresh();}
    private void choose(ParlonsContact contact){
        app.background.submit(()->app.store.mayGive(Sharing.Scope.PAGE,id),levels->{if(closed)return;if(levels.isEmpty()){app.alert("Only the document owner or an administrator can invite people.");return;}if(!levels.contains(selected))selected=levels.get(0);access(contact,levels);},e->app.alert("Could not read document permissions."));
    }
    private void access(ParlonsContact contact,List<Sharing.Level> levels){
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
