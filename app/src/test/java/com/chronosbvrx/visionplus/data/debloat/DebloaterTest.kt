package com.chronosbvrx.visionplus.data.debloat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El motor de apagado: lista de deshacer, verificación y copia antes de tocar la tele. */
class DebloaterTest {

    private class FakeTv(
        val installed: MutableSet<String>,
        val disabled: MutableSet<String> = mutableSetOf(),
        val refuse: Set<String> = emptySet(),
        val mirrorWorks: Boolean = true,
    ) {
        val commands = mutableListOf<String>()
        var props = mapOf(
            "ro.product.model" to "Smart TV Pro",
            "ro.product.device" to "G05",
            "ro.product.name" to "G05_4K_US",
            "ro.product.brand" to "TCL",
            "ro.product.manufacturer" to "TCL",
            "ro.build.version.sdk" to "30",
            "ro.build.version.release" to "11",
            "ro.serialno" to "abc",
        )
        var home = "com.google.android.apps.tv.launcherx/.HomeActivity"

        suspend fun shell(command: String): String {
            commands += command
            return when {
                command == DebloatCommands.GETPROP ->
                    props.entries.joinToString("\n") { "[${it.key}]: [${it.value}]" }
                command == DebloatCommands.LIST_PACKAGES ->
                    installed.joinToString("\n") { "package:$it" }
                command == DebloatCommands.LIST_DISABLED ->
                    disabled.joinToString("\n") { "package:$it" }
                command == DebloatCommands.RESOLVE_HOME -> home
                command.startsWith("settings get secure") -> ""
                command == DebloatCommands.TV_INPUTS -> ""
                command == DebloatCommands.READ_MIRROR ->
                    "com.tcl.ttvs\ncom.tcl.messagebox\nEND"
                command == DebloatCommands.CLEAR_MIRROR -> "cleared"
                command.startsWith("mkdir -p") -> if (mirrorWorks) "ok" else "no"
                command.startsWith("for p in ") -> {
                    val pkgs = command.substringAfter("for p in ").substringBefore(";").split(" ")
                    pkgs.joinToString("\n") { "@$it\npkgFlags=[ SYSTEM HAS_CODE ]" }
                }
                command.startsWith("pm disable-user --user 0 ") -> {
                    val pkg = command.substringAfterLast(' ')
                    if (pkg in refuse) "Error: java.lang.SecurityException: Cannot disable a protected package: $pkg"
                    else {
                        disabled += pkg
                        "Package $pkg new state: disabled-user"
                    }
                }
                command.startsWith("pm enable ") -> {
                    val pkg = command.substringAfterLast(' ')
                    disabled -= pkg
                    "Package $pkg new state: enabled"
                }
                command.startsWith("settings list ") -> "setting=value"
                else -> ""
            }
        }
    }

    @Test
    fun readsTheTvWithoutChangingIt() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(mutableSetOf("com.tcl.ttvs", "com.android.vending"), disabled = mutableSetOf("com.tcl.messagebox"))
        val debloater = TvDebloater(tv::shell)
        val snapshot = debloater.snapshot()
        assertEquals("TCL Smart TV Pro", snapshot.info.displayName)
        assertEquals(30, snapshot.info.sdk)
        assertEquals("11", snapshot.info.release)
        assertEquals(setOf("com.tcl.ttvs", "com.android.vending"), snapshot.installed)
        assertEquals(setOf("com.tcl.messagebox"), snapshot.disabled)
        assertEquals("com.google.android.apps.tv.launcherx/.HomeActivity", snapshot.home)
        assertTrue("no debe apagar nada al leer", tv.disabled == setOf("com.tcl.messagebox"))
    }

    @Test
    fun writesTheUndoListBeforeDisablingAndVerifiesEveryBatch() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(mutableSetOf("com.tcl.ttvs", "com.tcl.messagebox", "com.tcl.ota"))
        val debloater = TvDebloater(tv::shell, sleep = {})
        val outcome = debloater.run(listOf("com.tcl.ttvs", "com.tcl.messagebox", "no.instalada"))
        assertTrue(outcome is CleanupOutcome.Done)
        val done = outcome as CleanupOutcome.Done
        assertEquals(listOf("com.tcl.ttvs", "com.tcl.messagebox"), done.cleaned)
        assertEquals("no está instalada", done.skipped["no.instalada"])
        assertTrue(done.failed.isEmpty())
        assertTrue(tv.disabled.containsAll(listOf("com.tcl.ttvs", "com.tcl.messagebox")))
        val mirrorIndex = tv.commands.indexOfFirst { it.startsWith("mkdir -p") }
        val firstDisable = tv.commands.indexOfFirst { it.startsWith("pm disable-user") }
        assertTrue("la lista de deshacer se escribe antes del primer apagado", mirrorIndex in 0 until firstDisable)
        assertTrue("falta la verificación con pm list packages -d", tv.commands.count { it == DebloatCommands.LIST_DISABLED } >= 2)
    }

    @Test
    fun aProtectedPackageIsSkippedAndTheRestContinues() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(
            mutableSetOf("com.tcl.ttvs", "com.tcl.messagebox"),
            refuse = setOf("com.tcl.messagebox"),
        )
        val outcome = TvDebloater(tv::shell, sleep = {}).run(listOf("com.tcl.ttvs", "com.tcl.messagebox"))
        assertTrue(outcome is CleanupOutcome.Done)
        val done = outcome as CleanupOutcome.Done
        assertEquals(listOf("com.tcl.ttvs"), done.cleaned)
        assertEquals("el firmware la protege", done.skipped["com.tcl.messagebox"])
    }

    @Test
    fun aBrokenUndoListStopsBeforeChangingAnything() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(mutableSetOf("com.tcl.ttvs"), mirrorWorks = false)
        val outcome = TvDebloater(tv::shell, sleep = {}).run(listOf("com.tcl.ttvs"))
        assertTrue(outcome is CleanupOutcome.Stopped)
        assertTrue((outcome as CleanupOutcome.Stopped).reason.contains("lista de deshacer"))
        assertTrue("no puede apagar sin lista de deshacer", tv.disabled.isEmpty())
    }

    @Test
    fun alreadyDisabledAndPersistentAppsAreNotTouched() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(mutableSetOf("com.tcl.ttvs", "com.tcl.persistent"), disabled = mutableSetOf("com.tcl.ttvs"))
        // El paquete persistente se detecta por pkgFlags.
        val debloater = TvDebloater({ command ->
            if (command.startsWith("for p in ")) {
                "@com.tcl.persistent\npkgFlags=[ SYSTEM PERSISTENT ]"
            } else {
                tv.shell(command)
            }
        }, sleep = {})
        val outcome = debloater.run(listOf("com.tcl.ttvs", "com.tcl.persistent"))
        val done = outcome as CleanupOutcome.Done
        assertTrue(done.cleaned.isEmpty())
        assertEquals("ya estaba apagada", done.skipped["com.tcl.ttvs"])
        assertEquals("app persistente del sistema", done.skipped["com.tcl.persistent"])
        assertFalse(tv.commands.any { it.contains("disable-user") })
    }

    @Test
    fun backupRoundTripsWithSettingsAndPackages() = kotlinx.coroutines.runBlocking {
        val tv = FakeTv(mutableSetOf("com.tcl.ttvs", "com.android.vending"))
        val debloater = TvDebloater(tv::shell)
        val backup = debloater.backup(debloater.snapshot(), "tcl-g05", now = 1234L)
        val copy = TvBackup.fromJson(backup.toJson())
        assertEquals(backup, copy)
        assertEquals("tcl-g05", copy.listId)
        assertEquals(listOf("com.android.vending", "com.tcl.ttvs"), copy.packages)
        assertEquals("setting=value", copy.settings["global"])
        assertEquals("com.google.android.apps.tv.launcherx/.HomeActivity", copy.home)
    }

    @Test
    fun readsStorageAndCacheSizesFromDiskstats() {
        val out = """
            Data-Free: 1048576K / 8388608K total
            Package Names: ["com.tcl.ttvs", "com.netflix.ninja", "com.android.vending"]
            App Sizes: [100, 200, 300]
            App Data Sizes: [10, 20, 30]
            Cache Sizes: [4096, 8192, -1]
        """.trimIndent()
        val storage = DebloatParse.diskStats(out)
        assertEquals(1048576L, storage?.freeKb)
        assertEquals(8388608L, storage?.totalKb)
        assertEquals(12, storage?.cacheMb)
        assertEquals(listOf("com.netflix.ninja" to 8, "com.tcl.ttvs" to 4), storage?.top)
    }

    @Test
    fun parseToleratesWarningsButNotAnEmptyAnswer() {
        assertEquals(
            setOf("com.tcl.ttvs"),
            DebloatParse.packages("WARNING: linker: something\npackage:com.tcl.ttvs"),
        )
        try {
            DebloatParse.packages("error: no packages")
            throw AssertionError("una respuesta sin paquetes no puede pasar")
        } catch (expected: TvCommandError) {
            assertTrue(expected.message.orEmpty().contains("respuesta inesperada"))
        }
    }
}
