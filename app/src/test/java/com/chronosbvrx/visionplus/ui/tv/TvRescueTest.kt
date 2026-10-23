package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.FollowAiring
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.Airing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes puros del regreso del foco (sin composición): la decisión de rescate
 * nunca come teclas que no puede resolver, y el índice de la fila del regreso
 * réplica el orden real del `LazyColumn` del Inicio.
 */
class TvRescueTest {

    private fun film(id: String) = Film(
        id = id, title = id, year = 2020, durationMin = 60, synopsis = "",
        posterUrl = "", videoUrl = "https://example.com/$id", sourceUrl = "test",
    )

    private fun card(id: String) = ResumeCard(
        entry = ResumeEntry(
            key = id, title = id, xtvCode = id, positionMs = 0L, durationMs = 0L,
            updatedAt = 0L,
        ),
        film = film(id),
        progress = 0f,
    )

    private fun follow(id: String) = FollowAiring(
        film = film(id),
        airing = Airing(
            show = id, season = 1, episode = 1, title = "",
            airtimeMs = 0L, imdb = "", titleId = id,
        ),
        label = "T1 · C1",
        today = false,
    )

    // ---------------------------------------------------------- decisión de rescate

    @Test
    fun rescueConsumesOnlyWhenItCanLand() {
        assertTrue(shouldConsumeKey(dialogsOpen = 0, screenHasFocus = false, targetAttached = true))
        assertFalse(shouldConsumeKey(dialogsOpen = 1, screenHasFocus = false, targetAttached = true))
        assertFalse(shouldConsumeKey(dialogsOpen = 0, screenHasFocus = true, targetAttached = true))
        // El héroe fuera de pantalla (o sin componer): la tecla debe pasar, no tragarse.
        assertFalse(shouldConsumeKey(dialogsOpen = 0, screenHasFocus = false, targetAttached = false))
    }

    @Test
    fun rescueDoesNotEatKeysWithoutAComposedTarget() {
        FocusRescue.target = null
        FocusRescue.screenHasFocus = false
        FocusRescue.dialogsOpen = 0
        assertFalse("sin destino no se consume", FocusRescue.rescue())

        val detached = LandingFocus().apply { attached = false }
        FocusRescue.pushTarget(detached)
        assertFalse("destino sin componer no se consume", FocusRescue.rescue())

        // Un destino marcado como compuesto pero cuyo nodo no existe (sin composición real)
        // tampoco se come la tecla: el rescate VERIFICA el aterrizaje antes de consumir —
        // antes la tecla se perdía en silencio (el caso FOCUS_RESCUE_MISS).
        val phantom = LandingFocus().apply { attached = true }
        FocusRescue.pushTarget(phantom)
        assertFalse("un destino fantasma no se come la tecla", FocusRescue.rescue())

        FocusRescue.dialogsOpen = 1
        assertFalse("con diálogo no se consume", FocusRescue.rescue())
        FocusRescue.dialogsOpen = 0
        FocusRescue.screenHasFocus = true
        assertFalse("con foco no se consume", FocusRescue.rescue())
        FocusRescue.screenHasFocus = false
        FocusRescue.popTarget(phantom)
        FocusRescue.popTarget(detached)
    }

    @Test
    fun rescueCandidatesSkipDeadTargetsAndKeepTheOrder() {
        val dead = LandingFocus().apply { attached = false }
        val alive = LandingFocus().apply { attached = true }
        val top = LandingFocus().apply { attached = true }
        // Del último al primero, solo los compuestos: si el de arriba está muerto, el rescate
        // sigue con el de abajo en vez de comerse la tecla.
        assertEquals(listOf(top, alive), rescueCandidates(listOf(dead, alive, top), dialogsOpen = 0, screenHasFocus = false))
        assertTrue(rescueCandidates(listOf(dead, alive, top), dialogsOpen = 1, screenHasFocus = false).isEmpty())
        assertTrue(rescueCandidates(listOf(dead, alive, top), dialogsOpen = 0, screenHasFocus = true).isEmpty())
    }

    // ---------------------------------------------------------- índice de la fila

    private fun rows(ids: List<String>, prefix: String) =
        ids.map { CatalogRow("$prefix-$it", "Fila $it", listOf(film("x"))) }

    @Test
    fun returnRowIndexMirrorsTheHomeColumn() {
        val top10 = listOf(film("t1"), film("t2"))
        val premieres = listOf(film("e1"))
        // billboard(0) + hubs(1) → Top 10 en 2, Estrenos en 3.
        assertEquals(0, homeReturnRowIndex("hero", "hero", top10, premieres, emptyList(), emptyList(), emptyList(), emptyList(), false, false))
        assertEquals(2, homeReturnRowIndex("t2", "hero", top10, premieres, emptyList(), emptyList(), emptyList(), emptyList(), false, false))
        assertEquals(3, homeReturnRowIndex("e1", "hero", top10, premieres, emptyList(), emptyList(), emptyList(), emptyList(), false, false))
        // Sin regreso o sin fila: no se revela nada.
        assertEquals(-1, homeReturnRowIndex(null, "hero", top10, premieres, emptyList(), emptyList(), emptyList(), emptyList(), false, false))
        assertEquals(-1, homeReturnRowIndex("fantasma", "hero", top10, premieres, emptyList(), emptyList(), emptyList(), emptyList(), false, false))
    }

    @Test
    fun returnRowIndexCountsTheWeeklyTrendingRow() {
        // La fila «Populares de la semana» se pinta entre Estrenos y «Seguir viendo»: el
        // índice del regreso DEBE contarla o revela la fila de arriba y el foco del mando
        // se pierde (el caso real que reportó el usuario al volver de una serie).
        val top10 = listOf(film("t1"))
        val trending = listOf(film("w1"))
        val own = listOf(CatalogRow("home-kind-series", "Series", listOf(film("s1"))))
        // 0 billboard, 1 hubs, 2 top10, 3 trending, 4 propia (sin estrenos ni continue).
        assertEquals(
            3,
            homeReturnRowIndex("w1", "hero", top10, emptyList(), trending, emptyList(), emptyList(), emptyList(), false, false),
        )
        assertEquals(
            4,
            homeReturnRowIndex("s1", "hero", top10, emptyList(), trending, emptyList(), own, emptyList(), false, false),
        )
        // Sin la fila (servidor viejo o sin internet), el índice baja uno y sigue exacto.
        assertEquals(
            3,
            homeReturnRowIndex("s1", "hero", top10, emptyList(), emptyList(), emptyList(), own, emptyList(), false, false),
        )
    }

    @Test
    fun returnRowIndexCountsContinueAndOwnRows() {
        val cards = listOf(card("c1"))
        val own = listOf(CatalogRow("home-kind-series", "Series", listOf(film("s1"))))
        val other = listOf(CatalogRow("portal", "Portal", listOf(film("p1"))))
        // 0 billboard, 1 hubs, 2 continue (sin top10/estrenos), 3 propia, 4 resto.
        assertEquals(
            2,
            homeReturnRowIndex("c1", "hero", emptyList(), emptyList(), emptyList(), cards, own, other, false, false),
        )
        assertEquals(
            3,
            homeReturnRowIndex("s1", "hero", emptyList(), emptyList(), emptyList(), cards, own, other, false, false),
        )
        assertEquals(
            4,
            homeReturnRowIndex("p1", "hero", emptyList(), emptyList(), emptyList(), cards, own, other, false, false),
        )
        // «Seguir viendo» también casa por clave del historial o código XTV.
        assertEquals(
            2,
            homeReturnRowIndex("xtv:abc", "hero", emptyList(), emptyList(), emptyList(), listOf(card("xtv:abc")), emptyList(), emptyList(), false, false),
        )
    }

    @Test
    fun returnRowIndexSkipsPlaceholders() {
        // Con fantasmas de carga hay una fila extra (índice 2): todo se recorre uno.
        val top10 = listOf(film("t1"))
        assertEquals(
            3,
            homeReturnRowIndex("t1", "hero", top10, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), showPlaceholders = true, showNoRows = false),
        )
        assertEquals(
            3,
            homeReturnRowIndex("t1", "hero", top10, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), showPlaceholders = false, showNoRows = true),
        )
    }

    @Test
    fun homeRowSlotsFollowThePaintingOrder() {
        // Fuente única: el orden de las claves es el orden real de pintado y el índice del
        // regreso sale de la MISMA lista (agregar una fila ya no puede correrlo).
        val top10 = listOf(film("t1"))
        val premieres = listOf(film("e1"))
        val trending = listOf(film("w1"))
        val cards = listOf(card("c1"))
        val because = listOf(film("b1"))
        val followed = listOf(follow("f1"))
        val own = listOf(CatalogRow("home-kind-series", "Series", listOf(film("s1"))))
        val other = listOf(CatalogRow("portal", "Portal", listOf(film("p1"))))
        val slots = homeRowSlots(
            heroFilmId = "hero",
            top10 = top10,
            premieres = premieres,
            trending = trending,
            continueCards = cards,
            becauseTitle = "Porque viste algo",
            becauseFilms = because,
            followCards = followed,
            ownKindRows = own,
            otherRows = other,
            showPlaceholders = false,
            showNoRows = false,
        )
        assertEquals(
            listOf(
                "billboard", "home_quick_hubs", "home_top10", "home_estrenos", "home_trending_week",
                "continue", "home_because", "home_follow", "home-kind-series", "portal", "end_spacer",
            ),
            slots.map { it.key },
        )
        assertEquals("el héroe vive en la fila 0", 0, homeRowIndexOf(slots, "hero"))
        assertEquals(2, homeRowIndexOf(slots, "t1"))
        assertEquals(3, homeRowIndexOf(slots, "e1"))
        assertEquals(4, homeRowIndexOf(slots, "w1"))
        assertEquals(5, homeRowIndexOf(slots, "c1"))
        assertEquals("la fila nueva se cuenta para el regreso", 6, homeRowIndexOf(slots, "b1"))
        assertEquals("la agenda también", 7, homeRowIndexOf(slots, "f1"))
        assertEquals(8, homeRowIndexOf(slots, "s1"))
        assertEquals(9, homeRowIndexOf(slots, "p1"))
        assertEquals(-1, homeRowIndexOf(slots, "fantasma"))
        assertEquals(-1, homeRowIndexOf(slots, null))

        // Con fantasmas de carga (o el aviso de reintento) hay una fila extra justo después de hubs.
        val withStatus = homeRowSlots(
            heroFilmId = null,
            top10 = top10,
            premieres = emptyList(),
            trending = emptyList(),
            continueCards = emptyList(),
            ownKindRows = emptyList(),
            otherRows = emptyList(),
            showPlaceholders = true,
            showNoRows = false,
        )
        assertEquals(listOf("billboard", "home_quick_hubs", "home_status", "home_top10", "end_spacer"), withStatus.map { it.key })
    }

    // ---------------------------------------------------------- foco a prueba de balas

    @Test
    fun safeFocusRequestNeverThrowsOnDetachedRequester() {
        // `FocusRequester.requestFocus()` lanza IllegalStateException cuando su nodo no está
        // adjunto (tarjeta reciclada por el LazyColumn, control ausente por configuración):
        // el crash real de la ZTE en «Deportes en vivo» (dispatchKeyEvent → FocusRequester).
        // El envoltorio DEBE devolver false y jamás propagar la excepción.
        val detached = androidx.compose.ui.focus.FocusRequester()
        assertFalse("un requester huérfano no puede tumbar la pantalla", requestFocusSafely(detached))
    }
}
