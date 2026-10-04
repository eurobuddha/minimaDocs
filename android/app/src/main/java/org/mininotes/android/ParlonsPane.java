package org.mininotes.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Parlons' live address book, backed by its approved IPC connection. */
final class ParlonsPane {
    private final MainActivity app;private final MaximaConnection connection;
    private final String invitation;private final Consumer<String> accept;
    private AlertDialog dialog;private TextView status;private EditText search;private LinearLayout rows;
    private List<ParlonsContact> contacts=new ArrayList<>();private List<ParlonsInbox.Invitation> invitations=new ArrayList<>();
    private boolean loading,closed;
    ParlonsPane(MainActivity app,MaximaConnection connection,String invitation,Consumer<String> accept){
        this.app=app;this.connection=connection;this.invitation=invitation;this.accept=accept;
    }
    void show(){
        LinearLayout body=new LinearLayout(app);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(app.dp(18),app.dp(10),app.dp(18),app.dp(12));
        status=Design.note(app,"Connecting to Parlons…");body.addView(status);
        search=new EditText(app);search.setSingleLine(true);search.setHint("Find a contact");search.setContentDescription("Search Parlons contacts");body.addView(search);
        LinearLayout actions=new LinearLayout(app);
        TextView open=Design.inkButton(app,"Open Parlons");open.setOnClickListener(v->{
            Intent launch=app.getPackageManager().getLaunchIntentForPackage(MaximaConnection.TRANSPORT);
            if(launch==null){status.setText("Install Parlons to connect its contacts.");return;}app.startActivity(launch);
        });actions.addView(open,new LinearLayout.LayoutParams(0,app.dp(48),1));
        TextView refresh=Design.inkButton(app,"Refresh");refresh.setOnClickListener(v->refresh());actions.addView(refresh,new LinearLayout.LayoutParams(0,app.dp(48),1));body.addView(actions);
        rows=new LinearLayout(app);rows.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(app);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView add=Design.inkButton(app,"Add a Parlons contact");add.setOnClickListener(v->add());body.addView(add,new LinearLayout.LayoutParams(-1,app.dp(48)));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}public void afterTextChanged(android.text.Editable s){}});
        dialog=new AlertDialog.Builder(app).setTitle("Parlons contacts").setView(body).setNegativeButton("Close",null).create();
        dialog.setOnDismissListener(d->{closed=true;connection.changed(null);ParlonsInbox.changed=null;});dialog.show();
        dialog.getWindow().setLayout(-1,(int)(app.getResources().getDisplayMetrics().heightPixels*.85));
        connection.changed(()->refresh());ParlonsInbox.changed=()->app.runOnUiThread(this::loadInvitations);refresh();
    }
    void refresh(){
        if(closed||loading)return;loading=true;status.setText("Refreshing Parlons contacts…");
        connection.connect(()->connection.contacts(list->{loading=false;if(closed)return;contacts=list;status.setText(list.size()+" contacts · Changes are saved in Parlons");render();loadInvitations();},this::failed),this::failed);
    }
    private void failed(String why){loading=false;if(!closed)status.setText(why);}
    private void loadInvitations(){
        if(closed)return;app.background.submit(()->ParlonsInbox.list(app),list->{if(closed)return;invitations=list;render();},e->failed("Could not read invitations. Unlock minimaDocs and retry."));
    }
    private void render(){
        if(rows==null||closed)return;rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(ParlonsInbox.Invitation invite:invitations){
            String name="Parlons contact";for(ParlonsContact c:contacts)if(c.key.equalsIgnoreCase(invite.from))name=c.name;
            final String sender=name;
            TextView row=Design.inkButton(app,"Invitation from "+name);rows.addView(row);row.setOnClickListener(v->{
                Pairing.Said offer=Pairing.read(invite.line);
                new AlertDialog.Builder(app).setTitle(sender+" invited you")
                    .setMessage((offer.offer.isEmpty()?"Connect minimaDocs devices":offer.offer)+"\n\n"+offer.level.words())
                    .setNegativeButton("Later",null).setNeutralButton("Dismiss",(d,w)->dismissInvitation(invite))
                    .setPositiveButton("Review",(d,w)->{dialog.dismiss();accept.accept(invite.line);}).show();
            });
        }
        for(ParlonsContact contact:contacts){
            if(!contact.name.toLowerCase(Locale.ROOT).contains(query)&&!contact.key.contains(query))continue;
            TextView row=Design.inkButton(app,contact.name);row.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);row.setMinimumHeight(app.dp(52));
            rows.addView(row,new LinearLayout.LayoutParams(-1,-2));row.setOnClickListener(v->choose(contact));
        }
        if(contacts.isEmpty()&&!loading)rows.addView(Design.note(app,"Your Parlons contacts appear here after you connect."));
    }
    private void choose(ParlonsContact contact){
        new AlertDialog.Builder(app).setTitle(contact.name).setItems(new String[]{"Send minimaDocs invitation","Copy Parlons address","Remove from Parlons"},(d,which)->{
            if(which==0){
                if(invitation==null){failed("Open Share on a document to invite this contact.");return;}
                Pairing.Said offer=Pairing.read(invitation);
                new AlertDialog.Builder(app).setTitle("Invite "+contact.name+"?")
                    .setMessage((offer.offer.isEmpty()?"Connect your minimaDocs devices.":offer.offer+"\n"+offer.level.words())+"\n\nThey need minimaDocs linked to Parlons. They choose whether to accept.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Send invitation",(box,w)->connection.invite(contact,invitation,
                        ()->status.setText("Invitation queued for "+contact.name),this::failed)).show();
            }else if(which==1){
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Parlons address",contact.address));status.setText("Address copied");
            }else new AlertDialog.Builder(app).setTitle("Remove "+contact.name+" from Parlons?")
                .setMessage("This removes the contact from Parlons for every linked app. Existing document access is managed in minimaDocs sharing settings.")
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(box,w)->connection.removeContact(contact,this::refresh,this::failed)).show();
        }).show();
    }
    private void add(){
        EditText address=new EditText(app);address.setHint("Parlons contact address");address.setSingleLine(true);
        new AlertDialog.Builder(app).setTitle("Add contact to Parlons").setView(address).setNegativeButton("Cancel",null)
            .setPositiveButton("Add",(d,w)->connection.addContact(address.getText().toString().trim(),()->{status.setText("Introduction sent · The contact appears when they answer");refresh();},this::failed)).show();
    }
    private void dismissInvitation(ParlonsInbox.Invitation invitation){app.background.submit(()->{ParlonsInbox.dismiss(app,invitation.id);return null;},r->loadInvitations(),e->failed("Could not dismiss invitation."));}
    void close(){if(dialog!=null)dialog.dismiss();}
}
