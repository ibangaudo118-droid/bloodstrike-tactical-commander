plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.bloodstrike.tacticalcommander"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bloodstrike.tacticalcommander"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.0"))

    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    implementation("io.ktor:ktor-client-android:3.2.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
