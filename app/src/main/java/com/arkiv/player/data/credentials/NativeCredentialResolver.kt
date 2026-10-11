package com.arkiv.player.data.credentials

/**
 * The shim: same fully-qualified class name the credentials library expects, so its
 * RegisterNatives (done in JNI_OnLoad) binds here. Debug builds surface the three keys the
 * app ships for its own subtitles/catalog features; release builds never call them.
 */
object NativeCredentialResolver {

    init {
        System.loadLibrary("credentials")
    }

    external fun resolveTmdbKey(): String

    external fun resolveOpenSubtitlesKey(): String

    external fun resolveSubdlKey(): String

    external fun sealAgree(payload: ByteArray): ByteArray

    external fun sealSelfTest(): Boolean
}
