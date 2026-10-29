// Versions are chosen to be mutually compatible:
// Gradle 8.7 + AGP 8.5.2 + Kotlin 1.9.24 + KSP 1.9.24-1.0.20 + Compose compiler 1.5.14
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
