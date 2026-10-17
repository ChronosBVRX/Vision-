package com.chronosbvrx.visionplus.data.remote

import kotlin.random.Random

/**
 * Emparejamiento del control remoto: PIN de 4 dígitos la primera vez y token permanente después.
 *
 * Todo puro (sin Android) para poder probarlo: la tele genera el PIN y los tokens, y el teléfono
 * guarda el token que le tocó. La primera vez el usuario escribe el PIN que la tele muestra; a
 * partir de ahí la reconexión es silenciosa.
 */
object RemotePairing {
    const val PIN_LEN = 4

    /** Cuántos teléfonos recordados; al pasarse se suelta el más viejo. */
    const val MAX_TOKENS = 8

    /** PIN nuevo, con ceros a la izquierda (`"0342"` es válido). */
    fun newPin(random: Random = Random.Default): String =
        (0 until PIN_LEN).joinToString("") { random.nextInt(10).toString() }

    /** Token de 32 caracteres hex. */
    fun newToken(random: Random = Random.Default): String =
        (0 until 16).joinToString("") { random.nextInt(256).toString(16).padStart(2, '0') }

    /** Comparación del PIN con el de la sesión (normaliza: el usuario puede escribir espacios). */
    fun pinMatches(expected: String, typed: String?): Boolean {
        val clean = typed?.filter { it.isDigit() }.orEmpty()
        return expected.isNotEmpty() && clean == expected
    }

    /** ¿El token está recordado por la tele? */
    fun isValidToken(tokens: List<String>, token: String?): Boolean =
        !token.isNullOrBlank() && tokens.any { it == token }

    /**
     * Alta de un teléfono: el token entra al frente y, si se pasa del tope, se suelta el más
     * viejo. Volver a dar de alta el mismo token no duplica ni reordena.
     */
    fun addToken(tokens: List<String>, token: String, max: Int = MAX_TOKENS): List<String> {
        if (token.isBlank()) return tokens
        if (tokens.firstOrNull() == token) return tokens
        val rest = tokens.filter { it != token }
        return (listOf(token) + rest).take(max.coerceAtLeast(1))
    }

    /** El teléfono olvida su token (por ejemplo, si la tele lo rechaza). */
    fun removeToken(tokens: List<String>, token: String): List<String> =
        tokens.filter { it != token }
}
