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

El botón de engranaje de la esquina superior izquierda del pad abre el
editor. Mientras el editor está abierto el pad no manda teclas al juego y
las teclas que estaban pulsadas se sueltan.

El panel oscuro respeta las barras de estado/navegación y el recorte de la
cámara, incluso si están ocultos. En vertical prefiere la franja negra sobre
el juego; en horizontal aparece centrado arriba, entre los controles laterales.
Busca otra posición si hay controles debajo, dando prioridad al seleccionado.
**Mover panel** recorre seis posiciones (centro y esquinas, arriba y abajo), y
**Plegar / Desplegar** permite liberar espacio sin salir del editor.
El ancho se limita a la pantalla y a 400 dp; si falta altura, el contenido
central se desplaza verticalmente, con Cancelar y Guardar visibles al desplegarlo.
Los botones tienen objetivos táctiles de al menos 48 dp. Los textos del editor
se muestran en español o inglés según el idioma de Android.

| Botón | Qué hace |
|---|---|
| Añadir botón | Añade un botón nuevo (con la tecla Intro y la etiqueta `?`) y lo selecciona. |
| Restaurar | Carga el layout del juego para la orientación actual o el predeterminado. Es provisional hasta pulsar Guardar; Cancelar también descarta la restauración. |
| Mostrar FPS / Show FPS | Envía F2 al motor para alternar el contador. La casilla refleja los títulos que devuelve el motor; no depende de Guardar/Cancelar. |
| Cancelar | Descarta los cambios y cierra el editor. |
| Guardar | Guarda el layout en el archivo de usuario de la orientación actual y cierra el editor. |
| Asignar tecla | Elige letras, números, F1-F12, Intro, Esc, espacio, Mayús, Ctrl, Alt, Tab o flechas. «Pulsar una tecla física…» abre la captura del teclado o mando; Atrás cancela. |
| Nombre | Cambia la etiqueta del botón. |
| Tamaño | Deslizador en el propio panel; muestra el diámetro como porcentaje del lado más corto de la pantalla. |
| Eliminar | Borra el botón seleccionado. |

Sin selección aparece «Toca un botón para editarlo · arrástralo para moverlo».
Al seleccionar un botón aparecen su nombre, tecla y acciones; un aro ámbar lo
resalta sobre el pad. Para la cruceta se muestra solo el tamaño; su movimiento
sigue disponible arrastrándola. El pad conserva la ocultación automática al
usar teclado/mando y reaparece al tocar la pantalla.

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

1. Abrir el editor con Android en español; comprobar recorte de cámara, barras,
   Guardar/Cancelar y desplazamiento del panel. Probar Mover panel y Plegar.
2. Seleccionar y arrastrar botones y cruceta; cambiar tamaño, nombre y tecla
   (lista y teclado/mando), añadir y eliminar un botón.
3. Cancelar tras editar o Restaurar y reabrir: debe conservarse el diseño
   guardado. Guardar y reabrir: deben conservarse los cambios.
4. Girar con cambios pendientes: deben descartarse y cargarse los controles
   de la nueva orientación. Comprobar que teclado/mando aún oculta el pad y
   que tocar la pantalla lo recupera.
5. Alternar Mostrar FPS desde el engranaje y con F2 físico: comprobar etiqueta,
   casilla y apagado. Girar y mostrar/ocultar controles secundarios; comprobar
   que el contador respeta barras, cámara, engranaje y botones. Repetir en inglés.

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
