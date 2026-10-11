package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import okhttp3legacy.MediaType
import okhttp3legacy.OkHttpClient
import okhttp3legacy.Request
import okhttp3legacy.RequestBody

/**
 * Probe of the Xuper/Hydra portal using the same wire EXACTLY as the real client's stack:
 * okhttp 3.12.12 (shaded to okhttp3legacy) so the TLS fingerprint matches the one that passes
 * Cloudflare. The body is the 3DES+hex wire from XuperCrypto, the headers the interceptor from
 * the real client.
 */
object KoocanProbe {

    private const val OUR_KEY = "2b494e53756c664c2f44465245733572"

    private val hosts = listOf(
        "boi9osyg1uwtyafn.com",
        "icpsuawn1zy5amys.com",
        "judua3rtinpst0s.xyz",
        "ltlxvxjjmvhn.me",
        "mzhlhrfr8z.info",
        "ns8469rfvth42.xyz",
        "x9fnzrtl4x8pynsf.com",
        "zwyr157wwiu6eior.com",
        "os4work.com",
    )

    fun run(context: Context, log: (String) -> Unit) {
        val versionCode = 60507
        val bean = "{\"encMediaSupported\":1,\"appParams\":\"\",\"liveCodeList\":[]," +
            "\"userToken\":\"\",\"userId\":\"\",\"portalCode\":\"koocanmobile2\"," +
            "\"type\":\"merge\",\"appVer\":\"$versionCode\",\"lang\":\"pt\"," +
            "\"reserve1\":\"\",\"pipFlag\":\"0\",\"hasPay\":\"\",\"userIdentity\":\"\"}"
        val wire = XuperCrypto.encryptBody(bean, OUR_KEY)
        val sysVersion = android.os.Build.VERSION.RELEASE
        val sysSdk = android.os.Build.VERSION.SDK_INT.toString()

        log("host=${android.os.Build.MODEL} sys=$sysVersion sdk=$sysSdk wire=${wire.take(24)}…")
        for (host in hosts) {
            for (path in listOf(
                "api/portalCore/v15/getSlbInfo",
                "xuper/api/portalCore/v15/getSlbInfo",
                "api/portalCore/v13_1/getSlbInfo",
                "xuper/api/portalCore/v13_1/getSlbInfo",
            )) {
                val answer = post("https://$host/$path", wire, context, versionCode.toString(), sysVersion)
                log("$host/$path → $answer")
                if (answer.startsWith("200") && !answer.contains("portal200001")) return
            }
        }
        log("fin: ningún host entregó slbInfo")
    }

    private fun post(url: String, wire: String, context: Context, appVer: String, sysVer: String): String {
        // Con el proxy del sistema activo el tunnel pasivo sigue el flujo; NO_PROXY va directo.
        val client = OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url)
            .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), wire))
            .addHeader("apk", "com.msandroid.mobile")
            .addHeader("apkVer", appVer)
            .addHeader("spkgVer", sysVer)
            .addHeader("Cache-Control", "no-store")
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body()?.string().orEmpty()
                val mark = when {
                    text.contains("cf-ray", true) || text.contains("cloudflare", true) -> " [CLOUDFLARE]"
                    text.contains("Just a moment", true) -> " [CF-CHALLENGE]"
                    else -> ""
                }
                val decrypted = XuperCrypto.decryptBody(text.takeIf { it.matches(Regex("^[0-9a-f]+$")) } ?: "", OUR_KEY)
                    ?: "${resp.header("Content-Type").orEmpty()} len=${text.length} :: ${text.take(400).replace('\n', ' ')}"
                "${resp.code()}$mark ${decrypted.take(400)}"
            }
        } catch (t: Throwable) {
            "ERR ${t.javaClass.simpleName}: ${t.message ?: ""}"
        }
    }
}
