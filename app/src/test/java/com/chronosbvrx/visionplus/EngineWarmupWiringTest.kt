package com.chronosbvrx.visionplus

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes del precalentamiento: el splash y el launcher no pueden quedar como código
 * muerto ni saltarse el candado de firma. Si alguien borra el servicio o el permiso, la
 * integración deja de funcionar (o expone el motor) y esto lo dice antes de publicar.
 */
class EngineWarmupWiringTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theSplashWarmsTheEngineAndShowsIt() {
        val main = source("MainActivity.kt")
        assertTrue("el splash no pide el warmup", main.contains("EngineWarmup.awaitReadyOrWarm"))
        assertTrue("el splash no respeta la política", main.contains("EngineWarmup.shouldWarmOnSplash"))
        assertTrue("el splash no mide el resultado", main.contains("ENGINE_WARM_TIMEOUT"))
        assertTrue("el splash no reporta el warmup", main.contains("splash warm ready"))
        assertTrue("el splash no marca el warmup lento", main.contains("ENGINE_WARM_SLOW"))
        assertTrue("el splash no muestra el estado", main.contains("status = if (warming)"))
        val splash = source("ui/brand/VisionSplash.kt")
        assertTrue("el splash no acepta estado", splash.contains("status: String? = null"))
        assertTrue("el splash no pinta el estado", splash.contains("text = status ?: \"Iniciando Vision+...\""))
    }

    @Test
    fun theWarmupStateIsSharedSafelyBetweenProcesses() {
        val warm = source("data/xuper/EngineWarmup.kt")
        assertTrue("falta el archivo compartido", warm.contains("EnginePrewarmContract.READY_FILE"))
        assertTrue("falta el chequeo del proceso vivo", warm.contains("isProcessAlive"))
        assertTrue("falta la escritura atómica", warm.contains("renameTo"))
        assertTrue("falta el filtro por RAM", warm.contains("shouldWarmFor"))
        val receiver = source("data/xuper/X4KWarmupReceiver.kt")
        assertTrue("falta la acción del splash", receiver.contains("ACTION_PREWARM"))
        assertTrue("el receptor no usa el warmup compartido", receiver.contains("EngineWarmup.warmBlocking"))
    }

    @Test
    fun theLauncherServiceIsSignatureOnlyAndRunsInThePlayerProcess() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("falta el permiso de firma", manifest.contains("com.chronosbvrx.visionplus.permission.ENGINE_PREWARM"))
        assertTrue("el permiso no es signature", manifest.contains("android:protectionLevel=\"signature\""))
        assertTrue("falta el servicio", manifest.contains(".data.xuper.EngineWarmService"))
        assertTrue("el servicio no exige permiso", manifest.contains("android:permission=\"com.chronosbvrx.visionplus.permission.ENGINE_PREWARM\""))
        assertTrue("el servicio no vive en :x4k", manifest.contains("android:process=\":x4k\""))
        val service = source("data/xuper/EngineWarmService.kt")
        assertTrue("el servicio no calienta", service.contains("EngineWarmup.warmBlocking"))
        assertTrue("el servicio no respeta la política", service.contains("EngineWarmup.shouldWarm"))
        assertTrue("el servicio no evita pisar un reproductor activo", service.contains("X4KPlayerAty.isPlayerActive"))
    }
}
