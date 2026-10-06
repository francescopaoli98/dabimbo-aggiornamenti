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
        versionCode = 51
        versionName = "2.6"

        // Solo processori a 64 bit (tutti i OnePlus recenti): APK molto più leggero
        ndk {
            // Versione per provare sul PC (emulatore di Android Studio): ./gradlew assembleRelease -Ppc
            abiFilters += if (project.hasProperty("pc")) "x86_64" else "arm64-v8a"
        }
    }

    // Firma: la stessa chiave usata finora, così l'app si aggiorna sopra quella già installata
    signingConfigs {
        create("negozio") {
            storeFile = rootProject.file("firma/chiave-negozio.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Versione da installare: senza strumenti di prova (più fluida).
        // NIENTE compattazione R8: dalla 2.2 alla 2.5 rovinava la lettura dei cartellini (ML Kit),
        // e senza lettura l'app non sa più come girare le foto.
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("negozio")
        }
        debug {
            signingConfig = signingConfigs.getByName("negozio")
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    // Test generale dell'app sul PC, senza telefono (Robolectric simula Android)
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
