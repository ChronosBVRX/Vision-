package com.chronosbvrx.visionplus.data

/**
 * Lotes del «Ver más» de una sección por género.
 *
 * Las hileras llegan del servidor con un tope (40) para que el Inicio abra rápido;
 * al abrir el género, la cuadrícula puede pedir más páginas a `/v1/category`. El
 * tamaño del lote y el techo dependen del equipo: nunca se carga una lista enorme
 * de golpe en una TV de 1.5–2 GB (la causa de los cierres por memoria).
 */
object SectionPaging {
    internal const val LOW_END_BATCH = 24
    internal const val NORMAL_BATCH = 48
    internal const val LOW_END_CAP = 180
    internal const val NORMAL_CAP = 480

    internal fun batch(lowEnd: Boolean): Int = if (lowEnd) LOW_END_BATCH else NORMAL_BATCH

    internal fun cap(lowEnd: Boolean): Int = if (lowEnd) LOW_END_CAP else NORMAL_CAP

    /** Página que continúa una lista ya cargada (la 1 rellena el hueco inicial). */
    internal fun nextPage(loaded: Int, batch: Int): Int =
        if (batch <= 0) 1 else (loaded / batch) + 1

    /** ¿Queda algo por pedir y el equipo aún no llegó a su techo? */
    internal fun canLoadMore(loaded: Int, total: Int, cap: Int): Boolean =
        loaded < total && loaded < cap
}
