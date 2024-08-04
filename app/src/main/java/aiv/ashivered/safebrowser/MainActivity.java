package aiv.ashivered.safebrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int STORAGE_PERMISSION_CODE = 1;
    private WebView mWebView;
    private Button settingsButton;
    private SharedPreferences sp;
    private List<String> whiteHosts = new ArrayList<>();
    private String domain;
    private TextView websiteName;

    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String KEY_ACCEPTED = "acceptedTerms";

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "MissingInflatedId"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrashLogToFile(throwable);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });

        // Request permissions
        requestStoragePermission();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean accepted = settings.getBoolean(KEY_ACCEPTED, false);

        if (!accepted) {
            showTermsDialog();
        }

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean noNews = sp.getBoolean("news", false);
        String urlToLoad = noNews ? "https://ashivered.github.io/SafeBrowserResources/list_nonews.txt" : "https://ashivered.github.io/SafeBrowserResources/list_news.txt";

        new LoadHostsTask().execute(urlToLoad);

        mWebView = findViewById(R.id.activity_main_webview);
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

        if (noNews) {
            mWebView.loadUrl("https://ashivered.github.io/SafeBrowserResources/index_nonews.html"); //Replace The Link Here
        } else {
            mWebView.loadUrl("https://ashivered.github.io/SafeBrowserResources/index.html"); //Replace The Link Here
        }

        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> openSettingsActivity());
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission needed")
                        .setMessage("This permission is needed to write log files")
                        .setPositiveButton("ok", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE))
                        .setNegativeButton("cancel", (dialog, which) -> dialog.dismiss())
                        .create().show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            }
        } else {
            writeLogToFile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                writeLogToFile();
            } else {
                Log.e("MainActivity", "Permission denied");
            }
        }
    }

    private void writeLogToFile() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log.txt");

            try (FileWriter fileWriter = new FileWriter(logFile, true)) {
                Process process = Runtime.getRuntime().exec("logcat -d");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    fileWriter.write(line + "\n");
                }

                fileWriter.flush();
                Log.i("MainActivity", "Log written to " + logFile.getAbsolutePath());

            } catch (IOException e) {
                Log.e("MainActivity", "Error writing log to file", e);
            }
        } else {
            Log.e("MainActivity", "External storage not available");
        }
    }

    private void writeCrashLogToFile(Throwable throwable) {
        File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log.txt");
        try (FileWriter fileWriter = new FileWriter(logFile, true)) {
            fileWriter.write("Crash occurred at: " + System.currentTimeMillis() + "\n");
            fileWriter.write("Exception: " + throwable.toString() + "\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                fileWriter.write("    at " + element.toString() + "\n");
            }
            fileWriter.write("\n");
        } catch (IOException e) {
            Log.e("MainActivity", "Error writing crash log to file", e);
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private class HelloWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
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

    private class LoadHostsTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... urls) {
            List<String> hosts = new ArrayList<>();
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    hosts.add(line);
                }
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return hosts;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            whiteHosts.addAll(result);
            System.out.println(whiteHosts);
        }
    }

    private void showTermsDialog() {
        final TextView message = new TextView(this);
        message.setText(getClickableSpan());
        message.setMovementMethod(LinkMovementMethod.getInstance());
        int padding = (int) (16 * getResources().getDisplayMetrics().density); // מרווח 16dp
        message.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(R.string.terms_of_use_title)
                .setView(message)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean(KEY_ACCEPTED, true);
                        editor.apply();
                    }
                })
                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private Spannable getClickableSpan() {
        String termsText = getString(R.string.terms_of_use_message);
        SpannableString spannableString = new SpannableString(HtmlCompat.fromHtml(termsText, HtmlCompat.FROM_HTML_MODE_LEGACY));
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                String url = Locale.getDefault().getLanguage().equals("he") ?
                        "https://ashivered.github.io/SafeBrowserResources/terms" :
                        "https://ashivered.github.io/SafeBrowserResources/terms_en";
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        };

        String linkText = Locale.getDefault().getLanguage().equals("he") ?
                "תנאי השימוש" : "terms of use";

        int start = termsText.indexOf(linkText);
        int end = start + linkText.length();

        if (start >= 0 && end <= spannableString.length()) {
            spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            Log.e("MainActivity", "Invalid span indices: start=" + start + " end=" + end);
        }

        return spannableString;
    }


    public void blockString() {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean url = sp.getBoolean("URL", false);
        if (url) {
            Toast.makeText(this, domain + " " + getString(R.string.blocked_page), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.blocked_page, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
