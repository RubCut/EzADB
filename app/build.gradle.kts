plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.ezadb"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ezadb"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module"
        )
    }
}

// ---------------------------------------------------------------------------
// On-device helper: a tiny reflection-only Java agent, dexed and packaged as an
// asset. It is pushed to the device and run with app_process to fetch real app
// labels and icons in a single round trip. Compiles with a plain JDK, no
// android.jar needed, because every Android API is reached through reflection.
// ---------------------------------------------------------------------------
val helperSources = rootProject.file("tools/helper")
val helperClassesDir = layout.buildDirectory.dir("deviceHelper/classes")
val helperDexDir = layout.buildDirectory.dir("deviceHelper/dex")
val helperAssetsDir = layout.buildDirectory.dir("generated/helperAssets")
val buildToolsVersion = "36.0.0"

val buildDeviceHelper by tasks.registering {
    inputs.dir(helperSources)
    outputs.dir(helperAssetsDir)
    doLast {
        val classesDir = helperClassesDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val dexDir = helperDexDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val assetsDir = helperAssetsDir.get().asFile.apply { mkdirs() }
        val sources = helperSources.walkTopDown().filter { it.extension == "java" }.map { it.absolutePath }.toList()
        if (sources.isEmpty()) throw GradleException("No helper sources in $helperSources")

        fun run(command: List<String>) {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) throw GradleException("Helper build failed ($code): ${command.joinToString(" ")}\n$log")
            if (log.isNotBlank()) logger.lifecycle(log.trim())
        }

        val javac = File(File(System.getProperty("java.home"), "bin"), "javac").absolutePath
        run(listOf(javac, "--release", "8", "-nowarn", "-d", classesDir.absolutePath) + sources)

        val sdkDir = android.sdkDirectory
        val d8 = File(File(sdkDir, "build-tools/$buildToolsVersion"), "d8").absolutePath
        val classFiles = classesDir.walkTopDown().filter { it.extension == "class" }.map { it.absolutePath }.toList()
        run(listOf(d8, "--release", "--min-api", "21", "--lib", File(File(sdkDir, "platforms/android-36"), "android.jar").absolutePath, "--output", dexDir.absolutePath) + classFiles)

        val jar = File(assetsDir, "ezadb-helper.jar")
        java.util.zip.ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            File(dexDir, "classes.dex").inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        logger.lifecycle("Device helper packaged: ${jar.length()} bytes")
    }
}

android.sourceSets.getByName("main").assets.srcDir(buildDeviceHelper.map { helperAssetsDir.get() })

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildDeviceHelper)
}


dependencies {
    implementation(project(":core"))
    implementation(project(":kadb"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okio:okio:3.16.4")

    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
