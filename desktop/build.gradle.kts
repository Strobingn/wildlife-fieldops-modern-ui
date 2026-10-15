import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Version lock: 2026-10-14. The Compose Desktop and Kotlin versions below were
// verified working pairs when this scaffold was written; re-verify before a
// release build. Supabase/Ktor versions intentionally match the Android app
// (app/build.gradle.kts) so both clients speak the same wire protocol.
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.6.1")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.6.1")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.strobingn.wildlifefieldops.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "WildlifeFieldOps"
            packageVersion = "0.1.0"
        }
    }
}
