plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.strobingn.wildlifefieldops"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.strobingn.wildlifefieldops"
        minSdk = 29
        targetSdk = 35
        versionCode = 16
        versionName = "2.2.1-qwen35-litert"

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
            System.getenv(name)?.trim()?.trim('"')?.trim('\'').orEmpty()
        fun escapeBuildConfig(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "")

        val llmKey = envTrim("XAI_API_KEY").ifBlank { envTrim("LLM_API_KEY") }
        val llmBase = envTrim("LLM_BASE_URL")
            .ifBlank { envTrim("XAI_BASE_URL") }
            .ifBlank { "https://api.x.ai/v1" }
        val llmModel = envTrim("LLM_MODEL")
            .ifBlank { envTrim("XAI_MODEL") }
            .ifBlank { "grok-4.5" }

        logger.lifecycle(
            "LLM config: keyChars=${llmKey.length} base=$llmBase model=$llmModel " +
                "(XAI_API_KEY ${if (envTrim("XAI_API_KEY").isNotEmpty()) "set" else "empty"}, " +
                "LLM_API_KEY ${if (envTrim("LLM_API_KEY").isNotEmpty()) "set" else "empty"})"
        )

        buildConfigField("String", "LLM_API_KEY", "\"${escapeBuildConfig(llmKey)}\"")
        buildConfigField("String", "LLM_BASE_URL", "\"${escapeBuildConfig(llmBase)}\"")
        buildConfigField("String", "LLM_MODEL", "\"${escapeBuildConfig(llmModel)}\"")
        buildConfigField("int", "LLM_KEY_LENGTH", "${llmKey.length}")
        buildConfigField("String", "BUNDLED_LOCAL_LLM_ID", "\"qwen35-0.8b\"")

        manifestPlaceholders["GOOGLE_MAPS_API"] = mapsKey

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
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
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
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

    androidResources {
        noCompress += "litertlm"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    val roomVersion = "2.8.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.ar:core:1.45.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:object-detection:17.0.2")

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    val supabaseVersion = "2.6.1"
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:storage-kt:$supabaseVersion")
    implementation("io.ktor:ktor-client-android:2.3.12")

    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
