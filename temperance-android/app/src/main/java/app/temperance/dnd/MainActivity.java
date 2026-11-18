package app.temperance.dnd;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5e - Temperance: la web app (assets/index.html) dentro una WebView a schermo intero.
 * Aggiunge le funzioni native che il browser non offre in una WebView:
 * scelta dei file, finestre di dialogo, salvataggio dei file, tasto Indietro
 * e richieste di rete (importazione da 5e.tools) senza i limiti CORS.
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_SAVE = 2;
    private static final String BG = "#0E0B09";

    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private byte[] pendingSave;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.parseColor(BG));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(BG));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme();
                if (("http".equals(sc) || "https".equals(sc)) && "GET".equalsIgnoreCase(req.getMethod())) {
                    return proxy(u.toString());
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                List<String> mimes = new ArrayList<>();
                String[] acc = p.getAcceptTypes();
                if (acc != null) {
                    for (String a : acc) {
                        if (a != null && a.contains("/")) mimes.add(a.trim());
                    }
                }
                if (mimes.size() == 1) {
                    i.setType(mimes.get(0));
                } else {
                    i.setType("*/*");
                    if (mimes.size() > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                }
                try {
                    startActivityForResult(Intent.createChooser(i, "Scegli un file"), REQ_FILE);
                } catch (Exception e) {
                    chooser = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm())
                        .setNegativeButton("Annulla", (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    /** Esegue la richiesta HTTP al posto della pagina e aggiunge l'intestazione CORS. */
    private WebResourceResponse proxy(String url) {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "Temperance/1.0 (Android)");
            c.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) in = new ByteArrayInputStream(new byte[0]);
            String ct = c.getContentType();
            String mime = ct == null ? "application/octet-stream" : ct.split(";")[0].trim();
            String reason = c.getResponseMessage();
            if (reason == null || reason.isEmpty()) reason = code < 400 ? "OK" : "Error";
            if (code >= 300 && code < 400) code = 502;
            return new WebResourceResponse(mime, "utf-8", code, reason, h, in);
        } catch (Exception e) {
            byte[] b = String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return new WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", h, new ByteArrayInputStream(b));
        }
    }

    /** Funzioni richiamabili dalla pagina come window.AndroidBridge. */
    public class Bridge {
        @JavascriptInterface
        public void save(final String name, final String mime, String text) {
            final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            runOnUiThread(() -> {
                pendingSave = bytes;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Impossibile salvare il file", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            if (chooser != null) {
                Uri[] r = (res == RESULT_OK && data != null)
                        ? WebChromeClient.FileChooserParams.parseResult(res, data) : null;
                chooser.onReceiveValue(r);
                chooser = null;
            }
        } else if (req == REQ_SAVE) {
            if (res == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
                try (OutputStream o = getContentResolver().openOutputStream(data.getData())) {
                    o.write(pendingSave);
                    Toast.makeText(this, "File salvato", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Salvataggio non riuscito", Toast.LENGTH_LONG).show();
                }
            }
            pendingSave = null;
        }
    }

    /** Il tasto Indietro chiude prima i pannelli dell'app, poi torna alla Home, infine esce. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back&&window.__back()", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
