---
name: maker-studio-plugin
description: >
  El plugin Ruby MakerStudio que renderiza in-game los datos extra del editor Maker Studio
  (capas extendidas, efectos por tile, autotiles con nombre, fog/panorama/grupos custom, sombras
  runtime, map versions, comandos de evento de fog/frame). Estructura de archivos, modelo de datos
  (@extended_layers JSON, @expanded_autotiles), cómo parchea el motor (TilemapRenderer, Game_Map,
  Game.load_map, EventHandlers, MenuHandlers, Compiler) y cómo AÑADIR agregados respetando las
  reglas de Essentie. Usar para entender, depurar o extender la integración Ruby.
  Triggers: "plugin MakerStudio", "capas extendidas in-game", "RendererOverride", "GameMapOverride",
  "@extended_layers", "Reload Map Data", "sombra no se ve", "map version no cambia", "agregar al plugin".
---

# Plugin Ruby `MakerStudio` (integración in-game)

Plugin de **Toskan4134** que hace que Essentials/LBdS renderice los extras que el editor incrusta en el `.rxdata`. Es la contraparte del editor de escritorio → para entender el editor: skill **maker-studio**; para mods JS del editor: skill **maker-studio-mods**.

Fuente canónica (versión más completa, **estudiar esta**): `..\Maker Studio\MakerStudio\`. Instalado en `<game>/Plugins/MakerStudio/`. `meta.txt`: Name=MakerStudio, Version=1.0.0, Essentials=21.1, Credits=Toskan4134. La app del editor NO se bundlea (va aparte).

## Estructura y responsabilidades

| Archivo | Responsabilidad |
|---------|-----------------|
| `000_Settings.rb` | `module MakerStudio`: `ENABLED`, `DEBUG_LOG`, `NATIVE_LAYERS=3`, `MAX_TOTAL_LAYERS=16`, `DEFAULT_EXT_LAYERS=3`, `TILE_WIDTH/HEIGHT=32`, `EFFECT_RANGES`, rutas (`PLUGIN_DIR`, `EDITOR_EXE_PATH`, `DOWNLOAD_URL`), constantes de tileset (`TILESET_START_ID=384`, `TILES_PER_AUTOTILE=48`) |
| `001_Core/001_DataStore.rb` | `MakerStudio::DataStore`: lee `@extended_layers` (JSON) del RPG::Map y `@expanded_autotiles` de tilesets. `resolve_rpg_map`, `load_extended_data`, `get_extended_data`, `get_expanded_autotile` |
| `001_Core/003_TileEffects.rb` | Efectos visuales por tile (opacity/rotation/hue/saturation/lighting/flip), con caché (`clear_cache`) |
| `002_Integration/001_Hooks.rb` | Menú debug ("Maker Studio..." → Open / Reload Map Data), lanzador del editor (`pbOpenMakerStudio`, `find_editor_executable`), hot-reload (`pbRefreshLiveMapData`, `pbReloadCurrentMapData`), EventHandlers, alias de `Game.load_map` |
| `002_Integration/002_RendererOverride.rb` | El grande (~1760 líneas): parchea `TilemapRenderer` para dibujar capas extendidas + cross-tileset + autotiles extra + sombras runtime; gestión de caché y sprites |
| `002_Integration/003_GameMapOverride.rb` | Parchea `Game_Map`: paso/prioridad/terreno/bush/counter leyendo overrides por tile de capas extendidas |
| `002_Integration/004_FogOverride.rb` | Render multi-fog (scroll, hue, opacidad, blend) |
| `002_Integration/005_CompilerFix.rb` | Protege al compilador de eventos contra scripts nil del editor (`Compiler`, `Translator`) |
| `002_Integration/006_MapVersioning.rb` | Map versions: manifest, versión activa por switch/variable, swap in-place |
| `002_Integration/007_CustomSheetGrid.rb` | Sheet Cols/Rows custom en `Sprite_Character` (hojas no 4×4) |
| `002_Integration/008_FogCommands.rb` | Comandos de evento de fog (Change Map Settings tipo Fog, Fog Color/Opacity por capa) |
| `002_Integration/009_FrameCommands.rb` | Acciones Set Frame de move route (animación manual de frame) |
| `002_Integration/010_MinimapOverride.rb` | Integración con el minimapa |
| `003_Editor/Mods/` | Mods JS a nivel de proyecto (los lee el editor, no el juego) → skill maker-studio-mods |

(El `README.md` del plugin documenta una versión más antigua con menos archivos; la carpeta real trae 006–010. Confiar en los archivos, no en el README.)

## Modelo de datos in-game

- **Capas extendidas**: `RPG::Map#@extended_layers` = string JSON dentro del `.rxdata`. `DataStore.load_extended_data(map_id, game_map)` lo parsea → `{ "map_id", "map_width", "map_height", "layers":[{ "id","name","visible","opacity","tiles":{} }] }`. `resolve_rpg_map` saca el RPG::Map de un Game_Map (`@map`) o de un RPG::Map directo.
- **Autotiles expandidos**: `Tileset#@expanded_autotiles` (JSON), buscados por nombre con `get_expanded_autotile`.
- **IDs de tile**: autotiles 0–383 (8 autotiles × 48 patrones), tiles normales desde `TILESET_START_ID=384`, 8 por fila.
- **Sombras / fog / panorama / grupos custom**: config en el mapa; bitmaps generados en runtime. Grupos de capa custom (de mods) persisten en el `.rxdata` y el plugin los renderiza **aunque el mod no esté instalado**.

## Cómo parchea el motor (puntos de extensión)

- **TilemapRenderer** (`002_RendererOverride.rb`): reabre la clase; añade `ensure_extended_sprites`, `create_extended_sprites_for_map`, sombras (`create_shadow_sprites_for_map`, `generate_shadow_bitmap`), `apply_native_tile_properties`, etc. El render in-game vive aquí.
- **Game_Map** (`003_GameMapOverride.rb`): reabre la clase; `passable?`, `playerPassable?`, `terrain_tag`, `bush?`, `counter?`… consultan overrides extendidos y caen a `native_layer_*` para las capas nativas.
- **`Game.load_map`** (`001_Hooks.rb`): aliaseado a `__mkst__load_map` (con guardas) y redefinido **incondicionalmente** (sobrevive al soft-reset F12). En Continue con magic-number coincidente, es lo único que refresca los datos de MakerStudio → llama `pbRefreshLiveMapData`.
- **EventHandlers**: `:on_new_game` y `:on_game_map_setup` → `load_extended_layers_for_map`; `:on_leave_map` → `clear_extended_layers`.
- **MenuHandlers** (`:debug_menu`, solo `$DEBUG`): submenú "Maker Studio..." con Open / Reload Map Data. Nota del autor: evita `MenuHandlers.get` (ausente en algunas builds v21.1/LBDS → NoMethodError); `add` sobrescribe por key (no-op seguro al re-añadir).
- **Compiler/Translator** (`005_CompilerFix.rb`): aliasea `replace_scripts`, `fix_event_scripts`, `find_translatable_text_from_event_script` para sanear comandos de evento que el editor pudo dejar nil.

## Comandos de evento del editor ↔ Ruby

Los comandos de evento de mods JS (`ctx.events.registerCommand`) **no tienen dispatcher**: se guardan como comando Script (código 355) con Ruby literal que corre en el juego. Los comandos de fog/frame que trae el editor se apoyan en métodos Ruby del plugin (`008_FogCommands.rb`, `009_FrameCommands.rb`). **Si un comando de evento emite Ruby que el motor no conoce, ese método hay que proveerlo** — en el plugin (si es del autor) o en un parche `[ZBOX]` propio.

## Añadir agregados — reglas de Essentie

`MakerStudio` es **plugin de terceros** (Toskan4134) y se **actualiza desde Releases**. Por R11/R5, **no editar in-place**: crear un plugin `[ZBOX]` aparte que parchee con `alias_method` (convención `_ZBOX_<abrev>_orig_<metodo>`, nunca `prepend`). Excepción: si el usuario decide forkear/mantener MakerStudio como propio, se edita in-place (pierde upgrades). Detalle de arquitectura/persistencia/handlers/checklist: skill **plugin-architecture**.

Dos caminos para una feature nueva que dependa del editor:

1. **Comando de evento desde un mod JS** que emite Ruby — si el Ruby ya existe en el motor/plugin, **cero código Ruby nuevo** (preferido). Skill maker-studio-mods.
2. **Parche `[ZBOX]`** que aliasea métodos de `MakerStudio` o del motor — cuando hace falta lógica nueva in-game (nuevo efecto por tile, nuevo tipo de capa, hook de render/colisión). Reusar los primitivos del plugin (`DataStore`, `TileEffects`, `EFFECT_RANGES`, `load_extended_layers_for_map`) en vez de duplicar.

Convenciones del plugin a respetar: `module MakerStudio`; gate de diagnósticos con `DEBUG_LOG` (`Console.echoln`/`echo_error` solo si on — en transferencias entre mapas spamea); usar `EFFECT_RANGES` para rangos; `try_get` para datos opcionales; texto al jugador con `_INTL` (R1); validar `nil`/`$game_map`/`ENABLED` antes de actuar.

## Antes de tocar nada

Grep en `..\Maker Studio\MakerStudio\` para verificar firmas reales (los archivos son grandes; `essentials-codemap` para el motor). Comparar con la fuente antes de "arreglar" algo que no rompiste tú. Verificación manual del usuario tras cambios: cargar partida → entrar al mapa con extras → Reload Map Data → guardar/recargar → revisar `errorlog.txt`/`mkxp.log`. El agente no testea en runtime.
