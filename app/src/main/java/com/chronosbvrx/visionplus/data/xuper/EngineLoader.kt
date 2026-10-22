package com.chronosbvrx.visionplus.data.xuper

import dalvik.system.DexClassLoader

/**
 * Cargador del motor X4K con su espacio de nombres aislado.
 *
 * El motor viaja ofuscado en `assets/x4k` y se carga con `DexClassLoader`, que resuelve
 * PARENT-FIRST: cualquier clase de nuestro APK con el mismo nombre lo ensombrece. Esa fue la
 * causa del apagón de la 2.5.117 (`kotlin.collections.IndexedValue` cayó en `k4.x` y el
 * `onPrepared` murió con `NoSuchMethodError` en el proceso `:x4k`).
 *
 * Dos capas lo impiden:
 * 1. `-repackageclasses 'visionplus'` en `proguard-rules.pro` + la tarea `verifyEngineNamespace`
 *    (nada que R8 renombre puede llamarse como una clase del motor).
 * 2. Esta clase: para los nombres del espacio ofuscado del motor (paquetes raíz de 1-2 letras:
 *    `k4`, `yc`, `ed`, `bd`, `a1`…) se pregunta PRIMERO a su DEX. Si el nombre no está ahí se
 *    delega al padre, así que el comportamiento fuera de ese espacio es exactamente el de antes
 *    (librerías reales como `androidx.*` u `okhttp3.*` las sigue sirviendo la app).
 *
 * `-repackageclasses` impide que el problema exista; esto lo vuelve inofensivo aunque una
 * librería futura vuelva a ocupar un nombre corto.
 */
internal class EngineClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    private val lock = Any()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!isEngineNamespace(name)) return super.loadClass(name, resolve)
        synchronized(lock) {
            findLoadedClass(name)?.let {
                if (resolve) resolveClass(it)
                return it
            }
            val own = runCatching { findClass(name) }.getOrNull()
            if (own != null) {
                if (resolve) resolveClass(own)
                return own
            }
        }
        return super.loadClass(name, resolve)
    }
}

/**
 * ¿El nombre pertenece al espacio ofuscado del motor? Su ofuscación usa raíces de una o dos
 * letras/dígitos (`a`…`z`, `a0`…`z9`, `aa`…`zf`); medido sobre los 3 DEX: 16,395 clases.
 * Un nombre sin paquete (`Foo`) o con raíz real (`androidx`, `com`, `tv`) queda fuera.
 */
internal fun isEngineNamespace(className: String): Boolean {
    val dot = className.indexOf('.')
    if (dot <= 0) return false
    val root = className.substring(0, dot)
    if (root.length > 2) return false
    if (!root[0].isLowerCase()) return false
    return root.all { it in 'a'..'z' || it in '0'..'9' }
}

/**
 * Nombres del motor que el reproductor toca seguro. Si alguno NO lo sirve el cargador del motor
 * (lo resolvió nuestro APK), hay ensombrecimiento: es el bug de la 2.5.117 visto en vivo, así que
 * se reporta como anomalía en vez de esperar al crash.
 */
internal val ENGINE_PROBE_NAMES = listOf("k4.x", "ed.e", "yc.h", "bd.a", "uc.c")

/** Los nombres de [names] que resolvió el APK y no el DEX del motor (vacío = todo sano). */
internal fun leakedEngineNames(
    engine: ClassLoader,
    names: List<String> = ENGINE_PROBE_NAMES,
): List<String> = names.filter { name ->
    val resolved = runCatching { Class.forName(name, false, engine) }.getOrNull() ?: return@filter false
    // Las clases del bootstrap (java.*) no tienen cargador: no son una fuga.
    val owner = resolved.classLoader ?: return@filter false
    owner !== engine
}
