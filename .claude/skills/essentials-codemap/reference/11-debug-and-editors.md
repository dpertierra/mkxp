# Reference · Debug y editores in-game

Carpetas: `060_Debug`, `061_Editor screens`, `062_Animation editor`, `063_Debug menus`. Casi todo esto solo corre en modo `$DEBUG`. Para añadir entradas al menú F9 ver skill `menu-and-options`.

## 060_Debug
Herramientas de depuración y editores de datos del modo debug.

### 002_Editor_Utilities.rb
- **Core:** utilidades comunes de los editores de debug (propiedades editables, prompts).
- **Encontrarás:** los tipos de "property" reutilizados por los editores de datos.
- **Consulta si:** cómo se editan campos en los editores de debug.

### 002_Event Reporting/
Herramientas que reportan/buscan uso de switches, variables, audio y textos en eventos.
- **001_DebugMenu.rb** — entrada de menú de las herramientas de reporting.
- **002_SwitchAndVariable.rb** — buscar dónde se usan switches/variables.
- **003_Audio.rb** — reportar el audio usado en eventos.
- **004_EventStrings.rb** — extraer/buscar textos de eventos.
- **Consulta si:** auditar el uso de switches/variables/audio/texto en el proyecto.

### 003_Editor_DataTypes.rb
- **Core:** definición de tipos de propiedad para editar datos.
- **Encontrarás:** los "property types" (item, especie, move…) usados en los editores.
- **Consulta si:** un editor necesita un tipo de campo concreto.

### 003_Tileset Rearranger/
**(custom)** Herramienta de debug para reordenar tilesets.
- **002_TilesetRearranger_draw.rb / 003_help / 004_input / 005_load_and_save / 006_main / 007_menus / 008_utilities** — dibujado, ayuda, entrada, guardado, bucle principal, menús y utilidades de la herramienta.
- **Consulta si:** trabajas con la utilidad de reordenado de tilesets.

### 004_Editor_Listers.rb
- **Core:** listadores de datos para los editores.
- **Encontrarás:** las listas seleccionables de items/especies/moves/etc. en debug.
- **Consulta si:** mostrar un listado de datos en una herramienta de debug.

### 005_Debug Passability.rb
- **Core:** overlay de pasabilidad sobre el mapa.
- **Encontrarás:** la visualización de qué tiles son transitables.
- **Consulta si:** depurar colisiones del mapa.

### 006_Map Exporter.rb
- **Core:** exportar el mapa a imagen.
- **Encontrarás:** el volcado del mapa renderizado a archivo.
- **Consulta si:** exportar una captura del mapa.

### 007_Debug List Search.rb
- **Core:** búsqueda en listas del debug.
- **Encontrarás:** un buscador para los listadores de debug.
- **Consulta si:** buscar dentro de las listas de debug.

### 008_Debug Terminal.rb
- **Core:** terminal/REPL in-game.
- **Encontrarás:** la consola para ejecutar código Ruby en caliente.
- **Consulta si:** ejecutar código de prueba dentro del juego.

### 009_ItemBallPrinter.rb
- **Core:** **(custom)** generador/impresor de eventos de Poké Ball de item.
- **Encontrarás:** la utilidad que crea eventos de item-ball.
- **Consulta si:** generar eventos de items en el suelo.

## 061_Editor screens
Editores visuales accesibles desde debug.

### 002_EditorScreens.rb
- **Core:** base de las pantallas de editor visual.
- **Encontrarás:** el marco común de los editores visuales.
- **Consulta si:** un editor visual de debug.

### 003_EditorScreens_TerrainTags.rb
- **Core:** editor de terrain tags de un tileset.
- **Encontrarás:** la asignación visual de terrain tags.
- **Consulta si:** editar terrain tags.

### 004_EditorScreens_MapConnections.rb
- **Core:** editor de conexiones entre mapas.
- **Encontrarás:** la herramienta visual para unir mapas.
- **Consulta si:** conectar mapas (datos en `map_connections`).

### 005_EditorScreens_SpritePositioning.rb
- **Core:** editor de posición de sprites de Pokémon en batalla.
- **Encontrarás:** el ajuste visual de `SpeciesMetrics`.
- **Consulta si:** posicionar sprites de batalla (datos en `026/011`).

## 062_Animation editor
Editor de animaciones de batalla.

### 002_AnimEditor_SceneElements.rb
- **Core:** elementos de la escena del editor de animaciones.
- **Encontrarás:** el lienzo y los elementos visuales del editor.
- **Consulta si:** la UI del editor de animaciones.

### 003_AnimEditor_ControlsButtons.rb
- **Core:** controles/botones del editor.
- **Encontrarás:** la barra de herramientas y controles.
- **Consulta si:** los controles del editor de animación.

### 004_AnimEditor_Interpolation.rb
- **Core:** interpolación entre keyframes.
- **Encontrarás:** el cálculo de fotogramas intermedios.
- **Consulta si:** el tweening de animaciones.

### 005_AnimEditor_ExportImport.rb
- **Core:** exportar/importar animaciones.
- **Encontrarás:** la serialización de animaciones a/desde archivo.
- **Consulta si:** guardar/cargar animaciones.

### 006_AnimEditor_Functions.rb
- **Core:** funciones de apoyo del editor.
- **Encontrarás:** utilidades varias del editor de animaciones.
- **Consulta si:** helpers internos del editor.

## 063_Debug menus
El **menú de debug (F9)** y sus comandos.

### 002_Debug_Menus.rb
- **Core:** estructura del menú de debug.
- **Encontrarás:** la organización en categorías del menú F9.
- **Consulta si:** entender/organizar el menú de debug (ver skill `menu-and-options`).

### 003_Debug_MenuCommands.rb
- **Core:** comandos generales del menú de debug.
- **Encontrarás:** las entradas de debug del overworld (dar items, switches, etc.).
- **Consulta si:** añadir un comando general al debug.

### 004_Debug_MenuExtraCode.rb
- **Core:** código de apoyo de los comandos de debug.
- **Encontrarás:** funciones auxiliares llamadas por los comandos.
- **Consulta si:** lógica compartida de comandos de debug.

### 005_Debug_BattleCommands.rb
- **Core:** comandos de debug de batalla.
- **Encontrarás:** las entradas para probar combates desde debug.
- **Consulta si:** lanzar/probar combates desde el menú de debug.

### 006_Debug_BattlePkmnCommands.rb
- **Core:** comandos de debug sobre Pokémon en batalla.
- **Encontrarás:** acciones de debug aplicadas a un battler.
- **Consulta si:** manipular un Pokémon dentro de un combate de prueba.

### 007_Debug_BattleExtraCode.rb
- **Core:** código de apoyo de los comandos de debug de batalla.
- **Encontrarás:** utilidades compartidas por los comandos de batalla.
- **Consulta si:** lógica auxiliar de debug de combate.

### 008_Debug_PokemonCommands.rb
- **Core:** comandos de debug sobre un Pokémon (fuera de combate).
- **Encontrarás:** editar IVs/EVs/forma/etc. de un Pokémon desde debug.
- **Consulta si:** editar un Pokémon desde el menú de debug.

### 009_Debug_FilenameUpdater.rb
- **Core:** **(custom)** actualizador de nombres de archivo (assets).
- **Encontrarás:** una herramienta para renombrar/normalizar archivos en masa.
- **Consulta si:** migrar nombres de assets.
