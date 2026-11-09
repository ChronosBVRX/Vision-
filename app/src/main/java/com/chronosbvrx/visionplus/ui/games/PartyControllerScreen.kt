package com.chronosbvrx.visionplus.ui.games

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chronosbvrx.visionplus.data.party.PartyNetStack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.chronosbvrx.visionplus.data.party.PartyConfig
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import kotlinx.coroutines.delay

/**
 * Mando de Vision+ Party dentro de la app.
 *
 * El enlace `visionplus://play/join?room=…` entra aquí: se abre el controlador
 * web en un WebView a pantalla completa, sin barra de navegador. Así el QR
 * tiene prioridad para la app instalada; si no está, el puente `/join.html`
 * ofrece el controlador en el navegador.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PartyControllerScreen(roomCode: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var destroyed by remember { mutableStateOf(false) }

    fun close() {
        if (!destroyed) {
            destroyed = true
            webView?.apply {
                runCatching { (parent as? ViewGroup)?.removeView(this) }
                runCatching { stopLoading() }
                runCatching { loadUrl("about:blank") }
                runCatching { destroy() }
            }
            webView = null
        }
        view.keepScreenOn = false
        onBack()
    }

    BackHandler { close() }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(webView) {
        delay(120)
        webView?.requestFocus(View.FOCUS_DOWN)
    }

    // The same composable is reused when a deep link replaces the room code.
    // Without this effect the Activity kept the old WebView and the old game.
    LaunchedEffect(webView, roomCode) {
        webView?.loadUrl(PartyConfig.controllerUrl(context, roomCode))
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.rgb(7, 11, 19))
                    keepScreenOn = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setAllowFileAccessFromFileURLs(false)
                    settings.setAllowUniversalAccessFromFileURLs(false)
                    settings.setSaveFormData(false)
                    settings.textZoom = 100
                    settings.setGeolocationEnabled(false)
                    settings.setNeedInitialFocus(false)
                    settings.setOffscreenPreRaster(true)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.requestFocus(View.FOCUS_DOWN)
                        }
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            val hostBase = PartyConfig.host(context)
                            if (!PartyNetStack.shouldIntercept(
                                    hostBase,
                                    reqUrl,
                                    request.method ?: "GET",
                                    request.requestHeaders ?: emptyMap(),
                                )
                            ) {
                                return null
                            }
                            val range = request.requestHeaders?.entries
                                ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                            return PartyNetStack.fetch(hostBase, reqUrl, range)
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            details: android.webkit.RenderProcessGoneDetail?,
                        ): Boolean {
                            runCatching { view?.destroy() }
                            webView = null
                            return true
                        }
                    }
                    webView = this
                    requestFocus(View.FOCUS_DOWN)
                }
            },
            update = { it.keepScreenOn = true },
            onRelease = { view ->
                if (!destroyed) {
                    destroyed = true
                    runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                    runCatching { view.stopLoading() }
                    runCatching { view.loadUrl("about:blank") }
                    runCatching { view.destroy() }
                }
                webView = null
            },
        )
    }
}
