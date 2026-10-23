package com.gathin.tvhome;

import android.app.Activity;
import androidx.webkit.WebViewAssetLoader;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * mateusarce.dev's offline launcher UI. The exact design source is imported from
 * the owner's exported Claude HTML by tools/import_tv_design.py.
 *
 * Android stays responsible for querying/launching apps and TV inputs.
 * No HTTP URLs or untrusted pages may access the privileged JS bridge.
 */
public final class DesignLauncherActivity extends Activity {
    private static final String ASSET = "https://appassets.androidplatform.net/assets/design_launcher/index.html";
    private static final String PREFS = "mateusarce_tv_design";
    private WebView web;
    private boolean launchedExternal;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        try (java.io.InputStream ignored = getAssets().open("design_launcher/index.html")) {
            // The user-owned export is imported locally, not redistributed in the fork.
        } catch (IOException absent) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        web = new WebView(this);
        web.setBackgroundColor(android.graphics.Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBlockNetworkLoads(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://appassets.androidplatform.net/assets/"))
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(), "TvLauncherBridge");
        setContentView(web);
        web.loadUrl(ASSET);
        web.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null && launchedExternal) {
            launchedExternal = false;
            web.reload();
        }
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("TvLauncherBridge");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (web == null) return super.dispatchKeyEvent(event);
        String key = null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: key = "ArrowUp"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: key = "ArrowDown"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: key = "ArrowLeft"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: key = "ArrowRight"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: key = "Enter"; break;
            case KeyEvent.KEYCODE_BACK: key = "Escape"; break;
            default: break;
        }
        if (key != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                web.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown',{key:'"
                        + key + "',bubbles:true,cancelable:true}))", null);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void startExternal(Intent intent) {
        runOnUiThread(() -> {
            try {
                launchedExternal = true;
                startActivity(intent);
            } catch (RuntimeException e) {
                launchedExternal = false;
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String iconAsMask(Drawable icon) {
        if (icon == null) return "";
        try {
            Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, 64, 64);
            icon.draw(new Canvas(bmp));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 80, out);
            bmp.recycle();
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private final class Bridge {
        @JavascriptInterface public String getApps() {
            try {
                PackageManager pm = getPackageManager();
                List<ResolveInfo> all = new ArrayList<>(
                        pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
                all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER), 0));
                Set<String> seen = new HashSet<>();
                List<ResolveInfo> apps = new ArrayList<>();
                for (ResolveInfo info : all) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (!pkg.equals(getPackageName()) && seen.add(pkg)) apps.add(info);
                }
                apps.sort(Comparator.comparing(
                        info -> String.valueOf(info.loadLabel(pm)), String.CASE_INSENSITIVE_ORDER));
                JSONArray result = new JSONArray();
                for (ResolveInfo info : apps) {
                    String name = String.valueOf(info.loadLabel(pm));
                    if (name.length() > 64) name = name.substring(0, 64);
                    JSONObject row = new JSONObject();
                    row.put("name", name);
                    row.put("pkg", info.activityInfo.packageName);
                    row.put("iconData", iconAsMask(info.loadIcon(pm)));
                    row.put("accent", "#15F5BA");
                    result.put(row);
                }
                if (result.length() == 0)
                    result.put(new JSONObject().put("name", "Configurações").put("pkg", "__settings__"));
                return result.toString();
            } catch (Exception e) {
                return "[{\"name\":\"Configurações\",\"pkg\":\"__settings__\"}]";
            }
        }

        @JavascriptInterface public void launchApp(String pkg) {
            if (pkg == null || pkg.length() > 255) return;
            if ("__settings__".equals(pkg)) { openSettings("system"); return; }
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) startExternal(intent);
        }

        @JavascriptInterface public String getInputs() {
            JSONArray result = new JSONArray();
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            try {
                if (manager != null) for (TvInputInfo info : manager.getTvInputList()) {
                    int state = manager.getInputState(info.getId());
                    JSONObject row = new JSONObject();
                    row.put("id", info.getId());
                    row.put("port", info.getType() == TvInputInfo.TYPE_HDMI ? "HDMI" : "TV");
                    row.put("title", String.valueOf(info.loadLabel(DesignLauncherActivity.this)));
                    row.put("status", state == TvInputManager.INPUT_STATE_CONNECTED
                            ? "Conectado" : "Indisponível");
                    row.put("dot", state == TvInputManager.INPUT_STATE_CONNECTED ? "#15F5BA" : "#555");
                    result.put(row);
                }
            } catch (RuntimeException | org.json.JSONException ignored) {
                // OEMs may hide input APIs.
            }
            if (result.length() == 0) try {
                result.put(new JSONObject().put("id", "").put("port", "Entradas")
                        .put("title", "Abrir entradas da TV").put("status", ""));
            } catch (org.json.JSONException ignored) { }
            return result.toString();
        }

        @JavascriptInterface public void openInput(String id) {
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (id != null && manager != null) try {
                for (TvInputInfo info : manager.getTvInputList()) {
                    if (id.equals(info.getId())) {
                        startExternal(new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(id)));
                        return;
                    }
                }
            } catch (RuntimeException ignored) { }
            openSettings("inputs");
        }

        @JavascriptInterface public void openSettings(String action) {
            String selected;
            switch (action == null ? "" : action) {
                case "wifi": selected = Settings.ACTION_WIFI_SETTINGS; break;
                case "display": selected = Settings.ACTION_DISPLAY_SETTINGS; break;
                case "apps": selected = Settings.ACTION_APPLICATION_SETTINGS; break;
                case "home": selected = Settings.ACTION_HOME_SETTINGS; break;
                case "inputs": selected = "android.settings.TV_INPUT_SETTINGS"; break;
                default: selected = Settings.ACTION_SETTINGS;
            }
            startExternal(new Intent(selected));
        }

        @JavascriptInterface public String getNetworkLabel() {
            ConnectivityManager c = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            return c != null && c.getActiveNetwork() != null ? "Rede conectada" : "Sem conexão";
        }

        @JavascriptInterface public String getIpAddress() {
            try {
                Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
                while (list.hasMoreElements()) {
                    NetworkInterface n = list.nextElement();
                    if (!n.isUp() || n.isLoopback()) continue;
                    Enumeration<java.net.InetAddress> addresses = n.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address && !address.isLoopbackAddress())
                            return address.getHostAddress();
                    }
                }
            } catch (Exception ignored) { }
            return "Não disponível";
        }

        @JavascriptInterface public String getOptions() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString("options", "{}");
        }

        @JavascriptInterface public void saveOptions(String json) {
            if (json == null || json.length() > 8192) return;
            try {
                JSONObject options = new JSONObject(json);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("options", options.toString()).apply();
            } catch (org.json.JSONException ignored) { }
        }
    }
}
