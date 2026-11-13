plugins {
    id("com.android.application")
}

// Application identity.
// Defaults live in gradle.properties and can be overridden per build:
//   ./gradlew assembleDebug -Pmkxpz.applicationId=com.example.mygame
//   MKXPZ_APPLICATION_ID=com.example.mygame ./gradlew assembleDebug
val appId: String = providers.gradleProperty("mkxpz.applicationId")
    .orElse(providers.environmentVariable("MKXPZ_APPLICATION_ID"))
    .get()
val appVersionCode: Int = providers.gradleProperty("mkxpz.versionCode")
    .map(String::toInt)
    .getOrElse(1)
val appVersionName: String = providers.gradleProperty("mkxpz.versionName")
    .getOrElse("1.0.0")

android {
    namespace = "io.github.mkxpz.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // arm64 only (see android/meson-cross-arm64.ini)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Native code is prebuilt by Meson; Gradle only packages it.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Copies the SDL2 Java glue (package org.libsdl.app) from the pinned SDL2
// release tarball. The sources are not vendored; see fetch-sdl-java.sh.
val fetchSdlJava = tasks.register<Exec>("fetchSdlJava") {
    description = "Fetches the SDL2 Java glue (org.libsdl.app) from the pinned SDL2 release."
    group = "setup"
    workingDir = rootDir
    commandLine("./fetch-sdl-java.sh")
    // The destination is gitignored and may be empty: always run.
    outputs.upToDateWhen { false }
}

// Fails early (and clearly) when the SDL2 Java glue has not been fetched.
val checkSdlJava = tasks.register("checkSdlJava") {
    description = "Checks that the SDL2 Java glue is present."
    group = "verification"
    doLast {
        val sdlActivity = file("src/main/java/org/libsdl/app/SDLActivity.java")
        if (!sdlActivity.exists()) {
            throw GradleException(
                "SDL2 Java glue missing (${sdlActivity.path}).\n" +
                    "Run ./fetch-sdl-java.sh (needs network access to github.com)."
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(checkSdlJava)
}
