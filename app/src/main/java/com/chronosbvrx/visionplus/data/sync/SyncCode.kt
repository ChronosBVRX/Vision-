package com.chronosbvrx.visionplus.data.sync

import java.security.SecureRandom

/**
 * El código que comparten los equipos para sincronizarse (8 caracteres, sin 0/O/1/I
 * para poder dictarlo sin confusiones). El servidor lo guarda hasheado: aquí solo
 * se normaliza, se genera y se presenta en grupos de 4.
 */
internal const val SYNC_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
internal const val SYNC_CODE_LENGTH = 8

/** «abcd-efgh» → «ABCDEFGH»; vacío si no es un código válido (6..16 alfanuméricos). */
internal fun normalizeSyncCode(raw: String?): String {
    val text = raw.orEmpty().trim()
    val separatorsOk = text.all { it.isWhitespace() || it == '-' || it == '_' || (it.isLetterOrDigit() && it.code < 128) }
    if (!separatorsOk) return ""
    val clean = text.filter { it.isLetterOrDigit() && it.code < 128 }.uppercase()
    if (clean.length !in 6..16) return ""
    return clean
}

/** Código nuevo de 8 caracteres con `SecureRandom` (el `Random` es inyectable para tests). */
internal fun generateSyncCode(random: java.util.Random = SecureRandom()): String {
    val sb = StringBuilder(SYNC_CODE_LENGTH)
    repeat(SYNC_CODE_LENGTH) { sb.append(SYNC_ALPHABET[random.nextInt(SYNC_ALPHABET.length)]) }
    return sb.toString()
}

/** «ABCDEFGH» → «ABCD-EFGH» para leerlo en pantalla. */
internal fun formatSyncCode(code: String): String =
    code.trim().uppercase().chunked(4).joinToString("-")
