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
        versionCode = 338
        versionName = "2.5.102"
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // android.jar's org.json is a stub that throws on the JVM; the real one parses in unit tests.
    testImplementation("org.json:json:20240303")
}
