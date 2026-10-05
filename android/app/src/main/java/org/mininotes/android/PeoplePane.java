package org.mininotes.android;

import android.content.Intent;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Parlons' existing contact book surfaced as a first-class workspace page. */
final class PeoplePane {
    private final MainActivity app;private final MaximaConnection connection;private final Consumer<ParlonsContact> choose;
    private final LinearLayout root,rows,connectionActions;private final TextView status;private final EditText search;
    private List<ParlonsContact> contacts=new ArrayList<>();private List<ParlonsInbox.Invitation> invitations=new ArrayList<>();
    private boolean loading,closed;
    private final Runnable contactChange=this::refresh;
    private final Runnable inboxChange;
    PeoplePane(MainActivity app,MaximaConnection connection,String document,Consumer<ParlonsContact> choose){
        this.app=app;inboxChange=()->app.runOnUiThread(this::loadInvitations);this.connection=connection;this.choose=choose;root=WorkspaceUi.column(app);
        root.addView(WorkspaceUi.text(app,choose==null?"Your people":"Share with people",28,true));WorkspaceUi.gap(root,8);
        if(document!=null){root.addView(WorkspaceUi.note(app,document));WorkspaceUi.gap(root,8);}
        status=WorkspaceUi.note(app,"Connecting to Parlons…");status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);WorkspaceUi.gap(root,16);
        search=WorkspaceUi.search(app,"Find a Parlons contact");root.addView(search);WorkspaceUi.watch(search,this::render);WorkspaceUi.gap(root,12);
        connectionActions=WorkspaceUi.column(app);root.addView(connectionActions);
        ScrollView scroll=new ScrollView(app);rows=WorkspaceUi.column(app);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout foot=new LinearLayout(app);foot.addView(WorkspaceUi.button(app,"Open Parlons",false,this::openParlons),new LinearLayout.LayoutParams(0,app.dp(48),1));foot.addView(WorkspaceUi.button(app,"Refresh",false,this::refresh),new LinearLayout.LayoutParams(0,app.dp(48),1));root.addView(foot);
        if(choose==null){WorkspaceUi.gap(root,8);root.addView(WorkspaceUi.button(app,"Add a Parlons contact",false,this::add));}
        connection.watchContacts(contactChange);ParlonsInbox.watch(inboxChange);
    }
    LinearLayout view(){return root;}
    void refresh(){if(closed||loading)return;loading=true;status.setText("Refreshing Parlons contacts…");
        connection.connect(()->connection.contacts(found->{if(closed)return;loading=false;contacts=found;status.setText("Parlons connected · "+found.size()+" contacts");connectionActions.removeAllViews();render();loadInvitations();},this::failed),this::failed);
    }
    private void failed(String message){if(closed)return;loading=false;status.setText(message);connectionActions.removeAllViews();
        connectionActions.addView(WorkspaceUi.note(app,"1. Open Parlons\n2. Settings → Apps using Maxima → Approve minimaDocs\n3. Return here and refresh"));WorkspaceUi.gap(connectionActions,16);render();
    }
    private void openParlons(){Intent intent=app.getPackageManager().getLaunchIntentForPackage(MaximaConnection.TRANSPORT);if(intent!=null)app.startActivity(intent);else status.setText("Install Parlons to connect its contact book.");}
    private void loadInvitations(){if(closed)return;app.background.submit(()->ParlonsInbox.list(app),found->{if(!closed){invitations=found;render();}},e->{if(!closed)status.setText("Unlock minimaDocs to read invitations.");});}
    private void render(){if(closed)return;rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
        for(ParlonsContact contact:contacts){if(!contact.name.toLowerCase(Locale.ROOT).contains(query))continue;shown++;
            LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,app.dp(12),0,app.dp(12));
            TextView initials=WorkspaceUi.text(app,initials(contact.name),17,true);initials.setGravity(Gravity.CENTER);initials.setTextColor(Design.WHITE());initials.setBackground(WorkspaceUi.surface(app,0xFF526B68,0));row.addView(initials,new LinearLayout.LayoutParams(app.dp(48),app.dp(48)));
            LinearLayout words=WorkspaceUi.column(app);words.setPadding(app.dp(14),0,0,0);words.addView(WorkspaceUi.text(app,contact.name,16,true));words.addView(WorkspaceUi.note(app,"From Parlons"));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            row.setBackground(Design.ripple(Design.rect(Design.PAPER())));row.setFocusable(true);row.setContentDescription(contact.name+", Parlons contact");row.setOnClickListener(v->{if(choose!=null)choose.accept(contact);else manage(contact);});rows.addView(row);rows.addView(Design.softRule(app));
        }
        if(shown==0&&!loading){WorkspaceUi.gap(rows,18);rows.addView(WorkspaceUi.note(app,query.isEmpty()?"Your Parlons contacts will appear here once access is approved.":"No matching contacts."));}
        if(choose==null&&!invitations.isEmpty()){WorkspaceUi.gap(rows,24);rows.addView(WorkspaceUi.text(app,"Invitations",21,true));
            for(ParlonsInbox.Invitation invite:invitations){String name="Parlons contact";for(ParlonsContact c:contacts)if(c.key.equalsIgnoreCase(invite.from))name=c.name;
                final String sender=name;WorkspaceUi.gap(rows,8);rows.addView(WorkspaceUi.button(app,"Review invitation from "+sender,false,()->review(invite,sender)));}
        }
    }
    // Keep the existing ParlonsPane contact operations and permission checks.
    private void add(){
        EditText address=WorkspaceUi.search(app,"Parlons contact address");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(app).setTitle("Add contact to Parlons").setView(address).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->connection.connect(()->connection.addContact(address.getText().toString().trim(),()->{dialog.dismiss();refresh();app.toast("Introduction sent. The contact appears when they answer.");},why->address.setError(why)),why->address.setError(why)));
    }
    private void manage(ParlonsContact contact){
        new android.app.AlertDialog.Builder(app).setTitle(contact.name).setItems(new String[]{"Open Parlons","Copy Parlons address","Remove from Parlons"},(d,which)->{
            if(which==0)openParlons();else if(which==1){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Parlons address",contact.address));status.setText("Address copied");}
            else new android.app.AlertDialog.Builder(app).setTitle("Remove "+contact.name+" from Parlons?").setMessage("This removes the contact from Parlons for every linked app. Existing document access is managed separately under Share → Access & updates.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(box,w)->connection.removeContact(contact,this::refresh,this::failed)).show();
        }).setNegativeButton("Close",null).show();
    }
    private void review(ParlonsInbox.Invitation invitation,String sender){
        Pairing.Said offer=Pairing.read(invitation.line);
        new android.app.AlertDialog.Builder(app).setTitle(sender+" invited you").setMessage((offer.offer.isEmpty()?"Connect minimaDocs devices":offer.offer)+"\n\n"+offer.level.words()).setNegativeButton("Later",null).setNeutralButton("Dismiss",(d,w)->app.background.submit(()->{ParlonsInbox.dismiss(app,invitation.id);return null;},r->loadInvitations(),e->failed("Could not dismiss invitation."))).setPositiveButton("Review",(d,w)->app.acceptParlonsInvitation(invitation.line)).show();
    }
    private static String initials(String name){String[] words=name.trim().split("\\s+");String first=words.length==0||words[0].isEmpty()?"?":words[0].substring(0,words[0].offsetByCodePoints(0,1));if(words.length>1)first+=words[words.length-1].substring(0,words[words.length-1].offsetByCodePoints(0,1));return first.toUpperCase(Locale.ROOT);}
    void close(){if(closed)return;closed=true;connection.unwatchContacts(contactChange);ParlonsInbox.unwatch(inboxChange);}
}
