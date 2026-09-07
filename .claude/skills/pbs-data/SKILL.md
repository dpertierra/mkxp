---
name: pbs-data
description: >
  Base de conocimiento ENCICLOPÉDICA de los archivos PBS de Pokémon Essentials 21.1 / La Base de Sky:
  cada campo, cada código de formato del SCHEMA, cada valor de enum y cada método de evolución, extraído
  1:1 del código fuente real (025_Hardcoded data + 026_PBS data). Usar al definir o editar Pokémon,
  formas, items, movimientos, habilidades, tipos, trainers, encuentros, metadata, ribbons, phone, town
  map, dungeons, berries o cualquier dato del juego en texto plano. Para manipular Pokémon/entrenadores
  en RUNTIME (no PBS) ver skills [[pokemon-api]] y [[battles-runtime]]. Triggers: "añade un Pokémon",
  "define un item/move/habilidad/tipo", "edita el trainer", "cambia los encuentros", "qué campos acepta",
  "qué métodos de evolución hay", "PBS".
---

# PBS — datos del juego en texto plano (referencia exhaustiva)

Carpeta del proyecto: `..\LA BASE DE SKY\PBS\`. Texto plano → el **Compilador** (`Data/Scripts/065_Compiler/`) lo convierte a `.dat` en `Data/`. El juego NO lee los `.txt` en runtime: tras editar PBS hay que **recompilar** (mantener una tecla durante el arranque en modo debug, o Menú Debug F9 → opciones de compilación). Los datos compilados se consultan en código vía `GameData::Clase.try_get(:SIMBOLO)` / `.get(...)`.

**Fuente de verdad de los campos:** el `SCHEMA` de cada clase en `026_PBS data/` + las enumeraciones en `025_Hardcoded data/`. El parser real (qué significa cada código de formato) vive en `065_Compiler/` (`SchemaHelper` / `pbGetCsvRecord`). Esta skill documenta lo extraído 1:1 de esos `SCHEMA`. **Ante duda de un campo, verificar ahí — no inventar claves.**

## Cómo está organizada esta skill (lee el archivo que toque)

| Necesitas… | Lee |
|---|---|
| Entender los **códigos de formato** del SCHEMA (`m`, `s`, `q`, `u`, `v`, `e`, `*`, `^`…) | `reference/schema-format.md` |
| `pokemon.txt`, `pokemon_forms.txt`, `pokemon_metrics.txt` (todos los campos + diferencias forma/base) | `reference/species.md` |
| `moves.txt`, `items.txt`, `abilities.txt`, `types.txt` (todos los campos, flags, FunctionCode) | `reference/moves-items-abilities-types.md` |
| `trainer_types.txt`, `trainers.txt` (+ SUB_SCHEMA por Pokémon), `encounters.txt`, `ribbons.txt`, `shadow_pokemon.txt` | `reference/trainers-encounters.md` |
| `metadata.txt`, `player_metadata.txt`, `map_metadata.txt`, `town_map.txt`, `berry_plants.txt`, `dungeon_*.txt`, `phone.txt`, `battle_facility_lists.txt` y archivos del Frontier | `reference/metadata-misc.md` |
| Enumeraciones cerradas (GrowthRate, GenderRatio, EggGroup, BodyShape/Color, Habitat, Stat, Nature, Status, TerrainTag, Weather, EncounterType, Environment, BattleWeather/Terrain, Target) **+ todos los métodos de Evolution** | `reference/enums-and-evolution.md` |

## Lista de archivos PBS del proyecto

`abilities.txt`, `berry_plants.txt`, `dungeon_parameters.txt`, `dungeon_tilesets.txt`, `encounters.txt`, `items.txt`, `map_connections.txt`, `map_metadata.txt`, `metadata.txt`, `moves.txt`, `phone.txt`, `pokemon.txt`, `pokemon_forms.txt`, `pokemon_metrics.txt`, `regional_dexes.txt`, `ribbons.txt`, `shadow_pokemon.txt`, `town_map.txt`, `trainer_types.txt`, `trainers.txt`, `types.txt` + Battle Frontier: `battle_facility_lists.txt`, `battle_tower_pokemon.txt`, `battle_tower_trainers.txt`, `cup_*_pkmn.txt` / `cup_*_trainers.txt`. Carpetas `Gen 5 backup` … `Gen 8 backup` y `Shadow Pokémon backup` = respaldos alternativos completos (no se compilan salvo que se renombren).

Cualquier PBS admite **archivos divididos por sufijo**: `moves_XYZ.txt`, `pokemon_forms_megas.txt`, etc. (`pbs_file_suffix`). Todos los del mismo tipo se compilan juntos.

## Formato general de un PBS

- Secciones encabezadas por `[CLAVE]` (la clave es `SectionName` en el SCHEMA). Termina al empezar la siguiente sección.
- Líneas `Campo = Valor` (espacios opcionales alrededor del `=`).
- Separadores `#-------------------------------` y comentarios: cualquier línea que empieza por `#`.
- Encoding UTF-8 (algunos con BOM — **conservar el encoding existente del archivo al editar**).
- Algunos campos (los `^` en el SCHEMA) pueden repetirse en varias líneas (p.ej. `Evolution = ...` varias veces, `Point = ...` en town_map).
- Subpropiedades indentadas: solo en `trainers.txt`, donde las líneas bajo un `Pokemon = ...` describen a ese Pokémon (la indentación es cosmética, no obligatoria).

## Reglas al editar PBS

1. **Símbolos en MAYÚSCULAS sin espacios** (`[POTION]`, `[FIREBLAST]`, `[BULBASAUR]`); internamente se usan como `:POTION`. Los IDs numéricos (mapas, metadata, town_map) van como número.
2. **No inventes claves.** Si un campo no aparece en el SCHEMA de su clase (ver reference/), el compilador lo ignora o aborta. Copia una sección existente análoga y modifícala.
3. **Todo campo es opcional** salvo `SectionName`; los no definidos toman su valor por defecto (documentado en cada reference/). No borres campos pensando que son requeridos: ninguno lo es excepto la cabecera.
4. **IDs referenciados deben existir** (especie en evolución, move en `Moves`, item en trainer, tipo en `Types`…). El compilador aborta con error de validación si no.
5. Tras cambios en PBS, **recordar al usuario recompilar**; los `.dat` viejos siguen activos hasta entonces.
6. **Texto visible** (nombres, descripciones, Pokedex, LoseText) en el español del proyecto. Internamente se traduce vía `pbGetMessageFromHash(MessageTypes::...)`.
7. Si la petición toca **mecánica** además de datos (item con efecto nuevo, habilidad nueva, FunctionCode nuevo de un move): PBS define el dato y el comportamiento se implementa en código (`ItemHandlers`, `Battle::AbilityEffects`, `Battle::Move::FunctionCode`) — ver skill [[plugin-architecture]] y [[essentials-codemap]].
8. Orden de stats en PBS (`BaseStats`, `EVs`, `IV`, `EV`): **HP, Atk, Def, Spd, SpAtk, SpDef** — ¡Velocidad es el 4º! (definido por `pbs_order` en `GameData::Stat`).

## Documentación vanilla cruzada

`..\Wiki Essentials\` — `Defining a species.md`, `Defining a move.md`, `Defining an item.md`, `Defining a trainer.md`, etc. La wiki es v21.1 vanilla; **el código de Sky tiene prioridad** (campos como `SuperShinyHue`, `LoseText_F`, `SuperShiny`, métodos de evolución custom y otros NO están en la wiki).
