package com.chronosbvrx.visionplus.data.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Antirregresiones de la configuración remota (Fase 2): parseo y defaults seguros. */
class RemoteConfigTest {

    @Test
    fun parseFlagsExtractsFlagsObject() {
        val flags = RemoteConfig.parseFlags(
            """{"version":123,"flags":{"downloads_enabled":false,"diagnostics_forced_off":true}}""",
        )
        assertFalse(RemoteConfig.flagFrom(flags, "downloads_enabled", true))
        assertTrue(RemoteConfig.flagFrom(flags, "diagnostics_forced_off", false))
    }

    @Test
    fun parseFlagsIsSafeWithGarbage() {
        assertEquals(0, RemoteConfig.parseFlags("no-json").length())
        assertEquals(0, RemoteConfig.parseFlags(null).length())
        assertEquals(0, RemoteConfig.parseFlags("{}").length())
    }

    @Test
    fun flagFromAcceptsTypesAndDefaults() {
        val flags = JSONObject("""{"a":true,"b":"false","c":1,"d":"1"}""")
        assertTrue(RemoteConfig.flagFrom(flags, "a", false))
        assertFalse(RemoteConfig.flagFrom(flags, "b", true))
        assertTrue(RemoteConfig.flagFrom(flags, "c", false))
        assertTrue(RemoteConfig.flagFrom(flags, "d", false))
        // Ausente: manda el default (seguro = activo cuando se pide true).
        assertTrue(RemoteConfig.flagFrom(flags, "missing", true))
        assertFalse(RemoteConfig.flagFrom(flags, "missing", false))
    }

    @Test
    fun intFlagAcceptsNumbersAndStrings() {
        val flags = JSONObject("""{"a":327,"b":"328","c":"no-numero","d":true}""")
        assertEquals(327, RemoteConfig.intFlagFrom(flags, "a", 0))
        assertEquals(328, RemoteConfig.intFlagFrom(flags, "b", 0))
        // Basura o tipo raro: manda el default (0 = sin exigencia).
        assertEquals(0, RemoteConfig.intFlagFrom(flags, "c", 0))
        assertEquals(0, RemoteConfig.intFlagFrom(flags, "d", 0))
        assertEquals(7, RemoteConfig.intFlagFrom(flags, "missing", 7))
    }
}
