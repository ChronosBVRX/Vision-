package com.chronosbvrx.visionplus.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián del orden de pistas VOD: la resolución manda, pero dentro de la misma
 * resolución gana la pista que el usuario de Vision+ puede ver sin sorpresas:
 * audio latino, H.264/HEVC antes que AV1 y contenedores MP4/WEB-DL.
 */
class StreamRankerTest {

    private fun facts(
        quality: String = "1080p",
        audio: String = "",
        encode: String = "",
        video: String = "",
    ) = StreamRanker.Facts(quality, audio, encode, video)

    @Test
    fun tierUnderstandsPortalLabels() {
        assertEquals(2160, StreamRanker.tier("4K"))
        assertEquals(2160, StreamRanker.tier("2160p"))
        assertEquals(2160, StreamRanker.tier("UHD"))
        assertEquals(1080, StreamRanker.tier("1080p"))
        assertEquals(1080, StreamRanker.tier("FHD"))
        assertEquals(720, StreamRanker.tier("720p"))
        assertEquals(720, StreamRanker.tier("HD"))
        assertEquals(480, StreamRanker.tier("SD"))
        assertEquals(360, StreamRanker.tier("360p"))
        assertEquals(0, StreamRanker.tier("Auto"))
    }

    @Test
    fun explicitLatinoOutranksGenericLatinoAndOriginal() {
        val explicit = StreamRanker.score(facts(audio = "Español Latino (Dual)"))
        val generic = StreamRanker.score(facts(audio = "Latino"))
        val original = StreamRanker.score(facts(audio = "Inglés"))
        val castellano = StreamRanker.score(facts(audio = "Castellano"))
        assertTrue("latino explícito > latino ($explicit/$generic)", explicit > generic)
        assertTrue("latino > original ($generic/$original)", generic > original)
        assertTrue("original > castellano ($original/$castellano)", original > castellano)
    }

    @Test
    fun aForeignLanguageDoesNotStealTheLatinoBonus() {
        val mixed = StreamRanker.score(facts(audio = "Latino Hindi"))
        val pure = StreamRanker.score(facts(audio = "Latino"))
        assertEquals(150, mixed)
        assertTrue("una pista mixta no puede empatar con la latina pura", pure > mixed)
    }

    @Test
    fun av1FallsBehindH264AndHevcInTheSameResolution() {
        val av1 = StreamRanker.score(facts(encode = "AV1"))
        val h264 = StreamRanker.score(facts(encode = "H.264", video = "WEB-DL"))
        val hevc = StreamRanker.score(facts(encode = "HEVC"))
        assertTrue("H.264 > AV1 ($h264/$av1)", h264 > av1)
        assertTrue("HEVC > AV1 ($hevc/$av1)", hevc > av1)
        assertTrue("H.264 > HEVC por compatibilidad ($h264/$hevc)", h264 > hevc)
    }

    @Test
    fun mp4GetsItsOwnBonus() {
        assertTrue(StreamRanker.score(facts(encode = "H.264", video = "MP4")) >
            StreamRanker.score(facts(encode = "H.264")))
    }

    @Test
    fun theResolutionAlwaysWinsOverAudioAndCodec() {
        val ranked = StreamRanker.rank(
            listOf(
                facts(quality = "1080p", audio = "Español Latino", encode = "H.264"),
                facts(quality = "4K", audio = "Inglés", encode = "AV1"),
            ),
        ) { it }
        assertEquals("4K", ranked.first().quality)
    }

    @Test
    fun insideATierLatinoAndCompatibleCodecWin() {
        val ranked = StreamRanker.rank(
            listOf(
                facts(quality = "1080p", audio = "Inglés", encode = "AV1"),
                facts(quality = "1080p", audio = "Español Latino", encode = "H.264"),
                facts(quality = "1080p", audio = "Castellano", encode = "H.264"),
            ),
        ) { it }
        assertEquals("Español Latino", ranked[0].audio)
        assertEquals("Castellano", ranked[1].audio)
        assertEquals("Inglés", ranked[2].audio)
    }

    @Test
    fun tiesKeepThePortalOrder() {
        val first = facts(quality = "720p", audio = "Latino")
        val second = facts(quality = "720p", audio = "Latino")
        val ranked = StreamRanker.rank(listOf(first, second)) { it }
        assertEquals(listOf(first, second), ranked)
    }

    @Test
    fun aSingleMediaIsLeftAlone() {
        val only = facts(quality = "1080p", audio = "Inglés")
        assertEquals(listOf(only), StreamRanker.rank(listOf(only)) { it })
    }
}
