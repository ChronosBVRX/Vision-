package com.chronosbvrx.visionplus.data.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El servidor del control remoto, de punta a punta y sobre sockets reales: el teléfono pide
 * emparejar, la tele enseña el PIN, el PIN abre el token, las teclas llegan a la pantalla al
 * frente y la orden `open` se convierte en el deep link que la app sabe abrir.
 *
 * Es la prueba que un emulador no puede dar (dos AVD no comparten red); aquí la tele y el
 * teléfono son la misma máquina hablando por `127.0.0.1`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteServerFlowTest {

    private lateinit var context: Context
    private var server: RemoteServer? = null
    private val keys = CopyOnWriteArrayList<Pair<Int, String>>()

    private val sink = object : RemoteBus.Sink {
        override fun onRemoteKey(code: Int, action: String) {
            keys += code to action
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        RemoteBus.publishScreen("")
        RemoteBus.pairingPin.value = null
        RemoteBus.clients.value = 0
        keys.clear()
        RemoteBus.attach(sink)
        server = RemoteServer(context).also { it.start() }
    }

    @After
    fun tearDown() {
        RemoteBus.detach(sink)
        server?.stop()
        server = null
    }

    /** Un teléfono de prueba hablando por el TCP de control. */
    private inner class Phone : AutoCloseable {
        val socket: Socket = Socket("127.0.0.1", RemoteDiscovery.CONTROL_PORT).apply { soTimeout = 6_000 }
        private val out: BufferedWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        private val input: BufferedReader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

        fun send(msg: RemoteMsg) {
            out.write(RemoteProto.encode(msg))
            out.write("\n")
            out.flush()
        }

        fun read(): RemoteMsg = RemoteProto.decode(input.readLine()) ?: error("la tele no contestó")

        override fun close() {
            runCatching { socket.close() }
        }
    }

    @Test
    fun pinGrantsATokenAndThenSilentReconnect() {
        val pin: String
        val token: String
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, name = "POCO de Axel"))
            val need = phone.read()
            assertEquals(RemoteProto.NEED_PIN, need.t)
            // La tele solo enseña el PIN porque alguien lo pidió.
            pin = RemoteBus.pairingPin.value ?: error("la tele no mostró el PIN")
            assertEquals(RemotePairing.PIN_LEN, pin.length)
            phone.send(RemoteMsg(t = RemoteProto.PIN, pin = pin))
            val ok = phone.read()
            assertEquals(RemoteProto.OK, ok.t)
            token = ok.token ?: error("la tele no entregó token")
            assertEquals(32, token.length)
            assertEquals("POCO de Axel", RemoteBus.pairedWith.value)
            assertEquals(1, RemoteBus.clients.value)
        }
        // El PIN se refresca y ya no se pinta.
        assertEquals(null, RemoteBus.pairingPin.value)

        // Segunda visita con el token recordado: entra derechito, sin PIN.
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, name = "POCO de Axel", token = token))
            val hello = phone.read()
            assertEquals(RemoteProto.HELLO, hello.t)
            assertEquals(true, hello.paired)
        }
    }

    @Test
    fun wrongPinIsDeniedAndBringsthePinBack() {
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO))
            assertEquals(RemoteProto.NEED_PIN, phone.read().t)
            phone.send(RemoteMsg(t = RemoteProto.PIN, pin = "0000"))
            val denied = phone.read()
            assertEquals(RemoteProto.DENIED, denied.t)
            assertEquals(RemoteProto.DENIED_PIN, denied.reason)
            assertNotNull("con el intento fallido la tele vuelve a mostrar el PIN", RemoteBus.pairingPin.value)
        }
    }

    @Test
    fun anUnauthorizedPhoneCannotSendKeys() {
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO)) // aún sin emparejar
            assertEquals(RemoteProto.NEED_PIN, phone.read().t)
            phone.send(RemoteMsg(t = RemoteProto.KEY, code = RemoteKeys.CENTER, action = RemoteProto.ACTION_TAP))
            Thread.sleep(250)
            assertTrue("una tecla sin emparejar jamás debe llegar a la pantalla", keys.isEmpty())
        }
    }

    @Test
    fun pairedKeysReachTheScreenInFront() {
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            phone.read() // hello paired
            phone.read() // now
            phone.send(RemoteMsg(t = RemoteProto.KEY, code = RemoteKeys.DOWN, action = RemoteProto.ACTION_TAP))
            phone.send(RemoteMsg(t = RemoteProto.KEY, code = RemoteKeys.CENTER, action = RemoteProto.ACTION_TAP))
            val deadline = System.currentTimeMillis() + 3_000
            while (keys.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(30)
            assertEquals(listOf(RemoteKeys.DOWN, RemoteKeys.CENTER), keys.map { it.first })
        }
    }

    @Test
    fun aKeyOutsideTheAllowlistNeverReachesTheScreen() {
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            phone.read(); phone.read()
            // POWER y HOME no están en la lista blanca: la tele los ignora en silencio.
            phone.send(RemoteMsg(t = RemoteProto.KEY, code = android.view.KeyEvent.KEYCODE_POWER, action = RemoteProto.ACTION_TAP))
            phone.send(RemoteMsg(t = RemoteProto.KEY, code = android.view.KeyEvent.KEYCODE_HOME, action = RemoteProto.ACTION_TAP))
            Thread.sleep(300)
            assertTrue("nada fuera de la lista blanca llega a la pantalla", keys.isEmpty())
        }
    }

    @Test
    fun keysTravelByBroadcastWhenThePlayerIsInFront() {
        val token = pair(Phone())
        // El menú no está al frente: nada recibe la tecla por el puente local.
        RemoteBus.detach(sink)
        val received = CopyOnWriteArrayList<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i?.getIntExtra(RemoteBus.EXTRA_KEY_CODE, -1)?.let { received += it }
            }
        }
        context.registerReceiver(receiver, IntentFilter(RemoteBus.ACTION_REMOTE_KEY), Context.RECEIVER_NOT_EXPORTED)
        try {
            Phone().use { phone ->
                phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
                phone.read(); phone.read()
                phone.send(RemoteMsg(t = RemoteProto.KEY, code = RemoteKeys.PLAY_PAUSE, action = RemoteProto.ACTION_TAP))
                val deadline = System.currentTimeMillis() + 3_000
                while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                    // La tecla viaja como broadcast desde el hilo de red y Robolectric la entrega
                    // por el hilo principal, que es este mismo: hay que dejarlo correr.
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(20)
                }
                assertEquals(listOf(RemoteKeys.PLAY_PAUSE), received)
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            RemoteBus.attach(sink)
        }
    }

    @Test
    fun openBecomesTheDeepLinkTheAppKnows() {
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            phone.read(); phone.read()
            phone.send(RemoteMsg(t = RemoteProto.OPEN, ref = "xtvlive:4321", title = "Canal 5", poster = "http://l.png"))
            val deadline = System.currentTimeMillis() + 3_000
            var started: Intent? = null
            while (started == null && System.currentTimeMillis() < deadline) {
                started = shadowOf(context as android.app.Application).nextStartedActivity
                if (started == null) Thread.sleep(30)
            }
            assertNotNull("la tele debe abrir el canal en vivo", started)
            assertEquals("visionplus://live/4321?t=Canal%205&p=http%3A%2F%2Fl.png", started?.data.toString())
        }
    }

    @Test
    fun anEpisodeOpenCarriesTheSeriesContextInTheDeepLink() {
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            phone.read(); phone.read()
            phone.send(
                RemoteMsg(
                    t = RemoteProto.OPEN,
                    ref = "xtv:EP5",
                    title = "La Casa de Papel · T2 · C5",
                    poster = "p",
                    series = "t2ref",
                    seasonIndex = 1,
                    seasonIds = listOf("t1ref", "t2ref"),
                    seasonNames = listOf("T1", "T2"),
                ),
            )
            val deadline = System.currentTimeMillis() + 3_000
            var started: Intent? = null
            while (started == null && System.currentTimeMillis() < deadline) {
                started = shadowOf(context as android.app.Application).nextStartedActivity
                if (started == null) Thread.sleep(30)
            }
            assertNotNull("la tele debe abrir el capítulo exacto", started)
            val uri = started?.data.toString().orEmpty()
            assertTrue("el capítulo viaja como su propia ref", uri.contains("c=EP5"))
            assertTrue("la temporada activa viaja en s", uri.contains("s=t2ref"))
            assertTrue(uri.contains("sx=1"))
            assertTrue("las temporadas del grupo viajan separadas por |", uri.contains("sl=t1ref%7Ct2ref"))
            assertTrue(uri.contains("sn=T1%7CT2"))
        }
    }

    @Test
    fun theTvTellsThePhoneWhatIsOnScreen() {
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            phone.read(); phone.read()
            RemoteBus.publishScreen("Ajustes")
            val deadline = System.currentTimeMillis() + 3_000
            var screen = ""
            while (screen != "Ajustes" && System.currentTimeMillis() < deadline) {
                val msg = phone.read()
                if (msg.t == RemoteProto.NOW) screen = msg.screen.orEmpty()
            }
            assertEquals("Ajustes", screen)
        }
    }

    @Test
    fun beaconCarriesAStableIdentityAndTheHelloConfirmsIt() {
        val first = probeBeacon()
        assertNotNull("la tele debe contestar la sonda", first)
        val id = first!!.id
        assertTrue("la baliza trae una identidad estable", id.isNotBlank())
        assertEquals("y no cambia entre sondas", id, probeBeacon()?.id)

        // El teléfono que vuelve con su token recibe la MISMA identidad en el hello.
        val token = pair(Phone())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            val hello = phone.read()
            assertEquals(RemoteProto.HELLO, hello.t)
            assertEquals(true, hello.paired)
            assertEquals(id, hello.id)
        }
    }

    @Test
    fun theTvRemembersTheDevicesByNameAndForgetsThemOnDemand() {
        val token: String
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, name = "POCO de Axel"))
            assertEquals(RemoteProto.NEED_PIN, phone.read().t)
            val pin = RemoteBus.pairingPin.value ?: error("sin PIN")
            phone.send(RemoteMsg(t = RemoteProto.PIN, pin = pin))
            val ok = phone.read()
            assertEquals(RemoteProto.OK, ok.t)
            assertTrue("el ok también trae la identidad", !ok.id.isNullOrBlank())
            token = ok.token ?: error("sin token")
        }
        // La tele recuerda QUIÉN está vinculado (visible en Ajustes).
        assertEquals(listOf("POCO de Axel"), RemoteServer.rememberedDevices(context))
        assertEquals(listOf("POCO de Axel"), RemoteBus.pairedDevices.value)

        // Olvidar: el vínculo desaparece y ese token ya no entra solo (volverá a pedir PIN).
        assertEquals(1, RemoteServer.forgetPairings(context))
        assertTrue(RemoteServer.rememberedDevices(context).isEmpty())
        assertTrue(RemoteBus.pairedDevices.value.isEmpty())
        Phone().use { phone ->
            phone.send(RemoteMsg(t = RemoteProto.HELLO, token = token))
            assertEquals(RemoteProto.NEED_PIN, phone.read().t)
        }
    }

    /** Una sonda de descubrimiento por loopback; null si la tele no contesta a tiempo. */
    private fun probeBeacon(): RemoteDiscovery.Beacon? {
        return java.net.DatagramSocket().use { udp ->
            udp.soTimeout = 3_000
            val probe = RemoteDiscovery.probeLine().toByteArray(Charsets.UTF_8)
            udp.send(
                java.net.DatagramPacket(
                    probe,
                    probe.size,
                    java.net.InetAddress.getByName("127.0.0.1"),
                    RemoteDiscovery.PORT,
                ),
            )
            val buffer = ByteArray(512)
            val packet = java.net.DatagramPacket(buffer, buffer.size)
            if (runCatching { udp.receive(packet) }.isFailure) return null
            RemoteDiscovery.parseBeacon(String(packet.data, 0, packet.length, Charsets.UTF_8))
        }
    }

    /** Empareja un teléfono nuevo y devuelve su token. */
    private fun pair(phone: Phone): String {
        phone.send(RemoteMsg(t = RemoteProto.HELLO))
        assertEquals(RemoteProto.NEED_PIN, phone.read().t)
        val pin = RemoteBus.pairingPin.value ?: error("sin PIN")
        phone.send(RemoteMsg(t = RemoteProto.PIN, pin = pin))
        return phone.read().token ?: error("sin token")
    }
}
