import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
}

// Application identity.
// Defaults live in gradle.properties and can be overridden per build:
//   ./gradlew assembleDebug -Pmkxpz.applicationId=com.example.mygame
//   MKXPZ_APPLICATION_ID=com.example.mygame ./gradlew assembleDebug
val baseApk = providers.gradleProperty("mkxpz.baseApk").getOrElse("false").toBoolean()
val appId: String = if (baseApk) "io.github.mkxpz.base" else providers.gradleProperty("mkxpz.applicationId")
    .orElse(providers.environmentVariable("MKXPZ_APPLICATION_ID"))
    .get()
val appVersionCode: Int = providers.gradleProperty("mkxpz.versionCode")
    .map(String::toInt)
    .getOrElse(1)
val appVersionName: String = if (baseApk) "__MKXPZ_VERSION_NAME__" else providers.gradleProperty("mkxpz.versionName")
    .getOrElse("1.0.0")
val appName = providers.gradleProperty("mkxpz.appName").getOrElse("MKXP-Z")
val gameAssets = if (baseApk) null else providers.gradleProperty("mkxpz.gameAssets").orNull?.let { file(it) }
val gameIcon = if (baseApk) rootProject.file("base-icon.png") else providers.gradleProperty("mkxpz.icon").orNull?.let { file(it) }
val apkOnlyDirs = (if (baseApk) "Graphics,Audio" else providers.gradleProperty("mkxpz.apkOnlyDirs").getOrElse("Graphics,Audio"))
    .split(',').map(String::trim).filter(String::isNotEmpty).distinct()
require(apkOnlyDirs.all { name ->
    name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.isISOControl() }
}) { "mkxpz.apkOnlyDirs must be a comma-separated list of top-level game directories" }
val filteredAssets = layout.buildDirectory.dir("generated/gameBaseAssets")
val iconResources = layout.buildDirectory.dir("generated/gameIconRes")

val filterGameAssets = if (baseApk || gameAssets != null) {
    if (gameAssets != null) require(gameAssets.resolve("game").isDirectory && gameAssets.resolve("game.version").isFile) {
        "mkxpz.gameAssets must contain game/ and game.version: $gameAssets"
    }
    tasks.register<Sync>("filterGameAssets") {
        from("src/main/assets") {
            exclude("game", "game/**", "game.version")
        }
        into(filteredAssets)
    }
} else null

val generateGameIcon = if (gameIcon != null) {
    require(gameIcon.isFile && gameIcon.extension.equals("png", ignoreCase = true)) {
        "mkxpz.icon must be a PNG file: $gameIcon"
    }
    tasks.register("generateGameIcon") {
        inputs.file(gameIcon)
        outputs.dir(iconResources)
        doLast {
            val source = ImageIO.read(gameIcon)
                ?: throw GradleException("Cannot decode PNG icon: $gameIcon")
            val scaled = BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.drawImage(source, 0, 0, 192, 192, null)
            } finally {
                graphics.dispose()
            }
            // ponytail: one density plus legacy fallback; add density variants if needed.
            for (directory in listOf("mipmap-xxxhdpi", "mipmap")) {
                val output = iconResources.get().file("$directory/mkxpz_game_icon.png").asFile
                output.parentFile.mkdirs()
                check(ImageIO.write(scaled, "png", output)) { "PNG encoder unavailable" }
            }
        }
    }
} else null

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
        val escapedDirs = apkOnlyDirs.joinToString(",").replace("\"", "\\\"")
        buildConfigField("String", "APK_ONLY_DIRS", "\"$escapedDirs\"")
        val escapedName = appName.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        resValue("string", "app_name", "\"$escapedName\"")
        manifestPlaceholders["appLabel"] = if (baseApk) "__MKXPZ_APP_LABEL__" else "@string/app_name"
        manifestPlaceholders["launcherIcon"] = if (gameIcon == null) {
            "@mipmap/ic_launcher"
        } else "@mipmap/mkxpz_game_icon"

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

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main") {
        if (baseApk) assets.setSrcDirs(listOf(filteredAssets))
        else if (gameAssets != null) assets.setSrcDirs(listOf(filteredAssets, gameAssets))
        if (gameIcon != null) res.srcDir(iconResources)
    }

    androidResources {
        // Empty extension stores every asset, including extensionless game files.
        noCompress += ""
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
    filterGameAssets?.let { dependsOn(it) }
    generateGameIcon?.let { dependsOn(it) }
}
