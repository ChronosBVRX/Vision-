package com.chronosbvrx.visionplus.ui.games

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.View
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.data.party.PartyConfig
import com.chronosbvrx.visionplus.data.party.PartyNetStack
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * Host de Vision+ Party dentro de la app.
 *
 * La tele muestra la sala, el QR y el juego; los teléfonos entran por el
 * navegador. El APK no carga assets de juegos ni audios: todo vive en el
 * servidor, así que el tamaño se mantiene bajo.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PartyHostScreen(gameId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lowPower = remember(context) { DevicePerformance.shouldReduceEffects(context) }
    val url = remember(gameId, lowPower) { PartyConfig.hostUrl(context, gameId, lowPower) }
    val gameTitle = remember(gameId) {
        PARTY_GAMES.firstOrNull { it.id == gameId }?.title ?: "Vision+ Party"
    }
    val exitFocus = remember { FocusRequester() }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewDestroyed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    // La máscara de carga no depende solo de onPageFinished (en algunos
    // WebView con socket.io en vivo ese evento nunca llega y la pantalla
    // quedaba blanca para siempre). Tope absoluto de 12 s: pasado ese
    // tiempo la sala se ve aunque el evento no haya llegado.
    LaunchedEffect(gameId, reloadToken) {
        loading = true
        delay(12_000)
        loading = false
    }

    fun retry() {
        failed = false
        loading = true
        reloadToken++
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler {
        if (!webViewDestroyed) {
            webViewDestroyed = true
            webView?.apply {
                runCatching { (parent as? ViewGroup)?.removeView(this) }
                runCatching { stopLoading() }
                runCatching { pauseTimers() }
                runCatching { loadUrl("about:blank") }
                runCatching { settings.javaScriptEnabled = false }
                runCatching { destroy() }
            }
            webView = null
        }
        view.keepScreenOn = false
        onBack()
    }
    LaunchedEffect(webView) {
        delay(220)
        webView?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus(View.FOCUS_DOWN)
        }
    }

    // Pateo periódico del compositor: en TVs TCL el WebView deja de enviar
    // frames tras la primera pintura (pantalla negra/blanca congelada). Un
    // invalidate cada 400 ms mantiene viva la superficie sin cargar el CPU.
    LaunchedEffect(webView) {
        val w = webView ?: return@LaunchedEffect
        while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            runCatching { w.postInvalidateOnAnimation() }
            delay(400)
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.onPause()
                    webView?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView?.onResume()
                    webView?.resumeTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        key(reloadToken) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.rgb(7, 11, 19))
                    keepScreenOn = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
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
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                    if (com.chronosbvrx.visionplus.BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: android.webkit.ConsoleMessage?): Boolean {
                            if (cm != null && cm.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                android.util.Log.e("TVWEB", "console:${cm.message()} @${cm.lineNumber()}")
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            failed = false
                            loading = true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // La sala se crea y se selecciona el juego por socket.
                            // Dejamos un margen corto para que el primer render del
                            // lobby quede listo antes de retirar la máscara.
                            view?.requestFocus(View.FOCUS_DOWN)
                            view?.postDelayed({ loading = false }, 1500)
                        }
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            val hdrs = mutableMapOf<String, String>()
                            request.requestHeaders?.forEach { (k, v) -> hdrs[k] = v }
                            val hostBase = PartyConfig.host(context)
                            if (!PartyNetStack.shouldIntercept(hostBase, reqUrl, request.method ?: "GET", hdrs)) {
                                return null
                            }
                            val range = hdrs.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                            return PartyNetStack.fetch(hostBase, reqUrl, range)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                failed = true
                                loading = false
                            }
                        }
                        override fun onRenderProcessGone(view: WebView?, details: android.webkit.RenderProcessGoneDetail?): Boolean {
                            android.util.Log.e("TVWEB", "onRenderProcessGone didCrash=${details?.didCrash()}")
                            failed = true
                            loading = false
                            runCatching { view?.stopLoading() }
                            return details?.didCrash() != true
                        }
                    }
                    loadUrl(url)
                    webView = this
                    requestFocus(View.FOCUS_DOWN)
                }
            },
            update = { it.keepScreenOn = true },
            onRelease = { view ->
                if (!webViewDestroyed) {
                    webViewDestroyed = true
                    runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                    runCatching { view.stopLoading() }
                    runCatching { view.pauseTimers() }
                    runCatching { view.loadUrl("about:blank") }
                    runCatching { view.settings.javaScriptEnabled = false }
                    runCatching { view.destroy() }
                }
                webView = null
            },
        )
        }

        if (loading && !failed) {
            PartyHostLoadingScreen(gameTitle)
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .focusRequester(exitFocus)
                .background(Color(0xE6070B13), RoundedCornerShape(50))
                .border(1.dp, VisionSurfaceHigh, RoundedCornerShape(50))
                .clickable { onBack() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("Salir", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        if (failed) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .background(VisionSurface, RoundedCornerShape(20.dp))
                    .border(1.dp, VisionSurfaceHigh, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo abrir la sala", color = VisionTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(0.dp))
                Text(
                    "Revisa la conexión o configura el servidor de juegos en /v1/config (party_host).",
                    color = VisionTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .background(VisionBlue, RoundedCornerShape(50))
                            .clickable { retry() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text("Reintentar", color = Color.White, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier
                            .background(VisionSurfaceHigh, RoundedCornerShape(50))
                            .clickable { onBack() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text("Volver", color = VisionTextPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
