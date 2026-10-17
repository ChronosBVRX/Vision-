package com.chronosbvrx.visionplus.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.BufferedWriter
import java.io.Writer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El blindaje de las órdenes que salen de la pantalla del teléfono.
 *
 * Los botones del control remoto corren en el hilo principal: escribir el socket ahí dispara
 * `NetworkOnMainThreadException`, la excepción se tragaba en silencio y la tecla jamás llegaba a la
 * tele (el «no pasa nada al tocar»). La escritura tiene que viajar en SU hilo (`remote-send`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteClientWireTest {

    private val writes = CopyOnWriteArrayList<Pair<String, String>>()

    /** Un escritor de mentira que apunta en qué hilo y con qué texto se escribió el cable. */
    private val fake: BufferedWriter = BufferedWriter(object : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            writes += Thread.currentThread().name to String(cbuf, off, len)
        }

        override fun flush() {}
        override fun close() {}
    })

    @After
    fun tearDown() {
        RemoteClient.wireWriterForTest(null)
    }

    @Test
    fun aKeyFromTheUiThreadIsWrittenOnItsOwnThread() {
        RemoteClient.wireWriterForTest(fake)

        // Como si se tocara «OK» en la pantalla: el llamador es el hilo de la interfaz.
        RemoteClient.sendKey(RemoteKeys.CENTER)

        val line = awaitLine { it.contains("\"t\":\"key\"") }
            ?: error("la tecla nunca llegó al cable")
        assertEquals("la escritura del socket no puede quedarse en el hilo de la pantalla", "remote-send", line.first)
        assertTrue("la tecla viaja con su código", line.second.contains("\"code\":${RemoteKeys.CENTER}"))
    }

    @Test
    fun openingContentFromTheUiThreadIsWrittenOnItsOwnThread() {
        RemoteClient.wireWriterForTest(fake)

        RemoteClient.sendOpen("xtvlive:4321", "Canal 5")

        val line = awaitLine { it.contains("\"t\":\"open\"") }
            ?: error("la orden de abrir nunca llegó al cable")
        assertEquals("remote-send", line.first)
        assertTrue(line.second.contains("xtvlive:4321"))
    }

    @Test
    fun thePinFromTheDialogIsWrittenOnItsOwnThread() {
        RemoteClient.wireWriterForTest(fake)

        RemoteClient.submitPin("4321")

        val line = awaitLine { it.contains("\"t\":\"pin\"") }
            ?: error("el PIN nunca llegó al cable")
        assertEquals("remote-send", line.first)
        assertTrue(line.second.contains("\"pin\":\"4321\""))
    }

    /** Espera a que el hilo de escritura deje la línea pedida (con tope de tiempo). */
    private fun awaitLine(pick: (String) -> Boolean): Pair<String, String>? {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            writes.firstOrNull { pick(it.second) }?.let { return it }
            Thread.sleep(20)
        }
        return writes.firstOrNull { pick(it.second) }
    }
}
