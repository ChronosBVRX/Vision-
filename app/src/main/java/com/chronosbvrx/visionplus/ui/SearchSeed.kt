package com.chronosbvrx.visionplus.ui

/**
 * La consulta que llega de fuera (el buscador de Vision+ Home, con texto o dictada por voz).
 * El buscador la consume UNA sola vez al abrirse: no se queda pegada en la siguiente visita.
 */
object SearchSeed {
    @Volatile
    private var pending: String = ""

    fun seed(query: String) {
        pending = query.trim()
    }

    fun take(): String {
        val query = pending
        pending = ""
        return query
    }
}
