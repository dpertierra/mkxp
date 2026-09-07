---
name: overworld-field-systems
description: >
  Sistemas jugables del mapa (overworld) en Pokémon Essentials 21.1 / La Base de Sky: movimientos de
  campo / MOs vía HiddenMoveHandlers (Surf, Fuerza, Vuelo, Corte, Destello, Buceo, Excavar… y crear uno
  nuevo), mecánicas de terreno (ledges, Rock Smash, puentes), día/noche (PBDayNight), metadata de mapa/
  jugador, seguidores / Pokémon que te sigue (Game_Follower), guardería y CRÍA/huevos (DayCare:
  compatibilidad por grupo huevo/género/Ditto, movimientos huevo), pesca, plantas de bayas, Pokémon
  roaming y dungeons procedurales. Extraído de 037_Overworld, 011_Game classes (followers) y 039_Battle
  triggering (roaming). Para los DATOS (encuentros, town map, metadata en PBS) ver [[pbs-data]]; para
  disparar el combate del encuentro ver [[battles-runtime]]; para scripting de eventos ver
  [[events-and-messages]]. Triggers: "movimiento de campo", "MO/HM", "Surf", "Vuelo", "día y noche",
  "guardería", "cría", "huevo", "pesca", "bayas", "roaming", "Pokémon que sigue", "seguidor".
---

# Sistemas de campo (overworld)

Fuentes (verificar firmas — ver [[essentials-codemap]]): `037_Overworld/002_Overworld.rb` (mecánicas
base + `pbMoveRoute`/`pbWait`), `003_Overworld_Metadata.rb`, `004_Overworld_Time.rb` (día/noche),
`005_Overworld_FieldMoves.rb` (MOs), `006_Overworld_Fishing.rb`, `007_Overworld_BerryPlants.rb`,
`008_Overworld_DayCare.rb` (cría), `009_Overworld_RandomDungeons.rb`; followers en
`011_Game classes/011_Game_Follower.rb` + `012_Game_FollowerFactory.rb`; roaming en
`039_Battle triggering/006_Overworld_RoamingPokemon.rb`.

Esta skill cubre los **sistemas de juego del mapa**. Lo que cambia tras un paso, un movimiento de campo,
una hora del día, un huevo o una caña pertenece aquí. Para el *scripting* de eventos (diálogos, rutas,
switches) ver [[events-and-messages]]; para los **datos** (tablas de encuentros, town map, metadata de
mapa) ver [[pbs-data]].

## Mapa rápido: qué archivo toca qué

| Sistema | Archivo | Nota |
|---|---|---|
| MOs / movimientos de campo | `037/005` | `HiddenMoveHandlers` (Surf, Fuerza, Vuelo, Corte, Destello, Buceo, Excavar) |
| Surf, ledges, Rock Smash, puentes, Cut trigger | `037/002` | mecánicas base de terreno |
| Metadata de mapa/jugador en runtime | `037/003` | lee `MapMetadata`/`Metadata` ([[pbs-data]]) |
| Día/noche, tono horario | `037/004` | `PBDayNight` |
| Seguidor / Pokémon que sigue | `011/011`, `011/012` | `Game_Follower` (dependent events) |
| Guardería, cría, huevos | `037/008` | `DayCare` (compatibilidad, movimientos huevo) |
| Pesca | `037/006` | `pbFishing` |
| Plantas de bayas | `037/007` | `BerryPlantData` (fases de crecimiento) |
| Roaming | `039/006` | `Settings::ROAMING_SPECIES`, `$PokemonGlobal.roamPokemon` |
| Dungeons procedurales | `037/009` | a partir de `DungeonParameters`/`DungeonTileset` ([[pbs-data]]) |

## Dos formas de extender

- **Handler-driven** (preferido, aditivo): los MOs se registran en `HiddenMoveHandlers` con `.add`. Crear
  una MO nueva = registrar `CanUseMove`/`ConfirmUseMove`/`UseMove`. No se parchea nada (R8/R11). Detalle
  en `reference/01-movement-hms-time.md`.
- **Patch-driven**: para tocar mecánicas base (encuentros por paso, generación de huevos, crecimiento de
  bayas) se parchea el método del motor con `alias_method` en un plugin `[ZBOX]` (R5), nunca editando
  `Data/`.

## Reference

| Archivo | Cubre |
|---|---|
| `reference/01-movement-hms-time.md` | MOs (`HiddenMoveHandlers` + crear una nueva), mecánicas de terreno, día/noche, metadata, seguidores |
| `reference/02-breeding-encounters-berries.md` | Guardería/cría/huevos (`DayCare`), pesca, plantas de bayas, roaming, dungeons |

Skills relacionadas: [[pbs-data]] (encuentros, town map, metadata, dungeons, berries en PBS),
[[battles-runtime]] (disparar el combate de un encuentro/roamer), [[events-and-messages]] (eventos del
mapa, rutas de movimiento), [[pokemon-api]] (el huevo/Pokémon generado), [[plugin-architecture]]
(empaquetar el parche `[ZBOX]`).
