package com.chronosbvrx.visionplus.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * La memoria de vínculos del teléfono: recuerda las teles con las que ya se emparejó —por su
 * identidad estable, no por la dirección— y las lista para volver a entrar sin PIN (aunque la
 * IP haya cambiado o el descubrimiento no las vea).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteClientMemoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun knownTvsStayShortNewestFirstAndNeverDuplicate() {
        var raw: String? = null
        for (i in 1..7) raw = upsertKnownTv(raw, RemoteTv(host = "10.0.0.$i", name = "TV $i"))
        val list = parseKnownTvs(raw)
        assertEquals("la lista no crece sin fin", 5, list.size)
        assertEquals("la más reciente va al frente", "TV 7", list.first().name)
        // Volver a registrar la misma tele no la duplica: sube al frente.
        raw = upsertKnownTv(raw, RemoteTv(host = "10.0.0.4", name = "TV 4"))
        val again = parseKnownTvs(raw)
        assertEquals(5, again.size)
        assertEquals("TV 4", again.first().name)
    }

    @Test
    fun theIdentityWinsOverTheAddressWhenDeduping() {
        val sala = RemoteTv(host = "10.0.0.5", name = "Sala", id = "abc")
        val salaMoved = RemoteTv(host = "10.0.0.9", name = "Sala", id = "abc")
        val list = parseKnownTvs(upsertKnownTv(upsertKnownTv(null, sala), salaMoved))
        assertEquals("la misma tele con otra IP es una sola", 1, list.size)
        assertEquals("10.0.0.9", list.first().host)
        assertEquals("abc", list.first().key)
    }

    @Test
    fun theLegacyEntryAndTheIdentifiedOneAreTheSameBox() {
        // La tele se conoce primero por dirección (instalación vieja) y al reconectar aprende
        // su identidad: no queda el par duplicado de la migración.
        val legacy = RemoteTv(host = "10.0.0.5", name = "Sala")
        val learned = legacy.copy(id = "abc")
        val migrated = parseKnownTvs(upsertKnownTv(upsertKnownTv(null, legacy), learned))
        assertEquals(1, migrated.size)
        assertEquals("abc", migrated.first().id)

        // Al revés: una tele que ya se conoce con identidad no se reescribe desde la versión ciega.
        val again = parseKnownTvs(upsertKnownTv(upsertKnownTv(null, learned), legacy))
        assertEquals(1, again.size)
        assertEquals("abc", again.first().id)
    }

    @Test
    fun aGarbageListIsSimplyDiscarded() {
        assertTrue(parseKnownTvs(null).isEmpty())
        assertTrue(parseKnownTvs("no json").isEmpty())
        assertTrue(parseKnownTvs("""[{"host":""},{"port":-2,"host":"x"}]""").isEmpty())
    }

    @Test
    fun aLegacyTokenMigratesToTheStableIdentity() {
        val p = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)
        p.edit().putString("tok_10.0.0.5:48633", "TOKEN123").commit()

        val tv = RemoteTv(host = "10.0.0.5", id = "abc")
        assertEquals("el vínculo viejo se encuentra", "TOKEN123", RemoteClient.tokenFor(context, tv))
        assertEquals("y queda guardado por identidad", "TOKEN123", p.getString("tok_abc", null))
        assertTrue(RemoteClient.isPaired(context, tv))
    }

    @Test
    fun aTokenSavedByIdentitySurvivesTheAddressChange() {
        val p = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)
        p.edit().putString("tok_abc", "TOKEN123").commit()
        // La tele cambió de IP: el vínculo sigue (se busca por identidad, no por dirección).
        val tv = RemoteTv(host = "192.168.1.99", port = 48633, name = "Sala", id = "abc")
        assertTrue(RemoteClient.isPaired(context, tv))
    }

    @Test
    fun rememberedTvsKeepTheLastFirstAndOnlyTheLinkedOnes() {
        RemoteClient.rememberTv(context, RemoteTv(host = "10.0.0.5", name = "Sala", id = "a"))
        RemoteClient.rememberTv(context, RemoteTv(host = "10.0.0.6", name = "Cuarto", id = "b"))
        assertTrue("sin token no hay vinculada que recordar", RemoteClient.rememberedTvs(context).isEmpty())

        val p = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)
        p.edit().putString("tok_b", "T2").putString("tok_a", "T1").commit()
        val remembered = RemoteClient.rememberedTvs(context)
        assertEquals("la última usada va primero", listOf("b", "a"), remembered.map { it.id })
    }

    @Test
    fun rowLabelTellsLinkedFromFresh() {
        assertEquals("Vinculada", tvRowLabel(true))
        assertEquals("Conectar", tvRowLabel(false))
    }
}
