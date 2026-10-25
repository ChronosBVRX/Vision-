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

# --- AISLAMIENTO DEL ESPACIO DE NOMBRES DEL MOTOR (causa raiz del crash `k4.x`) ---
# El motor X4K vive en assets/x4k/x4k-sdk-*.dex (16,395 clases, FUERA de R8) y se
# carga con DexClassLoader(parent = nuestro classloader), que resuelve PARENT-FIRST:
# cualquier nombre de clase que exista en nuestro APK ENSOMBRECE a la del motor.
# Su ofuscacion usa TODO el espacio de 1-2 letras (a..z, a0..z9, aa..zf) — lo medimos
# hoy: 719 de nuestras clases caian en un nombre del motor. La colision que dolia era
# `k4.x`: en la OTA v2.5.117 R8 puso ahi `kotlin.collections.IndexedValue` y el
# `onPrepared` del motor (yc.e$h -> zc.c.d -> Observable.interval -> RxJava
# SchedulerPoolFactory) moria con
#   NoSuchMethodError: No static method a(AtomicReference,Object,Object)Z in class Lk4/x
# en el proceso :x4k => NADA reproducia (9 crashs en tele + telefono, 2026-10-22).
# En v2.5.78 el mismo bug fue con Tink (`AesGcmKey` -> `k4.x`); blindar solo esa clase
# fue jugar a los topos porque R8 reasigna nombres en CADA build.
# Fix estructural: TODO lo renombrado por R8 vive bajo `visionplus.` (el motor no tiene
# NI UNA clase en ese paquete: medido sobre los 3 DEX). Asi ninguna clase generada
# puede volver a llamarse `k4.x` ni ningun otro nombre del motor.
# Guardian: la tarea `verifyEngineNamespace` (app/build.gradle.kts) corre tras R8 y
# rompe el build si alguna clase renombrada sale de `visionplus.` — ver REGRESSIONS.md.
-repackageclasses 'visionplus'

# --- Tink (AndroidX security-crypto) SIN ofuscacion ---
# Se conserva de v2.5.78 (ahi el nombre que choco fue `AesGcmKey`). Con el repackage
# de arriba ya no hace falta para evitar colisiones, pero no estorba y ahorra sorpresas.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.joda.**

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

# --- ADB por Wi-Fi desde el teléfono (LibADB + BouncyCastle + Conscrypt + SPAKE2) ---
# El emparejamiento y el shell remoto usan reflexión/JCA: sin estos keep el release (R8)
# perdería los proveedores y el flujo «Inicio en la TV» solo funcionaría en debug.
-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-keep class com.google.security.spake2.** { *; }
-dontwarn com.google.security.spake2.**
-keep class * extends java.security.Provider { *; }
