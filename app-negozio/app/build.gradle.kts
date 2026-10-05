plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.francesco.fotonegozio"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.francesco.fotonegozio"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.15 (pezzo 2)"

        // Solo processori a 64 bit (tutti i OnePlus recenti): APK molto più leggero
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        compose = true
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
    // Base Android
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Interfaccia grafica (Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // ML Kit: lettura testo e codici a barre, gratis e senza internet (modello incluso nell'app)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Test sul PC (analizzatore del cartellino)
    testImplementation("junit:junit:4.13.2")
}
