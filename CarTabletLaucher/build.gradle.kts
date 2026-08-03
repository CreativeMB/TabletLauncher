plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.creativem.toblauncher"

    // REEMPLAZAMOS EL BLOQUE RARO POR ESTO:
    compileSdk = 37

    defaultConfig {
        applicationId = "com.creativem.toblauncher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // =========================================================================
    // 🎨 JETPACK COMPOSE (Sincronizado dinámicamente con BOM)
    // =========================================================================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Íconos extendidos completos (Hereda la versión automáticamente del BOM)
    implementation("androidx.compose.material:material-icons-extended")

    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // =========================================================================
    // 🎧 MEDIA3 EXOPLAYER (Video, Radio Online y Control del Volante - 1.3.0)
    // =========================================================================
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-extractor:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")
    implementation("androidx.media3:media3-session:1.3.0")
    implementation("androidx.media3:media3-common:1.3.0")

    // =========================================================================
    // 🗺️ MAPSFORGE OFFLINE (Todas unificadas a versión 0.25.0)
    // =========================================================================
    implementation("org.mapsforge:mapsforge-core:0.25.0")
    implementation("org.mapsforge:mapsforge-map:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-themes:0.18.0") // 👈 CORREGIDO (Estaba en 0.18.0)

    // Servicios de Ubicación GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // =========================================================================
    // 🧪 TESTING & DEBUGGING
    // =========================================================================
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}