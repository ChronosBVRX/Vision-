package com.chronosbvrx.visionplus.ui.sports

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URI

/** Resultado de abrir un `embed`: la URL real y las cabeceras con las que se pidió. */
internal data class GeeResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Resuelve un enlace `embed` de GeeSports igual que su app original: carga la página
 * en un WebView oculto y captura la primera petición de media (`.m3u8`, `.mpd`, `.ts`…).
 *
 * No es un enlace caído: la fuente no publica la URL hasta que su reproductor web
 * arranca. Por eso la lista lo muestra como «Se resuelve al tocar» y no como «Sin señal».
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GeeEmbedResolveDialog(
    tokenApi: String,
    title: String,
    onResolved: (GeeResolvedStream) -> Unit,
    onDismiss: () -> Unit,
) {
    val config = remember(tokenApi) { parseEmbed(tokenApi) }
    val api = config?.api.orEmpty()
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(tokenApi) {
        delay(20_000)
        if (!loaded) error = "La fuente tardó demasiado en publicar la señal."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = VisionSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.width(420.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        title.ifBlank { "Transmisión" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    if (error != null) {
                        Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB4AB))
                        TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(
                                color = VisionBlueBright,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Resolviendo enlace…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.86f),
                            )
                        }
                        Text(
                            "La fuente abre su reproductor y entrega la señal. Suele tardar unos segundos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White) }
                    }
                }
            }

            if (api.isNotBlank() && error == null) {
            AndroidView(
                modifier = Modifier.size(1.dp),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun report(url: String) {
                                    val clean = url.trim()
                                    if (!isMediaUrl(clean)) return
                                    post {
                                        if (loaded) return@post
                                        loaded = true
                                        onResolved(
                                            GeeResolvedStream(
                                                url = clean,
                                                headers = headersFor(api),
                                            ),
                                        )
                                    }
                                }
                            },
                            "VisionPlusEmbed",
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val url = request?.url?.toString().orEmpty()
                                if (isMediaUrl(url)) {
                                    val headers = request?.requestHeaders?.mapKeys { it.key.lowercase() }.orEmpty()
                                    view?.post {
                                        if (loaded) return@post
                                        loaded = true
                                        onResolved(GeeResolvedStream(url, headers))
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                val clean = url.orEmpty()
                                if (isMediaUrl(clean)) {
                                    view?.post {
                                        if (loaded) return@post
                                        loaded = true
                                        onResolved(GeeResolvedStream(clean, headersFor(api)))
                                    }
                                }
                                super.onLoadResource(view, url)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (isMediaUrl(url)) {
                                    view?.post {
                                        if (loaded) return@post
                                        loaded = true
                                        onResolved(GeeResolvedStream(url, headersFor(api)))
                                    }
                                    return true
                                }
                                return super.shouldOverrideUrlLoading(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(HOOK_JS, null)
                            }
                        }
                        loadUrl(api)
                    }
                },
                update = { /* la página se carga una sola vez en factory */ },
            )
            }
        }
    }
}

private data class EmbedConfig(val api: String, val type: String)

private fun parseEmbed(tokenApi: String): EmbedConfig? = runCatching {
    val json = JSONObject(tokenApi)
    EmbedConfig(
        api = json.optString("api").trim(),
        type = json.optString("type", "embed").trim().lowercase(),
    )
}.getOrNull()

private fun headersFor(api: String): Map<String, String> = runCatching {
    val uri = URI(api)
    buildMap {
        put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        put("referer", api)
        put("origin", "${uri.scheme}://${uri.host}")
    }
}.getOrDefault(emptyMap())

private fun isMediaUrl(raw: String): Boolean {
    val url = raw.trim().lowercase()
    if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return false
    val path = url.substringBefore('?').substringBefore('#')
    return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".ts") ||
        path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
        url.contains(".m3u8?") || url.contains(".mpd?")
}

private const val HOOK_JS = """
(function(){
  function report(u){ try { if (u && (String(u).indexOf('.m3u8') >= 0 || String(u).indexOf('.mpd') >= 0)) VisionPlusEmbed.report(String(u)); } catch(e) {} }
  try {
    var of = window.fetch;
    if (of) window.fetch = function(){ try { report(arguments[0]); } catch(e){} return of.apply(this, arguments); };
  } catch(e) {}
  try {
    var oo = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m,u){ try { report(u); } catch(e){} return oo.apply(this, arguments); };
  } catch(e) {}
  try {
    var d = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
    if (d && d.set) Object.defineProperty(HTMLMediaElement.prototype, 'src', { set: function(v){ report(v); return d.set.call(this, v); }, get: d.get });
  } catch(e) {}
})();
"""
