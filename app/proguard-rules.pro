# Reglas R8 del release de Vision+.
#
# El motor X4K (DEX en assets) se carga con DexClassLoader/InMemoryDexClassLoader: R8 no
# lo procesa, y la reflexión por nombre (routingProxy -> javaClass.methods, makePlayerListener
# -> method.name) apunta a clases del SDK. Lo que sí hay que blindar es el puente propio,
# el jar reubicado okhttp3legacy (huella TLS del gateway) y los modelos kotlinx.serialization.

# Stacks legibles en los reportes de campo (sin esto R8 borra archivo/línea).
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses,Signature,EnclosingMethod

# --- Puente del motor nativo ---
# Se conserva entero: sus nombres aparecen en logcat/stack traces y algún día el SDK
# podría llamarnos por nombre (los callbacks hoy van por Proxy a la vista del SDK).
-keep class com.chronosbvrx.visionplus.data.xuper.X4KTitan { *; }
-keep class com.chronosbvrx.visionplus.data.xuper.TitanSdk { *; }
-keep class com.chronosbvrx.visionplus.data.xuper.MagisTitan { *; }

# --- okhttp 3.12.12 reubicado a okhttp3legacy (huella TLS del gateway) ---
# No renombrar el paquete: el fingerprint de TLS depende de la pila exacta y el jar
# usa detección de plataformas opcionales por nombre.
-keep class okhttp3legacy.** { *; }
-dontwarn okhttp3legacy.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- kotlinx.serialization (modelos @Serializable del gateway/portal) ---
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
