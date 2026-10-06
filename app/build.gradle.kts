plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.strobingn.wildlifefieldops"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.strobingn.wildlifefieldops"
        minSdk = 29
        targetSdk = 35
        // Local/dev installs keep this hand-set code. GitHub Actions overrides with
        // 1_000_000 + GITHUB_RUN_NUMBER so branch APKs never VERSION_DOWNGRADE (48/49/50+).
        versionCode = 55
        versionName = "2.8.0-weather-alerts"
        buildConfigField("String", "UPDATE_RELEASE_TAG", "\"debug-latest\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"main\"")
        buildConfigField("String", "CI_SIGNER_SHA256", "\"EC:75:D0:BC:BC:62:30:6B:0C:38:91:76:9E:05:4C:EB:C7:7C:6A:84:4D:11:9B:40:18:B9:0C:7E:F7:57:0C:A6\"")
        System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.takeIf { it > 0 }?.let { runNumber ->
            versionCode = 1_000_000 + runNumber
        }

        val supabaseUrl = System.getenv("SUPABASE_URL") ?: "https://your-project.supabase.co"
        val supabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: "your-anon-key"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")

        val mapsKey = sequenceOf(
            "GOOGLE_MAPS_API",
            "GOOGLE_MAPS_API_KEY",
            "VITE_GOOGLE_MAPS_API_KEY",
            "VITE_GOOGLE_MAPS_API"
        ).mapNotNull { name ->
            System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
        }.firstOrNull().orEmpty()
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$mapsKey\"")
        buildConfigField("String", "GOOGLE_MAPS_API", "\"$mapsKey\"")
        val weatherKey = System.getenv("OPENWEATHER_API_KEY") ?: ""
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$weatherKey\"")

        fun envTrim(name: String): String =
            System.getenv(name)?.trim()?.trim('"')?.trim('\'') .orEmpty()
        fun escapeBuildConfig(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "")

        // Cloud Grok uses the Supabase ai-assistant function. Do not put that provider key in BuildConfig.
        val hfToken = envTrim("HF_TOKEN").ifBlank { envTrim("HUGGING_FACE_HUB_TOKEN") }
        buildConfigField("String", "HF_TOKEN", "\"${escapeBuildConfig(hfToken)}\"")

        // WorkManager 2.12 sync canary — default OFF; debug/canary build types turn it on.
        buildConfigField("boolean", "WM_SYNC_CANARY_ENABLED", "false")

        manifestPlaceholders["GOOGLE_MAPS_API"] = mapsKey

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        // Persistent CI debug key (PKCS12). Path/password come from env; never logged.
        create("ci") {
            val keystorePath = System.getenv("DEBUG_KEYSTORE_PATH")?.trim().orEmpty()
            if (keystorePath.isNotEmpty() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: ""
                keyPassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: ""
                keyAlias = "androiddebugkey"
                storeType = "PKCS12"
            }
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("boolean", "WM_SYNC_CANARY_ENABLED", "false")
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            buildConfigField("boolean", "WM_SYNC_CANARY_ENABLED", "true")
            val ciKeystore = System.getenv("DEBUG_KEYSTORE_PATH")?.trim().orEmpty()
            if (ciKeystore.isNotEmpty() && file(ciKeystore).exists()) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":observation-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager 2.12 canary (CoroutineWorker lives in work-runtime; ktx is empty at 2.12).
    val workVersion = "2.12.0"
    implementation("androidx.work:work-runtime:$workVersion")
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("androidx.work:work-analytics:$workVersion")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.ar:core:1.45.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:object-detection:17.0.2")

    // On-device wildlife evidence classifier (TFLite Interpreter)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Optional GPU delegate — safe no-op when unsupported
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // CameraX live analyzer (capture-clock traces + KEEP_ONLY_LATEST)
    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation("androidx.core:core-splashscreen:1.0.1")

    val supabaseVersion = "2.6.1"
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:storage-kt:$supabaseVersion")
    implementation("io.ktor:ktor-client-android:2.3.12")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.zxing:core:3.5.3")

    // On-device generative LLM (llama.cpp + abliterated Qwen2.5-3B/7B GGUF)
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.activity:activity-compose:1.8.2")
    testImplementation("androidx.core:core-ktx:1.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
