package org.mininotes.android;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONObject;

/** Offline editors with durable snapshots, causal shared updates, and native file export. */
final class EditorPane extends Dialog {
    static final int PICK=91;
    private final MainActivity app;
    private final String kind;
    private NoteStore.Held source;
    private String noteId=java.util.UUID.randomUUID().toString();
    private RichDocument viewed;
    private boolean conflictReview,readOnly,dirty,changedDuringSave,closeAfterSave,exportAfterSave;
    private long lastEdit,lastSaved,saveStarted,interval=60_000;
    private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autosave=new Runnable(){public void run(){
        if(closed)return;long now=android.os.SystemClock.elapsedRealtime();
        if(ready&&dirty&&!saving&&!readOnly&&!conflictReview&&now-lastSaved>=interval&&now-lastEdit>=2000)saveCopy();
        timer.postDelayed(this,5000);
    }};
    private final Background storage;
    private WebView web;
    private EditText title;
    private TextView status,save;
    private volatile String bootstrap="{}";
    private boolean exporting,saving,ready,closed,acceptingSave;
    private ValueCallback<Uri[]> picked;
    private String seedTitle,seedText;
    private boolean shareAfterSave;
    void seed(String name,String text){seedTitle=name;seedText=text==null?"":text;}

    EditorPane(MainActivity app,String kind,NoteStore.Held source) {
        super(app);this.app=app;this.kind=kind;this.source=source;
        storage=app.background;viewed=RichDocument.empty(kind);
        dirty=source==null;lastEdit=lastSaved=android.os.SystemClock.elapsedRealtime();
    }

    static String kindOf(String name) {
        if(name==null)return "";
        String n=name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".docx")||n.endsWith(".odt")||n.endsWith(".rtf")||n.endsWith(".doc"))return "docx";
        if(n.endsWith(".xlsx")||n.endsWith(".ods")||n.endsWith(".csv")||n.endsWith(".xls"))return "xlsx";
        if(n.endsWith(".minimadocs-image.json")||n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp"))return "image";
        return "";
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(app);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Design.PAPER());
        LinearLayout bar=new LinearLayout(app);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(app.dp(12),app.dp(8),app.dp(12),app.dp(8));
        TextView back=WorkspaceUi.textButton(app,"Files",this::onBackPressed);bar.addView(back,new LinearLayout.LayoutParams(app.dp(68),app.dp(48)));
        title=new EditText(app);title.setSingleLine(true);title.setTypeface(Design.sansBold());title.setTextColor(Design.INK());title.setTextSize(16);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        title.setText(source==null?(kind.equals("image")?"Untitled image":kind.equals("xlsx")?"Untitled sheet":"Untitled document"):source.name.replaceFirst("(?i)(\\.minimadocs-image\\.json|\\.[^.]+)$",""));
        if(seedTitle!=null)title.setText(seedTitle);
        title.setBackgroundColor(android.graphics.Color.TRANSPARENT);title.setPadding(app.dp(12),0,app.dp(12),0);
        title.setContentDescription("Document title");bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView share=WorkspaceUi.button(app,"Share",true,()->{if(!ready||saving){app.toast("Wait for the editor to finish.");return;}if(source==null||dirty){shareAfterSave=true;requestSave();}else app.shareDocument(noteId,title.getText().toString());});bar.addView(share,new LinearLayout.LayoutParams(app.dp(84),app.dp(48)));
        root.addView(bar);root.addView(Design.softRule(app));
        status=Design.note(app,"Opening editor…");status.setPadding(app.dp(14),app.dp(8),app.dp(14),app.dp(8));status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout statusLine=new LinearLayout(app);statusLine.setGravity(Gravity.CENTER_VERTICAL);statusLine.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        TextView exportTop=WorkspaceUi.textButton(app,"Export",()->{});exportTop.setOnClickListener(this::exportMenu);statusLine.addView(exportTop,new LinearLayout.LayoutParams(app.dp(76),app.dp(44)));
        TextView fileTop=WorkspaceUi.textButton(app,"File",()->{});fileTop.setOnClickListener(this::menu);statusLine.addView(fileTop,new LinearLayout.LayoutParams(app.dp(62),app.dp(44)));root.addView(statusLine);
        LinearLayout actions=new LinearLayout(app);actions.setPadding(app.dp(12),0,app.dp(12),app.dp(6));
        save=WorkspaceUi.button(app,"Save",false,this::requestSave);save.setEnabled(false);actions.addView(save,new LinearLayout.LayoutParams(0,app.dp(44),1));
        TextView export=WorkspaceUi.button(app,"Export",false,()->{});export.setOnClickListener(this::menu);actions.addView(export,new LinearLayout.LayoutParams(0,app.dp(44),1));
        TextView more=WorkspaceUi.button(app,"File options",false,()->{});more.setOnClickListener(this::menu);actions.addView(more,new LinearLayout.LayoutParams(0,app.dp(44),1));if(!kind.equals("docx"))root.addView(actions);
        web=new WebView(app);configureWeb();root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);setCanceledOnTouchOutside(false);
        Window window=getWindow();
        if(window!=null){window.setLayout(-1,-1);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setStatusBarColor(Design.PAPER());window.setNavigationBarColor(Design.PAPER());window.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(PhoneLock.locked(app))window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.setBackgroundDrawable(Design.rect(Design.PAPER()));}
        storage.submit(()->{
            if(!PhoneLock.open(app))throw new IllegalStateException("Unlock your notebook first.");
            JSONObject config=new JSONObject().put("kind",kind);
            if(seedText!=null)config.put("seedText",seedText);
            if(source!=null) {
                NoteStore.Held kept=app.store.keptFile(source.id);
                if(kept==null||kept.bytes>Enclosure.MOST)throw new IllegalStateException("This editor opens files up to 16 MiB.");
                NoteStore.Note note=app.store.get(kept.note);
                RichDocument document=note==null?null:RichDocument.read(note.body);
                if(document!=null&&document.heads.containsValue(kept.id)){if(!kind.equals(document.kind))throw new IllegalStateException("Document type does not match its file.");noteId=note.id;viewed=document;conflictReview=document.heads.size()>1;readOnly=app.store.onlyReads(noteId);config.put("title",note.title);}
                if(document==null||!document.heads.containsValue(kept.id))dirty=true;
                config.put("readonly",readOnly);
                byte[] bytes=app.store.bytesOf(kept);
                if(bytes.length>Enclosure.MOST)throw new IllegalStateException("This editor opens files up to 16 MiB.");
                config.put("name",kept.name).put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP));
            }
            return config.toString();
        },config->{if(closed)return;bootstrap=config;try{JSONObject c=new JSONObject(config);if(c.has("title"))title.setText(c.getString("title"));}catch(Exception ignored){}
            title.setEnabled(!readOnly);title.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){changed();}
                public void afterTextChanged(android.text.Editable s){}
            });web.loadUrl(EditorAssets.ORIGIN+"/workbench/index.html");},e->failed(e.getMessage()));
    }

    @android.annotation.SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    private void configureWeb() {
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new EditorAssets(app));
        web.addJavascriptInterface(new Bridge(),"MinimaDocs");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(picked!=null)picked.onReceiveValue(null);picked=callback;
                try{app.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),PICK);return true;}
                catch(Exception missing){picked=null;callback.onReceiveValue(null);failed("Could not open the file picker.");return true;}
            }
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                // No document text or file bytes in logs.
                if(message.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR)android.util.Log.w("minimaDocs/Editor","Editor error at line "+message.lineNumber());
                return true;
            }
        });
    }

    void picked(Uri uri){if(picked!=null){picked.onReceiveValue(uri==null?null:new Uri[]{uri});picked=null;}}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)app.onUserInteraction();return super.dispatchTouchEvent(event);}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){app.onUserInteraction();return super.dispatchKeyEvent(event);}
    private void menu(android.view.View anchor) {
        if(!ready||saving){app.toast("Wait for the editor to finish.");return;}
        android.widget.PopupMenu menu=new android.widget.PopupMenu(app,anchor);
        if(!readOnly)menu.getMenu().add("Save now").setOnMenuItemClickListener(item->{requestSave();return true;});
        menu.getMenu().add("Export to phone").setOnMenuItemClickListener(item->{exportAfterSave=true;if(dirty||source==null)requestSave();else{exportAfterSave=false;app.exportFile(source);}return true;});
        menu.getMenu().add(kind.equals("image")?"Export PNG":"Export PDF").setOnMenuItemClickListener(item->{
            saving=true;exporting=true;save.setEnabled(false);status.setText("Preparing export…");
            web.evaluateJavascript("minimaDocsExport("+JSONObject.quote(kind.equals("image")?"PNG":"PDF")+")",null);return true;
        });
        menu.getMenu().add("Save a separate copy").setOnMenuItemClickListener(item->{noteId=java.util.UUID.randomUUID().toString();viewed=RichDocument.empty(kind);conflictReview=false;source=null;readOnly=false;title.setEnabled(true);web.evaluateJavascript("minimaDocsWritable()",null);dirty=true;saveCopy();return true;});
        menu.getMenu().add("Share document").setOnMenuItemClickListener(item->{if(source==null||dirty)app.toast("Save the document before sharing.");else app.shareDocument(noteId,title.getText().toString());return true;});
        menu.getMenu().add("Close without saving").setOnMenuItemClickListener(item->{new AlertDialog.Builder(app).setTitle("Discard unsaved edits?").setMessage("The last saved version will remain.").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->{if(!saving)dismiss();}).show();return true;});
        menu.show();
    }
    private void exportMenu(android.view.View anchor){
        if(!ready||saving){app.toast("Wait for the editor to finish.");return;}
        android.widget.PopupMenu menu=new android.widget.PopupMenu(app,anchor);
        menu.getMenu().add("Export "+(kind.equals("docx")?"Word (.docx)":kind.equals("xlsx")?"Excel (.xlsx)":"layered image")).setOnMenuItemClickListener(item->{exportAfterSave=true;if(dirty||source==null)requestSave();else{exportAfterSave=false;app.exportFile(source);}return true;});
        menu.getMenu().add(kind.equals("image")?"Export PNG":"Export PDF").setOnMenuItemClickListener(item->{saving=true;exporting=true;save.setEnabled(false);status.setText("Preparing export…");web.evaluateJavascript("minimaDocsExport("+JSONObject.quote(kind.equals("image")?"PNG":"PDF")+")",null);return true;});menu.show();
    }
    @Override public void onBackPressed() {
        if(!ready||readOnly||(!dirty&&!saving)){dismiss();return;}
        closeAfterSave=true;if(!saving)requestSave();
    }
    void backgrounded(){if(ready&&dirty&&!saving&&!readOnly&&!conflictReview)saveCopy();}
    private void changed(){if(closed||readOnly)return;dirty=true;lastEdit=android.os.SystemClock.elapsedRealtime();if(saving)changedDuringSave=true;if(ready&&!saving)status.setText(conflictReview?"Unsaved changes · Save to choose this version":"Unsaved changes · Autosave on");}
    @Override public void dismiss() {
        closed=true;timer.removeCallbacksAndMessages(null);bootstrap="{}";picked(null);
        if(web!=null){web.removeJavascriptInterface("MinimaDocs");web.stopLoading();web.destroy();web=null;}
        super.dismiss();app.documentClosed(this);
    }

    private void requestSave(){
        if(!conflictReview){saveCopy();return;}
        new AlertDialog.Builder(app).setTitle("Use this version?")
            .setMessage("This replaces the concurrent versions you opened with the document shown here. Save a separate copy from the menu to keep them all.")
            .setNegativeButton("Keep reviewing",(d,w)->{closeAfterSave=false;exportAfterSave=false;shareAfterSave=false;})
            .setOnCancelListener(d->{closeAfterSave=false;exportAfterSave=false;shareAfterSave=false;})
            .setPositiveButton("Use this version",(d,w)->{conflictReview=false;saveCopy();}).show();
    }

    private void saveCopy(){
        if(!ready||saving||closed||readOnly)return;
        saving=true;changedDuringSave=false;acceptingSave=true;saveStarted=android.os.SystemClock.elapsedRealtime();
        save.setEnabled(false);status.setText("Saving…");web.evaluateJavascript("minimaDocsSave()",null);
    }
    private void failed(String message){app.runOnUiThread(()->{
        if(closed)return;saving=false;acceptingSave=false;if(!exporting)dirty=true;exporting=false;closeAfterSave=false;exportAfterSave=false;
        shareAfterSave=false;save.setEnabled(ready&&!readOnly);status.setText(message==null?"Could not save. Your previous version is safe.":message);
        lastSaved=android.os.SystemClock.elapsedRealtime();
    });}

    private final class Bridge {
        @JavascriptInterface public void used(){app.runOnUiThread(()->{app.onUserInteraction();lastEdit=android.os.SystemClock.elapsedRealtime();});}
        @JavascriptInterface public void changed(){app.runOnUiThread(EditorPane.this::changed);}
        @JavascriptInterface public String bootstrap(){return bootstrap;}
        @JavascriptInterface public void ready(){app.runOnUiThread(()->{if(closed||ready)return;ready=true;save.setEnabled(!readOnly);status.setText(readOnly?"Read only":viewed.heads.size()>1?"Reviewing a concurrent version · Save to choose it":"Autosave on · Saved versions stay on this phone");bootstrap="{}";timer.postDelayed(autosave,5000);});}
        @JavascriptInterface public void error(String message){failed(message);}
        @JavascriptInterface public void exported(String base64,String type){app.runOnUiThread(()->{
            if(closed||!saving||!exporting||!("PDF".equals(type)||"PNG".equals(type)))return;
            if(base64==null||base64.length()>4*((Enclosure.MOST+2)/3)){failed("Export exceeds 16 MiB.");return;}
            try{byte[] bytes=Base64.decode(base64,Base64.DEFAULT);if(bytes.length>Enclosure.MOST)throw new IllegalArgumentException();
                saving=false;exporting=false;closeAfterSave=false;save.setEnabled(!readOnly);status.setText(dirty?"Unsaved changes":"Export ready");
                app.exportBytes(title.getText().toString()+"."+type.toLowerCase(Locale.ROOT),type.equals("PDF")?"application/pdf":"image/png",bytes);
            }catch(IllegalArgumentException invalid){failed("Could not read the exported file.");}
        });}
        @JavascriptInterface public void saved(String base64){
            app.runOnUiThread(()->{
                if(closed||!saving||exporting||!acceptingSave)return;
                acceptingSave=false;
                if(base64==null||base64.length()>4*((Enclosure.MOST+2)/3)){failed("The edited file exceeds the 16 MiB sharing limit.");return;}
                final String named=title.getText().toString().trim();
                status.setText("Saving on this phone…");
                final String savingNote=noteId;final RichDocument savingView=viewed;
                storage.submit(()->DocumentStore.save(app,app.store,savingNote,named,savingView,Base64.decode(base64,Base64.DEFAULT)),result->{
                    source=result.file;viewed=result.viewed;app.documentSaved(savingNote);
                    if(closed)return;
                    saving=false;dirty=changedDuringSave;lastSaved=android.os.SystemClock.elapsedRealtime();
                    interval=Math.min(180_000,Math.max(30_000,(lastSaved-saveStarted)*300));save.setEnabled(!readOnly);
                    status.setText(result.current.heads.size()>1?"Concurrent versions kept · Reopen to review":dirty?"Unsaved changes · Autosave on":"Saved on this phone · Autosave on");
                    if(exportAfterSave){exportAfterSave=false;app.exportFile(source);}
                    if(shareAfterSave){if(dirty)saveCopy();else{shareAfterSave=false;app.shareDocument(noteId,title.getText().toString());}}
                    if(closeAfterSave){if(dirty)saveCopy();else dismiss();}
                },e->failed(e.getMessage()));
            });
        }
    }

    void remoteChanged() {
        if(closed||saving||source==null)return;
        final String id=noteId;
        storage.submit(()->{
            NoteStore.Note n=app.store.get(id);RichDocument doc=n==null?null:RichDocument.read(n.body);
            if(doc==null)return null;
            if(doc.heads.size()!=1)return new Object[]{doc,null};
            NoteStore.Held latest=app.store.file(doc.heads.values().iterator().next());
            return new Object[]{doc,latest};
        },result->{
            if(closed||saving||result==null)return;RichDocument current=(RichDocument)result[0];
            if(current.text().equals(viewed.text()))return;
            if(dirty||current.heads.size()>1){status.setText("Shared changes arrived · Your edits will be kept separately");return;}
            NoteStore.Held latest=(NoteStore.Held)result[1];
            if(latest==null||!noteId.equals(latest.note)){status.setText("Downloading shared update…");return;}
            dismiss();app.editDocument(kind,latest);
        },e->{});
    }
}
