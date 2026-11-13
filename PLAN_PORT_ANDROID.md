# Plan: portar MKXP-Z a Android

## Objetivo

Compilar MKXP-Z como app Android nativa (APK/AAB) que ejecute La Base de Sky sin pasar por reproductores de terceros (JoiPlay, Kirin, NaviaXP), con el mínimo de cambios al juego.

**Alcance inicial:** Android 8.0+ (API 26), `arm64-v8a`, renderer GLES, juego empaquetado junto al motor, controles táctiles básicos y mando físico. Fuera de la primera etapa: `armeabi-v7a`/x86, Steam, MiniFFI/Win32API, video Theora, MIDI y launcher genérico que cargue juegos arbitrarios.

La meta es conservar las APIs que Essentials necesita para arrancar, jugar y guardar; no reescribir el juego.

## Hechos revisados (en este repo)

- Plataformas soportadas: Windows, macOS y Linux (`src/system/system.h`). Android define `__linux__`, así que hoy caería en `MKXPZ_PLATFORM_LINUX` sin distinguirse.
- Ya existe backend GLES 2: opción `gfx_backend=gles` en `meson_options.txt` → `-DGLES2_HEADER`; `src/main.cpp` pide contexto ES 2.0 y `src/display/gl/shader.cpp` antepone `#define GLSLES`.
- Entrada táctil parcial: `src/eventthread.cpp` ya procesa `SDL_FINGERDOWN/UP/MOTION` (hasta `MAX_FINGERS` = 4) y eventos de ratón táctil.
- Config/guardados usan `SDL_GetPrefPath` (`src/config.cpp`), que en Android resuelve al almacenamiento interno de la app.
- Build con Meson; Linux tiene toolchains de cross-compile en `linux/` (arm64, armv7…) que sirven de referencia, no de solución.
- Dependencias: SDL2, SDL_image, SDL_ttf, SDL_sound, OpenAL, PhysFS, FreeType, Vorbis/Ogg, Theora, FluidSynth (opcional), OpenSSL (HTTPS), MRI Ruby 3.1 (`mri_version`).
- Hay ports comunitarios experimentales de MKXP-Z a Android (p.ej. `thehatkid/mkxp-z-android`): prueban viabilidad, no compatibilidad con La Base de Sky.

**Pendiente de confirmar:** versión exacta de Ruby que usa el ejecutable de referencia de la base; si algún plugin necesita MiniFFI/Win32API o extensiones nativas; si el port comunitario es reutilizable (licencia, estado, versión de MKXP-Z).

## Fase 0 — Decidir estrategia y preparar entorno

1. Evaluar el port comunitario: ¿fork/rebase sobre este repo o port propio? Criterio: cuánto diverge de nuestro MKXP-Z (perspectiva 2.5D, vibración de mandos, etc.).
2. Fijar NDK (r26+), API mínima, ABI `arm64-v8a`, versión de SDL2.
3. Estructura: proyecto Gradle en `android/` (wrapper Java/Kotlin basado en `SDLActivity`) + build nativo con Meson cross-file para NDK.
4. Mantener el port en esta rama; no cambiar defaults de Windows/Linux/macOS.
5. Revisar licencias de redistribución (MKXP-Z GPLv2, Ruby, SDL, OpenAL Soft LGPL, FluidSynth LGPL).

**Salida:** entorno reproducible que compile un APK "hello SDL" con contexto GLES.

## Fase 1 — Dependencias nativas para Android

| Dependencia | Estrategia |
|---|---|
| SDL2 + SDL_image/ttf/sound | Builds Android oficiales de SDL (Android.mk/CMake) |
| OpenGL | GLES 2/3 vía `gfx_backend=gles` existente |
| OpenAL | OpenAL Soft (backend OpenSL ES / AAudio) |
| PhysFS, FreeType, Ogg/Vorbis, libpng | Cross-compile con NDK |
| MRI Ruby 3.1 | Cross-compile para `aarch64-linux-android`, `--disable-shared`, extensiones estáticas mínimas (zlib, etc.) |
| OpenSSL | Opcional; `enable-https=false` en la primera etapa salvo que la base lo exija |
| Theora, FluidSynth | Desactivar inicialmente |
| MiniFFI | `use_miniffi=false` |
| Steamworks | Excluido |

Script de build de dependencias análogo a `linux/Makefile`, con cross-file Meson y toolchain del NDK.

**Salida:** todas las libs estáticas compilan para `arm64-v8a`.

## Fase 2 — Plataforma Android en MKXP-Z

- `src/system/system.h`: añadir `MKXPZ_PLATFORM_ANDROID` y comprobar `__ANDROID__` **antes** de `__linux__`. Revisar cada uso de `MKXPZ_PLATFORM_LINUX` para decidir si aplica a Android.
- `binding/binding-mri.cpp`: exponer `System.is_android?` y que `System.platform` devuelva un valor propio. (Aquí sí es una detección válida: el binario se compila para Android.)
- `src/system/systemImpl.cpp`: idioma (`SDL_GetPreferredLocales` o JNI), nombre de usuario, escalado.
- `meson.build`: rama `host_system == 'android'`, salida como `libmain.so` (lo que carga `SDLActivity`), sin binario ejecutable.
- `main()` → `SDL_main` ya lo gestiona SDL; revisar `chdir`/`workdir_current` y rutas de arranque.
- Logging a `logcat` (`__android_log_print`) además de `mkxp.log`.
- Ciclo de vida: pausa/reanudación (`SDL_APP_WILLENTERBACKGROUND`, pérdida de contexto GL), botón atrás, salida limpia.

**Criterio:** el APK arranca, inicializa Ruby y ejecuta un script mínimo que dibuja un bitmap.

## Fase 3 — Archivos y recursos del juego

1. Decidir distribución: assets dentro del APK (lectura vía `SDL_RWFromFile`/AssetManager, montaje en PhysFS) o copia al primer arranque a almacenamiento interno. Recomendación inicial: copiar al primer arranque (más simple con PhysFS y `Dir`/`File` de Ruby); medir tamaño.
2. Guardados y `mkxp.json` editable en `SDL_GetPrefPath` / almacenamiento externo de la app.
3. Revisar en scripts/plugins rutas absolutas, separadores `\`, mayúsculas/minúsculas (Android es case-sensitive como Linux) y `System.data_directory`.
4. Si el tamaño del juego supera límites de Play Store, evaluar Play Asset Delivery.

**Criterio:** el juego encuentra `Data/`, `Graphics/`, `Audio/` y guarda/carga partida tras cerrar la app.

## Fase 4 — Renderizado

1. Validar renderer con `gfx_backend=gles` en dispositivos reales (Adreno, Mali, PowerVR).
2. Revisar shaders con `GLSLES` (precisión, extensiones como `OES_texture_npot`), incluida la perspectiva 2.5D de esta rama de motor.
3. Escalado de 512×384 a pantallas panorámicas: letterbox con integer/smooth scaling ya existente.
4. Recrear recursos GL tras perder el contexto al volver de background.

**Criterio:** mapas, menús y batallas sin defectos visuales bloqueantes; 60 FPS en gama media.

## Fase 5 — Entrada

- Overlay táctil virtual (D-pad + A/B/C + menú) dibujado por el motor o por la capa Java; configurable en tamaño/opacidad.
- Reutilizar el manejo de `SDL_FINGER*` existente para tocar/arrastrar.
- Mando Bluetooth vía `SDL_GameController` (ya soportado, incluida vibración).
- Teclado virtual para `pbEnterText` / introducir nombre.

**Criterio:** se puede jugar completo solo con táctil y solo con mando.

## Fase 6 — Audio y APIs del sistema

- OpenAL Soft con salida AAudio/OpenSL; probar formatos de `Audio/` (OGG, WAV, MIDI si se usa).
- Pausar audio al ir a background.
- `HTTPLite`: decidir si se habilita OpenSSL según uso real de la base.
- APIs consumidas por la base (`System.uptime`, `System.data_directory`, etc.) operativas.

**Criterio:** una partida se juega, guarda, cierra y carga con audio correcto.

## Fase 7 — Essentials limpio

Validar por orden: arranque a título → nueva partida → movimiento/encuentros/diálogos → menús/equipo/inventario → combate → guardar/cerrar/cargar → audio en cambios de escena → suspender/reanudar la app.

Clasificar fallos como plataforma, render, Ruby, API, archivos, entrada o rendimiento.

## Fase 8 — La Base de Sky

1. Auditar plugins por Win32API/MiniFFI, `require` de extensiones nativas, rutas Windows, uso de teclado/ratón exclusivo.
2. Añadir grupos de plugins progresivamente y probar.
3. Adaptaciones del juego con `System.is_android?` en plugin `[ZBOX]` (p.ej. ocultar opciones de teclado, ajustar UI para dedos), sin tocar `Data/` salvo petición explícita.

## Fase 9 — Empaquetado y distribución

- Firma, `versionCode` desde la versión del juego (`mkxp.json`), icono y nombre.
- Build CI (GitHub Actions) que genere APK/AAB.
- Documentar cómo empaquetar otro juego Essentials con el mismo motor.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Ruby 3.1 cross-compile con extensiones | Empezar con extensiones estáticas mínimas; validar Marshal/save temprano |
| Diferencias de drivers GLES | Probar en varios GPUs desde Fase 4 |
| Pérdida de contexto GL en background | Recreación de texturas/FBOs o mantener contexto (`SDL_HINT_ANDROID_BLOCK_ON_PAUSE`) |
| Plugins dependientes de Windows | Auditoría en Fase 8; parches `[ZBOX]` |
| Tamaño del APK | Compresión de assets, Play Asset Delivery |
| Divergencia con upstream MKXP-Z | Código Android aislado en archivos/ramas `#ifdef` propios |
