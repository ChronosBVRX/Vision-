package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El launcher tiene que poder ponerse completo él solo: si en la tele solo está Vision+ Home,
 * desde su propio Inicio se descarga e instala Vision+ (del mismo release y con la misma
 * llave, que es lo que permite leer el feed).
 */
class LauncherInstallTest {

    private val update = require(
        "src/main/java/com/chronosbvrx/visionplus/home/data/HomeUpdate.kt",
        "launcher/src/main/java/com/chronosbvrx/visionplus/home/data/HomeUpdate.kt",
    )

    private val surfaces = require(
        "src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
        "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
    )

    private val manifest = require(
        "src/main/AndroidManifest.xml",
        "launcher/src/main/AndroidManifest.xml",
    )

    @Test
    fun theLauncherDownloadsTheAppItself() {
        assertTrue("falta la descarga de Vision+", update.contains("installVisionPlus"))
        assertTrue("el asset sale del contrato", update.contains("HomeContract.APP_ASSET"))
        assertTrue("la descarga reporta progreso", update.contains("State.Downloading(percent)"))
        assertTrue("abre el instalador del sistema", update.contains("application/vnd.android.package-archive"))
    }

    @Test
    fun theBannerOffersTheInstallNotJustAWarning() {
        assertTrue(surfaces.contains("Instalar Vision+"))
        assertTrue("el aviso muestra el progreso", surfaces.contains("Descargando Vision+…"))
        assertTrue("el aviso lleva a los ajustes de la tele", surfaces.contains("Ajustes"))
    }

    @Test
    fun theLauncherHasWhatInstallingNeeds() {
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun bothAssetsAreNamedInTheSharedContract() {
        val contract = require(
            "../home-contract/src/main/java/com/chronosbvrx/visionplus/home/contract/HomeContract.kt",
            "home-contract/src/main/java/com/chronosbvrx/visionplus/home/contract/HomeContract.kt",
        )
        assertTrue(contract.contains("APP_ASSET = \"VisionPlus.apk\""))
        assertTrue(contract.contains("LAUNCHER_ASSET = \"VisionPlusHome.apk\""))
    }

    private fun require(vararg candidates: String): String {
        val found = candidates.map(::File).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: ${candidates.joinToString()}", found)
        return found!!.readText()
    }
}
