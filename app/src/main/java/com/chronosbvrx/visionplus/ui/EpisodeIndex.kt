package com.chronosbvrx.visionplus.ui

/**
 * Índice de capítulo válido para una lista de [size] elementos, incluso si está vacía.
 *
 * Una serie puede abrir su ficha antes de que lleguen los capítulos del portal (o una
 * temporada puede no traer ninguno). En ese momento `lista.lastIndex` vale `-1` y
 * `index.coerceIn(0, -1)` lanza `IllegalArgumentException: Cannot coerce value to an
 * empty range`, que cierra la app desde el `LaunchedEffect` de la ficha. Con esta
 * guarda el índice siempre es usable y quien lo consuma decide con `getOrNull`.
 */
internal fun episodeIndexOrZero(index: Int, size: Int): Int =
    if (size <= 0) 0 else index.coerceIn(0, size - 1)
