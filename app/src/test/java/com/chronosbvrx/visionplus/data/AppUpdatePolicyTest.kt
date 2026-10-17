package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antirregresiones de la red de seguridad de actualizaciones (kill-switches remotos):
 *  - `min_version_code`: los builds viejos se fuerzan a actualizar sin publicar nada.
 *  - `ota_paused`: un build malo se deja de ofrecer a todo el mundo al instante.
 *
 * La política es pura y probada: la forzada SIEMPRE gana; la pausa bloquea solo las
 * ofertas normales; lo normal exige remota más nueva y APK instalable.
 */
class AppUpdatePolicyTest {

    @Test
    fun minVersionOnlyForcesOlderBuilds() {
        // Sin exigencia (flag ausente = 0): nadie se fuerza.
        assertFalse(AppUpdate.shouldForce(minVersionCode = 0, ownVersionCode = 326))
        // Al día: no se fuerza.
        assertFalse(AppUpdate.shouldForce(minVersionCode = 326, ownVersionCode = 326))
        // Build más nuevo que el mínimo: no se fuerza.
        assertFalse(AppUpdate.shouldForce(minVersionCode = 325, ownVersionCode = 326))
        // El mínimo subió por encima del build instalado: SE FUERZA.
        assertTrue(AppUpdate.shouldForce(minVersionCode = 327, ownVersionCode = 326))
    }

    @Test
    fun pausedStopsNormalOffersButForcedWins() {
        // Pausa: aunque haya remota más nueva e instalable, no se ofrece.
        assertFalse(AppUpdate.updateOffer(forced = false, paused = true, remoteNewer = true, apkInstallable = true))
        // Forzada: gana incluso con pausa y aunque la remota no se vea más nueva.
        assertTrue(AppUpdate.updateOffer(forced = true, paused = true, remoteNewer = false, apkInstallable = false))
        assertTrue(AppUpdate.updateOffer(forced = true, paused = false, remoteNewer = false, apkInstallable = true))
        // Camino normal: remota más nueva + APK instalable.
        assertTrue(AppUpdate.updateOffer(forced = false, paused = false, remoteNewer = true, apkInstallable = true))
        assertFalse(AppUpdate.updateOffer(forced = false, paused = false, remoteNewer = false, apkInstallable = true))
        assertFalse(AppUpdate.updateOffer(forced = false, paused = false, remoteNewer = true, apkInstallable = false))
    }
}
