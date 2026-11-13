// Android app shell for MKXP-Z.
//
// The native engine (libmain.so) and the SDL2 runtime (libSDL2.so) are NOT
// built by Gradle: they are cross-compiled with Meson + the Android NDK and
// dropped into app/src/main/jniLibs/arm64-v8a/ before assembling.
// See ../README.md and ../android/README.md.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mkxpz-android"

include(":app")
