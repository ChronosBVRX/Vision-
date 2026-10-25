package com.chronosbvrx.visionplus.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * El manifest es la mitad del Inicio: si el filtro HOME no existe (o nace empatado con el
 * launcher de fábrica) ninguna pantalla de la app puede arreglarlo. Estos guardianes evitan
 * que una edición futura vuelva a mezclar HOME con los filtros de apps o le quite la prioridad.
 */
class LauncherManifestTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private fun launcherActivity(): Element {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("falta el manifest del launcher", file.exists())
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(file)
        val activities = doc.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            if (activity.getAttributeNS(android, "name").endsWith("LauncherActivity")) return activity
        }
        throw AssertionError("no hay actividad de Inicio en el manifest")
    }

    private fun filters(activity: Element): List<Element> {
        val out = ArrayList<Element>()
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "intent-filter") out.add(node)
        }
        return out
    }

    private fun categories(filter: Element): Set<String> {
        val out = HashSet<String>()
        val children = filter.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "category") {
                out.add(node.getAttributeNS(android, "name"))
            }
        }
        return out
    }

    @Test
    fun theHomeIntentHasItsOwnFilter() {
        val home = filters(launcherActivity()).firstOrNull {
            categories(it).contains("android.intent.category.HOME")
        }
        assertNotNull("el launcher no declara CATEGORY_HOME: la tele no puede ofrecerlo", home)
        assertEquals(
            "el filtro del Inicio lleva categorías de más (varios firmware dejan de ofrecerlo)",
            setOf("android.intent.category.HOME", "android.intent.category.DEFAULT"),
            categories(home!!),
        )
    }

    @Test
    fun theHomeFilterDoesNotPretendToOutrankWithPriority() {
        val home = filters(launcherActivity()).first {
            categories(it).contains("android.intent.category.HOME")
        }
        // Android recorta a 0 la prioridad de una app no privilegiada: declararla daría una
        // falsa sensación de arreglo. La elección va por rol/Ajustes, no por prioridad.
        assertEquals(
            "no se declara prioridad: Android la recorta a 0 en apps no privilegiadas",
            "",
            home.getAttributeNS(android, "priority"),
        )
    }

    @Test
    fun theAppStillAppearsInTheTvAppList() {
        val leanback = filters(launcherActivity()).firstOrNull {
            categories(it).contains("android.intent.category.LEANBACK_LAUNCHER")
        }
        assertNotNull("sin LEANBACK_LAUNCHER el launcher desaparece de la lista de apps", leanback)
        assertTrue(
            "HOME y LEANBACK_LAUNCHER no deben compartir filtro",
            !categories(leanback!!).contains("android.intent.category.HOME"),
        )
    }

    @Test
    fun theLauncherCanSeeTheOtherHomeApps() {
        val file = File("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(file)
        val queries = doc.getElementsByTagName("queries")
        assertEquals("falta el bloque <queries>", 1, queries.length)
        val intents = (queries.item(0) as Element).getElementsByTagName("intent")
        var seesHome = false
        for (i in 0 until intents.length) {
            val categories = (intents.item(i) as Element).getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val name = (categories.item(j) as Element).getAttributeNS(android, "name")
                if (name == "android.intent.category.HOME") seesHome = true
            }
        }
        // Android 11+ oculta los paquetes no declarados: sin esta consulta el launcher
        // reportaba como Inicio a otra app (el asistente de configuración) en vez del real.
        assertTrue("sin consultar HOME, el estado del Inicio miente en Android 11+", seesHome)
    }
}
