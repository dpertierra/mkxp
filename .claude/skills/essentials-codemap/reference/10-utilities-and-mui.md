# Reference · Utilidades (libs externas + custom Zik/Marin/Luka) y MUI

Carpetas: `058_Utilities`, `069_MUI`. Aquí vive gran parte del **código propio de La Base de Sky** y de librerías de terceros integradas.

## 058_Utilities
Helpers generales, librerías y utilidades custom.

### 002_Utilities.rb
- **Core:** utilidades de propósito general.
- **Encontrarás:** helpers misceláneos usados por todo el motor (combinaciones, formato, etc.).
- **Consulta si:** buscas una función de utilidad genérica.

### 002_Libraries/
Librerías estándar de Ruby portadas (no parte de Essentials). **Consulta si:** usas estas estructuras de datos.
- **001_set.rb** — clase `Set`.
- **002_tsort.rb** — ordenación topológica (usada para resolver dependencias de carga).

### 003_Utilities_Pokemon.rb
- **Core:** utilidades centradas en Pokémon (apodar, almacenar, comprobaciones de cajas).
- **Encontrarás:** helpers de alto nivel sobre Pokémon/almacenamiento.
- **Consulta si:** operaciones comunes con Pokémon fuera de combate.

### 004_Utilities_BattleAudio.rb
- **Core:** elección de música de combate según el rival/zona.
- **Encontrarás:** la lógica que decide el BGM de un combate salvaje/entrenador.
- **Consulta si:** qué música suena en un combate.

### 005_Misc.rb
- **Core:** **(custom)** misceláneos del proyecto (p.ej. reproducir GIFs con mensaje).
- **Encontrarás:** utilidades sueltas añadidas por el proyecto.
- **Consulta si:** una utilidad custom no clasificable.

### 006_Networking.rb
- **Core:** comprobación de conexión a internet.
- **Encontrarás:** detección de red sobre HTTPLite.
- **Consulta si:** algo depende de tener conexión.

### 007_Search_Sort.rb
- **Core:** ordenación natural y normalización de acentos (con workaround para JoiPlay).
- **Encontrarás:** comparadores de orden natural y limpieza de acentos.
- **Consulta si:** ordenar listas de texto correctamente.

### 008_VersionChecker.rb
- **Core:** comparación de números de versión.
- **Encontrarás:** helpers para comparar versiones (newer/older).
- **Consulta si:** comparar versiones del juego/plugins.

### 009_PokeAPI.rb
- **Core:** integración con PokeAPI (caché en datos globales).
- **Encontrarás:** consultas a la API externa de Pokémon y su caché.
- **Consulta si:** traer datos de PokeAPI.

### 010_Turbo.rb
- **Core:** **(custom)** acelerador del juego (speed-up).
- **Encontrarás:** el sistema de turbo/aceleración de la velocidad de juego.
- **Consulta si:** la función de acelerar el juego.

### 011_BaseSearcher.rb
- **Core:** clase base para interfaces de búsqueda por texto.
- **Encontrarás:** la funcionalidad común de los buscadores (Pokédex/especies/mochila).
- **Consulta si:** crear un buscador con campo de texto.

### 012_Marin's Scripting Utilities.rb
- **Core:** **(lib custom, Marin)** utilidades de scripting (extensiones a `Dir`, archivos, etc.).
- **Encontrarás:** helpers de bajo nivel de la librería de Marin.
- **Consulta si:** ves utilidades atribuidas a Marin.

### 012_Pokedex_Searcher.rb
- **Core:** buscador de la Pokédex (sobre `BaseSearcher`).
- **Encontrarás:** búsqueda de especies dentro de la dex.
- **Consulta si:** buscar en la Pokédex.

### 013_Easy Mouse.rb
- **Core:** **(lib custom, Luka)** sistema de ratón fácil.
- **Encontrarás:** API de alto nivel para interacción con ratón.
- **Consulta si:** soporte de ratón en UI (ver también `mkxp-z-runtime`).

### 013_Species_Searcher.rb
- **Core:** buscador de especies (sobre `BaseSearcher`).
- **Encontrarás:** búsqueda por nombre de especie.
- **Consulta si:** elegir una especie por texto.

### 014_Bag_Searcher.rb
- **Core:** buscador de items en la mochila.
- **Encontrarás:** búsqueda dentro de un bolsillo.
- **Consulta si:** buscar un item en la mochila.

### 014_Storage System Utilities.rb
- **Core:** **(custom)** utilidades del sistema de almacenamiento (PC).
- **Encontrarás:** helpers para operar sobre las cajas.
- **Consulta si:** manipulaciones avanzadas del PC.

### 015_KeybindingReader.rb
- **Core:** **(custom)** lectura del nombre de la tecla asignada a cada botón.
- **Encontrarás:** mapeo botón lógico → tecla física para mostrarla en pantalla.
- **Consulta si:** mostrar "pulsa [X]" con la tecla real configurada.

### 016_Zik_Utilities.rb
- **Core:** **(custom, Zik)** utilidades propias del autor (p.ej. conversión HSL↔RGB, helpers de color).
- **Encontrarás:** funciones propias usadas por el código custom del proyecto.
- **Consulta si:** una utilidad propia del proyecto (especialmente de color).

### 003_Luka's Scripting Utilities/
**(lib de terceros, Luka S.J.)** Un framework de scripting con su propio compilador, extensiones a clases nativas, sprites con animación/tweening, UI y manejo de ratón. Lo usan varias pantallas/efectos custom. **Consulta si:** ves clases/sprites con estilo "Luka" o tweening/lerp.

- **002_Compiler.rb** — compilador propio de la librería.
- **003_ErrorMessages.rb / 004_Logger.rb / 005_Math.rb** — mensajes de error, logger y matemáticas auxiliares.
- **006_SpriteHash.rb / 007_MouseInput.rb** — colección de sprites con clave y entrada de ratón.
- **002_Concerns/** — mixins reutilizables: `Animatable`, `BlockConstructor`, `Floatable`, `QuickAnimatable` (animación/construcción por bloque/valores flotantes).
- **003_Core/** — **extensiones a clases nativas y RGSS** (un archivo por clase): `AnimatedPlane`, `Array`, `AttributeContainer`, `CallbackWrapper`, `Color`, `Console`, `Dir`, `Env`, `File`, `FloatValues`, `Graphics`, `Hash`, `NilClass`, `Numeric`, `PluginManager`, `Rect`, `Sprite`, `String`, `Symbol`, `Tone`, `Viewport`. Consulta el que toque cuando un método "extra" sobre uno de esos tipos no sea de Ruby estándar.
- **004_Sprites/** — sprites avanzados: `Base`, `Bitmap`, `Scrolling`, `Rainbow`, `Sheet` (spritesheets), `Trailing` (estela).
- **005_UI/** — componentes de UI de la lib: `Cursor`, `Window`.
- **006_PluginManager/002_Aliaser.rb** — utilidad de aliasado de métodos.
- **007_Bitmaps/002_Polygon.rb** — dibujado de polígonos en bitmaps.

## 069_MUI
**Suite de UI modular de La Base de Sky** (Modular UI). Mejora Summary/Pokédex/Party/Storage con un sistema de páginas registrables. **Consulta si:** trabajas en el Summary/Pokédex/Party/Storage mejorados o quieres añadir una página modular.

### 002_MUI Modular UI Scenes/
Framework de páginas modulares para Summary y Pokédex.
- **002_UI Module.rb** — el framework `UIHandlers` (registro de páginas/opciones de menús modulares).
- **003_UI Party Commands.rb** — colores/comandos del menú de party.
- **004_Summary Settings.rb / 005_Summary Handlers.rb / 006_Summary Utilities.rb / 007_Summary Rewrites.rb** — settings, handlers de página, utilidades y reescrituras de la pantalla de Summary modular.
- **008_Pokedex Settings.rb / 009_Pokedex Handlers.rb / 010_Pokedex Rewrites.rb** — equivalente para la info de Pokédex modular.

### 003_MUI Enhanced Pokemon UI/
Mejoras visuales concretas sobre las pantallas de Pokémon.
- **003_Utilities.rb** — métodos de display (p.ej. Shiny Leaf).
- **004_Legacy Data.rb** — trackers de datos legacy (consumo de items, etc.).
- **005_Party UI.rb / 006_Storage UI.rb / 007_Summary UI.rb** — ediciones visuales de Party, Almacenamiento y Summary.

### 004_MUI Pokedex Data Page/
Página de datos extendida en la Pokédex.
- **002_GameData.rb** — datos extra (p.ej. Habitat adicional) para la página.
- **003_Page Setup.rb** — settings/rutas de la página.
- **004_Main Page.rb** — la página principal de datos.
- **005_Sub Menu.rb** — dibujado de cuadrículas de iconos de especie.
- **006_Moves Menu.rb** — lista de movimientos aprendibles dentro de la página.
- **007_Data Messages.rb** — textos contextuales de la página.

### 005_IV EV Summary Screen/
Página de IVs/EVs en el Summary.
- **002_Summary Handlers.rb** — registro de la página de IV/EV.
- **003_Stats Summary Page.rb** — el dibujado de la página de stats (resaltando subidas/bajadas por naturaleza).
