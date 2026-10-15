package com.chronosbvrx.visionplus.data.subs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.xuper.XuperCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Subtítulos del proveedor: sobre cifrado (vector capturado EN VIVO contra `masnew`), lista
 * del servicio, parser SRT y búsqueda del cue del tic. El motor nativo no participa: aquí
 * vive todo lo que la app sí puede romper sin darse cuenta.
 *
 * Robolectric porque el sobre cifrado usa `android.util.Base64` (la misma pieza que en el APK).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SubsTest {

    /** `{"programId":"A81209…"}` tal como lo cifró la sonda contra el servicio real. */
    @Test
    fun requestWireIsByteIdenticalToWhatThePortalAccepted() {
        val plain = """{"programId":"A812091245EA4DA6B58B3D70E325A39B"}"""
        assertEquals(
            "692f75764359346b334a4664386e32555471463861545476474f577a39693055524c5938456b" +
                "326a6c4a782b6c676d2f5a7a2b6a617768666a7573766c697251556d6556536871344759553d",
            XuperCrypto.encryptBody(plain, SubsStore.SUBS_KEY),
        )
    }

    /** El `data` hex de una respuesta real del servicio descifra a la lista de es/en. */
    @Test
    fun liveResponseWireDecryptsToTheSubtitleList() {
        val data =
            "4c426d3950784379546372506431314b6a3476444f754b6d6c4b67794b656a5666626765477" +
                "768634b377850325373424d2b394750677067325930757039713751794b6348704852352b4" +
                "e5148674263666a50636b64523930437042685351537078493165457538303430324d7169353" +
                "04f4f5530307047513732764d5658334375556849377a2b2b4d536652536c4c5a61346e2b5" +
                "57442334250587445336d484e53314e47554d6e5038343077773779646b6d47376a534e5644" +
                "586871446d7849546d5347544d4d3974363530624d576271635061365a73702f4964522f5" +
                "55457345a79764c38694f464d7359737444675947307170355935745a426b574941674c5" +
                "7636b5070436c6f497a52624f4f63546a3964655a6f484c314f6b4359662b3553696e627651" +
                "304f7743372f55487a414558316b514d2b343871657348456a5250306a6d794e52734c6f5a4" +
                "d5648363958787955464578716e6979756a2f376f6c55522b744759313243316e496a4d4c72" +
                "4b413d3d"
        val json = XuperCrypto.decryptBody(data, SubsStore.SUBS_KEY)
        assertTrue(json != null)
        val entries = parseSubsList(json)
        assertEquals(listOf("en", "es"), entries.map { it.language })
        assertTrue(entries.all { it.fileType == "srt" })
        assertEquals(
            "http://vpnwqk.k9ijj2.com/public/subs/e5921b1f-85e3-4144-89cb-6fb8e3c03ea7.srt",
            entries.first { it.language == "es" }.url,
        )
    }

    @Test
    fun listDedupesLanguagesKeepingTheSrt() {
        val json = """[
            {"language":"es","file":[{"url":"http://x/a.ass","fileType":"ass"}]},
            {"language":"es","file":[{"url":"http://x/b.srt","fileType":"srt"}]},
            {"language":"en","file":[{"url":"http://x/c.srt","fileType":"srt"}]},
            {"language":"pt","file":[{"url":"http://x/d.srt","fileType":"srt"}]}
        ]"""
        val entries = parseSubsList(json)
        assertEquals(4, entries.size)
        val langs = distinctLanguages(entries)
        assertEquals(listOf("es", "en", "pt"), langs.map { it.language })
        assertEquals("http://x/b.srt", langs.first().url)
    }

    @Test
    fun missingOrBrokenListIsEmpty() {
        assertEquals(0, parseSubsList(null).size)
        assertEquals(0, parseSubsList("").size)
        assertEquals(0, parseSubsList("nope").size)
        assertEquals(0, parseSubsList("[]").size)
        assertEquals(0, parseSubsList("""[{"language":"es","file":[{"fileType":"srt"}]}]""").size)
    }

    /** El caché del índice lo escribe `entriesToJson` y lo relee `parseSubsList`: deben coincidir. */
    @Test
    fun cacheJsonRoundTripsThroughTheServiceParser() {
        val entries = listOf(
            SubEntry("es", "http://x/a.srt", "srt", 0),
            SubEntry("en", "http://x/b.srt", "srt", 1, md5 = "abc123"),
        )
        assertEquals(entries, parseSubsList(SubsStore.entriesToJson(entries)))
    }

    /** El selector pone primero el idioma activo y luego Español (audiencia de la casa). */
    @Test
    fun pickerOrdersCurrentThenSpanish() {
        val langs = listOf(
            SubEntry("en", "http://x/en.srt", "srt", 0),
            SubEntry("pt", "http://x/pt.srt", "srt", 1),
            SubEntry("es", "http://x/es.srt", "srt", 2),
        )
        assertEquals(listOf("es", "en", "pt"), orderSubsForPicker(langs, "").map { it.language })
        assertEquals(listOf("en", "es", "pt"), orderSubsForPicker(langs, "en").map { it.language })
        assertEquals(listOf("es", "en", "pt"), orderSubsForPicker(langs, "ES").map { it.language })
        // Un solo idioma se deja tal cual (sin reordenar de más).
        assertEquals(listOf("en"), orderSubsForPicker(listOf(langs[0]), "es").map { it.language })
    }

    // ------------------------------------------------------------------ SRT

    private val sampleSrt = "1\r\n00:00:23,391 --> 00:00:26,709\r\n[**]\r\n\r\n" +
        "2\r\n00:00:26,809 --> 00:00:29,863\r\n[CHANTING]\r\n<i>Mamá</i> vino.<br>Y se fue.\r\n\r\n" +
        "sin indice aqui\r\n00:01:10.545 --> 00:01:14.174\r\n{\\an8}Las Desapariciones\r\n"

    @Test
    fun srtParserReadsTheRealShapeOfProviderFiles() {
        val cues = parseSrt(sampleSrt)
        assertEquals(3, cues.size)
        assertEquals(23_391L, cues[0].startMs)
        assertEquals(26_709L, cues[0].endMs)
        assertEquals("[**]", cues[0].text)
        // <br> vuelve salto real; las etiquetas de posición se van; las de estilo se quedan.
        assertEquals("[CHANTING]\n<i>Mamá</i> vino.\nY se fue.", cues[1].text)
        assertEquals(70_545L, cues[2].startMs)
        assertEquals("Las Desapariciones", cues[2].text)
    }

    @Test
    fun srtParserSkipsBogusBlocks() {
        val broken = "1\n99:99:99,999 --> 00:00:01,000\nnope\n\n" +
            "2\n00:00:02,000 --> 00:00:02,000\ncero\n\n"
        assertTrue(parseSrt(broken).isEmpty())
        assertTrue(parseSrt("").isEmpty())
    }

    @Test
    fun cueAtAndPlayheadFollowTheClock() {
        val cues = parseSrt(sampleSrt)
        assertNull(cueAt(cues, 0L))
        assertEquals("[**]", cueAt(cues, 25_000L)?.text)
        assertNull(cueAt(cues, 30_000L))
        // Con cue activo, el siguiente cambio es su final; sin cue, el inicio del que viene.
        assertEquals(26_710L, nextCuePlayhead(cues, 25_000L))
        assertEquals(23_391L, nextCuePlayhead(cues, 21_000L))
        assertEquals(Long.MAX_VALUE, nextCuePlayhead(cues, 999_000L))
    }

    @Test
    fun tickDelayIsBoundedAndFollowsTheNextCue() {
        val cues = parseSrt(sampleSrt)
        assertEquals(391L, SubsStore.tickDelayMs(cues, 23_000L))
        // Pegado al final del cue: el mínimo evita un bucle de tics a 1 ms.
        assertEquals(80L, SubsStore.tickDelayMs(cues, 26_700L))
        assertEquals(1000L, SubsStore.tickDelayMs(emptyList(), 10_000L))
    }

    @Test
    fun byteDecodingHandlesBomUtf8AndLatin1() {
        val utf8 = "Mamá!".toByteArray(Charsets.UTF_8)
        assertEquals("Mamá!", decodeSrtBytes(utf8))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8
        assertEquals("Mamá!", decodeSrtBytes(bom))
        val latin1 = byteArrayOf(0x4D, 0x61, 0x6D, 0xE1.toByte(), 0x21)
        assertEquals("Mamá!", decodeSrtBytes(latin1))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Mamá!".toByteArray(Charsets.UTF_16LE)
        assertEquals("Mamá!", decodeSrtBytes(utf16))
    }

    @Test
    fun labelsAndLocalNames() {
        assertEquals("Español", languageName("es"))
        assertEquals("Inglés", languageName("EN"))
        assertEquals("", languageName("xx"))
        assertEquals("A812_us", fileNameSafe("A812/us"))
        assertEquals("es_0.srt", SubsStore.srtFileName(SubEntry("es", "http://x", "srt", 0)))
        assertEquals("es_3.srt", SubsStore.srtFileName(SubEntry("ES ", "http://x", "SRT", 3)))
    }
}
