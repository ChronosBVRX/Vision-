package com.chronosbvrx.visionplus.data

/**
 * Catálogo semilla precompilado de Vision+:
 * Garantiza que en el primer arranque en frío (instalación limpia sin caché o con conexión lenta)
 * la pantalla de Inicio se pinte de inmediato (0 ms) con contenido curado de alta calidad,
 * sin presentar una pantalla negra vacía ni provocar bloqueos en Smart TV / Chromecast.
 */
object CatalogSeed {
    fun initialRows(): List<CatalogRow> = listOf(
        CatalogRow(
            id = "seed-destacados",
            title = "Destacados Vision+",
            films = listOf(
                Film(
                    id = "xtv:seed_1",
                    title = "Gladiador II",
                    year = 2024,
                    durationMin = 148,
                    synopsis = "Años después de presenciar la muerte del admirado héroe Máximo a manos de su tío, Lucio debe entrar en el Coliseo tras ser conquistado su hogar por los tiránicos emperadores que ahora lideran Roma.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/2cxhvwyEwRlysAmRHbtVIxDmVoo.jpg",
                    videoUrl = "xtv:seed_1",
                    sourceUrl = "visionplus.tv",
                    score = 7.1,
                ),
                Film(
                    id = "xtv:seed_2",
                    title = "Deadpool & Wolverine",
                    year = 2024,
                    durationMin = 128,
                    synopsis = "Un apático Wade Wilson se esfuerza por adaptarse a la vida civil. Sus días como el mercenario moralmente flexible Deadpool han quedado atrás. Pero cuando su mundo se enfrenta a una amenaza existencial, Wade debe volver a enfundarse el traje.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                    videoUrl = "xtv:seed_2",
                    sourceUrl = "visionplus.tv",
                    score = 7.7,
                ),
                Film(
                    id = "xtv:seed_3",
                    title = "Duna: Parte Dos",
                    year = 2024,
                    durationMin = 166,
                    synopsis = "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra los conspiradores que destruyeron a su familia.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
                    videoUrl = "xtv:seed_3",
                    sourceUrl = "visionplus.tv",
                    score = 8.2,
                ),
                Film(
                    id = "xtv:seed_4",
                    title = "IntensaMente 2",
                    year = 2024,
                    durationMin = 96,
                    synopsis = "Las pequeñas voces dentro de la cabeza de Riley la conocen por dentro y por fuera, pero todo cambia cuando el cuartel general sufre una demolición repentina para hacer espacio para nuevas emociones.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                    videoUrl = "xtv:seed_4",
                    sourceUrl = "visionplus.tv",
                    score = 7.6,
                ),
                Film(
                    id = "xtv:seed_5",
                    title = "Oppenheimer",
                    year = 2023,
                    durationMin = 180,
                    synopsis = "La historia del científico estadounidense J. Robert Oppenheimer y su papel en el desarrollo de la bomba atómica.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                    videoUrl = "xtv:seed_5",
                    sourceUrl = "visionplus.tv",
                    score = 8.1,
                )
            )
        ),
        CatalogRow(
            id = "seed-series",
            title = "Series Populares",
            films = listOf(
                Film(
                    id = "xtv:seed_s1",
                    title = "The Last of Us",
                    year = 2023,
                    durationMin = 60,
                    synopsis = "Veinte años después de que una plaga diezmara la civilización, Joel es contratado para sacar a Ellie, una niña de 14 años, de una zona de cuarentena opresiva.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/uKvVjK1qsn2VgxAcB0unmGKiPtq.jpg",
                    videoUrl = "xtv:seed_s1",
                    sourceUrl = "visionplus.tv",
                    score = 8.6,
                ),
                Film(
                    id = "xtv:seed_s2",
                    title = "La Casa del Dragón",
                    year = 2022,
                    durationMin = 65,
                    synopsis = "La historia de la Casa Targaryen ambientada 200 años antes de los eventos de Game of Thrones.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
                    videoUrl = "xtv:seed_s2",
                    sourceUrl = "visionplus.tv",
                    score = 8.4,
                ),
                Film(
                    id = "xtv:seed_s3",
                    title = "Severance",
                    year = 2022,
                    durationMin = 50,
                    synopsis = "Mark lidera un equipo de empleados de oficina cuyos recuerdos han sido separados quirúrgicamente entre su vida laboral y personal.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/jtACk5jT6eK5c9Z8eCcl21U99L.jpg",
                    videoUrl = "xtv:seed_s3",
                    sourceUrl = "visionplus.tv",
                    score = 8.5,
                ),
                Film(
                    id = "xtv:seed_s4",
                    title = "Shōgun",
                    year = 2024,
                    durationMin = 60,
                    synopsis = "En el Japón del año 1600, Lord Yoshii Toranaga lucha por su vida mientras sus enemigos en el Consejo de Regentes se unen contra él.",
                    posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
                    videoUrl = "xtv:seed_s4",
                    sourceUrl = "visionplus.tv",
                    score = 8.5,
                )
            )
        )
    )
}
