---
name: essentials-codemap
description: >
  Mapa de Navegación Maestro de Data/Scripts de Pokémon Essentials 21.1 / La Base de Sky: la arquitectura
  de la carpeta Scripts, un árbol visual de toda la jerarquía de carpetas, y un Reference que documenta
  la RESPONSABILIDAD de cada archivo (qué sistema controla, qué encontrarás dentro, cuándo consultarlo).
  Usar SIEMPRE antes de implementar, parchear o buscar algo en el motor, para saber EXACTO en qué archivo
  mirar sin iterar a ciegas. Triggers: "dónde está", "dónde busco", "qué archivo maneja", "cómo funciona
  el sistema de", "en qué script", "busca en el motor", o cualquier feature que requiera localizar código.
---

# Codemap — Mapa de Navegación Maestro de `Data/Scripts`

Raíz: `..\LA BASE DE SKY\Data\Scripts\`. **READ-ONLY** (R2): aquí se lee y se grepa; se modifica solo si el usuario lo pide. **Esta skill te dice en qué archivo mirar; SIEMPRE verifica la firma real con grep antes de aliasar o llamar una API** (los nombres de clase/método cambian con el desarrollo; aquí se documenta *responsabilidad*, no firmas).

## Arquitectura de la carpeta Scripts (macro)

Todo el motor vive en `Data/Scripts/`, repartido en ~46 carpetas numeradas. **El número del prefijo `NNN_` es el orden de carga**: las dependencias se definen antes (Settings → parches Ruby → GameData → clases de juego → batalla → UI → utilidades) y `999_Main` arranca el juego al final. La misma convención aplica dentro de cada carpeta (`002_`, `003_`…). Por eso, para entender un sistema, su archivo "base" suele ser el de número más bajo de su carpeta.

**Capas (de cimientos a contenido):**
1. **Cimientos / runtime** (`001`–`024`): configuración global (`001_Settings`), compatibilidad MKXP y parches a Ruby (`003_Technical`), depuración (`004`), archivos/HTTP (`005`), **guardado** (`007`), **arranque + intérprete de eventos + EventHandlers** (`009`), clases RGSS del juego `Game_*` (`011`), switches/variables (`012`), sprites de mapa (`014`), **renderer de tilemap** (`016`), ventanas/mensajes/caché de bitmaps (`018`), audio (`020`), transiciones/cutscenes (`022`), **registro `GameData`** (`024`).
2. **Datos del juego** (`025`–`026`, y su compilador `065`): enums hardcoded (`025`), clases `GameData` cargadas de PBS (`026`), y el **Compilador PBS→.dat** (`065`).
3. **Batalla** (`028`–`035`): clase `Battle` y sus fases (`028`), `Battler` (`029`), `Move` + implementación de cada efecto (`030`), escena visual (`031`), IA (`032`–`033`), efectos de habilidad/item/pokéball, captura, clauses, animaciones (`034`), modos de combate alternos (`035`).
4. **Overworld** (`037`–`039`): mecánicas de mapa (MOs, pesca, bayas, guardería, día-noche…) (`037`), visuales (clima, sombras, VS bars) (`038`), **disparo de combates y encuentros salvajes** (`039`).
5. **Contenido y pantallas** (`041`–`056`): items y mochila (`041`), clase `Pokemon` y derivados (`043`–`044`), trainers/jugador (`046`), controles de UI (`047`), **todas las pantallas** (`048`–`049`), minijuegos (`051`), modos alternos overworld (`053`), Battle Frontier (`054`–`056`).
6. **Tooling y custom** (`058`–`069`): utilidades y librerías externas, incl. **código propio de Sky (Zik/Marin/Luka)** (`058`), debug y editores in-game (`060`–`063`), compilador (`065`), y la **UI modular MUI** (`069`).
7. **Boot** (`999`): bucle eterno que arranca el juego y captura errores duros.

**El "pegamento" del motor — sistemas de handlers (cómo se invoca y extiende todo sin tocar el núcleo):** el motor expone colecciones donde el código se *registra* en vez de modificar el flujo. Los principales:
- `EventHandlers` (definido en `009_Game processing`) — ganchos del juego (`:on_frame_update`, `:on_player_step_taken`, `:on_start_battle`, `:on_end_battle`, `:on_wild_pokemon_created`, `:on_trainer_load`…). Es el bus de eventos del overworld y la batalla.
- `MenuHandlers` (`009`) — entradas de menús (pausa, debug F9, party, PC, opciones…).
- `ItemHandlers` (`041`) — qué hace cada item al usarlo (campo/batalla).
- `Battle::AbilityEffects` / `Battle::ItemEffects` / `Battle::PokeBallEffects` (`034`) — efectos en combate.
- `MultipleForms` (`044`) — comportamiento de formas alternativas.
- `UIHandlers` / page handlers de MUI (`069`) — páginas modulares de Summary/Pokédex.
- `PluginManager` (`003`/`058`) — registra plugins (los de `Plugins/` se cargan aparte y parchean el motor con `alias_method`).

**Puntos de entrada / flujo de ejecución de los sistemas grandes:**
- **Arranque:** `999_Main` → carga + pantalla de título (`009_Game processing/002_StartGame.rb`) → `Scene_Map` (`009/003`) corre el bucle del overworld.
- **Overworld:** `Scene_Map#update` mueve a `$game_player` (`011_Game classes`), dispara `EventHandlers` y comprueba encuentros (`039`). El **intérprete de eventos** (`009/004`-`005`) ejecuta los comandos de los eventos del mapa.
- **Combate:** un evento o un encuentro llama a `TrainerBattle.start` / `WildBattle.start` (`039`), que crean `Battle` (`028`) y corren `pbStartBattle` (fases comando → ataque → fin de ronda), apoyándose en `Battler` (`029`), `Move` (`030`), `AI` (`032`) y la `Scene` (`031`). Detalle de uso en runtime: skill `battles-runtime`.
- **Datos:** cualquier dato del juego se consulta vía `GameData::X.get/try_get` (`024`+`026`); los `.dat` los produce el compilador (`065`) desde los PBS (skill `pbs-data`).
- **Guardado:** `SaveData` (`007`) registra qué globales se persisten; `Game.save`/`Game.load` (`009/002`).
- **UI:** conviven dos generaciones — el patrón clásico `PokemonX_Scene` + `PokemonXScreen`, y el framework moderno `UI::` (`048/000_UI_base.rb`) con controles MUI (`047`) y escenas modulares MUI (`069`).

## Método de búsqueda

1. Ubica la carpeta candidata en el árbol de abajo y abre su archivo en `reference/` para saber qué `.rb` exacto toca.
2. Grep para confirmar clase/método/firma reales (con `-n`):
   - `def pbNombre` / `def self.metodo` — firma exacta.
   - `class NombreClase` / `module NombreModulo` — definición y reaperturas (motor + plugins).
   - `EventHandlers.add(:` / `MenuHandlers.add(:` / `ItemHandlers.add(` — handlers ya registrados (imitar el patrón).
   - `alias_method` en `..\LA BASE DE SKY\Plugins\` — ver quién más parchea el mismo método.
   - `_INTL("texto visible")` — localizar la pantalla/flujo que muestra un mensaje conocido.
3. Lee el método completo + contexto antes de parchear.

## Árbol de directorios de `Data/Scripts`

```
Data/Scripts/
├── 001_Settings/
├── 003_Technical/
├── 004_Debugging/
├── 005_Files/
├── 007_Save data/
├── 009_Game processing/
├── 011_Game classes/
├── 012_Switches and Variables/
├── 014_Sprites/
├── 016_Map renderer/
├── 018_Objects and windows/
├── 020_Audio/
├── 022_Scenes/
├── 024_Data/
├── 025_Hardcoded data/
├── 026_PBS data/
├── 028_Battle/
├── 029_Battler/
├── 030_Move/
├── 031_Scene/
├── 032_AI/
├── 033_AI MoveEffects/
├── 034_Other battle code/
├── 035_Other battle types/
├── 037_Overworld/
├── 038_Overworld visuals/
├── 039_Battle triggering/
├── 041_Items/
├── 043_Pokemon/
├── 044_Pokemon-related/
├── 046_Trainers and player/
├── 047_UI Elements/
│   └── ControlElements/
├── 048_UI/
│   ├── 002_Pokegear Themes/
│   └── 003_Box Auto-Sort/
├── 049_Non-interactive UI/
├── 051_Minigames/
├── 053_Alternate battle modes/
├── 054_Battle Frontier/
├── 055_Battle Frontier rules/
├── 056_Battle Frontier generator/
├── 058_Utilities/
│   ├── 002_Libraries/
│   └── 003_Luka's Scripting Utilities/
│       ├── 002_Concerns/
│       ├── 003_Core/
│       ├── 004_Sprites/
│       ├── 005_UI/
│       ├── 006_PluginManager/
│       └── 007_Bitmaps/
├── 060_Debug/
│   ├── 002_Event Reporting/
│   └── 003_Tileset Rearranger/
├── 061_Editor screens/
├── 062_Animation editor/
├── 063_Debug menus/
├── 065_Compiler/
├── 069_MUI/
│   ├── 002_MUI Modular UI Scenes/
│   ├── 003_MUI Enhanced Pokemon UI/
│   ├── 004_MUI Pokedex Data Page/
│   └── 005_IV EV Summary Screen/
└── 999_Main/
```

## Reference — responsabilidad de cada archivo

El detalle por archivo (Propósito Core · Qué encontrarás · Cuándo consultar) está en `reference/`, agrupado por dominio respetando la jerarquía de carpetas de arriba. Lee el archivo que cubra la carpeta que te interesa:

| Carpetas | Reference |
|---|---|
| `001_Settings`, `003_Technical`, `004_Debugging`, `005_Files`, `020_Audio`, `022_Scenes`, `999_Main` | `reference/00-core-and-boot.md` |
| `007_Save data`, `009_Game processing`, `012_Switches and Variables`, `024_Data` | `reference/01-save-and-game-processing.md` |
| `011_Game classes`, `014_Sprites`, `016_Map renderer`, `018_Objects and windows` | `reference/02-game-classes-and-rendering.md` |
| `025_Hardcoded data`, `026_PBS data`, `065_Compiler` | `reference/03-gamedata-and-pbs.md` |
| `041_Items`, `043_Pokemon`, `044_Pokemon-related`, `046_Trainers and player` | `reference/04-pokemon-items-trainers.md` |
| `028_Battle`, `029_Battler`, `030_Move`, `031_Scene`, `034_Other battle code`, `035_Other battle types` | `reference/05-battle.md` |
| `032_AI`, `033_AI MoveEffects` | `reference/06-battle-ai.md` |
| `037_Overworld`, `038_Overworld visuals`, `039_Battle triggering` | `reference/07-overworld-and-encounters.md` |
| `047_UI Elements`, `048_UI`, `049_Non-interactive UI` | `reference/08-ui.md` |
| `051_Minigames`, `053_Alternate battle modes`, `054`/`055`/`056_Battle Frontier` | `reference/09-minigames-and-frontier.md` |
| `058_Utilities`, `069_MUI` | `reference/10-utilities-and-mui.md` |
| `060_Debug`, `061_Editor screens`, `062_Animation editor`, `063_Debug menus` | `reference/11-debug-and-editors.md` |

## Skills relacionadas
- Datos PBS (campos, enums, evolución): `pbs-data`. · Manipular Pokémon en runtime: `pokemon-api`. · Combates desde código: `battles-runtime`.
- Crear/parchear plugins, persistencia, handlers: `plugin-architecture`. · UI: `game-ui-design` / `menu-and-options`. · Runtime MKXP-Z: `mkxp-z-runtime`. · Assets: `assets-pipeline`.
