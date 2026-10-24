package com.chronosbvrx.visionplus.home.contract

/**
 * Lo que Vision+ y Vision+ Home comparten: la autoridad del feed, el permiso de firma, la
 * versión del esquema y los nombres de paquete. Un cambio aquí rompe la compilación de los
 * dos APK, nunca la tele.
 */
object HomeContract {
    /** El provider (en Vision+) del que el launcher lee el feed. */
    const val AUTHORITY = "com.chronosbvrx.visionplus.homefeed"
    const val FEED_URI = "content://$AUTHORITY/feed"

    /** El buscador del launcher: el mismo del servidor que usa la app. */
    const val SEARCH_URI = "content://$AUTHORITY/search"

    /**
     * Permiso de nivel `signature`: solo se concede a un APK firmado con la MISMA llave
     * histórica. El feed jamás queda expuesto a una app ajena.
     */
    const val PERMISSION = "com.chronosbvrx.visionplus.permission.HOME_FEED"

    /** La única columna que devuelve el provider: el JSON del feed. */
    const val COLUMN = "feed"

    /** Versión del esquema del feed. El launcher ignora lo que no conoce y cae a su caché. */
    const val VERSION = 1

    const val APP_PACKAGE = "com.chronosbvrx.visionplus"
    const val LAUNCHER_PACKAGE = "com.chronosbvrx.visionplus.home"

    /** Versión del launcher que se publica junto a esta build. */
    const val LAUNCHER_VERSION_NAME = "1.0.10"
    const val LAUNCHER_VERSION_CODE = 11

    /** El asset del release con el APK del launcher. */
    const val LAUNCHER_ASSET = "VisionPlusHome.apk"

    /** El asset del release con la app (lo descarga el launcher si está solo). */
    const val APP_ASSET = "VisionPlus.apk"
}
