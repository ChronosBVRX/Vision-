package com.chronosbvrx.visionplus.data.debloat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las listas de CleanYourTV y el emparejamiento por modelo, sin tocar una tele. */
class DebloatListTest {

    @Test
    fun parsesATestedListAndKeepsItsSections() {
        val text = """
            # TCL Smart TV Pro, US (device G05, product G05_4K_US)
            # Android 11 (Google TV), stock launcher: com.google.android.apps.tv.launcherx
            # Keep com.tcl.tvinput, com.tcl.settings and com.tcl.keyhelp (remote).

            # Ads and tracking
            com.tcl.ttvs
            com.tcl.messagebox
            # Voice
            # com.tcl.smartalexa  # Alexa
        """.trimIndent()
        val result = ListParser.parse("tcl-g05", text)
        assertTrue(result is ParseResult.Ok)
        val list = (result as ParseResult.Ok).list
        assertEquals("TCL Smart TV Pro, US (device G05, product G05_4K_US)", list.title)
        assertEquals(listOf("com.tcl.ttvs", "com.tcl.messagebox"), list.packages)
        assertEquals("Ads and tracking", list.entries.first().section)
        assertEquals("com.tcl.smartalexa", list.optional.single().pkg)
        assertEquals("com.google.android.apps.tv.launcherx", list.stockLauncher)
        assertTrue("la lista pide conservar la entrada de TV", "com.tcl.tvinput" in list.keep)
        assertTrue("la lista pide conservar el mando", "com.tcl.keyhelp" in list.keep)
        assertFalse(list.isFireOs)
    }

    @Test
    fun rejectsAListWithACommandInsteadOfAPackage() {
        val bad = """
            # Tele rara
            com.example.ads
            pm uninstall com.example.tv
        """.trimIndent()
        val result = ListParser.parse("rara", bad)
        assertTrue(result is ParseResult.Rejected)
        val rejected = result as ParseResult.Rejected
        assertTrue(rejected.reasons.any { it.contains("not a package name") })
    }

    @Test
    fun rejectsDuplicatesInsteadOfDisablingTwice() {
        val result = ListParser.parse("dup", "# Tele\ncom.example.a\ncom.example.a")
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reasons.any { it.contains("listed twice") })
    }

    @Test
    fun everyBundledListParses() {
        val dir = File("src/main/assets/debloat")
        assertTrue("faltan las listas empaquetadas", dir.isDirectory)
        val files = dir.listFiles { file -> file.isFile && file.extension == "txt" && !file.name.startsWith("LICENSE") }
            .orEmpty()
            .sortedBy { it.name }
        assertTrue("deberían viajar cientos de listas", files.size >= 300)
        val rejected = files.mapNotNull { file ->
            val result = ListParser.parse(file.nameWithoutExtension, file.readText())
            result as? ParseResult.Rejected
        }
        assertTrue("listas rechazadas: ${rejected.take(3)}", rejected.isEmpty())
    }

    private val tcl = TvInfo(
        model = "Smart TV Pro",
        device = "G05",
        product = "G05_4K_US",
        brand = "TCL",
        manufacturer = "TCL",
        sdk = 30,
        release = "11",
        serial = "abc",
    )

    @Test
    fun matchesTheTestedModelByCodenameAndAndroidVersion() {
        val list = (ListParser.parse(
            "tcl-g05-google-tv",
            "# TCL Smart TV Pro, US (device G05, product G05_4K_US)\n# Android 11 (Google TV)\ncom.tcl.ttvs",
        ) as ParseResult.Ok).list
        val match = ModelMatcher.match(tcl, listOf(list), installed = setOf("com.tcl.ttvs")).first()
        assertTrue(match.exact)
        assertTrue(match.score >= ModelMatcher.EXACT)
    }

    @Test
    fun aGenericModelDoesNotMatchByItself() {
        val list = (ListParser.parse(
            "tcl-otra",
            "# TCL otra tele\n# Android 11\ncom.tcl.ttvs\ncom.tcl.ads",
        ) as ParseResult.Ok).list
        val generic = tcl.copy(model = "Smart TV", device = "OTRO", product = "OTRO")
        val match = ModelMatcher.match(generic, listOf(list), installed = setOf("com.tcl.ttvs")).firstOrNull()
        assertNotNull("la marca parecida sigue ofreciéndose como no probada", match)
        assertFalse(match!!.exact)
    }

    @Test
    fun neverTouchesAndroidHomeOrLiveApps() {
        listOf(
            "android",
            "com.android.systemui",
            "com.android.tv.settings",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.vending",
            "com.google.android.gms",
            "com.chronosbvrx.visionplus",
            "com.chronosbvrx.visionplus.home",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
        ).forEach { pkg ->
            assertNotNull("$pkg debe estar protegido", Protected.reason(pkg))
        }
        assertNotNull(
            "un IME en uso no se puede apagar",
            Protected.reason("com.example.keyboard", live = setOf("com.example.keyboard")),
        )
        assertNull(Protected.reason("com.tcl.ttvs"))
    }

    @Test
    fun defaultsOnlyTurnOffAdsAndPreloadedApps() {
        val text = """
            # TCL G05 (device G05)
            # Android 11
            # Ads
            com.tcl.ttvs
            # Preloaded
            com.tcl.messagebox
            # Voice
            com.google.android.katniss
            # Casting
            com.google.android.apps.mediashell
            # Updates
            com.tcl.ota
            # Streaming
            com.netflix.ninja
            # Optional
            # com.tcl.airplay2  # AirPlay
        """.trimIndent()
        val list = (ListParser.parse("tcl-g05", text) as ParseResult.Ok).list
        val snapshot = TvSnapshot(
            info = tcl,
            props = emptyMap(),
            installed = list.packages.toSet() + list.optional.map { it.pkg },
            disabled = emptySet(),
            home = "com.google.android.apps.tv.launcherx/.HomeActivity",
            liveProtected = emptySet(),
        )
        val match = ListMatch(list, ModelMatcher.EXACT)
        val plan = DebloatRules.plan(match, snapshot, CleanupGroup.DEFAULTS, exact = true)
        assertEquals(listOf("com.tcl.ttvs", "com.tcl.messagebox"), plan.selected)
        assertEquals(2, plan.selectedCount)
        assertTrue("el asistente no se marca solo", "com.google.android.katniss" !in plan.selected)
        assertTrue("el casting no se marca solo", "com.google.android.apps.mediashell" !in plan.selected)
        assertTrue("las actualizaciones no se marcan solas", "com.tcl.ota" !in plan.selected)
        assertTrue("el streaming no se marca solo", "com.netflix.ninja" !in plan.selected)
        assertTrue("las opcionales no se marcan solas", "com.tcl.airplay2" !in plan.selected)
    }

    @Test
    fun aListCannotTurnOffAndroidPlayStoreOrVisionPlus() {
        val list = (ListParser.parse(
            "peligrosa",
            "# Tele\n# Ads\ncom.android.vending\ncom.google.android.gms\ncom.android.systemui\n" +
                "com.chronosbvrx.visionplus\ncom.chronosbvrx.visionplus.home\ncom.google.android.apps.tv.launcherx\ncom.tcl.ttvs",
        ) as ParseResult.Ok).list
        val snapshot = TvSnapshot(
            info = tcl,
            props = emptyMap(),
            installed = list.packages.toSet(),
            disabled = emptySet(),
            home = null,
            liveProtected = emptySet(),
        )
        val plan = DebloatRules.plan(ListMatch(list, ModelMatcher.EXACT), snapshot, CleanupGroup.DEFAULTS, exact = true)
        assertEquals(listOf("com.tcl.ttvs"), plan.selected)
    }

    @Test
    fun theRemoteMicAndAssistantAreOptInEvenIfAListPutsThemUnderPreloaded() {
        val list = (ListParser.parse(
            "tcl-voz",
            "# TCL voz\n# Preloaded apps\ncom.google.android.katniss\ncom.google.android.tv.remote.service\ncom.tcl.messagebox",
        ) as ParseResult.Ok).list
        val snapshot = TvSnapshot(
            info = tcl,
            props = emptyMap(),
            installed = list.packages.toSet(),
            disabled = emptySet(),
            home = null,
            liveProtected = emptySet(),
        )
        val match = ListMatch(list, ModelMatcher.EXACT)
        val safe = DebloatRules.plan(match, snapshot, CleanupGroup.DEFAULTS, exact = true)
        assertTrue("el asistente no se apaga solo", "com.google.android.katniss" !in safe.selected)
        assertTrue("el servicio del mando no se apaga solo", "com.google.android.tv.remote.service" !in safe.selected)
        assertEquals(listOf("com.tcl.messagebox"), safe.selected)
        val withVoice = DebloatRules.plan(match, snapshot, CleanupGroup.DEFAULTS + CleanupGroup.VOICE, exact = true)
        assertTrue(withVoice.selected.contains("com.google.android.katniss"))
        assertTrue(withVoice.selected.contains("com.google.android.tv.remote.service"))
    }

    @Test
    fun offersAppsFromOtherListsButNeverPicksThemAlone() {
        val main = (ListParser.parse(
            "tcl-g05",
            "# TCL G05 (device G05)\n# Android 11\n# Ads\ncom.tcl.ttvs",
        ) as ParseResult.Ok).list
        val other = (ListParser.parse(
            "hisense-otra",
            "# Hisense otra tele\n# Ads\ncom.other.ads\ncom.tcl.messagebox",
        ) as ParseResult.Ok).list
        val snapshot = TvSnapshot(
            info = tcl,
            props = emptyMap(),
            installed = setOf("com.tcl.ttvs", "com.other.ads", "com.tcl.messagebox"),
            disabled = emptySet(),
            home = null,
            liveProtected = emptySet(),
        )
        val plan = DebloatRules.plan(
            ListMatch(main, ModelMatcher.EXACT),
            snapshot,
            CleanupGroup.DEFAULTS,
            exact = true,
            catalog = listOf(main, other),
        )
        val extra = plan.candidates.first { it.pkg == "com.other.ads" }
        assertEquals(CleanupSource.OTHER, extra.source)
        assertEquals(listOf("com.tcl.ttvs"), plan.selected)
        assertTrue("la app de otra lista existe pero no se marca", extra.pkg in plan.candidates.map { it.pkg })
    }

    @Test
    fun aSimilarListStartsWithNothingSelected() {
        val list = (ListParser.parse(
            "tcl-otra",
            "# TCL otra tele (device OTRO)\n# Android 11\ncom.tcl.ttvs\ncom.tcl.messagebox",
        ) as ParseResult.Ok).list
        val snapshot = TvSnapshot(
            info = tcl,
            props = emptyMap(),
            installed = list.packages.toSet(),
            disabled = emptySet(),
            home = null,
            liveProtected = emptySet(),
        )
        val plan = DebloatRules.plan(ListMatch(list, 40), snapshot, emptySet(), exact = false)
        assertFalse(plan.tested)
        assertTrue(plan.selected.isEmpty())
        assertEquals(emptySet<CleanupGroup>(), plan.groups)
        val optIn = DebloatRules.plan(ListMatch(list, 40), snapshot, setOf(CleanupGroup.PRELOADED), exact = false)
        assertTrue("si la persona elige un grupo, se respeta aunque sea un modelo parecido", optIn.selected.isNotEmpty())
    }
}
