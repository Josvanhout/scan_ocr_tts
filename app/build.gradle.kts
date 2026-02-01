plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.scan_ocr_tts"
    compileSdk = 36  // ↓ Réduit de 36 à 34 (Android 14 → Android 13)

    defaultConfig {
        ndk {
            // ⚡ GARDE SEULEMENT ARM64 (95% des appareils)
            abiFilters += listOf("arm64-v8a")  // ↑ Supprime armeabi-v7a
        }

        applicationId = "com.example.scan_ocr_tts"
        minSdk = 30
        targetSdk = 34  // ↓ Réduit aussi
        versionCode = 5
        versionName = "1.5"

        // 🎯 OPTIMISATION RESSOURCES
        resConfigs("fr", "en", "es")  // Langues uniquement
        resConfigs("xxhdpi")          // Densité unique (la plus commune)

        // 🚫 Désactive le multidex (tu n'as pas +64K méthodes)
        multiDexEnabled = false

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // ⚡ ACTIVE LA MINIFICATION !
            isMinifyEnabled = true
            isShrinkResources = true  // ⭐ Nouveau !
            isCrunchPngs = true       // ⭐ Compresse les PNG

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Active aussi en debug pour tester
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    // ⚡ COMPRESSION PACKAGING
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/LICENSE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "**/*.proto",
                "**/*.dex",
                "**/kotlin/**",
                "**/*.version",
                "DebugProbesKt.bin"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 🎯 OPENCV OPTIMISÉ
    // OPTION A : OpenCV Android officiel (plus léger que le module)
    // implementation("org.opencv:opencv-android:4.8.0")

    // OPTION B : Garde ton module mais avec exclusion
    implementation(project(":opencv")) {
        exclude(group = "org.bytedeco", module = "*")  // Exclut les bindings lourds
    }

    // 🎯 ML KIT OPTIMISÉ (UNE SEULE FOIS !)
    // REMPLACE par la version Play Services (plus légère)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    // implementation("com.google.mlkit:text-recognition:16.0.0")  // ⛔ SUPPRIME

    implementation ("com.google.mlkit:language-id:17.0.4")
    implementation ("androidx.compose.material:material-icons-extended")

    // 🎯 CAMERAX
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // 🎯 COMPOSE (version spécifique au lieu du BOM pour contrôle)
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")

    // ⛔ SUPPRIME LES DOUBLONS
    // implementation(libs.androidx.remote.creation.compose)  // Inutile pour ton app
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")

    // CORES
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // TESTS
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}