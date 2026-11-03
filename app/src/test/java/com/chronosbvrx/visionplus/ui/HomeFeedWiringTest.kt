package com.chronosbvrx.visionplus.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cableado entre los DOS APK. Si alguien cambia un host, borra el provider o se olvida
 * del refresco, el launcher se queda sin Inicio y este test lo caza.
 */
class HomeFeedWiringTest {

    @Test
    fun theAppAnswersTheLinksTheLauncherFires() {
        val main = require("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("atiende el host `open`", main.contains("\"open\".equals(uri.host"))
        assertTrue("atiende el host `title`", main.contains("\"title\".equals(uri.host"))
        assertTrue("cablea el buscador con la consulta dictada", main.contains("SearchSeed.seed("))
        assertTrue("resuelve la ficha del título", main.contains("private fun titleRouteFor"))
        assertTrue("trae la sección desde el launcher", main.contains("\"section\" -> uri.pathSegments"))
        assertTrue("abre Mi lista / historial", main.contains("\"library\" -> Route.Library"))
    }

    @Test
    fun theFeedIsKeptWarmWithoutBlockingTheApp() {
        val main = require("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("refresco al volver a la app", main.contains("HomeFeedStore.refreshAsync(this)"))
        assertTrue(
            "refresco al terminar de cargar el catálogo",
            main.contains("HomeFeedStore.refreshAsync(this@MainActivity)"),
        )
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("el refresco vive en segundo plano", store.contains("Dispatchers.IO"))
        assertTrue("el provider sirve primero el disco", store.contains("if (!historyDirty && age in 0..STALE_MS)"))
        assertTrue("nunca se queda sin respuesta", store.contains("HomeFeed(meta = meta(app))"))
    }

    @Test
    fun settingsCanInstallAndChooseTheLauncher() {
        val settings = require("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        assertTrue("ofrece instalar Vision+ Home", settings.contains("LauncherInstall.installLatest(context)"))
        assertTrue("abre el selector de Inicio del sistema", settings.contains("LauncherInstall.openHomeSettings(context)"))
        assertTrue("explica el modo", settings.contains("Poner Vision+ Home como Inicio"))
        val install = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/LauncherInstall.kt")
        assertTrue("descarga el asset del contrato", install.contains("HomeContract.LAUNCHER_ASSET"))
        assertTrue("abre el instalador del sistema", install.contains("application/vnd.android.package-archive"))
    }

    @Test
    fun theLauncherSearchesThroughTheApp() {
        val provider = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedProvider.kt")
        assertTrue("el provider atiende /search", provider.contains("\"search\""))
        assertTrue("usa el buscador de la app", provider.contains("HomeFeedStore.searchItems("))
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("el buscador es el del servidor", store.contains("RemoteIndexSource.search("))
    }

    @Test
    fun theSportsFeedUsesTheRealMatchPhaseAndNotTheSignalCount() {
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("usa la misma agenda que la app", store.contains("GeeAgenda.phaseOf(event, now)"))
        assertTrue("manda el estado de la agenda", store.contains("status = event.status"))
        assertTrue("manda el inicio resuelto", store.contains("startMs = GeeAgenda.startMs(event)"))
        assertFalse("`alive` no puede decidir si el partido está en vivo", store.contains("event.alive > 0"))
        assertTrue("el terminado se rotula Final", store.contains("\"Final\""))
    }

    @Test
    fun theGuideCoversTheLiveMosaicAndRefreshesFromTheProvider() {
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("la guía cubre los 400 canales del mosaico", store.contains("EPG_CHANNELS = 400"))
        assertTrue("una sola petición masiva", store.contains("X4KApi.epgBulkFor("))
        assertTrue("respaldo por canal si el servidor es viejo", store.contains("EPG_FALLBACK_CHANNELS = 120"))
        assertTrue("se pide en paralelo como respaldo", store.contains("EPG_PARALLEL"))
        assertTrue("la ventana viaja al launcher", store.contains("startMs = guide?.startMs"))
        assertTrue("y también el fin", store.contains("endMs = guide?.endMs"))
        val api = require("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt")
        assertTrue("el endpoint masivo del VPS", api.contains("/v1/epg/bulk"))
        assertTrue("mapea ESPN/Star+ igual que el individual", api.contains("epgNameFor(code, name)"))
        val provider = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedProvider.kt")
        assertTrue("el provider mantiene la guía caliente", provider.contains("HomeFeedStore.refreshAsync(context)"))
    }

    @Test
    fun theLauncherOpensTheExactEventInSports() {
        val main = require("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("atiende open/sports", main.contains("\"sports\" ->"))
        assertTrue("siembra el partido", main.contains("DeportesReturn.pickingId = event"))
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("el feed trae la agenda", store.contains("GeeSportsSource.cachedEvents("))
        assertTrue("y su enlace al evento", store.contains("VisionLinks.sports(event.id)"))
    }

    @Test
    fun continueWatchingOfASeriesOpensItsTitleAndSitsOnTop() {
        val phone = require("src/main/java/com/chronosbvrx/visionplus/ui/home/HomeScreen.kt")
        val tv = require("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt")
        for ((name, source) in listOf("móvil" to phone, "TV" to tv)) {
            assertTrue("$name usa la regla única de serie", source.contains("continueOpensSeries(card)"))
            assertTrue("$name abre la ficha de la serie", source.contains("onOpenFilm("))
            assertTrue("$name tiene ficha de respaldo", source.contains("resumeSeriesFilm(card.entry)"))
        }
        // En el móvil la fila se pinta antes de las categorías (Top 10, Novedades…).
        val continueAt = phone.indexOf("item(key = \"continue\")")
        val topAt = phone.indexOf("item(key = \"home_top10\")")
        assertTrue("«Seguir viendo» va antes del Top 10", continueAt in 0 until topAt)
        // En la tele la fuente única de filas también la coloca arriba.
        val slots = tv.indexOf("if (continueCards.isNotEmpty()) add(HomeRowSlot.Continue(continueCards))")
        val top10 = tv.indexOf("if (top10.isNotEmpty()) add(HomeRowSlot.Top10(top10))")
        assertTrue("la tele pinta «Seguir viendo» antes del Top 10", slots in 0 until top10)

        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("el feed del launcher abre la ficha en series", store.contains("openLink = if (episode)"))
        val launcher = File("launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
            .takeIf { it.exists() }
            ?: File("../launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertNotNull("falta LauncherScreen.kt", launcher)
        assertTrue(
            "el launcher prefiere la ficha de la serie",
            launcher!!.readText().contains("item.openLink.ifBlank { item.playLink }"),
        )
    }

    @Test
    fun theLauncherFeedRefreshesAsSoonAsTheHistoryChanges() {
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("detecta la escritura del historial", store.contains("PlaybackHistory.lastChangedAt(context)"))
        assertTrue("parche barato de Seguir viendo", store.contains("fun refreshContinueNow"))
        assertTrue("no espera el freno de 20 s", store.contains("if (!force && !historyChanged && now - lastRefreshAt < REFRESH_THROTTLE_MS) return"))
        assertTrue("el provider reconstruye al detectar el cambio", store.contains("if (historyDirty)"))
        assertTrue("el rebuild completo también parchea primero", store.contains("if (historyChanged) runCatching { refreshContinueNow(app) }"))
        val repository = File("launcher/src/main/java/com/chronosbvrx/visionplus/home/data/FeedRepository.kt")
            .takeIf { it.exists() }
            ?: File("../launcher/src/main/java/com/chronosbvrx/visionplus/home/data/FeedRepository.kt")
        assertNotNull(repository)
        assertTrue("el launcher observa el provider", repository!!.readText().contains("registerContentObserver"))
        val screen = File("launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
            .takeIf { it.exists() }
            ?: File("../launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertNotNull(screen)
        val text = screen!!.readText()
        assertTrue("se refresca al volver del reproductor", text.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue("escucha los avisos del feed", text.contains("feeds.observe"))
    }

    @Test
    fun theLiveFeedPutsMexicoFirst() {
        val store = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("ordena México primero", store.contains("val mexico = all.filter { isMexico(it.name) }"))
        assertTrue("los canales de la casa mandan", store.contains("mxPriority(it.name)"))
        assertTrue("el feed marca el canal mexicano", store.contains("mexico = isMexico(channel.name)"))
    }

    @Test
    fun theManifestDeclaresEveryHostTheLauncherFires() {
        val manifest = require("src/main/AndroidManifest.xml")
        for (host in listOf("watch", "live", "title", "open")) {
            assertTrue("falta el host $host en el filtro VIEW", manifest.contains("android:host=\"$host\""))
        }
    }

    @Test
    fun theGameJoinPathNeverRestrictsTheLauncherLinks() {
        // Regresión real: un pathPrefix en el MISMO intent-filter se aplica a todos los
        // hosts (watch/live/title/open quedaron exigiendo /join y el launcher no abría nada).
        val manifest = require("src/main/AndroidManifest.xml")
        val filters = manifest.split("<intent-filter>").drop(1).map { it.substringBefore("</intent-filter>") }
        val launcher = filters.first { it.contains("android:host=\"watch\"") }
        for (host in listOf("watch", "live", "title", "open")) {
            assertTrue("el filtro del launcher debe declarar $host", launcher.contains("android:host=\"$host\""))
        }
        assertFalse(
            "el pathPrefix de Vision+ Play no puede viajar en el filtro del launcher",
            launcher.contains("pathPrefix") || launcher.contains("pathPattern"),
        )
        val play = filters.first { it.contains("android:host=\"play\"") }
        assertTrue("Play conserva su ruta /join en un filtro propio", play.contains("android:pathPrefix=\"/join\""))
    }

    @Test
    fun thePermissionIsSignatureAndTheProviderIsReadOnly() {
        val manifest = require("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:protectionLevel=\"signature\""))
        assertTrue(manifest.contains("android:readPermission=\"com.chronosbvrx.visionplus.permission.HOME_FEED\""))
        assertTrue(manifest.contains("android:name=\".data.homefeed.HomeFeedProvider\""))
        val provider = require("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedProvider.kt")
        assertTrue("no se puede escribir", provider.contains("UnsupportedOperationException"))
    }

    @Test
    fun bothApksShareTheContractModule() {
        val settings = File("settings.gradle.kts").takeIf { it.exists() } ?: File("../settings.gradle.kts")
        assertTrue("falta el contrato", settings.readText().contains("include(\":home-contract\")"))
        assertTrue("falta el launcher", settings.readText().contains("include(\":launcher\")"))
        val build = File("app/build.gradle.kts").takeIf { it.exists() } ?: File("../app/build.gradle.kts")
        assertTrue(build.readText().contains("project(\":home-contract\")"))
        val launcherBuild = File("launcher/build.gradle.kts")
            .takeIf { it.exists() } ?: File("../launcher/build.gradle.kts")
        assertTrue(launcherBuild.readText().contains("project(\":home-contract\")"))
    }

    @Test
    fun theLauncherNeverGrowsItsOwnStreamingEngine() {
        val launcherBuild = File("launcher/build.gradle.kts")
            .takeIf { it.exists() } ?: File("../launcher/build.gradle.kts")
        val text = launcherBuild.readText()
        assertTrue("no debe empaquetar media3", !text.contains("media3"))
        assertTrue("no debe empaquetar okhttp", !text.contains("okhttp"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
