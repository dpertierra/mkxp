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

### Un APK por juego (sin recompilar bibliotecas nativas)

Con las bibliotecas ya presentes en `app/src/main/jniLibs/` y el Java de SDL2
descargado, basta con Bash, `rsync`, las utilidades GNU (`realpath`, `find`,
`sort`, `xargs`, `sha256sum`), JDK 17 y el SDK:

```bash
./android/package-game.sh "/ruta/al/juego" io.github.autor.mijuego "Mi juego"
./android/package-game.sh "/ruta/al/juego" io.github.autor.mijuego "Mi juego" "/ruta/icono.png" -- -Pmkxpz.versionCode=2 -Pmkxpz.versionName=1.1
```

El resultado es `android/build-games/io.github.autor.mijuego.apk` (debug,
firmado con la clave debug local). Un `applicationId` distinto permite instalar
varios juegos a la vez, cada uno con sus datos privados. Para actualizar uno,
mantén su identificador y clave de firma; aumenta `versionCode` cuando proceda.
No se compila código nativo ni se instala nada en el teléfono.

El script sincroniza una copia en `android/build-games/<applicationId>/game/`.
Excluye archivos y carpetas ocultos, ejecutables y accesos directos de escritorio,
ZIP, `lib64/`, `Game_Linux`, `graphify-out/`, `poke_updater/`, `MapExporter/`,
`AGENTS.md`, `CLAUDE.md`, `skills-lock.json` y `*.makerstudio`.
Materializa los enlaces simbólicos y borra de la copia los archivos retirados
del origen. No modifica la carpeta original. `game.version` es un SHA-256 de
los nombres y contenidos copiados; un cambio hace que el instalador vuelva a
extraer los datos al siguiente arranque. Los argumentos tras el icono opcional
(o tras `--`) se pasan a Gradle.

También se puede usar Gradle directamente desde `android/`:

```bash
./gradlew assembleDebug -Pmkxpz.applicationId=io.github.autor.mijuego \
  '-Pmkxpz.appName=Mi juego' -Pmkxpz.gameAssets=/ruta/al/paquete \
  -Pmkxpz.icon=/ruta/icono.png
```

- `mkxpz.gameAssets`: directorio con `game/` y `game.version`; sustituye los
  datos del juego de `app/src/main/assets/`, conservando sus demás assets.
  Las rutas relativas se resuelven desde `android/app/`.
- `mkxpz.appName`: nombre de la app en todos los idiomas; por defecto `MKXP-Z`.
- `mkxpz.apkOnlyDirs`: directorios de primer nivel que se leen desde el APK,
  separados por comas; por defecto `Graphics,Audio`. Más detalles abajo.
- `mkxpz.icon`: PNG opcional, escalado a 192×192 en `app/build/generated/`,
  con recurso xxxhdpi y fallback legacy; sin propiedad se mantiene el icono
  adaptativo de MKXP-Z. Conviene usar una imagen cuadrada.

Todos los assets se guardan sin compresión (`Stored`) en el APK, incluidos los
archivos sin extensión y formatos propios del juego. Esto permite lectura y
seek de PhysFS sin descompresión, también para audio en streaming. Gradle dispone
de hasta 4 GB de heap: el equipo necesita memoria y espacio libre para la copia,
los intermedios y el APK. Un build pequeño no valida los límites de un juego
grande ni los límites de distribución de las tiendas.

Verificación manual: instalar el APK, comprobar nombre/icono, abrir el juego;
cambiar un dato, volver a empaquetar y actualizar para comprobar la extracción.
Con otro identificador, comprobar que ambas apps conservan datos separados.

### Compilar el motor y preparar el APK base

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

1. `SplashActivity` copia los archivos pequeños de `assets/game` a
   `getFilesDir()/game` en un hilo
   secundario, con barra de progreso (no bloquea el hilo principal → no hay
   ANR). La copia va primero a `getFilesDir()/.game-install`; el juego anterior
   se aparta y solo se borra cuando el nuevo está completo, así una copia
   interrumpida no deja el juego a medias ni borra el que ya funcionaba.
2. No copia los directorios de `mkxpz.apkOnlyDirs` (por defecto `Graphics` y
   `Audio`, comparación sin distinguir mayúsculas). La copia se hace en el
   primer arranque y se repite cuando cambia `assets/game.version` **o la
   lista de directorios APK-only**. Sustituir y borrar el árbol anterior
   retira también las copias grandes de versiones antiguas; un fallo de
   borrado se muestra como error de instalación para poder reintentarlo.
3. `MkxpActivity` publica la ruta final con
   `Os.setenv("MKXPZ_GAME_DIR", "<filesDir>/game", true)` **antes** de que SDL
   arranque el hilo nativo. También publica `MKXPZ_APK_PATH` con
   `getApplicationInfo().sourceDir`.

El motor lee `MKXPZ_GAME_DIR` al inicio de `main()` (`src/main.cpp`, bloque
`__ANDROID__`) y hace `chdir()` ahí antes de cargar `Data/`. En Android se compila
con `WORKDIR_CURRENT`, así que esa carpeta es la raíz del juego.

Después monta la carpeta extraída y añade el APK como ZIP de PhysFS, con
`PHYSFS_setRoot(..., "assets/game")`. La carpeta extraída tiene prioridad sobre
el APK; los parches y RGSSAD conservan su prioridad anterior, y los RTP se
añaden después. El montaje precede a `createPathCache()`, así que el caché de
rutas y las búsquedas del motor incluyen los assets empaquetados. Hace falta
PhysFS con `PHYSFS_setRoot` (las dependencias Android incluyen 3.2).

### Configurar la extracción

```bash
# Añadir otra carpeta de solo lectura:
./android/package-game.sh /ruta/al/juego io.github.autor.mijuego "Mi juego" -- \
  '-Pmkxpz.apkOnlyDirs=Graphics,Audio,Movies'

# Compatibilidad: volver a extraer todo.
./android/package-game.sh /ruta/al/juego io.github.autor.mijuego "Mi juego" -- \
  -Pmkxpz.apkOnlyDirs=
```

Solo se admiten nombres de directorio de primer nivel, sin `/` ni `\`, y
distintos de `.` y `..`. La lista se normaliza en Gradle y llega al instalador
mediante `BuildConfig.APK_ONLY_DIRS`. Mantén `mkxp.json`, `Data`, scripts,
plugins, fuentes y datos que deban escribirse entre lo extraído. El APK
completo se monta aunque la lista esté vacía.

Espacio estable aproximado: **APK + archivos extraídos + guardados**. Un
juego de 1,5 GB con 150 MB fuera de Graphics/Audio ronda 1,65 GB, más el motor
y datos de la app; no es una medición en dispositivo. Una actualización
necesita espacio temporal para el APK, el árbol anterior y la nueva extracción.
Como en el instalador anterior, reinstalar sustituye todo `filesDir/game`;
los guardados y datos generados que deban persistir deben quedar fuera de él.
Los archivos RGSSAD no se dividen: se extraen si no están dentro de una carpeta
APK-only. Los splits y Play Asset Delivery no están contemplados: se monta
solo `sourceDir` del APK único producido por este proyecto.

### Compatibilidad Ruby

Solo en Android, y solo después de montar el APK, el binding expone tres
helpers internos (`MKXPZ_APKFS.native_stat`, `native_read`, `native_entries`)
y evalúa `binding/android-apk-filesystem.rb` **antes** del script personalizado,
`preload.rb` y los scripts RGSS. No se modifica el juego ni el comportamiento
del binding de escritorio.

Cobertura del shim:

- `File.exist?`/`exists?`, `File.file?`, `File.directory?` y los equivalentes
  de `FileTest`; `Dir.exist?`/`exists?`; `File.size`.
- `File.open` de lectura (`r`, `rb`, modo entero de solo lectura, opciones de
  codificación habituales), con o sin bloque; `File.read` y `File.binread`,
  con longitud y desplazamiento. El stream es un `StringIO` con `path`,
  `read`, `getbyte`, `seek`, `pos`, `eof?`, `close` y enumeración de líneas.
- `Dir.entries`, `children`, `foreach` (bloque o enumerador), `glob` y `[]`.
  Mezcla disco y PhysFS sin duplicar nombres. Globs con `*`, `?`, `**`,
  clases `[]`, `{a,b}`, flags de `File.fnmatch`, `base:` y `sort:`.

Solo busca en PhysFS para rutas **relativas** que permanezcan dentro de la
raíz del juego, incluso tras un `Dir.chdir` a una subcarpeta extraída. Las
rutas absolutas o que escapan de esa raíz siguen usando disco. Los archivos
reales tienen prioridad; las escrituras siguen pasando a `File` nativo.
Los helpers de Essentials como `safeExists?` y `pbResolveBitmap` pueden usar
estas operaciones sin una copia de los gráficos. La resolución virtual de
nombres usa el caché del motor y, para directorios o caché desactivado,
recorre componentes con comparación de mayúsculas ASCII.

Límites conocidos:

- Escribir dentro de Graphics/Audio APK-only no está soportado. Para un
  plugin que modifica assets, quita su carpeta de `apkOnlyDirs`. Crear
  directorios con `FileUtils.mkdir_p` también puede ver el directorio virtual
  como existente sin crear uno físico.
- `File.open` virtual carga **un archivo completo en RAM**. No es un descriptor
  nativo: no ofrece `fileno`, `mmap`, `IO.select` ni compatibilidad con APIs C
  que exigen un archivo físico. El audio del motor sí mantiene streaming y
  seek con PhysFS; no pasa por este buffer Ruby.
- No se interceptan `File.new`, `File.stat`/`lstat`, `IO.read`, `FileTest.size?`,
  `Kernel#test`, `Dir.open`/`Dir.new`/`Dir.chdir` hacia carpetas solo virtuales,
  `require`/`load` de scripts no extraídos ni librerías externas con I/O propio.
  Tampoco se emulan permisos de `File.open`, `File.read(open_args:)` o todas
  las conversiones de codificación de MRI; se cubre lectura habitual de assets.
- Cada glob recorre su subárbol literal y filtra con `File.fnmatch`; no hay
  índice persistente ni orden estable con `sort: false`. No desciende enlaces
  simbólicos físicos, para no recorrer ciclos.

### Comprobaciones sin teléfono

```bash
ruby android/tests/apk-filesystem-test.rb
./android/build-native.sh
cd android && ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug
unzip -v build-games/io.github.autor.mijuego.apk | grep 'assets/game/'
```

La comprobación Ruby usa assets falsos y disco temporal, sin cargar el motor.
Revisa `Stored` también para `.rxdata`, `.rb`, extensiones propias y archivos
sin extensión, no solo PNG/audio. No valida arranque, Java en ejecución ni
audio en Android.

Verificación manual en dispositivo:

1. Actualizar una instalación antigua con el mismo identificador y firma;
   abrirla y comprobar que no queden copias de Graphics/Audio en los datos
   privados. Revisar el espacio ocupado tras terminar la instalación.
2. Cargar partida, cambiar de mapa y abrir menús con gráficos. Probar BGM/BGS
   largos, efectos, cambios de pista y desplazamientos de reproducción.
3. Guardar y recargar. Revisar plugins que enumeran carpetas y leen assets;
   si fallan, consultar `errorlog.txt`, `mkxp.log` y los logs de la app.
4. Repetir con `-Pmkxpz.apkOnlyDirs=` y después volver al valor por defecto,
   sin cambiar los archivos de juego, para comprobar la migración por política.

## Controles táctiles

`MkxpActivity` añade una capa de controles táctiles (`TouchControlsView`)
encima de la superficie de SDL. Los toques se convierten en teclas de SDL
(`SDLActivity.onNativeKeyDown`), así que el motor los recibe como entrada de
teclado normal y los mapea con `src/input/keybindings.cpp`:

| Botón | Keycode de Android | Scancode de SDL | Input:: (RGSS 1) | Input:: (RGSS 2+) |
|---|---|---|---|---|
| Cruceta arriba/abajo/izquierda/derecha | `KEYCODE_DPAD_*` | `UP`/`DOWN`/`LEFT`/`RIGHT` | `Up`/`Down`/`Left`/`Right` | igual |
| C | `KEYCODE_C` | `C` | `Input::C` (confirmar) | — |
| X | `KEYCODE_X` | `X` | `Input::B` (cancelar, menú) | igual |
| Z | `KEYCODE_Z` | `Z` | `Input::A` (acción, correr) | `Input::C` (confirmar) |
| A | `KEYCODE_A` | `A` | `Input::X` | igual |
| S | `KEYCODE_S` | `S` | `Input::Y` | igual |
| D | `KEYCODE_D` | `D` | `Input::Z` (menú rápido) | igual |
| Q | `KEYCODE_Q` | `Q` | `Input::L` | igual |
| W | `KEYCODE_W` | `W` | `Input::R` | igual |
| Alt | `KEYCODE_ALT_LEFT` | `LALT` | `Input::Alt` | igual |
| Enter | `KEYCODE_ENTER` | `RETURN` | `Input::C` | igual |

El motor detecta la versión de RGSS del juego (`src/config.cpp`): si el juego
usa `Data/Scripts.rxdata` se trata como RGSS 1 y la tecla Z es la de acción;
con `.rvdata`/`.rvdata2` (RGSS 2 y 3) Z es la de confirmar.

Detalles del comportamiento:

- La cruceta acepta diagonales (pulsa dos teclas a la vez) y se puede deslizar
  el dedo de una dirección a otra sin soltar.
- Multitáctil: la cruceta y los botones se pueden usar a la vez; las teclas se
  sueltan al levantar el dedo, al cancelar el gesto y al pausar la app.
- Las teclas que no caen en un control no se consumen: la superficie de SDL
  las sigue recibiendo (el juego las usa como ratón).
- La cruceta y los botones se dibujan en las barras laterales que deja el
  juego (el motor mantiene su proporción 4:3). En vertical se colocan en la
  franja negra bajo la pantalla del juego.
- El pad se oculta solo cuando se usa un teclado o un mando físico, y vuelve
  a aparecer al tocar la pantalla.
- El teclado de Android funciona en los campos de texto del juego: escribir,
  borrar (la tecla de borrar del teclado se reenvía al motor) y confirmar con
  Enter (el teclado se cierra y se envía la tecla Intro).

### Layout propio por juego

Un juego puede traer su propio layout en `<gameDir>/android_controls.json`
(`<gameDir>` es la carpeta que `MkxpActivity` publica como `MKXPZ_GAME_DIR`).
Si el archivo no existe o no es válido se usa el layout por defecto y se avisa
en el log (`adb logcat -s TouchControls`).

El editor guarda un layout independiente por orientación, con el mismo formato:

- Vertical: `<filesDir>/controls_user_portrait.json`.
- Horizontal: `<filesDir>/controls_user_landscape.json`.

El orden de carga para la orientación actual es:

1. El archivo de usuario de esa orientación (el del editor).
2. `<gameDir>/android_controls.json` (el del juego).
3. El layout por defecto, cuya cuadrícula se adapta a ambas orientaciones.

Una orientación nunca editada usa el layout del juego o el de por defecto.
El antiguo `<filesDir>/controls_user.json` se ignora y se borra al cargar:
no contiene información de orientación y hay que volver a editar el pad.

```json
{
  "buttons": [
    {"label": "Z", "key": "Z", "x": 0.855, "y": 0.870, "size": 0.145},
    {"label": "X", "key": "X", "x": 0.951, "y": 0.870, "size": 0.145},
    {"label": "Esc", "key": "ESCAPE", "x": 0.855, "y": 0.130, "size": 0.145}
  ],
  "dpad": {"x": 0.115, "y": 0.740, "size": 0.300},
  "toggle": {"label": "≡", "x": 0.945, "y": 0.465, "size": 0.075}
}
```

El archivo del juego puede incluir objetos `portrait` y `landscape` en la raíz.
Cada uno contiene un layout completo con el mismo esquema (`buttons`, `dpad`,
`toggle`), por ejemplo:

```json
{
  "portrait": {
    "buttons": [
      {"label": "Z", "key": "Z", "x": 0.70, "y": 0.85, "size": 0.145}
    ],
    "dpad": {"x": 0.18, "y": 0.85, "size": 0.300}
  },
  "landscape": {
    "buttons": [
      {"label": "Z", "key": "Z", "x": 0.90, "y": 0.75, "size": 0.145}
    ],
    "dpad": {"x": 0.10, "y": 0.75, "size": 0.300}
  }
}
```

Si no existe el objeto de la orientación actual, se lee el layout de la raíz.
Así, los archivos anteriores siguen aplicando el mismo layout a ambas
orientaciones. Si el layout elegido no es válido, se usa el de por defecto.

| Clave | Qué es |
|---|---|
| `buttons` | Lista de botones. Obligatoria. |
| `buttons[].label` | Texto que se dibuja en el botón. |
| `buttons[].key` | Nombre de una constante `KeyEvent.KEYCODE_*` sin el prefijo (`"Z"`, `"DPAD_UP"`, `"SHIFT_LEFT"`, `"ESCAPE"`...). Una clave desconocida se avisa en el log y el botón se descarta. |
| `buttons[].x` | Centro del botón, como fracción del ancho de la pantalla (0 a 1). |
| `buttons[].y` | Centro del botón, como fracción del alto de la pantalla (0 a 1). |
| `buttons[].size` | Diámetro del botón, como fracción del lado más corto de la pantalla. |
| `buttons[].secondary` | Opcional. `true` lo esconde hasta que se pulse el botón de la izquierda. |
| `dpad` | La cruceta. `false` la oculta; con un objeto se indica su posición y tamaño. Si no se menciona, se usa la de por defecto. |
| `toggle` | Botón que muestra y esconde los botones marcados como `secondary`. Opcional. |

El layout por defecto pone las teclas como en un teclado de PC:

```
    Q W
  A S D
  Z X C Alt
```

con la cruceta a la izquierda.

### Editor de los controles

El botón de engranaje de la esquina superior izquierda del pad abre un menú
oscuro junto al engranaje, con cuatro opciones:

| Opción | Qué hace |
|---|---|
| Editar controles | Abre el editor del pad. |
| Mando / Gamepad | Reasigna botones de mandos Bluetooth o USB a teclas del teclado. |
| Mostrar FPS / Show FPS | Envía F2 al motor para alternar el contador. La casilla refleja los títulos que devuelve el motor; no depende de Guardar/Cancelar. |
| Ocultar controles | Oculta el pad hasta el siguiente toque en la pantalla. |

Al abrir el menú se sueltan las teclas pulsadas. Mientras el editor está
abierto el pad no manda teclas al juego.

El panel oscuro respeta las barras de estado/navegación y el recorte de la
cámara, incluso si están ocultos. En vertical prefiere la franja negra sobre
el juego; en horizontal aparece centrado arriba, entre los controles laterales.
En horizontal busca otra posición si hay controles debajo, dando prioridad
al seleccionado. La cabecera mantiene el título en una línea y ofrece iconos
para **Restaurar**, **Plegar / Desplegar** y, solo en horizontal, **Mover panel**.
Mover recorre seis posiciones (centro y esquinas, arriba y abajo). Plegar
libera espacio sin salir del editor; el engranaje también vuelve a desplegarlo.
Los iconos tienen descripción accesible y una ayuda al mantenerlos pulsados.

El panel mide lo que ocupa su contenido, con un máximo de 400 dp de ancho.
Una fila contextual muestra la ayuda o la selección y sus acciones, seguida
del deslizador de tamaño. La fila inferior reúne **+ Botón**, **Cancelar** y
**Guardar**, resaltado en ámbar. El estado normal ocupa aproximadamente 220 dp
con un botón seleccionado (cabecera, acciones, tamaño y pie), por debajo de los
360 dp de la banda superior de referencia. No incluye listas de teclas dentro
de esa banda. Solo si falta altura o aumenta el tamaño de fuente, el panel se
desplaza verticalmente para poder alcanzar las acciones. Los objetivos táctiles del
editor miden al menos 40 dp, y los iconos 44 dp. Los textos se muestran en
español o inglés según el idioma de Android.

| Botón | Qué hace |
|---|---|
| + Botón | Añade un botón nuevo (con la tecla Intro y la etiqueta `?`) y lo selecciona. |
| Restaurar | Carga el layout del juego para la orientación actual o el predeterminado. Es provisional hasta pulsar Guardar; Cancelar también descarta la restauración. |
| Cancelar | Descarta los cambios y cierra el editor. |
| Guardar | Guarda el layout en el archivo de usuario de la orientación actual y cierra el editor. |
| Tecla | Abre el selector modal de letras, números, flechas y especiales (F1-F12, Intro, Esc, espacio, Mayús, Ctrl, Alt y Tab). «Pulsa una tecla…» activa la captura del teclado o mando; Atrás cancela el selector. |
| Nombre | Cambia la etiqueta del botón. |
| Tamaño | Deslizador en el propio panel; muestra el diámetro como porcentaje del lado más corto de la pantalla. |
| Eliminar | Borra el botón seleccionado. |

Sin selección aparece «Toca un botón para editarlo».
Al seleccionar un botón aparecen su etiqueta en una ficha y las acciones
Tecla, Nombre y Eliminar; un aro ámbar lo resalta sobre el pad. La descripción
accesible de la ficha incluye la tecla asignada. Para la cruceta se muestra
solo el tamaño; su movimiento sigue disponible arrastrándola. El pad conserva
la ocultación automática al usar teclado/mando y reaparece al tocar la pantalla.

El contador muestra, por ejemplo, `60 FPS` en una esquina libre, respetando
insets y recorte de cámara. Busca espacio en los bordes si hay controles y se
oculta si no encuentra sitio. Sigue visible al ocultar el pad con teclado/mando.
Lee los títulos FPS del motor mediante `onTitleChanged`; un título FPS que no
pueda interpretar se muestra completo. Al restaurarse el título normal, se
oculta y desmarca la casilla, también si F2 procede de un teclado físico.
No guarda esta preferencia entre ejecuciones.

Los botones y la cruceta se mueven arrastrándolos. Las posiciones se guardan
como fracciones de la pantalla, por separado para vertical y horizontal.
Al cambiar de orientación se recarga el layout correspondiente. Si el editor
está abierto, se cierra y descarta los cambios sin guardar.

Verificación manual (teléfono, ambas orientaciones):

1. Abrir el engranaje y después Editar controles con Android en español;
   comprobar recorte de cámara, barras y todas las filas sin recortes. Probar
   Restaurar y Plegar/Desplegar; Mover panel solo debe aparecer en horizontal.
   Con poca altura, comprobar que el scroll permite alcanzar todas las acciones.
2. Seleccionar y arrastrar botones y cruceta; cambiar tamaño, nombre y tecla
   (lista y teclado/mando), añadir y eliminar un botón.
3. Cancelar tras editar o Restaurar y reabrir: debe conservarse el diseño
   guardado. Guardar y reabrir: deben conservarse los cambios.
4. Girar con cambios pendientes: deben descartarse y cargarse los controles
   de la nueva orientación. Comprobar que teclado/mando aún oculta el pad y
   que tocar la pantalla lo recupera. Repetir con Ocultar controles desde el menú.
5. Alternar Mostrar FPS desde el engranaje y con F2 físico: comprobar etiqueta,
   casilla y apagado. Girar y mostrar/ocultar controles secundarios; comprobar
   que el contador respeta barras, cámara, engranaje y botones. Repetir en inglés.

### Reasignar botones del mando

**Engranaje > Mando** abre una hoja modal a pantalla completa sobre el juego,
con fondo oscurecido y el estilo del editor táctil. Una rejilla agrupa los 16
botones en **Botones**, **Gatillos**, **Sticks**, **Menú** y **Cruceta**. Cada
casilla muestra el nombre del botón en grande, su tecla actual debajo y
una breve indicación para Essentials en C, X, Z y Esc. Un punto de color marca
las casillas que difieren del valor por defecto. Estas son las asignaciones
iniciales, explícitas en Java y equivalentes al teclado RGSS1:

| Botón | Tecla | Uso habitual en Essentials |
|---|---|---|
| A | C | Aceptar |
| B | X | Atrás / Menú |
| X | Z | Acción/Correr |
| Y | A | |
| L1 / R1 | Q / W | |
| L2 / R2 | Sin asignar | |
| L3 / R3 | S / D | Pulsación de los sticks |
| Start | Esc | Atrás / Menú |
| Select | Sin asignar | |
| D-pad ↑ ↓ ← → | Flechas ↑ ↓ ← → | Movimiento |

En vertical se usan cuatro columnas (dos en pantallas estrechas); Sticks y Menú
comparten una fila. En horizontal las secciones se reparten en filas más anchas,
con casillas de al menos 64 dp de alto frente a 80 dp en vertical. La ayuda tiene
su propia línea y puede envolver, sin puntos suspensivos. **Restaurar todo**,
**Cancelar** y **Guardar** permanecen en el pie, fuera del área desplazable.
La rejilla está dimensionada para caber sin scroll a unos 412 × 900 dp y su
orientación horizontal; el scroll queda como alternativa para áreas pequeñas o
fuentes ampliadas. La hoja respeta barras del sistema y recorte de cámara.
Solo el editor del pad y el menú del engranaje conservan la banda sobre la imagen
4:3; Mando y el selector de teclas pueden cubrir el juego.

Pulsar un botón físico resalta el borde de su casilla sin activar el juego; si no
cabe en una pantalla pequeña, también la lleva a la vista. Tocar una casilla abre
el mismo selector modal de teclas que **Tecla** en el editor táctil. Las teclas se
agrupan en **Letras**, **Números**, **Flechas** y **Especiales**, con objetivos de
44 dp y etiquetas ajustadas al ancho. En horizontal los cuatro grupos se colocan
lado a lado, en lugar de apilarse. El pie queda fijo y solo se desplaza la rejilla
cuando falta espacio. **Sin asignar** muestra **—** y desactiva el botón;
**Por defecto** recupera su asignación inicial. Esas dos opciones solo aparecen
al editar un mando. **Pulsa una tecla…** activa/desactiva la captura física sin
ocultar la rejilla; desde Mando solo captura teclado, desde el pad también mando.
Pulsar el mando desde su selector vuelve a identificar su casilla. Atrás/Esc
cancela el selector (Esc puede asignarse durante la captura). Las capas bloquean
toques fuera de la hoja y consumen también las liberaciones de teclas que las
cerraron, sin enviarlas al juego.

**Restaurar todo** pide confirmación dentro del panel y devuelve todo el mapa
a sus valores iniciales. **Cancelar** descarta los cambios; **Guardar** escribe
el archivo y activa el mapa. Si falla la escritura, el panel conserva los
cambios y permite reintentar. Atrás cancela una captura, selector o confirmación;
desde la lista cierra sin guardar. Al pausar la app o girar la pantalla se
descartan los cambios pendientes.

El archivo es `<filesDir>/gamepad_map.json`, compartido por todos los mandos y
ambas orientaciones, separado de los layouts táctiles y de los datos del juego:

```json
{
  "BUTTON_A": "ENTER",
  "BUTTON_B": "NONE",
  "BUTTON_START": "ENTER",
  "DPAD_UP": "W"
}
```

Las claves son nombres de botones Android sin `KEYCODE_`: `BUTTON_A/B/X/Y`,
`BUTTON_L1/R1/L2/R2`, `BUTTON_START/SELECT/MODE`, `BUTTON_THUMBL/THUMBR`,
`BUTTON_1` a `BUTTON_16`, `DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, las diagonales
`DPAD_UP_LEFT/UP_RIGHT/DOWN_LEFT/DOWN_RIGHT` o `BACK` si lo emite el mando.
Los valores usan los mismos nombres de teclas que los layouts del
pad (`C`, `ENTER`, `SHIFT_LEFT`, `DPAD_UP`, etc.); `"NONE"` o `null` indican
sin asignar. Solo se guardan diferencias respecto a los valores por defecto;
sin archivo o con `{}` se aplica la tabla anterior. Los archivos del formato
anterior siguen siendo válidos. Las asignaciones antiguas de botones
adicionales se conservan, aunque no tienen casilla en el panel; Restaurar todo
también las borra al guardar. Las entradas desconocidas se ignoran con aviso
en el log. La escritura usa `AtomicFile` para conservar el archivo anterior
si falla.

`MkxpActivity.dispatchKeyEvent` intercepta los botones antes del despacho de
SDL. Comprueba el origen del evento y también
`SDLControllerManager.isDeviceSDLJoystick`: algunos mandos publican sus
botones con origen de teclado. Para cada botón de la tabla envía directamente
`SDLActivity.onNativeKeyDown/Up` con la tecla mostrada y consume el original,
incluso cuando usa su valor por defecto. Sin asignar consume sin enviar tecla.
Así evita la ruta `SDLSurface.onKey > SDLActivity.handleKeyEvent >
SDLControllerManager.onNativePadDown/Up`, que recibiría el botón del mando.
Los botones adicionales sin asignación explícita siguen esa ruta habitual;
los sticks y los gatillos que solo emiten ejes analógicos conservan su
comportamiento. No se usa la
ventana adicional del menú F1 del motor ni se modifica código nativo.

Se ignoran las repeticiones de botones interceptados mientras están pulsados.
El seguimiento distingue dispositivo y botón, y permite que varios botones
mantengan una misma tecla hasta soltar el último. Cada liberación usa la tecla
enviada al pulsar, aunque el mapa cambie antes de soltar. Se sueltan las teclas al
abrir un editor, perder el foco, pausar o desconectar el mando. Las pulsaciones
reasignadas siguen contando como entrada física para ocultar el pad; un toque
en pantalla vuelve a mostrarlo.

Verificación manual (Bluetooth y USB, español e inglés):

1. Abrir Mando sin archivo de mapa: comprobar las 16 casillas y la tabla anterior.
   A unos 412 × 900 dp y en horizontal, revisar que caben todas sin scroll, con
   ayuda completa, pie visible e insets libres. Pulsar cada botón y comprobar
   resaltado sin acciones en el juego.
   Probar también desde el selector y la confirmación de restauración.
2. Cambiar A con la cuadrícula y B con «Pulsa una tecla…»; comprobar el punto de
   cambio, Sin asignar y Por defecto. Guardar y probar repetición, liberación,
   Start/Esc y ambos sticks. L2/R2 y Select sin asignar no deben activar nada
   si emiten botones; los ejes analógicos conservan la ruta SDL.
3. Cancelar cambios y restauraciones; reabrir y comprobar el mapa guardado.
   Guardar, reiniciar y comprobar persistencia. Probar un mapa antiguo y
   valores NONE/null. Restaurar todo y guardar debe dejar `{}` y los defaults.
   Asignar dos botones a una tecla y soltarlos por separado: se libera al
   soltar el último.
4. Mantener un botón y pausar, abrir el panel o desconectar el mando; comprobar
   que no queda una tecla pulsada. Girar durante la captura: debe cerrarse sin
   guardar. Comprobar ocultación por entrada física y recuperación al tocar.
5. Con teclado, recorrer casillas y cuadrícula con Tab/Enter y cancelar con
   Escape/Atrás. Probar captura mantenida y soltar tras cerrar la hoja, sin teclas
   activas en el juego. Revisar scroll de respaldo con una ventana pequeña o
   fuente ampliada y los logs
   de la app si falla la carga o el guardado. Revisar bordes inferiores de todos
   los botones en editor, engranaje y Mando, también al final del scroll. En
   vertical, comprobar que el editor del pad queda sobre la imagen sin tapar su
   primera línea: cabecera, contexto, tamaño y pie deben caber sin scroll en una
   banda de unos 360 dp. Abrir **Tecla** desde allí: el selector cubre el juego y
   muestra todos los grupos sin scroll a tamaño de fuente normal.

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
