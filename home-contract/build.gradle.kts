plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chronosbvrx.visionplus.home.contract"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // org.json es parte de Android en runtime; el jar real solo hace falta en los tests JVM.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
