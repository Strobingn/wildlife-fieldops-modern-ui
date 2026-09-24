// Top-level build file for Wildlife FieldOps native Android app
plugins {
    // AGP 8.6.1 + Kotlin 2.1.20 are the WorkManager 2.12.0 compile train
    // (AAR minAgpVersion + Kotlin 2.1 metadata). Maps/CameraX/ML Kit pins stay.
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
}
