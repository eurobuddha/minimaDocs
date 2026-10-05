package org.mininotes.android;

import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Document-first launcher. Storage, access control and transport remain in their existing owners. */
final class Workspace {
    private final MainActivity app;
    private String section="Files",kind="docx",query="";
    private LinearLayout content,list;
    private PeoplePane people;
    private int generation;
    private boolean closed;
    private List<Entry> entries=new ArrayList<>();
    private static final class Entry {
        final NoteStore.Note note;final String kind;final boolean shared;String preview="";
        Entry(NoteStore.Note n,String k,boolean s){note=n;kind=k;shared=s;}
    }
    Workspace(MainActivity app){this.app=app;}
    void show(){
        if(people!=null){people.close();people=null;}
        generation++;app.workspaceShell();
        LinearLayout header=new LinearLayout(app);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(app.dp(22),app.dp(14),app.dp(22),app.dp(14));
        header.addView(WorkspaceUi.text(app,"minimaDocs",25,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView network=WorkspaceUi.note(app,"Maxima");network.setMinHeight(app.dp(48));network.setGravity(Gravity.CENTER);network.setOnClickListener(v->app.workspaceNetwork());header.addView(network);
        app.root.addView(header);app.root.addView(Design.softRule(app));
        content=WorkspaceUi.column(app);content.setPadding(app.dp(22),app.dp(20),app.dp(22),0);app.root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        if(section.equals("People")){
            people=new PeoplePane(app,app.parlonsConnection(),null,null);content.addView(people.view(),new LinearLayout.LayoutParams(-1,-1));people.refresh();
        }else if(section.equals("Settings"))settings();else files();
        app.root.addView(Design.softRule(app));LinearLayout nav=new LinearLayout(app);
        for(String name:new String[]{"Files","Shared","People","Settings"}){
            TextView item=WorkspaceUi.text(app,name,13,section.equals(name));item.setGravity(Gravity.CENTER);item.setMinHeight(app.dp(60));
            if(section.equals(name)){item.setTextColor(Design.ACCENT());item.setBackgroundColor(0x0DE63312);}
            item.setOnClickListener(v->{section=name;show();});nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        app.root.addView(nav);
    }
    private void files(){
        EditText search=WorkspaceUi.search(app,"Search your files");search.setText(query);content.addView(search);WorkspaceUi.watch(search,()->{query=search.getText().toString();render();});
        WorkspaceUi.gap(content,22);content.addView(WorkspaceUi.text(app,section.equals("Shared")?"Shared with people":"Your documents",28,true));WorkspaceUi.gap(content,12);
        LinearLayout filters=new LinearLayout(app);
        String[] values={"","docx","xlsx","image"},names={"All","Docs","Sheets","Images"};
        for(int i=0;i<names.length;i++){final String value=values[i];TextView tab=WorkspaceUi.text(app,names[i],14,kind.equals(value));tab.setGravity(Gravity.CENTER);tab.setOnClickListener(v->{kind=value;show();});tab.setFocusable(true);tab.setSelected(kind.equals(value));LinearLayout slot=WorkspaceUi.column(app);slot.addView(tab,new LinearLayout.LayoutParams(-1,app.dp(46)));View line=new View(app);line.setBackgroundColor(kind.equals(value)?Design.ACCENT():Design.SOFT());slot.addView(line,new LinearLayout.LayoutParams(-1,app.dp(2)));if(kind.equals(value))tab.setTextColor(Design.ACCENT());filters.addView(slot,new LinearLayout.LayoutParams(0,app.dp(48),1));}
        content.addView(filters);WorkspaceUi.gap(content,16);
        LinearLayout actions=new LinearLayout(app);
        String type=kind.equals("xlsx")?"spreadsheet":kind.equals("image")?"image":"document";
        actions.addView(WorkspaceUi.button(app,"New "+type,true,()->app.editDocument(kind.isEmpty()?"docx":kind,null)),new LinearLayout.LayoutParams(0,app.dp(50),1));
        TextView imp=WorkspaceUi.button(app,"Import",false,()->app.chooseDocumentImport(kind.isEmpty()?"docx":kind));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(app.dp(100),app.dp(50));ip.setMarginStart(app.dp(10));actions.addView(imp,ip);content.addView(actions);WorkspaceUi.gap(content,20);
        ScrollView scroll=new ScrollView(app);list=WorkspaceUi.column(app);scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));refresh();
    }
    void refresh(){
        if(closed)return;
        if(people!=null){people.refresh();return;}if(list==null||section.equals("Settings"))return;
        final int token=generation;
        app.background.submit(()->{
            List<Entry> result=new ArrayList<>();
            for(NoteStore.Branch b:app.store.lately(500)){
                NoteStore.Note n=app.store.get(b.id);if(n==null)continue;
                RichDocument d=RichDocument.read(n.body);Entry entry=new Entry(n,d==null?"docx":d.kind,app.store.sharedAtAll(NoteStore.Branch.Kind.PAGE,n.id));
                if(d==null)entry.preview=n.body==null?"":n.body.substring(0,Math.min(600,n.body.length()));
                else if(result.size()<4&&d.kind.equals("docx")&&d.heads.size()==1){NoteStore.Held file=app.store.file(d.heads.values().iterator().next());if(file!=null)try{entry.preview=DocumentPreview.text(app.store.bytesOf(file));}catch(Exception unavailable){}}
                result.add(entry);
            }
            return result;
        },found->{if(token!=generation)return;entries=found;render();},e->{if(token==generation){list.removeAllViews();list.addView(WorkspaceUi.note(app,"Could not read your documents. Try again."));list.addView(WorkspaceUi.button(app,"Retry",false,this::refresh));}});
    }
    private void render(){
        if(list==null)return;list.removeAllViews();String needle=query.trim().toLowerCase(Locale.ROOT);int shown=0;LinearLayout previews=null;
        for(Entry entry:entries){
            if(!kind.isEmpty()&&!kind.equals(entry.kind)||section.equals("Shared")&&!entry.shared)continue;
            String name=entry.note.heading();if(!name.toLowerCase(Locale.ROOT).contains(needle))continue;shown++;
            if(shown<=2&&entry.kind.equals("docx")){
                if(previews==null){previews=new LinearLayout(app);list.addView(previews);WorkspaceUi.gap(list,16);}
                LinearLayout card=WorkspaceUi.column(app),paper=WorkspaceUi.column(app);paper.setPadding(app.dp(14),app.dp(20),app.dp(14),app.dp(14));paper.setBackground(WorkspaceUi.surface(app,Design.WHITE(),Design.SOFT()));
                TextView title=WorkspaceUi.text(app,name,16,true);title.setMaxLines(3);paper.addView(title);WorkspaceUi.gap(paper,12);
                TextView text=WorkspaceUi.text(app,entry.preview.isEmpty()?"Word document\nOpen to edit":entry.preview,11,false);text.setLineSpacing(0,1.3f);text.setMaxLines(8);paper.addView(text,new LinearLayout.LayoutParams(-1,0,1));
                card.addView(paper,new LinearLayout.LayoutParams(-1,app.dp(210)));WorkspaceUi.gap(card,10);TextView label=WorkspaceUi.text(app,name,14,true);label.setMaxLines(2);card.addView(label);card.addView(WorkspaceUi.note(app,entry.shared?"Shared document":"On this device"));
                card.setOnClickListener(v->open(entry.note));card.setFocusable(true);card.setContentDescription("Open "+name);LinearLayout.LayoutParams place=new LinearLayout.LayoutParams(0,-2,1);place.setMargins(0,0,app.dp(10),0);previews.addView(card,place);continue;
            }
            LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,app.dp(12),0,app.dp(12));row.setMinimumHeight(app.dp(94));
            TextView mark=WorkspaceUi.text(app,entry.kind.equals("xlsx")?"XLSX":entry.kind.equals("image")?"IMG":"DOCX",11,true);mark.setGravity(Gravity.CENTER);mark.setTextColor(entry.kind.equals("xlsx")?0xFF356653:entry.kind.equals("image")?0xFF745082:0xFF305F91);mark.setBackground(WorkspaceUi.surface(app,Design.WHITE(),Design.SOFT()));row.addView(mark,new LinearLayout.LayoutParams(app.dp(58),app.dp(72)));
            LinearLayout words=WorkspaceUi.column(app);words.setPadding(app.dp(16),0,app.dp(8),0);TextView title=WorkspaceUi.text(app,name,16,true);title.setMaxLines(2);words.addView(title);WorkspaceUi.gap(words,6);
            boolean legacy=!RichDocument.marked(entry.note.body);
            words.addView(WorkspaceUi.note(app,(entry.shared?"Shared":"On this device")+" · "+(legacy?"Text document":DocumentStore.extension(entry.kind).toUpperCase(Locale.ROOT))));
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setBackground(Design.ripple(Design.rect(Design.PAPER())));row.setOnClickListener(v->open(entry.note));row.setContentDescription("Open "+name);row.setFocusable(true);list.addView(row);list.addView(Design.softRule(app));
        }
        if(shown==0){WorkspaceUi.gap(list,36);list.addView(WorkspaceUi.text(app,needle.isEmpty()?(section.equals("Shared")?"Work together.":"Start something new."):"No matching documents",22,true));WorkspaceUi.gap(list,10);list.addView(WorkspaceUi.note(app,needle.isEmpty()?(section.equals("Shared")?"Open a document and choose Share to invite someone from Parlons.":"Create a document or import a file from your phone."):"Try another name or choose All."));}
        WorkspaceUi.gap(list,24);
    }
    void open(NoteStore.Note note){app.openWorkspaceDocument(note);}
    void library(String selected){kind=selected;section="Files";show();}
    private void settings(){
        content.addView(WorkspaceUi.text(app,"Settings",28,true));WorkspaceUi.gap(content,20);
        content.addView(WorkspaceUi.button(app,"Parlons contacts",false,()->{section="People";show();}));WorkspaceUi.gap(content,12);
        content.addView(WorkspaceUi.button(app,"Connection settings",false,app::workspaceNetwork));WorkspaceUi.gap(content,12);
        content.addView(WorkspaceUi.button(app,"Security and backups",false,app::workspaceSettings));WorkspaceUi.gap(content,24);
        content.addView(WorkspaceUi.note(app,"minimaDocs "+app.version()+"\nDocuments, spreadsheets and images.\nShared privately through Maxima."));
    }
    boolean back(){if(!section.equals("Files")){section="Files";show();return true;}return false;}
    void close(){closed=true;generation++;if(people!=null)people.close();}
}
