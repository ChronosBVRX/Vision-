package com.chronosbvrx.visionplus.home.contract

/**
 * Contrato mínimo entre Vision+ y Vision+ Home para calentar el motor de video ANTES de
 * que el usuario abra la app.
 *
 * El launcher no reproduce ni carga el motor: solo se vincula a un servicio de Vision+.
 * El permiso es `signature` (los dos APK usan la llave histórica), así que ninguna app
 * ajena puede arrancar el motor. Si el launcher no está o Vision+ es viejo, no pasa nada:
 * la app se calienta sola desde su splash.
 *
 * Regla de oro: el servicio NO acuña sesión, NO reproduce y NO crea la vista del
 * reproductor. Solo arranca el proceso `:x4k`, hace `boot` del motor y, si ya hay
 * `Env`/`Entries` en caché, los envía. La ventana/SurfaceView y el primer cuadro se
 * siguen pagando al dar Play.
 */
object EnginePrewarmContract {
    /** Permiso de firma: solo un APK con la llave histórica puede vincularse. */
    const val PERMISSION = "com.chronosbvrx.visionplus.permission.ENGINE_PREWARM"

    /** Paquete y componente del servicio que vive en el proceso `:x4k`. */
    const val APP_PACKAGE = "com.chronosbvrx.visionplus"
    const val SERVICE_CLASS = "com.chronosbvrx.visionplus.data.xuper.EngineWarmService"

    /** Versión del contrato; sube si cambia la semántica del servicio. */
    const val VERSION = 1

    /**
     * Umbral de RAM para el precalentamiento "completo". Los equipos de 1.5 GB
     * (Chromecast HD/4K, Fire TV Stick) se quedan con el camino diferido actual:
     * arrancar `:x4k` durante el arranque en frío les provoca LMK.
     */
    const val MIN_RAM_MB = 1_800L

    /** El launcher espera a su primer frame antes de pedir el motor. */
    const val BIND_DELAY_MS = 2_500L

    /** Cuánto se mantiene vinculado el servicio (la ventana útil del arranque). */
    const val BIND_WINDOW_MS = 15L * 60L * 1_000L

    /** Archivo compartido entre procesos (filesDir) con el estado del warmup. */
    const val READY_FILE = "x4k_warm_ready"

    /** Un warmup más viejo que esto se considera frío y se repite. */
    const val READY_TTL_MS = 15L * 60L * 1_000L
}
