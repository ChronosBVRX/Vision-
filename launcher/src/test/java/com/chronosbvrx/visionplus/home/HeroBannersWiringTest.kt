package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El hero no puede quedarse en una idea: el launcher pide los banners diarios al VPS,
 * los cachea, mezcla los partidos del feed y conserva el respaldo sin red. El worker
 * del VPS busca el arte una vez al día.
 */
class HeroBannersWiringTest {

    @Test
    fun theLauncherAsksTheVpsForTheDailyBanners() {
        val repo = source("data/BannersRepository.kt")
        assertTrue("endpoint diario", repo.contains("/v1/banners"))
        assertTrue("misma llave que el índice", repo.contains("X-Api-Key"))
        assertTrue("caché en disco", repo.contains("launcher_banners.json"))
        assertTrue("parseo puro", repo.contains("parseBanners("))
        assertTrue("caducidad del servidor", repo.contains("parseBannersExpiry"))
        assertTrue("una sola descarga", repo.contains("Mutex()"))
        assertTrue("enfriamiento si falla", repo.contains("FAIL_BACKOFF_MS"))
        assertTrue("sin sondeo en bucle", repo.contains("nextRefreshAt"))
        assertTrue("https", repo.contains("https://visionplusv2.duckdns.org"))
        assertTrue("no hay cleartext", !repo.contains("http://"))

        val screen = source("LauncherScreen.kt")
        assertTrue("lee la caché al primer frame", screen.contains("BannersRepository.cached(context)"))
        assertTrue("refresca en segundo plano", screen.contains("BannersRepository.load(context)"))
        assertTrue("mezcla cine + deportes", screen.contains("composeHeroSlides("))
        assertTrue("se lo pasa al Inicio", screen.contains("hero = heroSlides"))
        assertTrue("se pausa con el foco", screen.contains("heroPaused = heroFocused"))
        assertTrue("solo refresca con el Inicio al frente", screen.contains("repeatOnLifecycle"))
    }

    @Test
    fun theHeroShowsTheSportsScoreboardAndThePausedCarousel() {
        val hero = source("ui/Hero.kt")
        assertTrue("modelo unificado", hero.contains("items: List<HeroSlide>"))
        assertTrue("animación de arte", hero.contains("Animatable("))
        assertTrue("transición de diapositiva", hero.contains("Crossfade("))
        assertTrue("se pausa al enfocar", hero.contains("pausedState.value"))
        assertTrue("indicador con progreso", hero.contains("scaleX = progress"))
        assertTrue("marcador", hero.contains("SportsScoreboard("))
        assertTrue("punto en vivo", hero.contains("LiveDot("))
        assertTrue("agenda desde el hero", hero.contains("onSportsHub()"))
        assertTrue("clave estable del arte", hero.contains("bannerArtCacheKey"))
        assertTrue("caché de Coil por ruta", hero.contains("diskCacheKey"))
        assertTrue("petición de arte cacheada", hero.contains("bannerImageRequest("))
        assertTrue("siguiente diapositiva lista", hero.contains("Coil.imageLoader(context).enqueue"))

        val surfaces = source("ui/Surfaces.kt")
        assertTrue("el Inicio usa el hero nuevo", surfaces.contains("HeroStage("))
        assertTrue("le pasa las diapositivas", surfaces.contains("items = hero"))
        assertTrue("pausa cableada", surfaces.contains("heroPaused"))
        assertTrue("deportes al hub", surfaces.contains("onOpenSports"))
        val screen = source("LauncherScreen.kt")
        assertTrue("no recorta el hero al volver", screen.contains("LaunchedEffect(heroFocused)"))
        assertTrue("ancla la lista al tope", screen.contains("homeListState.scrollToItem(0)"))
    }

    @Test
    fun theVpsRefreshesTheBannersOnceADay() {
        val server = file("server/api/banners.py")
        assertTrue("vigencia diaria", server.contains("BANNER_TTL_SECONDS = 20 * 3600"))
        assertTrue("tabla propia", server.contains("launcher_banners"))
        assertTrue("arte panorámico TMDB", server.contains("w1280"))
        assertTrue("selección pura", server.contains("def pick_movie_candidates("))
        assertTrue("refresco en segundo plano", server.contains("background.add_task(refresh_movies"))
        assertTrue("un solo refresco en el VPS", server.contains("pg_try_advisory_lock"))
        assertTrue("enfriamiento del refresco", server.contains("_BACKGROUND_RETRY_SECONDS"))
        assertTrue("próximo refresco futuro", server.contains("next_at ="))
        val app = file("server/api/app.py")
        assertTrue("ruta /v1/banners", app.contains("@app.get(\"/v1/banners\")"))
        assertTrue("importa el módulo", app.contains("import banners as launcher_banners"))
    }

    private fun source(rel: String): String = file("launcher/src/main/java/com/chronosbvrx/visionplus/home/$rel")

    private fun file(rel: String): String {
        val found = listOf(File(rel), File("../$rel"), File("../../$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
