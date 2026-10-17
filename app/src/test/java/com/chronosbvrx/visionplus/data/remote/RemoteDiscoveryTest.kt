package com.chronosbvrx.visionplus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El descubrimiento en la misma red: sonda del teléfono y baliza de la tele. Solo entran
 * balizas con puerto válido, así ninguna respuesta rara se cuela como si fuera una tele.
 */
class RemoteDiscoveryTest {

    @Test
    fun probeIsRecognizableAndOneJsonPerDatagram() {
        val probe = RemoteDiscovery.probeLine()
        assertTrue(RemoteDiscovery.isProbe(probe))
        assertFalse("una baliza no es una sonda", RemoteDiscovery.isProbe(RemoteDiscovery.beaconLine("TV", "Android")))
        assertFalse(RemoteDiscovery.isProbe("basura"))
        assertFalse(RemoteDiscovery.isProbe(null))
    }

    @Test
    fun beaconCarriesNameModelAndControlPort() {
        val line = RemoteDiscovery.beaconLine("Sala", "Chromecast")
        val beacon = RemoteDiscovery.parseBeacon(line)
        assertEquals(RemoteDiscovery.CONTROL_PORT, beacon?.port)
        assertEquals("Sala", beacon?.name)
        assertEquals("Chromecast", beacon?.model)
    }

    @Test
    fun beaconWithoutPortIsNotATElevision() {
        assertNull(RemoteDiscovery.parseBeacon("""{"t":"hello","name":"TV"}"""))
        assertNull(RemoteDiscovery.parseBeacon("""{"t":"probe"}"""))
        assertNull(RemoteDiscovery.parseBeacon(""))
    }

    @Test
    fun beaconRejectsOutOfRangePorts() {
        assertNull(RemoteDiscovery.parseBeacon("""{"t":"hello","name":"TV","port":0}"""))
        assertNull(RemoteDiscovery.parseBeacon("""{"t":"hello","name":"TV","port":70000}"""))
        assertNull(RemoteDiscovery.parseBeacon("""{"t":"hello","name":"TV","port":-1}"""))
        assertEquals(48633, RemoteDiscovery.parseBeacon("""{"t":"hello","name":"TV","port":48633}""")?.port)
    }

    @Test
    fun keyIsStableForTheSameTvAndDiffersForAnother() {
        assertEquals(
            RemoteTv(host = "10.0.0.5").key,
            RemoteTv(host = "10.0.0.5", name = "otro nombre").key,
        )
        assertTrue(RemoteTv(host = "10.0.0.5").key != RemoteTv(host = "10.0.0.6").key)
    }
}
