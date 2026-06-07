package com.chronosbvrx.vision;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String LIVE_ENTRY_URL = "https://chronosbvrx.github.io/Vision-/";

    private WebView mWebView;
    private FrameLayout mCustomViewContainer;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private View mCustomView;
    private MyWebChromeClient mWebChromeClient;
    
    private long lastBackPressTime = 0;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fullscreen and hide action bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        // Prevent screen sleep while playing video
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.webview);
        mCustomViewContainer = findViewById(R.id.custom_view_container);

        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // [opencode] Always resolve the current live redirect instead of reusing an old packaged/cached build.
        mWebView.clearCache(true);
        mWebView.clearHistory();
        WebStorage.getInstance().deleteAllData();
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance()
                    .getServiceWorkerWebSettings()
                    .setCacheMode(WebSettings.LOAD_NO_CACHE);
        }
        
        // Viewport scale optimizations
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        
        // Better rendering
        mWebView.setScrollbarFadingEnabled(true);
        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        
        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(mWebView, true);
        }

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep navigation within WebView
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showErrorPage(view);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request.isForMainFrame()) {
                        showErrorPage(view);
                    }
                }
            }
        });

        mWebChromeClient = new MyWebChromeClient();
        mWebView.setWebChromeClient(mWebChromeClient);

        // Crucial for Android TV: request focus so key events go to WebView
        mWebView.setFocusable(true);
        mWebView.setFocusableInTouchMode(true);
        mWebView.requestFocus();

        mWebView.loadUrl(buildLiveEntryUrl());
    }

    private String buildLiveEntryUrl() {
        return LIVE_ENTRY_URL + "?apk=true&tv=true&_live=" + System.currentTimeMillis();
    }

    // Inner class to handle HTML5 Video Fullscreen
    private class MyWebChromeClient extends WebChromeClient {

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            // If a view already exists, hide it and replace it
            if (mCustomView != null) {
                onHideCustomView();
                return;
            }

            mCustomView = view;
            mCustomViewCallback = callback;
            
            // Hide the web view
            mWebView.setVisibility(View.GONE);
            
            // Add custom view (video) to layout and show it
            mCustomViewContainer.addView(mCustomView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            mCustomViewContainer.setVisibility(View.VISIBLE);
            
            // Lock landscape orientation for video playback
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView == null) {
                return;
            }

            // Hide custom view and remove it
            mCustomViewContainer.setVisibility(View.GONE);
            mCustomViewContainer.removeView(mCustomView);
            mCustomView = null;
            
            if (mCustomViewCallback != null) {
                mCustomViewCallback.onCustomViewHidden();
            }

            // Show WebView again
            mWebView.setVisibility(View.VISIBLE);
            
            // Restore default orientation
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            
            mWebView.requestFocus();
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            // Auto grant permissions (like microphone/camera/etc.) if requested
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request.grant(request.getResources());
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN) {
            // If custom view (fullscreen video) is open, exit fullscreen first
            if (mCustomView != null) {
                mWebChromeClient.onHideCustomView();
                return true;
            }

            // Dispatch GoBack keydown to the WebView.
            // main.jsx catches GoBack (keyCode 461) and converts it to Escape.
            // This matches what Android TV remotes generate natively.
            mWebView.evaluateJavascript(
                "window.dispatchEvent(new KeyboardEvent('keydown', { key: 'GoBack', code: 'GoBack', keyCode: 461, bubbles: true }));",
                null
            );

            // Double-back-exit: 2 presses within 3 seconds
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < 3000 && lastBackPressTime > 0) {
                if (backToast != null) {
                    backToast.cancel();
                }
                finish();
            } else {
                lastBackPressTime = currentTime;
                backToast = Toast.makeText(MainActivity.this, "Presiona ATRÁS nuevamente para salir", Toast.LENGTH_SHORT);
                backToast.show();
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void showErrorPage(WebView view) {
        String errorHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<style>" +
            "body { background-color: #070709; color: #ffffff; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }" +
            "h2 { color: #e50914; font-size: 1.6rem; margin-bottom: 8px; font-weight: 700; }" +
            "p { color: #a0a0a0; font-size: 0.95rem; margin-bottom: 24px; max-width: 80%; line-height: 1.5; }" +
            ".btn { background-color: #7c3aed; color: #ffffff; border: none; padding: 12px 28px; font-size: 1rem; font-weight: bold; border-radius: 8px; cursor: pointer; outline: none; box-shadow: 0 4px 15px rgba(124, 58, 237, 0.4); transition: background 0.2s, transform 0.2s; }" +
            ".btn:focus, .btn:hover { background-color: #9061f9; box-shadow: 0 0 0 3px #ffffff; transform: scale(1.05); }" +
            "</style></head><body>" +
            "<h2>Error de Conexión</h2>" +
            "<p>No se pudo conectar al servidor de Vision+. Verifica la conexión a Internet de tu TV e intenta de nuevo.</p>" +
            "<button class='btn' onclick='window.location.reload()' autofocus>Reintentar Conexión</button>" +
            "<script>setTimeout(function() { document.querySelector('.btn').focus(); }, 100);</script>" +
            "</body></html>";
        view.loadDataWithBaseURL("file:///android_asset/", errorHtml, "text/html", "UTF-8", null);
    }
}
