import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firma de release: la llave vive FUERA del repo (keystore.properties está en .gitignore;
// también acepta variables de entorno para CI). Sin llave, el release se firma con debug
// para poder probar R8 en local/CI sin exponer secretos.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "VISION_KEYSTORE_FILE")?.let { file(it) }
val releaseStorePassword = signingValue("storePassword", "VISION_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "VISION_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "VISION_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.chronosbvrx.visionplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chronosbvrx.visionplus"
        minSdk = 24
        targetSdk = 35
        versionCode = 400
        versionName = "2.5.161"
        ndk {
            // Solo ARM: el APK no empaqueta libs x86/x86_64 (los AAR traían credentials/
            // graphics.path de x86_64). En dispositivos x86_64 con traducción ARM (emulador
            // con imagen Google APIs/Play, ChromeOS) la app se instala como arm64 y su proceso
            // corre en modo invitado (libndk_translation): así el motor nativo X4K carga bien.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    flavorDimensions += "laboratorio"
    productFlavors {
        create("normal") { dimension = "laboratorio" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Llave propia si existe (keystore.properties / env); si no, debug para pruebas.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Build de laboratorio para sondas nativas: firma debug, sin debuggable
        // (para librerías que rechazan procesos con debugging activado).
        register("probe") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric runs the TV focus tests on the JVM with the app's real resources.
        unitTests.isIncludeAndroidResources = true
        // OJO: el JVM de pruebas arranca con 512 MB por defecto y la suite completa (más de 100
        // clases Robolectric + Compose, cada una con su carga) lo ahoga: los tests pesados de
        // `ui.tv.*` no alcanzaban a quedar «idle» en 60 s y la suite entera caía ahí (pasaban
        // aislados). Con heap y metaspace holgados la suite completa queda verde.
        unitTests.all {
            it.maxHeapSize = "2g"
            it.jvmArgs("-XX:MetaspaceSize=256m", "-XX:MaxMetaspaceSize=1g")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// --- Guardián del espacio de nombres del motor X4K (ver proguard-rules.pro) ---
// El DEX del motor (assets/x4k, ~16k clases) se resuelve PARENT-FIRST contra nuestro APK y su
// ofuscación ocupa todo el espacio de 1-2 letras (a..z, a0..z9, aa..zf). Si R8 le pone a una
// clase NUESTRA el mismo nombre que una del motor, la del motor queda ensombrecida y el
// reproductor muere (OTA 2.5.117: `kotlin.collections.IndexedValue -> k4.x` mató `onPrepared`
// con NoSuchMethodError; NADA reproducía). Esta tarea lee los DEX del motor y el mapping de R8
// y rompe el build si vuelve a pasar.
fun dexClassNames(dex: File): Set<String> {
    val bytes = dex.readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun u32(off: Int): Int = buf.getInt(off)
    val nStr = u32(0x38); val offStr = u32(0x3C)
    val nType = u32(0x40); val offType = u32(0x44)
    val nCls = u32(0x60); val offCls = u32(0x64)
    val strings = Array(nStr) { i ->
        var p = u32(offStr + 4 * i)
        var len = 0
        var shift = 0
        while (true) {
            val b = bytes[p].toInt() and 0xFF
            p++
            len = len or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        String(bytes, p, len, Charsets.UTF_8)
    }
    val types = Array(nType) { i -> strings[u32(offType + 4 * i)] }
    return (0 until nCls).mapNotNull { i ->
        val d = types[u32(offCls + 32 * i)]
        if (d.startsWith("L") && d.endsWith(";")) d.substring(1, d.length - 1).replace('/', '.') else null
    }.toSet()
}

val verifyEngineNamespace = tasks.register("verifyEngineNamespace") {
    group = "verification"
    description = "Falla si R8 le da a una clase nuestra el nombre de una clase del motor X4K."
    val mapping = layout.buildDirectory.file("outputs/mapping/normalRelease/mapping.txt")
    val engineDex = fileTree("src/main/assets/x4k") { include("*.dex") }
    outputs.upToDateWhen { false }
    doLast {
        val file = mapping.get().asFile
        if (!file.exists() || file.length() == 0L) {
            logger.lifecycle("verifyEngineNamespace: sin mapping (build sin R8), se omite.")
            return@doLast
        }
        val engine = HashSet<String>(20000)
        engineDex.files.filter { it.extension == "dex" }.forEach { engine += dexClassNames(it) }
        val offenders = ArrayList<String>()
        var renamed = 0
        var shared = 0
        file.forEachLine { line ->
            if (line.isEmpty() || line[0].isWhitespace() || line.startsWith("#")) return@forEachLine
            val arrow = line.indexOf(" -> ")
            if (arrow <= 0 || !line.endsWith(":")) return@forEachLine
            val orig = line.substring(0, arrow)
            val plain = line.substring(arrow + 4, line.length - 1)
            if (plain == orig) {
                if (plain in engine) shared++
                return@forEachLine
            }
            // R8 anota las clases que ELIMINÓ con `R8$$REMOVED$$CLASS$$N`: no existen en el APK.
            if (plain.startsWith("R8\$\$REMOVED\$\$")) return@forEachLine
            renamed++
            if (plain in engine) offenders += "$orig -> $plain"
        }
        logger.lifecycle(
            "verifyEngineNamespace: motor=${engine.size} clases, nuestras renombradas=$renamed, " +
                "nombres reales compartidos=$shared, colisiones generadas=${offenders.size}.",
        )
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "R8 generó ${offenders.size} nombre(s) que ENSOMBRECEN clases del motor X4K (crash " +
                    "NoSuchMethodError en :x4k: no se reproduce nada). Revisa -repackageclasses en " +
                    "proguard-rules.pro.\n" + offenders.take(20).joinToString("\n"),
            )
        }
    }
}
tasks.matching { it.name == "minifyNormalReleaseWithR8" }.configureEach {
    finalizedBy(verifyEngineNamespace)
}

// Conscrypt trae un backend nativo que Robolectric no puede cargar en la JVM (y los tests no
// hablan ADB): se saca del classpath de pruebas, no del APK.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }
    .configureEach {
        exclude(group = "org.conscrypt", module = "conscrypt-android")
    }

dependencies {
    // El contrato del feed que lee Vision+ Home (launcher): DTOs y deep links compartidos.
    implementation(project(":home-contract"))
    // Pila okhttp 3.12.12 (el mismo TLS que hydra usa) reubicada a okhttp3legacy — solo del gateway.
    implementation(files("libs/okhttp3legacy.jar"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.0.0")

    // Canal Watch Next: «Continuar viendo» en el launcher de Android TV / Google TV.
    implementation("androidx.tvprovider:tvprovider:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // The one source's engine, packed inside: the 3DES portalCore client and its HLS proxy.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ADB por Wi-Fi desde el teléfono (solo móvil): emparejamiento y shell remoto en la tele.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Certificado autofirmado de la llave ADB (bcprov ya viene con libadb, misma versión).
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // android.jar's org.json is a stub that throws on the JVM; the real one parses in unit tests.
    testImplementation("org.json:json:20240303")
}
