plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.flyfishxu.kadb"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82")
    api("com.squareup.okio:okio:3.16.4")

    implementation("com.github.Flyfish233:spake2-java:1.1.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
