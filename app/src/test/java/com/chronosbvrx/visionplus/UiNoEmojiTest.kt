package com.chronosbvrx.visionplus

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardián del lote «UI Premium»: ningún emoji puede volver a colarse en la interfaz.
 *
 * La casa tiene su propio vocabulario de iconos (`VisionIcons` en Compose y los vectores
 * `ic_vp_*` del reproductor nativo); los emojis dependen de la fuente del sistema y en
 * Smart TV baratas salen monocolor o como cuadro. Este test recorre las fuentes de UI y
 * falla si encuentra uno.
 *
 * Lo único perdonado es el `☆` de los códigos del proveedor (`ST☆R+HD_cyx`), que es dato,
 * no diseño. Las flechas y signos tipográficos de la casa (`→`, `−`, `·`…) también pasan.
 */
class UiNoEmojiTest {

    /** Fuentes que se revisan: toda la UI y las etiquetas de datos que se pintan. */
    private val roots = listOf(
        "src/main/java/com/chronosbvrx/visionplus/ui",
        "src/main/java/com/chronosbvrx/visionplus/data/Popularity.kt",
        "src/main/java/com/chronosbvrx/visionplus/data/LiveTaxonomy.kt",
        "src/main/java/com/chronosbvrx/visionplus/data/sports/SportsLinker.kt",
    )

    /** El único carácter «de dato» que se permite. */
    private val allowed = setOf('☆')

    /** Flechas y signos que sí son parte de la tipografía de la casa (no son emoji). */
    private val typographic = setOf('→', '←', '↔', '↑', '↓')

    private fun isEmoji(c: Char): Boolean {
        val code = c.code
        return when {
            code in 0x1F000..0x1FAFF -> true // emoji modernos (🔥, 🎬, 🇲🇽…)
            code in 0x2600..0x27BF -> true // símbolos (★, ✓, ✕, ✂…)
            code in 0x2B00..0x2BFF -> true // flechas decoradas (⬆, ⬇…)
            code in 0x2300..0x23FF -> true // técnicos que Android pinta como emoji (⏱, ⏭…)
            code in 0x2190..0x21FF -> c !in typographic // flechas: solo las tipográficas pasan
            code == 0xFE0F -> true // selector de presentación emoji
            else -> false
        }
    }

    @Test
    fun uiSourcesHaveNoEmoji() {
        val offenders = ArrayList<String>()
        var scanned = 0
        for (root in roots) {
            val rootFile = File(root)
            assertTrue("No existe la raíz a revisar: ${rootFile.absolutePath}", rootFile.exists())
            val files = if (rootFile.isDirectory) {
                rootFile.walkTopDown().filter { it.extension == "kt" }.toList()
            } else {
                listOf(rootFile)
            }
            assertTrue("Sin fuentes que revisar en $root", files.isNotEmpty())
            scanned += files.size
            for (file in files) {
                file.readLines().forEachIndexed { index, raw ->
                    val trimmed = raw.trimStart()
                    // Los comentarios documentan el pasado (o el porqué): no son interfaz.
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    val bad = raw.filter { it !in allowed && isEmoji(it) }
                    if (bad.isNotEmpty()) {
                        offenders += "${file.path}:${index + 1} → '$bad' en: ${raw.trim()}"
                    }
                }
            }
        }
        assertTrue("El guardián debe recorrer la UI completa (se revisaron $scanned archivos)", scanned >= 50)
        assertTrue(
            "La interfaz no debe llevar emojis (usa VisionIcons / los vectores ic_vp_*). Infractores:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
