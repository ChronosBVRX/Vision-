package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La puerta de Inicio del launcher: el botón principal pide el rol del sistema (ventana para
 * elegir el launcher predeterminado) y el botón manual sigue disponible para volver al otro.
 */
class LauncherHomeRoleWiringTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/home/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theRepositoryRequestsTheSystemHomeRoleAndKeepsTheManualSettingsFallback() {
        val roleApi = source("data/HomeRoleApi29.kt")
        assertTrue("no pide el rol HOME", roleApi.contains("RoleManager.ROLE_HOME"))
        assertTrue("no abre la ventana del sistema", roleApi.contains("createRequestRoleIntent"))
        assertTrue("no aísla la API 29 del Android viejo", roleApi.contains("@RequiresApi(29)"))
        val repo = source("data/AppsRepository.kt")
        assertTrue("no usa el puente de API 29", repo.contains("HomeRoleApi29"))
        assertTrue("no conserva la puerta manual", repo.contains("Settings.ACTION_HOME_SETTINGS"))
        assertTrue(
            "no intenta la pantalla de apps predeterminadas",
            repo.contains("Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS"),
        )
    }

    @Test
    fun theControlCenterOffersTheDirectActionAndTheManualOne() {
        val overlays = source("ui/Overlays.kt")
        assertTrue("el centro de control no recibe la acción directa", overlays.contains("onRequestHome"))
        assertTrue("falta el botón para ponerlo como Inicio", overlays.contains("Poner Vision+ Home como Inicio"))
        assertTrue("falta la opción manual", overlays.contains("Cambiar el Inicio (ajustes)"))
        assertTrue("no se muestra cuando ya es el Inicio", overlays.contains("Vision+ Home ya es tu Inicio"))
    }

    @Test
    fun theAppsSurfaceAlsoMakesItObvious() {
        val surfaces = source("ui/Surfaces.kt")
        assertTrue("Apps no recibe la acción directa", surfaces.contains("onRequestHome"))
        assertTrue("falta el botón directo en Apps", surfaces.contains("\"Poner como Inicio\""))
        assertTrue("falta el acceso manual en Apps", surfaces.contains("\"Cambiar el Inicio\""))
        val screen = source("LauncherScreen.kt")
        assertTrue("el launcher no pide el rol", screen.contains("requestHomeRole()"))
        assertTrue("el launcher no abre los ajustes manuales", screen.contains("openHomeSettings()"))
    }
}
