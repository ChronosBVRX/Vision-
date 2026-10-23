package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes del rol de sistema: si alguien borra la categoría HOME o el permiso de firma,
 * el launcher deja de ser la pantalla de inicio (o peor: expone el feed a cualquier app) y
 * este test lo dice antes de publicar.
 */
class LauncherManifestTest {

    private val launcherManifest = requireSource(
        File("src/main/AndroidManifest.xml"),
        File("launcher/src/main/AndroidManifest.xml"),
        File("../launcher/src/main/AndroidManifest.xml"),
    )

    private val appManifest = requireSource(
        File("../app/src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    )

    @Test
    fun theActivityIsAHomeTheSystemCanOffer() {
        assertTrue("falta la categoría HOME", launcherManifest.contains("android.intent.category.HOME"))
        assertTrue("falta DEFAULT (obligatorio junto a HOME)", launcherManifest.contains("android.intent.category.DEFAULT"))
        assertTrue("debe poder abrirse desde el cajón de apps", launcherManifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue("sin estado que restaurar", launcherManifest.contains("android:stateNotNeeded=\"true\""))
        assertTrue("una sola instancia", launcherManifest.contains("android:launchMode=\"singleTask\""))
        assertTrue("no debe aparecer en recientes", launcherManifest.contains("android:excludeFromRecents=\"true\""))
    }

    @Test
    fun theLauncherOnlySeesAppsItCanLaunch() {
        assertTrue("se consulta el lanzador de Leanback", launcherManifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue("se declara Vision+ para leer su versión", launcherManifest.contains("com.chronosbvrx.visionplus\""))
    }

    @Test
    fun theFeedIsSignatureOnly() {
        assertTrue(
            "el permiso del feed debe ser de nivel signature",
            appManifest.contains("com.chronosbvrx.visionplus.permission.HOME_FEED") &&
                appManifest.contains("android:protectionLevel=\"signature\""),
        )
        assertTrue(
            "el provider debe exigir el permiso de lectura",
            appManifest.contains("android:readPermission=\"com.chronosbvrx.visionplus.permission.HOME_FEED\""),
        )
        assertTrue(
            "la autoridad debe ser la del contrato",
            appManifest.contains("\${applicationId}.homefeed"),
        )
        assertTrue(
            "el launcher debe pedir el permiso del feed",
            launcherManifest.contains("com.chronosbvrx.visionplus.permission.HOME_FEED"),
        )
    }

    @Test
    fun theApkCanUpdateItselfWithoutAStore() {
        assertTrue(
            "hace falta permiso de instalación in-app",
            launcherManifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"),
        )
        assertTrue(
            "hace falta el FileProvider del instalador",
            launcherManifest.contains("androidx.core.content.FileProvider"),
        )
    }

    private fun requireSource(vararg candidates: File): String {
        val found = candidates.firstOrNull { it.exists() }
        assertNotNull("no se encontró el manifest entre ${candidates.joinToString()}", found)
        return found!!.readText()
    }
}
