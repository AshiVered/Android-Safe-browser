package aiv.ashivered.safebrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LoadUrl extends Activity {
    private final int STORAGE_PERMISSION_CODE = 1;
    private WebView mWebView;
    private Button settingsButton;
    private SharedPreferences sp;
    private List<String> whiteHosts = new ArrayList<>();
    private String domain;
    private TextView websiteName;

    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestStoragePermission();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_url); // Ensure this is the correct layout

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        // Determine URL based on SharedPreferences
        boolean noNews = sp.getBoolean("news", false);
        String urlToLoad = noNews ? "https://ashivered.github.io/SafeBrowserResources/list_nonews.txt" : "https://ashivered.github.io/SafeBrowserResources/list_news.txt";

        // Load whiteHosts from the URL specified in SharedPreferences
        new Thread(() -> {
            whiteHosts = loadWhiteHostsFromUrl(urlToLoad);
            runOnUiThread(this::initializeWebView);
        }).start();

        // Initialize settings button
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> openSettingsActivity());
    }

    private void initializeWebView() {
        mWebView = findViewById(R.id.activity_load_url_webview);
        mWebView.addJavascriptInterface(new GetTitleUsingJs(), "AndroidFunction");
        websiteName = findViewById(R.id.website_name);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setPluginState(WebSettings.PluginState.ON);
        webSettings.setAllowFileAccess(false);
        mWebView.setWebViewClient(new HelloWebViewClient());

        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri source = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(source);
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Downloading File...");
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, R.string.downloading, Toast.LENGTH_LONG).show();
        });

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String host = Uri.parse(data.toString()).getHost();
                domain = Uri.parse(data.toString()).getHost();
                if (whiteHosts.contains(host)) {
                    mWebView.loadUrl(data.toString());
                } else {
                    blockString();
                    finish();
                }
            }
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed to download files")
                    .setPositiveButton("ok", (dialog, which) -> ActivityCompat.requestPermissions(LoadUrl.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE))
                    .setNegativeButton("cancel", (dialog, which) -> dialog.dismiss())
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    private List<String> loadWhiteHostsFromUrl(String urlString) {
        List<String> hosts = new ArrayList<>();
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                hosts.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return hosts;
    }

    private void blockString() {
        boolean showUrl = sp.getBoolean("URL", false);
        if (showUrl) {
            Toast.makeText(this, domain + " " + getString(R.string.blocked_page), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.blocked_page, Toast.LENGTH_LONG).show();
        }
    }

    private class HelloWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            // Clear the website name and icon at the start of loading a new page
            websiteName.setText("");
        }
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Boolean photos = sp.getBoolean("photos", false);
            WebSettings webFilters = mWebView.getSettings();
            String host = Uri.parse(url).getHost();
            domain = Uri.parse(url).getHost();
            if (whiteHosts.contains(host)) {
                if (photos) {
                    webFilters.setLoadsImagesAutomatically(false);
                    return false;
                } else {
                    return false;
                }
            } else {
                blockString();
                return true;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Boolean photosInFinish = sp.getBoolean("photos", false);
            super.onPageFinished(view, url);
            view.loadUrl("javascript:window.AndroidFunction.setTitle(document.title);");

            if (photosInFinish) {
                view.loadUrl("javascript: (() => { function handle(node) { if (node.tagName === 'IMG' && node.style.visibility !== 'hidden' && node.width > 32 && node.height > 32) { const blankImageUrl = 'data:image/gif;base64,R0lGODlhAQABAIAAAP///////yH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='; const { width, height } = window.getComputedStyle(node); node.src = blankImageUrl; node.style.visibility = 'hidden'; node.style.background = 'none'; node.style.backgroundImage = `url(${blankImageUrl})`; node.style.width = width; node.style.height = height; } else if (node.tagName === 'VIDEO' || node.tagName === 'IFRAME' || ((!node.type || node.type.includes('video')) && node.tagName === 'SOURCE') || node.tagName === 'OBJECT') { node.remove(); } } document.querySelectorAll('img,video,source,object,embed,iframe,[type^=video]').forEach(handle); const observer = new MutationObserver((mutations) => mutations.forEach((mutation) => mutation.addedNodes.forEach(handle))); observer.observe(document.body, { childList: true, subtree: true }); })();");
            }
        }

    }
    private class GetTitleUsingJs {
        @JavascriptInterface
        public void setTitle(String title) {
            runOnUiThread(() -> websiteName.setText(title));
        }
    }
}
