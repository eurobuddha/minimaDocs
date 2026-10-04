package org.mininotes.android;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import java.util.concurrent.CountDownLatch;

/** Instrumentation-only host; absent from release builds. Runs the packaged editor and bridge. */
public final class EditorTestActivity extends Activity {
    static volatile Session session;
    WebView web;
    static final class Session {
        String bootstrap;volatile String error;volatile byte[] bytes;
        CountDownLatch ready=new CountDownLatch(1),saved=new CountDownLatch(1);
        Session(String bootstrap){this.bootstrap=bootstrap;}
    }
    @Override @android.annotation.SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    public void onCreate(Bundle state){
        super.onCreate(state);web=new WebView(this);
        android.widget.FrameLayout stage=new android.widget.FrameLayout(this);stage.addView(web);setContentView(stage);
        // The same system-bar/keyboard inset handling used by MainActivity.shell().
        stage.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top=bars.top;bottom=Math.max(bars.bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            stage.setPadding(0,top,0,bottom);return insets;
        });stage.requestApplyInsets();
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new EditorAssets(this));web.addJavascriptInterface(new Bridge(),"MinimaDocs");
        web.loadUrl(EditorAssets.ORIGIN+"/workbench/index.html");
    }
    final class Bridge {
        @JavascriptInterface public String bootstrap(){return session.bootstrap;}
        @JavascriptInterface public void used(){}
        @JavascriptInterface public void changed(){}
        @JavascriptInterface public void ready(){session.ready.countDown();}
        @JavascriptInterface public void error(String message){session.error=message;session.ready.countDown();session.saved.countDown();}
        @JavascriptInterface public void saved(String data){session.bytes=android.util.Base64.decode(data,android.util.Base64.DEFAULT);session.saved.countDown();}
        @JavascriptInterface public void exported(String data,String type){saved(data);}
    }
    @Override public void onDestroy(){web.destroy();super.onDestroy();}
}
