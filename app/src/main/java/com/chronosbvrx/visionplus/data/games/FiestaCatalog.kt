package com.chronosbvrx.visionplus.data.games

/**
 * Los seis juegos de Vision+ Fiesta 3.0. Viven separados del catálogo histórico
 * para que la UI nueva sepa cuándo usar el motor genérico y cuándo las pantallas
 * antiguas.
 */
object FiestaCatalog {
    const val UltimaPalabra = "ultima_palabra"
    const val VoxPopuli = "vox_populi"
    const val FueraDeEpoca = "fuera_de_epoca"
    const val TextoRoto = "texto_roto"
    const val FranelaFest = "franela_fest"
    const val ElTasador = "el_tasador"

    val ids: Set<String> = setOf(
        UltimaPalabra, VoxPopuli, FueraDeEpoca, TextoRoto, FranelaFest, ElTasador,
    )

    fun isFiesta(id: String?): Boolean = id in ids

    fun hostName(id: String?): String = when (id) {
        UltimaPalabra -> "Nora Vela"
        VoxPopuli -> "Don Chisme"
        FueraDeEpoca -> "Profesor Tic Tac"
        TextoRoto -> "La Máquina"
        FranelaFest -> "El Crítico"
        ElTasador -> "El Tasador"
        else -> "Dalia"
    }

    fun hostVoice(id: String?): String = when (id) {
        UltimaPalabra -> "nora"
        VoxPopuli -> "chisme"
        FueraDeEpoca -> "tictac"
        TextoRoto -> "maquina"
        FranelaFest -> "critico"
        ElTasador -> "tasador"
        else -> "dalia"
    }
}
