// Asset-only WebView serving adapted from PocketWeb's AppServer.java.
package org.mininotes.android;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class EditorAssets extends WebViewClient {
    static final String HOST="editors.minimadocs.local", ORIGIN="https://"+HOST;
    private final Context context;
    EditorAssets(Context context){this.context=context;}

    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
        Uri uri=request.getUrl();
        return !HOST.equals(uri.getHost())||!"https".equals(uri.getScheme())||uri.getPort()!=-1;
    }

    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
        Uri uri=request.getUrl();
        if(!HOST.equals(uri.getHost())||!"https".equals(uri.getScheme())||uri.getPort()!=-1)return refused(403,"Blocked");
        String path=MiniwebUrl.decodeAndNormalize(uri.getEncodedPath());
        if(path==null||!"GET".equals(request.getMethod()))return refused(403,"Blocked");
        // No service worker: editor assets and document bytes are owned by the APK/notebook.
        if(path.endsWith("/sw.js")||path.endsWith("/document_editor_service_worker.js"))return refused(404,"Not Found");
        if(path.equals("/editor"))path="/editor.html";
        String asset=path.startsWith("/workbench/")?path.substring(1):"editors"+path;
        String mime=path.endsWith(".wasm.br")?"application/wasm":MimeTypes.forPath(path);
        try {
            java.io.InputStream input=context.getAssets().open(asset);
            if(mime.equals("text/html")) {
                String html;
                try(java.io.InputStream in=input){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] part=new byte[8192];int n;while((n=in.read(part))!=-1)out.write(part,0,n);html=out.toString("UTF-8");}
                html=html.replaceFirst("(?i)<head>","<head><script src=\"/workbench/memory-storage.js\"></script>");
                input=new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            WebResourceResponse answer=new WebResourceResponse(mime,MimeTypes.isText(mime)?"UTF-8":null,input);
            Map<String,String> headers=new HashMap<>();
            headers.put("Cache-Control","no-store");
            headers.put("Content-Security-Policy","default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; connect-src 'self' blob:; frame-src 'self' blob:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'none'");
            answer.setResponseHeaders(headers);
            return answer;
        } catch(IOException missing){return refused(404,"Not Found");}
    }

    private static WebResourceResponse refused(int status,String reason) {
        return new WebResourceResponse("text/plain","UTF-8",status,reason,new HashMap<>(),new ByteArrayInputStream(new byte[0]));
    }
}
