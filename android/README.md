# Android: app shell de MKXP-Z

Proyecto Gradle del APK Android. **Gradle no compila nada nativo**: el motor
(`libmain.so`) y el runtime de SDL2 (`libSDL2.so`) se compilan aparte con
Meson + NDK y se copian a `app/src/main/jniLibs/arm64-v8a/` antes de empaquetar.
No hay `externalNativeBuild`.

```
android/
├── app/                                  módulo único
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/mkxpz/android/
│       │   ├── MkxpActivity.java        extends org.libsdl.app.SDLActivity
│       │   ├── SplashActivity.java      copia los datos del juego y luego lanza el juego
│       │   └── GameDataInstaller.java   assets/game -> getFilesDir()/game
│       ├── java/org/libsdl/app/          (gitignored) Java de SDL2, copiado por fetch-sdl-java.sh
│       ├── assets/game/                  (gitignored) datos del juego que se empaquetan
│       ├── assets/game.version           (gitignored) marcador de versión de esos datos
│       ├── jniLibs/arm64-v8a/            (gitignored) libSDL2.so, libmain.so
│       └── res/                          icono, tema, pantalla de copia
├── fetch-sdl-java.sh                     baja el Java de SDL2 2.30.12 y lo copia
├── meson-cross-arm64.ini                 cross-file de Meson para el NDK
├── gradle/                               wrapper
└── settings.gradle.kts, build.gradle.kts, gradle.properties
```

## Requisitos

- JDK 17
- Android SDK: plataforma `android-36`, build-tools `36.0.0`
  (`android/local.properties` con `sdk.dir=...`, o variable `ANDROID_HOME`)
- NDK r27.2.12479018 o r30.0.16248370 (`~/Android/Sdk/ndk/`)
- Meson + Ninja
- SDL2 **2.30.12** (la Java la baja `fetch-sdl-java.sh`; la nativa la compila
  el script de deps con la misma versión)

## Build

### 1. Dependencias nativas

Los scripts de `android/deps/` dejan el runtime de SDL2 listo
(`libSDL2.so`, shared) y el resto de librerías estáticas enlazadas dentro del
motor.

### 2. Motor con Meson

```bash
meson setup build-android --cross-file android/meson-cross-arm64.ini \
    -Dbuildtype=release -Dgfx_backend=gles
ninja -C build-android
```

El `meson.build` de la raíz necesita la rama `host_system == 'android'` (Fase 2
del plan) para generar `libmain.so` en lugar de un ejecutable.

Para cambiar de versión de NDK, edita la ruta en `android/meson-cross-arm64.ini`:

```bash
sed -i 's|ndk/[0-9.]*/|ndk/30.0.16248370/|' android/meson-cross-arm64.ini
```

### 3. Copiar los `.so`

```bash
mkdir -p android/app/src/main/jniLibs/arm64-v8a
cp build-android/src/libmain.so          android/app/src/main/jniLibs/arm64-v8a/
cp <ndk-build>/libSDL2.so                android/app/src/main/jniLibs/arm64-v8a/
```

Si SDL2 se enlaza estático dentro de `libmain.so`, no hay `libSDL2.so` que
copiar y `MkxpActivity.getLibraries()` debe devolver solo `"main"`.

### 4. Datos del juego

```bash
mkdir -p android/app/src/main/assets/game
cp -r <juego>/Data    android/app/src/main/assets/game/
cp -r <juego>/Graphics android/app/src/main/assets/game/
cp -r <juego>/Audio   android/app/src/main/assets/game/
cp    <juego>/mkxp.json android/app/src/main/assets/game/
echo "1.2.1.1" > android/app/src/main/assets/game.version
```

`game.version` es el marcador: si su contenido cambia, la app reinstala los
datos en el primer arranque posterior a la actualización.

### 5. Java de SDL2

```bash
./android/fetch-sdl-java.sh
```

Descarga el tarball oficial de SDL2 2.30.12 (con checksum), lo verifica y
copia `android-project/app/src/main/java/org/libsdl/app/*.java` a
`app/src/main/java/org/libsdl/app/`. El destino está en `.gitignore`: el Java
de SDL2 no está vendorizado. También hay tarea de Gradle: `./gradlew
fetchSdlJava`.

### 6. APK

```bash
cd android
./gradlew assembleDebug
# android/app/build/outputs/apk/debug/app-debug.apk
```

## Directorio del juego (mecanismo elegido)

1. `SplashActivity` copia `assets/game` a `getFilesDir()/game` en un hilo
   secundario, con barra de progreso (no bloquea el hilo principal → no hay
   ANR). La copia va primero a `getFilesDir()/.game-install` y se renombra al
   terminar, así una copia interrumpida no deja el juego a medias.
2. La copia se hace en el primer arranque y se repite cuando cambia
   `assets/game.version`.
3. `MkxpActivity` publica la ruta final con
   `Os.setenv("MKXPZ_GAME_DIR", "<filesDir>/game", true)` **antes** de que SDL
   arranque el hilo nativo.

**El motor tiene que leer `MKXPZ_GAME_DIR` y hacer `chdir()` ahí antes de
cargar `Data/`.** `mkxp_fs::getDefaultGameRoot()` devuelve `SDL_GetBasePath()`,
que en Android es `<filesDir>`, no `<filesDir>/game`, así que sin este paso el
motor no encuentra el juego. Sitio natural para el enganche: el bloque
`#ifndef WORKDIR_CURRENT` de `src/main.cpp` (unos 20 líneas antes de
`mkxp_fs::setCurrentDirectory(dataDir)`), por ejemplo:

```c
#if defined(__ANDROID__)
    char *env = getenv("MKXPZ_GAME_DIR");
    if (env) {
        strncpy(dataDir, env, sizeof(dataDir) - 1);
    }
#endif
```

Alternativa sin tocar el motor: copiar el árbol del juego directamente en
`getFilesDir()` (sin subcarpeta `game`) para que `SDL_GetBasePath()` apunte al
sitio correcto. Es un cambio de una línea en `GameDataInstaller.gameDir()`.

## Configuración

| Qué | Cómo | Por defecto |
|---|---|---|
| `applicationId` | `-Pmkxpz.applicationId=com.example.mygame` o `MKXPZ_APPLICATION_ID=...` | `io.github.mkxpz.android` |
| `versionCode` / `versionName` | `-Pmkxpz.versionCode=2` / `-Pmkxpz.versionName=1.2.1` | `1` / `1.0.0` |
| Ruta del SDK | `android/local.properties` (`sdk.dir=`) o `ANDROID_HOME` | — |

ABIs: solo `arm64-v8a`. `minSdk 26`, `targetSdk 35` (`compileSdk 36`).

## Notas / pendientes

- El motor debe exportar `SDL_main`: es lo que llama `SDLActivity`
  (`getMainFunction()`), y debe coincidir con el `SDL2` nativo que se
  empaqueta (la Java avisa si la versión C/Java no coincide).
- Render por GLES 2: compilar con `-Dgfx_backend=gles`.
- `AndroidManifest.xml`: GLES 2.0 requerido; pantalla táctil, gamepad,
  Bluetooth y USB opcionales; `android:isGame="true"`;
  `configChanges` como en la plantilla de SDL; `sensorLandscape` y pantalla
  encendida (`FLAG_KEEP_SCREEN_ON`) en `MkxpActivity`.
- Fuera de esta app shell (Fase 2 del plan): `MKXPZ_PLATFORM_ANDROID`, logging
  a logcat, ciclo de vida pausa/reanudación y botón atrás.
