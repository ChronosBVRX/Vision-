package com.chronosbvrx.visionplus.data

import com.chronosbvrx.visionplus.data.config.RemoteConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Canal de actualizaciones (pedido del usuario): la app está distribuida y **no** puede recibir
 * cada iteración. Las pruebas se publican como *pre-release* con tag fijo `beta` (GitHub no la
 * ofrece en `/releases/latest`) y solo la reciben los equipos marcados desde el servidor.
 */
class UpdateChannelTest {

    private fun release(version: String) = Release(
        version = version,
        url = "https://github.com/ChronosBVRX/Vision-/releases/tag/v$version",
        apkUrl = "https://example/VisionPlus.apk",
    )

    @Test
    fun stableOnlySeesTheOfficialRelease() {
        val chosen = AppUpdate.pickRelease(release("2.5.140"), release("2.5.141"), AppUpdate.CHANNEL_STABLE)
        assertEquals("2.5.140", chosen?.version)
    }

    @Test
    fun aTesterSeesWhicheverIsNewer() {
        val betaNewer = AppUpdate.pickRelease(release("2.5.140"), release("2.5.141"), AppUpdate.CHANNEL_BETA)
        assertEquals("2.5.141", betaNewer?.version)
        // Cuando la beta ya salió como oficial, el equipo de pruebas recibe la oficial.
        val officialNewer = AppUpdate.pickRelease(release("2.5.142"), release("2.5.141"), AppUpdate.CHANNEL_BETA)
        assertEquals("2.5.142", officialNewer?.version)
    }

    @Test
    fun missingSidesDoNotBreakTheChoice() {
        assertNull(AppUpdate.pickRelease(null, null, AppUpdate.CHANNEL_BETA))
        assertEquals("2.5.141", AppUpdate.pickRelease(null, release("2.5.141"), AppUpdate.CHANNEL_BETA)?.version)
        assertEquals("2.5.140", AppUpdate.pickRelease(release("2.5.140"), null, AppUpdate.CHANNEL_BETA)?.version)
        assertNull(AppUpdate.pickRelease(null, release("2.5.141"), AppUpdate.CHANNEL_STABLE))
    }

    @Test
    fun theVersionComesFromTheTagOrFromTheTitle() {
        assertEquals("2.5.140", AppUpdate.releaseVersion("v2.5.140", "Vision+ 2.5.140"))
        assertEquals("2.5.141", AppUpdate.releaseVersion("V2.5.141", ""))
        // La beta usa tag fijo: la versión va en el título.
        assertEquals("2.5.141", AppUpdate.releaseVersion("beta", "Vision+ 2.5.141 (pruebas)"))
        assertEquals("", AppUpdate.releaseVersion("beta", "sin versión"))
    }

    @Test
    fun theTestDevicesComeFromTheServer() {
        val array = JSONObject().put("beta_device_ids", JSONArray().put("9a053d28c255422c").put("2a371bd1ec724566"))
        assertEquals(
            listOf("9a053d28c255422c", "2a371bd1ec724566"),
            RemoteConfig.stringListFrom(array, "beta_device_ids"),
        )
        val text = JSONObject().put("beta_device_ids", "9a053d28c255422c, 2a371bd1ec724566")
        assertEquals(
            listOf("9a053d28c255422c", "2a371bd1ec724566"),
            RemoteConfig.stringListFrom(text, "beta_device_ids"),
        )
        assertTrue(RemoteConfig.stringListFrom(JSONObject(), "beta_device_ids").isEmpty())
    }

    @Test
    fun theSwitchWinsOverTheServerInBothDirections() {
        // Encendido a mano: recibe betas aunque el servidor no lo tenga como probador.
        assertEquals(AppUpdate.CHANNEL_BETA, AppUpdate.channelFor("beta", testerFromServer = false))
        // Apagado a mano: vuelve a la oficial aunque sí estuviera marcado.
        assertEquals(AppUpdate.CHANNEL_STABLE, AppUpdate.channelFor("stable", testerFromServer = true))
        // Sin elección local manda el servidor.
        assertEquals(AppUpdate.CHANNEL_BETA, AppUpdate.channelFor(null, testerFromServer = true))
        assertEquals(AppUpdate.CHANNEL_STABLE, AppUpdate.channelFor(null, testerFromServer = false))
        assertEquals(AppUpdate.CHANNEL_STABLE, AppUpdate.channelFor("   ", testerFromServer = false))
    }

    @Test
    fun theSwitchIsInSettingsOnPhoneAndTvAndChecksRightAway() {
        val phone = File("src/main/java/com/chronosbvrx/visionplus/ui/phone/SettingsScreen.kt").readText()
        assertTrue("Ajustes del teléfono no tiene el interruptor", phone.contains("Canal de pruebas (beta)"))
        assertTrue("el interruptor no guarda la elección", phone.contains("AppUpdate.setBetaChannel"))
        assertTrue("no busca la beta al encenderlo", phone.contains("update.run()"))
        val tv = File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt").readText()
        assertTrue("Ajustes de la tele no tiene el interruptor", tv.contains("Canal de pruebas (beta)"))
        assertTrue("el interruptor de la tele no guarda la elección", tv.contains("AppUpdate.setBetaChannel"))
        val api = File("src/main/java/com/chronosbvrx/visionplus/data/AppUpdate.kt").readText()
        assertTrue(api.contains("fun betaChannel("))
        assertTrue(api.contains("fun setBetaChannel("))
    }

    @Test
    fun theUpdaterOnlyInstallsTheApp() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/data/AppUpdate.kt").readText()
        // Cada release lleva VisionPlus.apk y VisionPlusHome.apk: nunca instalar el launcher.
        assertTrue(source.contains("name.equals(\"VisionPlus.apk\", ignoreCase = true)"))
        assertTrue(source.contains("beta_device_ids"))
        assertTrue(source.contains("releases/tags/beta"))
        // Manifiesto propio: descarga normal, sin el límite por IP de la API de GitHub.
        assertTrue(source.contains("releases/latest/download/version.json"))
        assertTrue(source.contains("releases/download/beta/version.json"))
        assertTrue(source.contains("releases/download/beta/VisionPlus.apk"))
    }

    @Test
    fun thePublisherHasATestChannel() {
        val script = File("../tools/publish_ota.ps1").readText()
        assertTrue("sin canal en el publicador", script.contains("[ValidateSet(\"stable\", \"beta\")]"))
        assertTrue("la beta no es pre-release", script.contains("--prerelease"))
        assertTrue("la beta no usa el tag fijo", script.contains("gh release create \"beta\""))
        // El version.json viaja en los DOS canales.
        assertTrue("no se genera version.json", script.contains("version.json"))
        assertTrue("la estable no sube el manifiesto", script.contains("VisionPlusHome.apk \$versionFile"))
    }
}
