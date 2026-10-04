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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import org.json.JSONObject;

/** Local editors. A saved copy is a new note/file, through the notebook's existing encrypted storage. */
final class EditorPane extends Dialog {
    static final int PICK=91;
    private final MainActivity app;
    private final String kind;
    private final NoteStore.Held source;
    private final Background storage;
    private WebView web;
    private EditText title;
    private TextView status,save;
    private volatile String bootstrap="{}";
    private boolean saving,ready,closed,acceptingSave;
    private ValueCallback<Uri[]> picked;

    EditorPane(MainActivity app,String kind,NoteStore.Held source) {
        super(app);this.app=app;this.kind=kind;this.source=source;
        storage=app.background;
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
        TextView back=Design.inkButton(app,"←");back.setContentDescription("Close editor");back.setOnClickListener(v->onBackPressed());bar.addView(back,new LinearLayout.LayoutParams(app.dp(48),app.dp(48)));
        title=new EditText(app);title.setSingleLine(true);title.setTypeface(Design.sansBold());title.setTextColor(Design.INK());title.setTextSize(16);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        title.setText(source==null?(kind.equals("image")?"Untitled image":kind.equals("xlsx")?"Untitled sheet":"Untitled document"):source.name.replaceFirst("(?i)(\\.minimadocs-image\\.json|\\.[^.]+)$",""));
        title.setContentDescription("Document title");bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar);root.addView(Design.rule(app,1));
        status=Design.note(app,"Opening editor…");status.setPadding(app.dp(14),app.dp(8),app.dp(14),app.dp(8));status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        web=new WebView(app);configureWeb();root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout foot=new LinearLayout(app);foot.setPadding(app.dp(12),app.dp(8),app.dp(12),app.dp(10));
        save=Design.button(app,"Save a copy",true);save.setEnabled(false);save.setOnClickListener(v->saveCopy());foot.addView(save,new LinearLayout.LayoutParams(-1,app.dp(50)));root.addView(foot);
        setContentView(root);setCanceledOnTouchOutside(false);
        Window window=getWindow();
        if(window!=null){window.setLayout(-1,-1);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(PhoneLock.locked(app))window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.setBackgroundDrawable(Design.rect(Design.PAPER()));}
        storage.submit(()->{
            if(!PhoneLock.open(app))throw new IllegalStateException("Unlock your notebook first.");
            JSONObject config=new JSONObject().put("kind",kind);
            if(source!=null) {
                NoteStore.Held kept=app.store.keptFile(source.id);
                if(kept==null||kept.bytes>Enclosure.MOST)throw new IllegalStateException("This editor opens files up to 16 MiB.");
                byte[] bytes=app.store.bytesOf(kept);
                if(bytes.length>Enclosure.MOST)throw new IllegalStateException("This editor opens files up to 16 MiB.");
                config.put("name",kept.name).put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP));
            }
            return config.toString();
        },config->{if(closed)return;bootstrap=config;web.loadUrl(EditorAssets.ORIGIN+"/workbench/index.html");},e->failed(e.getMessage()));
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
    @Override public void onBackPressed() {
        if(saving){app.toast("Wait for the copy to finish saving.");return;}
        new AlertDialog.Builder(app).setTitle("Close editor?").setMessage("Save a copy before closing to keep your edits.")
            .setNegativeButton("Keep editing",null).setPositiveButton("Close",(d,w)->dismiss()).show();
    }
    @Override public void dismiss() {
        closed=true;bootstrap="{}";picked(null);
        if(web!=null){web.removeJavascriptInterface("MinimaDocs");web.stopLoading();web.destroy();web=null;}
        super.dismiss();
    }

    private void saveCopy(){if(!ready||saving||closed)return;saving=true;acceptingSave=true;save.setEnabled(false);status.setText("Preparing edited copy…");web.evaluateJavascript("minimaDocsSave()",null);}
    private void failed(String message){app.runOnUiThread(()->{if(closed)return;saving=false;save.setEnabled(ready);status.setText(message==null?"Could not complete that operation.":message);});}

    private final class Bridge {
        @JavascriptInterface public void used(){app.runOnUiThread(app::onUserInteraction);}
        @JavascriptInterface public String bootstrap(){return bootstrap;}
        @JavascriptInterface public void ready(){app.runOnUiThread(()->{if(closed)return;ready=true;save.setEnabled(true);status.setText("Offline editor · Save a copy to keep and share your work");bootstrap="{}";});}
        @JavascriptInterface public void error(String message){failed(message);}
        @JavascriptInterface public void saved(String base64){
            app.runOnUiThread(()->{
                if(closed||!saving||!acceptingSave)return;
                acceptingSave=false;
                if(base64==null||base64.length()>4*((Enclosure.MOST+2)/3)){failed("The edited file exceeds the 16 MiB sharing limit.");return;}
                final String named=title.getText().toString().trim();
                status.setText("Keeping your copy…");
                storage.submit(()->keepCopy(named,base64),id->{
                    if(closed)return;saving=false;dismiss();app.editorSaved(id);
                },e->failed(e.getMessage()));
            });
        }
    }

    private String keepCopy(String named,String base64) throws Exception {
        if(!PhoneLock.open(app))throw new IllegalStateException("Your notebook is locked. Unlock it before saving.");
        byte[] key=NoteStore.key();
        if(PhoneLock.locked(app)&&key==null)throw new IllegalStateException("Your notebook is locked. Unlock it before saving.");
        byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
        if(bytes.length==0||bytes.length>Enclosure.MOST)throw new IllegalArgumentException("The file must be between 1 byte and 16 MiB.");
        if(app.store.weight()+bytes.length>Attachment.PLENTY)throw new IllegalStateException(Given.FULL);
        NoteStore.Note note=new NoteStore.Note();note.title=named.isEmpty()?"Untitled":named;
        note.book=Things.HOME;note.body=kind.equals("image")?"Layered image project":kind.equals("xlsx")?"Spreadsheet":"Word document";
        note.revision=1;
        String extension=kind.equals("image")?"minimadocs-image.json":kind;
        String mime=kind.equals("image")?"application/json":kind.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        NoteStore.Held held=app.store.opening(NoteStore.Branch.Kind.PAGE,note.id,note.title+"."+extension,mime,bytes.length);
        File file=app.store.fileFor(held.id);
        boolean committed=false;
        try {
            try(FileOutputStream out=new FileOutputStream(file)) {
                if(key==null)out.write(bytes);else Sealed.seal(key,new ByteArrayInputStream(bytes),out);
                out.getFD().sync();
            }
            net.zetetic.database.sqlcipher.SQLiteDatabase db=app.store.getWritableDatabase();db.beginTransaction();
            try{app.store.save(db,note);app.store.keep(db,held);db.setTransactionSuccessful();}
            finally{db.endTransaction();}
            committed=true;return note.id;
        } finally {if(!committed)file.delete();}
    }
}
