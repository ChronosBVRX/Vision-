package com.chronosbvrx.visionplus.data.profile

import androidx.annotation.DrawableRes
import com.chronosbvrx.visionplus.R

/** Los tres estantes del selector de imágenes de perfil. */
internal enum class ProfileAvatarCategory(val label: String) {
    CINE("Cine"),
    SERIES("Series"),
    ANIME("Anime"),
}

/**
 * Una imagen que se puede elegir como avatar. El [id] es lo que se guarda en el perfil:
 * nunca se guarda el recurso ni una URL, así una actualización de la app puede mejorar el
 * arte sin romper los perfiles existentes.
 */
internal data class ProfileAvatarOption(
    val id: String,
    val label: String,
    val category: ProfileAvatarCategory,
    @DrawableRes val drawable: Int,
)

/**
 * Catálogo fijo de avatares con **personajes originales** de cine, series y anime, con variedad de público adulto, familiar e infantil.
 *
 * Las imágenes viajan dentro del APK (WebP de 256×256, ~830 KB en total) para que el
 * selector funcione sin red y en una tele con poca memoria. Son retratos de personajes
 * originales (no pósters ni recortes) para que se reconozcan en un círculo de 56-76 dp.
 * Si un perfil guardado apunta a un id que ya no existe, la interfaz cae al avatar de inicial.
 */
internal object ProfileAvatars {

    val all: List<ProfileAvatarOption> = listOf(
        ProfileAvatarOption("cine_noir", "Detective noir", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_noir),
        ProfileAvatarOption("cine_agente", "Agente secreto", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_agente),
        ProfileAvatarOption("cine_dama", "Dama misteriosa", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_dama),
        ProfileAvatarOption("cine_pistolero", "Pistolero", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_pistolero),
        ProfileAvatarOption("cine_gladiador", "Gladiador", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_gladiador),
        ProfileAvatarOption("cine_mafia", "Jefe mafioso", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_mafia),
        ProfileAvatarOption("cine_cientifica", "Científica loca", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_cientifica),
        ProfileAvatarOption("cine_vampiro", "Vampiro elegante", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_vampiro),
        ProfileAvatarOption("cine_pirata", "Capitán pirata", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_pirata),
        ProfileAvatarOption("cine_astronauta", "Astronauta", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_astronauta),
        ProfileAvatarOption("cine_heroe", "Superhéroe escarlata", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_heroe),
        ProfileAvatarOption("cine_mago", "Mago anciano", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_mago),
        ProfileAvatarOption("cine_princesa", "Princesa guerrera", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_princesa),
        ProfileAvatarOption("cine_robot", "Robot amigo", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_robot),
        ProfileAvatarOption("cine_dragon", "Dragón bebé", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_dragon),
        ProfileAvatarOption("cine_pirata_nino", "Pirata niño", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_pirata_nino),
        ProfileAvatarOption("cine_exploradora", "Exploradora", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_exploradora),
        ProfileAvatarOption("cine_hada", "Hada del bosque", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_hada),
        ProfileAvatarOption("cine_zorro", "Zorro aventurero", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_zorro),
        ProfileAvatarOption("cine_ranger", "Ranger espacial", ProfileAvatarCategory.CINE, R.drawable.profile_avatar_cine_ranger),
        ProfileAvatarOption("series_detective", "Detective de serie", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_detective),
        ProfileAvatarOption("series_doctora", "Doctora de urgencias", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_doctora),
        ProfileAvatarOption("series_abogado", "Abogado brillante", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_abogado),
        ProfileAvatarOption("series_hacker", "Hacker nocturno", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_hacker),
        ProfileAvatarOption("series_reina", "Reina de fantasía", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_reina),
        ProfileAvatarOption("series_superviviente", "Superviviente", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_superviviente),
        ProfileAvatarOption("series_agente", "Agente federal", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_agente),
        ProfileAvatarOption("series_chef", "Chef estrella", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_chef),
        ProfileAvatarOption("series_periodista", "Periodista", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_periodista),
        ProfileAvatarOption("series_vampiro", "Vampiro moderno", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_vampiro),
        ProfileAvatarOption("series_bruja", "Bruja adolescente", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_bruja),
        ProfileAvatarOption("series_estudiante", "Estudiante curioso", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_estudiante),
        ProfileAvatarOption("series_mascota", "Mascota divertida", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_mascota),
        ProfileAvatarOption("series_alien", "Alien amistoso", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_alien),
        ProfileAvatarOption("series_heroe_joven", "Héroe adolescente", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_heroe_joven),
        ProfileAvatarOption("series_perro", "Perro detective", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_perro),
        ProfileAvatarOption("series_robot_casa", "Robot de casa", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_robot_casa),
        ProfileAvatarOption("series_ninera", "Niñera mágica", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_ninera),
        ProfileAvatarOption("series_grumete", "Grumete valiente", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_grumete),
        ProfileAvatarOption("series_aventurera", "Aventurera juvenil", ProfileAvatarCategory.SERIES, R.drawable.profile_avatar_series_aventurera),
        ProfileAvatarOption("anime_samurai", "Samurái errante", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_samurai),
        ProfileAvatarOption("anime_ninja", "Ninja de la luna", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_ninja),
        ProfileAvatarOption("anime_mecha", "Piloto de mecha", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_mecha),
        ProfileAvatarOption("anime_cazadora", "Cazadora de demonios", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_cazadora),
        ProfileAvatarOption("anime_espadachin", "Espadachín marcado", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_espadachin),
        ProfileAvatarOption("anime_hechicero", "Hechicero moderno", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_hechicero),
        ProfileAvatarOption("anime_capitan", "Capitán del cielo", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_capitan),
        ProfileAvatarOption("anime_magica", "Chica mágica", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_magica),
        ProfileAvatarOption("anime_gato", "Gato robot", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_gato),
        ProfileAvatarOption("anime_entrenador", "Entrenador de monstruos", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_entrenador),
        ProfileAvatarOption("anime_ninja_novato", "Ninja novato", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_ninja_novato),
        ProfileAvatarOption("anime_nino_dragon", "Niño dragón", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_nino_dragon),
        ProfileAvatarOption("anime_shonen", "Héroe shonen", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_shonen),
        ProfileAvatarOption("anime_estudiante", "Estudiante de instituto", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_estudiante),
        ProfileAvatarOption("anime_idol", "Idol del escenario", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_idol),
        ProfileAvatarOption("anime_detective", "Detective escolar", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_detective),
        ProfileAvatarOption("anime_alquimista", "Alquimista", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_alquimista),
        ProfileAvatarOption("anime_cadete", "Cadete espacial", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_cadete),
        ProfileAvatarOption("anime_hada", "Hada anime", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_hada),
        ProfileAvatarOption("anime_sumo", "Luchador sumo", ProfileAvatarCategory.ANIME, R.drawable.profile_avatar_anime_sumo),
    )

    private val byId: Map<String, ProfileAvatarOption> = all.associateBy { it.id }

    fun find(id: String): ProfileAvatarOption? = byId[id]

    fun of(category: ProfileAvatarCategory): List<ProfileAvatarOption> =
        all.filter { it.category == category }
}
