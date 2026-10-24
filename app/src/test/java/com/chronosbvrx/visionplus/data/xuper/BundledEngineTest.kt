package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El motor dentro del APK (lo que hace Crystal): los 3 DEX viajan como `classes2/3/4.dex`, así
 * Android los deja verificados al instalar y la app no los copia ni los verifica en runtime
 * (era el origen del `VerifyError` de ART en la ZTE y de un arranque en frío más largo).
 *
 * La red de seguridad es doble: si el APK no trae las clases, la app sigue con los assets; y si
 * las libs nativas no precargan, también se cae a los assets.
 */
class BundledEngineTest {

    private fun read(path: String): String {
        val file = File(path)
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theAppUsesTheBundledClassesWhenTheApkCarriesThem() {
        val titan = read("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KTitan.kt")
        // Kill-switch encendido por defecto: el camino nuevo ya está validado en Chromecast.
        assertTrue(
            "falta la bandera engine_bundled",
            titan.contains("flag(app, \"engine_bundled\", true)"),
        )
        assertTrue(
            "no se detecta el motor dentro del APK",
            titan.contains("Class.forName(\"yc.h\", false, X4KTitan::class.java.classLoader)"),
        )
        assertTrue(
            "no se adopta el classloader de la app",
            titan.contains("adoptLoader(X4KTitan::class.java.classLoader)"),
        )
        // El respaldo a assets debe seguir existiendo.
        assertTrue("se perdió el respaldo de assets", titan.contains("engineFileNeedsExtraction"))
        assertTrue("se perdió el cargador aislado", titan.contains("EngineClassLoader("))
        // Precargar las libs fue justo lo que rompió el Chromecast: `libranger-jni.so` no tiene
        // SONAME y Android acababa cargando la copia del motor legado.
        assertTrue("no debe precargarse nada a mano", !titan.contains("preloadEngineLibs"))
    }

    @Test
    fun theNativeLibsInTheApkAreTheGoodEngine() {
        val abis = listOf("arm64-v8a", "armeabi-v7a")
        val libs = listOf(
            "libranger-jni.so",
            "libijkffmpeg.so",
            "libijkplayer.so",
            "libijksdl.so",
            "libtnet-3.1.14.so",
            "libc++_shared.so",
        )
        abis.forEach { abi ->
            libs.forEach { name ->
                val packed = File("src/main/jniLibs/$abi/$name")
                val original = File("src/main/assets/x4k/lib/$abi/$name")
                assertTrue("falta $name en jniLibs/$abi", packed.exists())
                assertTrue("falta $name en assets/x4k/$abi", original.exists())
                // Mismo build byte a byte: si vuelve el del motor legado, esto falla.
                org.junit.Assert.assertEquals(
                    "jniLibs/$abi/$name no es el build del motor X4K",
                    original.length(),
                    packed.length(),
                )
            }
        }
    }

    @Test
    fun theLegacyEngineCannotLoadTheGoodLibraries() {
        val titanSdk = read("src/main/java/com/chronosbvrx/visionplus/data/xuper/TitanSdk.kt")
        // El motor legado se deja inerte: si cargara, usaría las libs buenas con su DEX viejo.
        assertTrue("el motor legado sigue cargando", titanSdk.contains("MOTOR LEGADO RETIRADO"))
        assertTrue(
            "load() debe devolver false de inmediato",
            titanSdk.contains("fun load(context: Context): Boolean {") &&
                titanSdk.substringAfter("fun load(context: Context): Boolean {").trimStart().startsWith("// MOTOR LEGADO RETIRADO"),
        )
    }

    @Test
    fun thePipelineInjectsTheEngineAndSignsWithTheHistoricKey() {
        val script = read("../tools/publish_ota.ps1")
        assertTrue("no se inyecta el motor", script.contains("inject_engine_dex.py"))
        assertTrue("no se re-firma", script.contains("app-normal-release-engine-signed.apk"))
        assertTrue("no se alinea después de inyectar", script.contains("-p -f 4"))
        assertTrue("no se verifica el contenido inyectado", script.contains("classes{i}.dex"))
        // Si algo falla, el APK que se publica no sale (la firma se comprueba después).
        assertTrue(script.contains("La re-firma con la llave historica fallo"))
    }

    @Test
    fun theInjectionScriptRefusesBrokenDex() {
        val inject = read("../tools/inject_engine_dex.py")
        assertTrue("no valida el magic", inject.contains("blob[:4] != b\"dex\\n\""))
        assertTrue("no valida el tamaño declarado", inject.contains("declared != len(blob)"))
        // Deben entrar sin compresión: ART los mapea directo.
        assertTrue("no se guardan sin comprimir", inject.contains("compress_type = zipfile.ZIP_STORED"))
        assertTrue("no se apuntan a classes2/3/4", inject.contains("classes2.dex"))
    }
}
