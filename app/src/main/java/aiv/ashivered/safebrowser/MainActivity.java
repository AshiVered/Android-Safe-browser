package aiv.ashivered.safebrowser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

/**
 * MainActivity – the single-Activity browser built on GeckoView (Mozilla Firefox engine).
 *
 * Responsibilities:
 *  1. Initialise GeckoRuntime (singleton) and GeckoSession.
 *  2. Fetch the domain whitelist from the server and enforce it on every navigation.
 *  3. Provide a 3-dot overflow menu: Settings, Feedback, Share, About.
 *  4. Handle Android "default browser" intents (ACTION_VIEW with http/https).
 *  5. Block images/videos via an installable GeckoView Web Extension.
 *  6. Show a Terms of Use dialog on the very first launch.
 *  7. Download files via DownloadManager.
 */
public class MainActivity extends AppCompatActivity {

    // ─── GeckoView singletons ────────────────────────────────────────────────
    /** Singleton GeckoRuntime – must never be created more than once per process. */
    private static GeckoRuntime sRuntime;
    /** Singleton Web Extension that hides images/video (installed when photos=ON). */
    private static WebExtension sMediaBlockerExtension = null;

    private GeckoSession session;
    private GeckoView geckoView;
    private ProgressBar progressBar;
    private boolean canGoBack = false;
    /** Tracks the "news" setting at last load so onResume can detect changes. */
    private boolean wasNoNews = false;

    // ─── App preferences ─────────────────────────────────────────────────────
    /** General SharedPreferences via PreferenceManager (settings switches). */
    private SharedPreferences sp;
    /** Key for "user has accepted terms" flag (stored in a separate prefs file). */
    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String KEY_ACCEPTED = "acceptedTerms";

    // ─── Domain whitelist ─────────────────────────────────────────────────────
    /** Set of allowed hostnames populated from the server JSON. */
    private final Set<String> whitehosts = new HashSet<>();

    /**
     * Hosts that are ALWAYS allowed regardless of the server whitelist.
     * Includes the app's own home-page domain, feedback form, and infrastructure.
     */
    private static final Set<String> HARDCODED_HOSTS = new HashSet<String>() {{
        add("ashivered.github.io"); // home page + whitelist JSON source
        add("docs.google.com");     // feedback form
        add("forms.gle");           // Google Forms short URL
    }};

    private String blockedDomain = "";

    // ─── Remote resource URLs ────────────────────────────────────────────────
    private static final String URL_WHITELIST_NEWS   = "https://ashivered.github.io/SafeBrowserResources/list_news.json";
    private static final String URL_WHITELIST_NONEWS = "https://ashivered.github.io/SafeBrowserResources/list_nonews.json";
    private static final String URL_HOME             = "https://ashivered.github.io/SafeBrowserResources/index.html";
    private static final String URL_HOME_NONEWS      = "https://ashivered.github.io/SafeBrowserResources/index_nonews.html";
    private static final String URL_FEEDBACK         = "https://docs.google.com/forms/d/e/1FAIpQLScrkV2nmeszXD5kdeyIZT1Z4H3XeRx3r2W59Np_bO72Rjwhxw/viewform?usp=header";

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Attach the Toolbar as the ActionBar so the 3-dot menu works
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        geckoView = findViewById(R.id.geckoview);
        progressBar = findViewById(R.id.progress_bar);

        initGeckoSession();

        // Show Terms of Use dialog on very first launch
        SharedPreferences firstRunPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!firstRunPrefs.getBoolean(KEY_ACCEPTED, false)) {
            showTermsDialog();
        } else {
            loadWhitelistAndNavigate();
        }
    }

    /** Load whitelist + navigate to home (or incoming URL). */
    private void loadWhitelistAndNavigate() {

        boolean noNews = sp.getBoolean("news", false);
        wasNoNews = noNews;
        String whitelistUrl = noNews ? URL_WHITELIST_NONEWS : URL_WHITELIST_NEWS;
        String homeUrl      = noNews ? URL_HOME_NONEWS      : URL_HOME;

        // Check for an incoming http/https Intent (launched as default browser)
        String intentUrl = getIncomingUrl();

        // Load whitelist first (async), then navigate
        loadWhitelist(whitelistUrl, () -> runOnUiThread(() -> {
            if (intentUrl != null) {
                handleIncomingUrl(intentUrl);
            } else {
                session.loadUri(homeUrl);
            }
        }));
    }

    // ─── Terms of Use dialog ──────────────────────────────────────────────────

    /**
     * Shows the Terms of Use dialog on first launch.
     * Accept  → saves accepted flag, initialises the browser.
     * Decline → closes the app.
     */
    private void showTermsDialog() {
        // Build HTML message with a clickable link
        CharSequence rawHtml = HtmlCompat.fromHtml(
                getString(R.string.terms_of_use_message),
                HtmlCompat.FROM_HTML_MODE_COMPACT);
        SpannableString spannable = new SpannableString(rawHtml);

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        // Make the link open in our browser and move dialog to bottom
        ClickableSpan[] existingSpans = spannable.getSpans(0, spannable.length(), ClickableSpan.class);
        for (ClickableSpan span : existingSpans) {
            int start = spannable.getSpanStart(span);
            int end   = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    String termsUrl = "https://ashivered.github.io/SafeBrowserResources/terms";
                    session.loadUri(termsUrl);
                    
                    if (dialogHolder[0] != null && dialogHolder[0].getWindow() != null) {
                        dialogHolder[0].getWindow().setGravity(android.view.Gravity.BOTTOM);
                        dialogHolder[0].getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    }
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.terms_of_use_title);
        builder.setCancelable(false); // force the user to choose

        TextView messageView = new TextView(this);
        messageView.setText(spannable);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setTextSize(16);
        int p = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        messageView.setPadding(p, p, p, p);
        builder.setView(messageView);

        builder.setPositiveButton(R.string.accept, (dialog, which) -> {
            // Save acceptance and start the browser
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ACCEPTED, true).apply();
            loadWhitelistAndNavigate();
        });
        builder.setNegativeButton(R.string.decline, (dialog, which) -> finish());
        
        dialogHolder[0] = builder.create();
        dialogHolder[0].show();
    }

    // ─── GeckoView initialisation ─────────────────────────────────────────────

    /**
     * Creates a new GeckoSession, sets delegates, opens the GeckoRuntime (singleton),
     * and attaches the session to the GeckoView widget.
     */
    private void initGeckoSession() {
        session = new GeckoSession();

        // ProgressDelegate – shows/hides the progress bar on page load
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession s, @NonNull String url) {
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
            }

            @Override
            public void onPageStop(@NonNull GeckoSession s, boolean success) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        });

        // ContentDelegate – intercepts file downloads (non-browser content)
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onExternalResponse(@NonNull GeckoSession s, @NonNull WebResponse response) {
                String contentDisposition = response.headers.get("Content-Disposition");
                String contentType        = response.headers.get("Content-Type");
                downloadFile(response.uri, contentDisposition, contentType);
            }
        });

        // NavigationDelegate – enforces the whitelist on every page navigation
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @NonNull
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession s,
                                                          @NonNull LoadRequest request) {
                try {
                    String uriStr = request.uri;
                    // Always allow internal URIs (javascript:, about:, data:)
                    if (uriStr.startsWith("javascript:") ||
                            uriStr.startsWith("about:") ||
                            uriStr.startsWith("data:")) {
                        return GeckoResult.allow();
                    }
                    URI uri = new URI(uriStr);
                    String host = uri.getHost();
                    if (host != null && isHostAllowed(host)) {
                        return GeckoResult.allow();
                    } else {
                        blockedDomain = (host != null) ? host : uriStr;
                        runOnUiThread(MainActivity.this::blockString);
                        return GeckoResult.deny();
                    }
                } catch (Exception e) {
                    Log.e("NavDelegate", "URI parse error: " + e.getMessage());
                    return GeckoResult.deny();
                }
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession s, boolean canGoBackNow) {
                canGoBack = canGoBackNow;
            }
        });

        // GeckoRuntime is a singleton – create once per process
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this);
        }

        session.open(sRuntime);
        geckoView.setSession(session);

        // Sync media-blocker extension state with the "photos" preference
        updateMediaBlocker();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Called when returning from SettingsActivity.
     * Detects if the "block news" setting changed and reloads whitelist+home accordingly.
     * Also syncs the media-blocker extension.
     */
    @Override
    protected void onResume() {
        super.onResume();
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (session == null) return; // not yet initialised (terms not accepted)
        updateMediaBlocker();
        boolean noNews = sp.getBoolean("news", false);
        if (noNews != wasNoNews) {
            wasNoNews = noNews;
            String whitelistUrl = noNews ? URL_WHITELIST_NONEWS : URL_WHITELIST_NEWS;
            final String homeUrl = noNews ? URL_HOME_NONEWS : URL_HOME;
            loadWhitelist(whitelistUrl, () -> runOnUiThread(() -> session.loadUri(homeUrl)));
        }
    }

    // ─── Media Blocker (GeckoView Web Extension) ──────────────────────────────

    /**
     * Installs or uninstalls the media-blocker extension based on the "photos" setting.
     *
     * Extension files: assets/media_blocker/manifest.json + content.js
     * When installed, the content script injects CSS to hide all images and videos
     * at document_start, before any content is rendered.
     */
    private void updateMediaBlocker() {
        boolean blockMedia = sp.getBoolean("photos", false);
        if (sRuntime == null) return;

        if (blockMedia && sMediaBlockerExtension == null) {
            sRuntime.getWebExtensionController()
                    .installBuiltIn("resource://android/assets/media_blocker/")
                    .then(extension -> {
                        sMediaBlockerExtension = extension;
                        return null;
                    }, e -> {
                        Log.e("MediaBlocker", "Install failed: " + e.getMessage());
                        return null;
                    });
        } else if (!blockMedia && sMediaBlockerExtension != null) {
            sRuntime.getWebExtensionController()
                    .uninstall(sMediaBlockerExtension)
                    .then(unused -> {
                        sMediaBlockerExtension = null;
                        return null;
                    }, e -> {
                        Log.e("MediaBlocker", "Uninstall failed: " + e.getMessage());
                        return null;
                    });
        }
    }

    // ─── Default Browser – incoming URL handling ──────────────────────────────

    /** Returns the URL from an ACTION_VIEW intent, or null if launched normally. */
    private String getIncomingUrl() {
        Intent intent = getIntent();
        if (intent != null
                && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null) {
            return intent.getData().toString();
        }
        return null;
    }

    /**
     * Validates an incoming URL against the whitelist.
     * Allowed  → load it in GeckoView.
     * Blocked  → show block toast and close activity.
     */
    private void handleIncomingUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && isHostAllowed(host)) {
                session.loadUri(url);
            } else {
                blockedDomain = (host != null) ? host : url;
                blockString();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_loading_list), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ─── 3-dot Overflow Menu ──────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            // Settings may be password-protected
            if (PasswordUtils.isSettingsLockEnabled(this)) {
                promptForPasswordAndOpenSettings();
            } else {
                openSettingsActivity();
            }
            return true;
        } else if (id == R.id.menu_feedback) {
            session.loadUri(URL_FEEDBACK);
            return true;
        } else if (id == R.id.menu_share) {
            shareApp();
            return true;
        } else if (id == R.id.menu_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Opens a system share sheet to share the app. */
    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
    }

    // ─── Settings password gate ───────────────────────────────────────────────

    /**
     * Shows a password dialog before opening SettingsActivity.
     * Only displayed when lock_settings is enabled in SharedPreferences.
     */
    private void promptForPasswordAndOpenSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enter_password_title);
        builder.setMessage(R.string.settings_locked);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.password_hint);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p, p, p, p);
        input.setLayoutParams(lp);
        container.addView(input);
        builder.setView(container);
        builder.setPositiveButton(R.string.enter, null);
        builder.setNegativeButton(R.string.cancel, (d, w) -> d.cancel());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (PasswordUtils.checkPassword(this, input.getText().toString())) {
                    dialog.dismiss();
                    openSettingsActivity();
                } else {
                    input.setError(getString(R.string.incorrect_password));
                }
            });
        });
        dialog.show();
    }

    private void openSettingsActivity() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ─── Blocking ─────────────────────────────────────────────────────────────

    /**
     * Shows a toast when a blocked domain is navigated to.
     * If the "show URL" setting is on, the domain name is included in the message.
     */
    public void blockString() {
        boolean showUrl = sp.getBoolean("URL", false);
        if (showUrl && !blockedDomain.isEmpty()) {
            Toast.makeText(this, blockedDomain + " — " + getString(R.string.blocked_page),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.blocked_page, Toast.LENGTH_LONG).show();
        }
    }

    // ─── Whitelist logic ──────────────────────────────────────────────────────

    /**
     * Returns true if the given host is allowed.
     * Checks hardcoded infrastructure hosts first, then the server whitelist.
     * Supports subdomain matching: "sub.example.com" matches whitelist entry "example.com".
     */
    private boolean isHostAllowed(String host) {
        String h = host.toLowerCase();
        if (HARDCODED_HOSTS.contains(h)) return true;
        for (String hardcoded : HARDCODED_HOSTS) {
            if (h.endsWith("." + hardcoded)) return true;
        }
        if (whitehosts.contains(h)) return true;
        for (String allowed : whitehosts) {
            if (h.endsWith("." + allowed)) return true;
        }
        return false;
    }

    /**
     * Fetches and parses the JSON whitelist from the server on a background thread.
     * Calls {@code onSuccess} on the main thread after parsing is complete.
     *
     * JSON format: [{"host": "example.com"}, ...]
     */
    private void loadWhitelist(String whitelistUrl, Runnable onSuccess) {
        whitehosts.clear();
        new Thread(() -> {
            try {
                URL url = new URL(whitelistUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();
                parseWhitelistJson(response.toString());
                new Handler(Looper.getMainLooper()).post(onSuccess);
            } catch (Exception e) {
                Log.e("Whitelist", "Error loading whitelist: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.error_loading_list),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Parses the JSON array and populates {@link #whitehosts}. */
    private void parseWhitelistJson(String jsonStr) throws JSONException {
        JSONArray array = new JSONArray(jsonStr);
        whitehosts.clear();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            whitehosts.add(obj.getString("host").toLowerCase());
        }
    }

    // ─── File Downloads ───────────────────────────────────────────────────────

    /**
     * Enqueues a file download using Android's DownloadManager.
     * Handles blob: and data: URIs gracefully (skips them).
     * Applies best-effort filename resolution from Content-Disposition and URL.
     */
    private void downloadFile(String url, String contentDisposition, String mimeType) {
        if (url.startsWith("blob:") || url.startsWith("data:")) return;
        try {
            String filename = getBestFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.addRequestHeader("User-Agent",
                    "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0");
            request.setTitle(filename);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, getString(R.string.downloading) + " " + filename,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Attempts to determine the best filename for a download.
     * Priority: Content-Disposition (filename*=) → Content-Disposition (filename=) →
     *           URL path segment → URLUtil guess.
     */
    private String getBestFileName(String url, String contentDisposition, String mimeType) {
        String filename = null;
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            for (String part : contentDisposition.split(";")) {
                part = part.trim();
                if (part.toLowerCase().startsWith("filename*=")) {
                    String encoded = part.substring(10);
                    if (encoded.toLowerCase().startsWith("utf-8''")) encoded = encoded.substring(7);
                    try { filename = URLDecoder.decode(encoded, "UTF-8"); }
                    catch (UnsupportedEncodingException e) { filename = encoded; }
                    break;
                }
            }
            if (filename == null) {
                for (String part : contentDisposition.split(";")) {
                    part = part.trim();
                    if (part.toLowerCase().startsWith("filename=")) {
                        String name = part.substring(9).replace("\"", "").replace("'", "");
                        if (!name.isEmpty()) { filename = name; break; }
                    }
                }
            }
        }
        if (filename == null || filename.trim().isEmpty()) {
            try {
                String decoded = URLDecoder.decode(url, "UTF-8");
                if (decoded.contains("?")) decoded = decoded.substring(0, decoded.indexOf("?"));
                if (!decoded.endsWith("/")) filename = decoded.substring(decoded.lastIndexOf('/') + 1);
            } catch (UnsupportedEncodingException ignored) { }
        }
        if (filename == null || filename.trim().isEmpty()) {
            filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        }
        if (filename != null && !filename.contains(".")) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null) filename = filename + "." + ext;
        }
        return filename;
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    /** Navigates back within GeckoView, or exits the Activity if no history. */
    @Override
    public void onBackPressed() {
        if (canGoBack) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }
}