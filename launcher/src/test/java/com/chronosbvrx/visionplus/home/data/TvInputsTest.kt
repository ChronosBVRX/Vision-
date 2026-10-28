package com.chronosbvrx.visionplus.home.data

import android.media.tv.TvInputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las entradas de video del televisor son lógica pura hasta que se abren: aquí se fija el
 * orden que espera cualquiera (TV abierta, HDMI 1, HDMI 2…) y los nombres que se pintan.
 */
class TvInputsTest {

    private fun input(
        id: String,
        label: String = id,
        kind: TvSourceKind = TvSourceKind.HDMI,
        port: Int = 0,
    ) = TvInputSource(id = id, label = label, kind = kind, port = port)

    @Test
    fun theTunerComesFirstAndTheHdmiPortsGoInOrder() {
        val ordered = orderTvInputs(
            listOf(
                input("hw:3", "HDMI 3", port = 3),
                input("av", "AV", kind = TvSourceKind.OTHER),
                input("hw:1", "HDMI 1", port = 1),
                input("dtv", "TV abierta", kind = TvSourceKind.TUNER),
                input("hw:2", "HDMI 2", port = 2),
            ),
        )
        assertEquals(listOf("dtv", "hw:1", "hw:2", "hw:3", "av"), ordered.map { it.id })
    }

    @Test
    fun theLabelSaysHdmiWithItsNumberEvenIfTheSystemNameIsDirty() {
        assertEquals("HDMI 3", tvInputLabel("HDMI3 (ARC)", TvSourceKind.HDMI, 3))
        // Sin número de puerto del sistema, se saca del nombre: nunca «HDMI3 (ARC)».
        assertEquals("HDMI 3", tvInputLabel("HDMI3 (ARC)", TvSourceKind.HDMI, 0))
        assertEquals("HDMI", tvInputLabel("", TvSourceKind.HDMI, 0))
        assertEquals("TV abierta", tvInputLabel("", TvSourceKind.TUNER, 0))
        assertEquals("DTV", tvInputLabel("DTV", TvSourceKind.TUNER, 0))
        assertEquals("Entrada de video", tvInputLabel("  ", TvSourceKind.OTHER, 0))
    }

    @Test
    fun theSubtitleExplainsWhatTheSourceIs() {
        assertEquals("Sintonizador de la tele · TV abierta", tvInputSubtitle(input("a", kind = TvSourceKind.TUNER)))
        assertEquals("Entrada HDMI 2", tvInputSubtitle(input("b", port = 2)))
        assertEquals("Entrada HDMI", tvInputSubtitle(input("c")))
        assertEquals("Entrada de video del televisor", tvInputSubtitle(input("d", kind = TvSourceKind.OTHER)))
    }

    @Test
    fun theSystemTypeIsTranslatedToTheThreeKindsTheLauncherUnderstands() {
        assertEquals(TvSourceKind.TUNER, tvSourceKind(TvInputInfo.TYPE_TUNER))
        assertEquals(TvSourceKind.HDMI, tvSourceKind(TvInputInfo.TYPE_HDMI))
        // Cualquier otra entrada (AV, componente, VGA…) cuenta como entrada de video.
        assertEquals(TvSourceKind.OTHER, tvSourceKind(TvInputInfo.TYPE_COMPOSITE))
        assertEquals(TvSourceKind.OTHER, tvSourceKind(TvInputInfo.TYPE_VGA))
    }

    @Test
    fun hidingAnInputOnlyTakesItOutOfTheLauncher() {
        val all = listOf(
            input("dtv", kind = TvSourceKind.TUNER),
            input("hw:1", port = 1),
            input("hw:2", port = 2),
        )
        assertEquals(listOf("dtv", "hw:2"), visibleInputs(all, setOf("hw:1")).map { it.id })
        assertTrue(visibleInputs(all, emptySet()).isNotEmpty())
        assertEquals("dtv", tunerOf(all)?.id)
        assertEquals(null, tunerOf(all.filter { it.kind != TvSourceKind.TUNER }))
    }

    @Test
    fun duplicatedIdsFromTheSystemAreListedOnce() {
        val repeated = listOf(input("hw:1", port = 1), input("hw:1", port = 1))
        assertEquals(1, orderTvInputs(repeated).size)
    }

    @Test
    fun theSameHdmiAnnouncedTwiceBecomesOneCard() {
        // Pasa en teles con dos apps que exponen el mismo puerto con ids distintos.
        val twice = listOf(
            input("com.tv/.input/hdmi1", "HDMI 1", port = 1),
            input("com.other/.input/hdmi1", "HDMI 1", port = 1),
            input("com.tv/.input/hdmi2", "HDMI 2", port = 2),
        )
        assertEquals(listOf("HDMI 1", "HDMI 2"), orderTvInputs(twice).map { it.label })
    }

    @Test
    fun theHdmiNumberIsReadFromTheNameWhenThePortIsMissing() {
        assertEquals(3, hdmiPortIn("HDMI3 (ARC)"))
        assertEquals(2, hdmiPortIn("hdmi-2"))
        assertEquals(1, hdmiPortIn("Entrada HDMI 1"))
        assertEquals(0, hdmiPortIn("HDMI"))
        assertEquals(0, hdmiPortIn("AV"))
    }
}
