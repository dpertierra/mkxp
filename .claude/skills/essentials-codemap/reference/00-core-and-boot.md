# Reference · Cimientos y arranque

Carpetas: `001_Settings`, `003_Technical`, `004_Debugging`, `005_Files`, `020_Audio`, `022_Scenes`, `999_Main`. Formato por archivo: **Core** (qué sistema controla) · **Encontrarás** (qué lógica/config vive ahí) · **Consulta si** (qué peticiones te traen aquí).

## 001_Settings
Configuración global del juego. Es lo primero que se carga; casi todo lo demás lee de aquí.

### 002_Settings.rb
- **Core:** ajustes generales del juego (mecánicas por generación, límites, IDs de switches/variables especiales, opciones de jugabilidad).
- **Encontrarás:** constantes globales de configuración: nivel máximo, tamaño de equipo, generación de mecánicas, IDs de variables/switches reservados, flags de comportamiento.
- **Consulta si:** el usuario quiere cambiar una regla global del juego, un límite, o necesitas el nombre de una constante de configuración estándar.

### 003_BattleSettings.rb
- **Core:** configuración específica de combate.
- **Encontrarás:** constantes que afectan la batalla (probabilidades, generación de mecánicas de captura/críticos, toggles de comportamiento de batalla).
- **Consulta si:** ajustar una regla numérica de combate global.

### 004_Settings_Extra_Base.rb
- **Core:** **constantes custom de La Base de Sky** (no vanilla).
- **Encontrarás:** paletas/colores de UI propios, horarios día-noche custom, definición de bolsillos de la Mochila, colores de Pokédex y otros ajustes añadidos por el proyecto.
- **Consulta si:** una constante de configuración no aparece en `002`/`003`; suele estar aquí. Imprescindible para tocar UI o bolsillos propios.

## 003_Technical
Capa de compatibilidad con el runtime y parches de bajo nivel.

### 002_MKXP_Compatibility.rb
- **Core:** compatibilidad entre RGSS y el runtime MKXP-Z.
- **Encontrarás:** shims y redefiniciones para que código pensado para RPG Maker XP funcione en MKXP-Z.
- **Consulta si:** algo del motor se comporta distinto bajo MKXP; ver también skill `mkxp-z-runtime`.

### 003_RubyUtilities.rb
- **Core:** parches y extensiones a clases base de Ruby.
- **Encontrarás:** métodos añadidos a tipos nativos (String, Array, Integer…) usados por todo el motor.
- **Consulta si:** un método "raro" sobre un tipo nativo no existe en Ruby estándar; probablemente se define aquí. Ver también skill `ruby-essentials`.

### 004_Intl_Messages.rb
- **Core:** sistema de localización `_INTL`.
- **Encontrarás:** la maquinaria de traducción de texto visible e interpolación `{1}`/`{2}`.
- **Consulta si:** dudas sobre cómo se resuelve el texto mostrado al jugador o cómo cargar traducciones.

### 005_Input.rb
- **Core:** mapeo de entrada (teclado/gamepad) a botones del juego.
- **Encontrarás:** definición de los botones lógicos y su lectura.
- **Consulta si:** input de overworld/menús; para input crudo/ratón ver skill `mkxp-z-runtime`.

### 006_PluginManager.rb
- **Core:** registro y validación de plugins.
- **Encontrarás:** la API para registrar plugins, declarar dependencias, versión y orden de carga.
- **Consulta si:** creas un plugin o necesitas entender el orden de carga / `meta.txt` (ver skill `plugin-architecture`).

### 007_RPG_Sprite.rb
- **Core:** sprite base animado del motor RGSS.
- **Encontrarás:** la clase de sprite con soporte de animación/parpadeo usada como base.
- **Consulta si:** trabajas con sprites de bajo nivel del motor.

## 004_Debugging
Herramientas de diagnóstico y manejo de errores.

### 002_PBDebug.rb
- **Core:** logging a `errorlog.txt`.
- **Encontrarás:** la utilidad para registrar mensajes/errores en el log.
- **Consulta si:** quieres dejar trazas de depuración o entender cómo se escribe el errorlog.

### 003_DebugConsole.rb
- **Core:** consola de depuración.
- **Encontrarás:** salida de consola para desarrollo.
- **Consulta si:** depuración por consola.

### 004_Errors.rb
- **Core:** captura de errores y diálogos de crash.
- **Encontrarás:** el flujo que muestra el error al jugador y vuelca el stack.
- **Consulta si:** personalizar el comportamiento ante un crash o entender un mensaje de error.

### 005_Validation.rb
- **Core:** validación de argumentos.
- **Encontrarás:** helpers que verifican tipos/valores y lanzan errores claros.
- **Consulta si:** ves `validate ... => Tipo` y quieres saber qué hace.

### 006_Deprecation.rb
- **Core:** avisos de deprecación.
- **Encontrarás:** el mecanismo que avisa de métodos obsoletos (a retirar en v22).
- **Consulta si:** un método avisa "deprecated" y buscas su reemplazo.

## 005_Files
Acceso a archivos y red.

### 002_FileTests.rb
- **Core:** resolución/existencia de recursos (gráficos, audio).
- **Encontrarás:** funciones que localizan un archivo probando rutas/extensiones (`pbResolveBitmap`-like).
- **Consulta si:** un asset "no se encuentra" o quieres saber cómo el juego resuelve rutas de recursos (ver skill `assets-pipeline`).

### 003_FileMixins.rb
- **Core:** lectura/escritura serializada (`save_data`/`load_data`).
- **Encontrarás:** las primitivas de carga/guardado de archivos `.dat`/`.rxdata`.
- **Consulta si:** lees o escribes un `.dat`/datos serializados.

### 004_HTTP_Utilities.rb
- **Core:** wrappers HTTP.
- **Encontrarás:** helpers para peticiones de red (sobre HTTPLite).
- **Consulta si:** algo necesita conexión a internet; ver también `mkxp-z-runtime`.

## 020_Audio
Reproducción de audio.

### 002_Audio.rb
- **Core:** resolución de rutas de audio y SEs de UI.
- **Encontrarás:** lógica de localización de pistas y efectos de interfaz.
- **Consulta si:** un audio no suena o buscas el SE de un elemento de UI.

### 003_Audio_Play.rb
- **Core:** API de reproducción (BGM/BGS/ME/SE, fades, gritos de Pokémon).
- **Encontrarás:** las funciones para reproducir/parar/fundir música y sonidos y para tocar gritos.
- **Consulta si:** reproducir música/sonido/grito desde código.

## 022_Scenes
Transiciones y cutscenes.

### 002_Transitions.rb
- **Core:** transiciones visuales de pantalla (20+ animaciones tipo HGSS).
- **Encontrarás:** las animaciones de fundido a negro usadas al entrar en combate o cambiar de escena.
- **Consulta si:** añadir/cambiar una transición o una animación VS.

### 003_EventScene.rb
- **Core:** framework de cutscenes scripteadas.
- **Encontrarás:** la maquinaria para coreografiar escenas (mover sprites, esperar, sincronizar).
- **Consulta si:** montar una cinemática controlada por script.

## 999_Main
### 999_Main.rb
- **Core:** punto de entrada del juego (bucle eterno).
- **Encontrarás:** el arranque que llama a la carga/título y reinicia el bucle capturando errores duros.
- **Consulta si:** entender el boot del juego o el manejo de errores de máximo nivel.
