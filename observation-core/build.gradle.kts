plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.strobingn.wildlifefieldops"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
