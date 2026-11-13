# Android: build nativo y APK de MKXP-Z

Proyecto Gradle del APK Android. **Gradle no compila nada nativo**: el motor
(`libmain.so`) y el runtime de SDL2 (`libSDL2.so`) se compilan aparte con
Meson + NDK. `build-native.sh` los copia junto a `libc++_shared.so` a
`app/src/main/jniLibs/arm64-v8a/` antes de empaquetar.
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
│       ├── jniLibs/arm64-v8a/            (gitignored) libmain.so, libSDL2.so, libc++_shared.so
│       └── res/                          icono, tema, pantalla de copia
├── build-native.sh                       compila, valida y copia las tres bibliotecas
├── fetch-sdl-java.sh                     baja el Java de SDL2 2.28.1 y lo copia
├── meson-cross-arm64.ini                 cross-file de Meson para el NDK
├── gradle/                               wrapper
└── settings.gradle.kts, build.gradle.kts, gradle.properties
```

## Requisitos

- Host Linux x86_64, JDK 17, Python 3 con venv/pip, `pkg-config`, `xxd` y `unzip`
- Android SDK: plataforma `android-36`, build-tools `36.0.0`
  (`android/local.properties` con `sdk.dir=...`, o variable `ANDROID_HOME`)
- NDK **30.0.16248370**, API 26, ABI `arm64-v8a`
- Meson + Ninja (build verificado con Meson 1.12.1 y Ninja 1.13.2)
- SDL2 **2.28.1**, fork `mkxp-z-2.28.1` de las deps. El Java oficial de
  esa versión coincide con el Java del fork; no usar Java 2.30.12 con él.

## Build

Todos los comandos parten de la raíz del repositorio, salvo el bloque Gradle.

### 1. Herramientas locales, sin sudo

```bash
python3 -m venv "$HOME/.cache/mkxpz/android-build-tools"
source "$HOME/.cache/mkxpz/android-build-tools/bin/activate"
pip install meson==1.12.1 ninja==1.13.2
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/30.0.16248370}"
```

### 2. Dependencias nativas

Los scripts de `android/deps/` dejan el runtime de SDL2 listo
(`libSDL2.so`, shared) y el resto de librerías estáticas enlazadas dentro del
motor.

```bash
make -C android/deps
```

Si ya existen las deps iniciales, **no recompilarlas**. Añadir solo las cinco
bibliotecas que faltaban en ese primer conjunto:

```bash
export ANDROID_DEP_PREFIX=/ruta/absoluta/al/build-arm64/existente
make -C android/deps libopusfile pixman uchardet iconv \
    BUILD_PREFIX="$ANDROID_DEP_PREFIX" \
    DOWNLOADS=/ruta/absoluta/a/downloads/arm64/existente
```

`libopusfile` añade también `libopus`. Mantener las rutas absolutas originales
del prefix: sus `.pc` y los builds de las deps contienen esas rutas. Ver
`deps/README.md` para requisitos y licencias.

### 3. Motor, comprobaciones ELF y copia automática

```bash
./android/build-native.sh
```

Variables opcionales:

- `ANDROID_DEP_PREFIX`: por defecto `android/deps/build-arm64`.
- `ANDROID_NDK_HOME`: por defecto `~/Android/Sdk/ndk/30.0.16248370`.
- `MKXPZ_ANDROID_BUILD_DIR`: por defecto `android/build-native`.
- `JOBS`: paralelismo de Ninja; por defecto `nproc`.

El script configura Meson con `meson-cross-arm64.ini`, aísla
`PKG_CONFIG_LIBDIR` y pasa `-Dmri_version=3.1 -Denable-https=false
-Duse_miniffi=false -Dshared_fluid=false -Dgfx_backend=gles`. No descarga ni
recompila las deps. El cross-file resuelve los compiladores desde el NDK en
`PATH`; no contiene rutas personales.

Ninja genera `android/build-native/libmain.so` (**no** `src/libmain.so`).
Meson instala una copia sin RPATH del host en `build-native/stage/lib/`;
el script comprueba AArch64, `SDL_main`, los puntos JNI de SDL2 y los
`NEEDED` de las tres bibliotecas. El enlazado usa `--no-undefined` y alineación
de segmentos de 16 KB. Después copia y elimina símbolos de depuración de
las tres `.so` en `app/src/main/jniLibs/arm64-v8a/`.

SDL2 sigue siendo **shared**; `MkxpActivity.getLibraries()` carga `SDL2`
antes de `main`. El loader resuelve su dependencia de `libc++_shared.so`
desde el mismo APK.

### 4. Java de SDL2 y APK sin datos de juego

```bash
./android/fetch-sdl-java.sh
cd android
./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'lib/arm64-v8a/.*\.so'
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`. La lista
debe incluir `libmain.so`, `libSDL2.so` y `libc++_shared.so`. No hace falta
incluir datos de juego para compilar/empaquetar; este APK vacío no demuestra
el arranque del juego.

`fetch-sdl-java.sh` descarga SDL2 **2.28.1** con SHA-256 y copia sus nueve
archivos Java (gitignored). También se puede ejecutar la tarea
`./gradlew fetchSdlJava`. Gradle puede avisar de que no puede quitar símbolos
nativos si no encuentra su NDK por defecto: el script nativo ya los quita.

Comprobaciones adicionales, desde la raíz del repo:

```bash
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" \
    -d android/app/src/main/jniLibs/arm64-v8a/libmain.so
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify \
    android/app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 \
    android/app/build/outputs/apk/debug/app-debug.apk
```

`libmain.so` enlazado necesita solo `liblog.so`, `libdl.so`, `libm.so`,
`libSDL2.so`, `libc++_shared.so` y `libc.so`. Los otros `NEEDED` transitivos
de SDL2 son bibliotecas del sistema Android.

### 5. Datos del juego (opcional para este build)

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

## Directorio del juego (mecanismo elegido)

1. `SplashActivity` copia `assets/game` a `getFilesDir()/game` en un hilo
   secundario, con barra de progreso (no bloquea el hilo principal → no hay
   ANR). La copia va primero a `getFilesDir()/.game-install`; el juego anterior
   se aparta y solo se borra cuando el nuevo está completo, así una copia
   interrumpida no deja el juego a medias ni borra el que ya funcionaba.
2. La copia se hace en el primer arranque y se repite cuando cambia
   `assets/game.version`.
3. `MkxpActivity` publica la ruta final con
   `Os.setenv("MKXPZ_GAME_DIR", "<filesDir>/game", true)` **antes** de que SDL
   arranque el hilo nativo.

El motor lee `MKXPZ_GAME_DIR` al inicio de `main()` (`src/main.cpp`, bloque
`__ANDROID__`) y hace `chdir()` ahí antes de cargar `Data/`. En Android se compila
con `WORKDIR_CURRENT`, así que esa carpeta es la raíz del juego.

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
- Android omite Theora, FluidSynth, OpenSSL, MiniFFI y las dependencias de
  escritorio (X11/GTK, bzip2, libcharset, SDL2main). `Graphics.playMovie`
  informa de que la reproducción de vídeo no está disponible.
- Ruby se enlaza estático con `libruby-ext.a`; empaquetar el runtime Ruby y
  los datos que necesite un juego real sigue siendo trabajo de integración.
- Verificado aquí: compilación/enlace, exportaciones, dependencias ELF,
  empaquetado, firma debug y alineación ZIP de 16 KB. Sin dispositivo:
  arranque, render, audio, JNI en ejecución, pausa/reanudación, botón atrás
  y guardado siguen sin comprobarse.
