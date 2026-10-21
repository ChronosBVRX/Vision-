package com.chronosbvrx.visionplus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El historial guarda el playCode XTV pelado (`abc`), pero el control remoto maneja la
 * referencia con prefijo (`xtv:abc`). Estas son las claves que se prueban antes de decir que
 * no hay minuto guardado: si alguna falla, se abre desde el principio (nunca un crash).
 */
class RemoteResumeTest {

    @Test
    fun anXtvRefAlsoTriesTheBarePlayCode() {
        assertEquals(listOf("xtv:ABC", "ABC"), resumeKeyCandidates("xtv:ABC"))
    }

    @Test
    fun aDirectUrlIsItsOwnKey() {
        assertEquals(listOf("https://cdn.example/v.mp4"), resumeKeyCandidates("https://cdn.example/v.mp4"))
    }

    @Test
    fun blankRefsHaveNoKeys() {
        assertTrue(resumeKeyCandidates("").isEmpty())
        assertTrue(resumeKeyCandidates("   ").isEmpty())
    }
}
