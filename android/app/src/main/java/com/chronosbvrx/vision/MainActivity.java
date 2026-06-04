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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

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
        
        // Cache settings
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Better rendering
        mWebView.setScrollbarFadingEnabled(true);
        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        
        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        }

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep navigation within WebView
                view.loadUrl(url);
                return true;
            }
        });

        mWebChromeClient = new MyWebChromeClient();
        mWebView.setWebChromeClient(mWebChromeClient);

        // Crucial for Android TV: request focus so key events go to WebView
        mWebView.setFocusable(true);
        mWebView.setFocusableInTouchMode(true);
        mWebView.requestFocus();

        mWebView.loadUrl("https://chronosbvrx.github.io/Vision-/");
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

            // Inject Escape and Backspace events into the WebView page
            // This allows the web app to catch them and close details modals/players
            mWebView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }));", null);
            mWebView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', code: 'Backspace', keyCode: 8, bubbles: true }));", null);

            // Toast / Double press exit mechanism
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < 2000) {
                if (backToast != null) {
                    backToast.cancel();
                }
                finish();
            } else {
                backToast = Toast.makeText(this, "Presiona ATRÁS nuevamente para salir", Toast.LENGTH_SHORT);
                backToast.show();
                lastBackPressTime = currentTime;
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }
}
