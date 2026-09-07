# Reference · Cría/huevos, pesca, bayas, roaming y dungeons

Fuentes: `037_Overworld/008_Overworld_DayCare.rb` (cría), `006_Overworld_Fishing.rb` (pesca),
`007_Overworld_BerryPlants.rb` (bayas), `039_Battle triggering/006_Overworld_RoamingPokemon.rb` (roaming),
`037_Overworld/009_Overworld_RandomDungeons.rb` (dungeons).

## Guardería y cría (`DayCare`)

El módulo/clase `DayCare` gestiona los dos huecos, la subida de nivel y la generación del huevo. API a
nivel de clase (la que llaman los eventos de la guardería):

- `DayCare.deposit(party_index)` — depositar un Pokémon del equipo.
- `DayCare.withdraw(index)` — retirar el del hueco `index` (0/1).
- `DayCare.choose(message, choice_var)` — elegir un Pokémon del equipo para depositar.
- `DayCare.count` — cuántos hay depositados.
- `DayCare.get_details(index, name_var, cost_var)` — nombre y coste de retirada en variables de juego.
- `DayCare.get_level_gain(index, name_var, level_var)` — niveles ganados.
- `DayCare.get_compatibility(compat_var)` — compatibilidad de cría en una variable (0..3).
- `DayCare.egg_generated?` / `DayCare.reset_egg_counters` — estado del huevo.
- `DayCare.collect_egg` — recoger el huevo generado (lo añade como `Pokemon` huevo, ver [[pokemon-api]]).

`DayCare[index]` da el hueco; cada hueco rastrea el Pokémon y los pasos. `update_on_step_taken` avanza el
contador de huevo por paso.

### Compatibilidad de cría
`compatibility` devuelve **0..3** (mayor = más probable). Reglas (de `008`):
- **0** si los géneros son incompatibles, si comparten solo el grupo huevo `Undiscovered`, o si no
  comparten grupo huevo (salvo **Ditto**, que cría con casi todo vía `pokemon_in_ditto_egg_group?`).
- La especie del bebé sale del lado **madre** (o no-Ditto), validada con
  `species_data.breeding_can_produce?(baby_species)` (cadena de pre-evoluciones, incensos…).
- **Movimientos huevo**: `share_egg_move` / `compatible_with_move?` heredan movimientos del **padre** que
  el bebé pueda aprender como egg move.

Para tocar las reglas (p.ej. cría más permisiva, herencia de IVs/naturaleza distinta) parchea el método de
`DayCare` con `alias_method` en `[ZBOX]` (R5); el huevo resultante se construye/edita con [[pokemon-api]].

## Pesca (`006_Overworld_Fishing.rb`)

```ruby
pbFishing(hasEncounter, rodType = 1)   # rodType 1=vieja, 2=buena, 3=súper
```
Envuelve `pbFishingBegin` (lanza la animación/caña) y `pbFishingEnd`. `hasEncounter` decide si pica algo
(normalmente lo calcula la tabla de encuentros de pesca del mapa). La caña suele darse vía un evento o el
menu de party; el **encuentro** que dispara sale de la tabla `OldRod`/`GoodRod`/`SuperRod`
([[pbs-data]] → encuentros) y el combate lo lanza el sistema de encuentros ([[battles-runtime]]).

## Plantas de bayas (`007_Overworld_BerryPlants.rb`)

`BerryPlantData` modela una maceta/tile de baya: `growth_stage` (0 = nada plantado, sube por fases),
`watered_this_stage` (mecánica Gen 3), y métodos `plant(berry_id)`, `replant`, `planted?`, `growing?`. El
modelo de crecimiento (Gen 3 con riego vs Gen 4 con humedad/mutación) depende de la configuración del
proyecto (`001_Settings`). Las bayas y sus tiempos se definen en el PBS de items/bayas ([[pbs-data]]).
Cada planta vive en un evento del mapa; para crear zonas de cultivo, replica el patrón del evento de baya.

## Pokémon roaming (`039/006`)

- Los roamers se definen en `Settings::ROAMING_SPECIES` (lista de entradas con `:species`, nivel,
  `:game_switch` que los habilita, tipo de encuentro…).
- `each_active_roamer(ignore_caught = true)` itera los roamers activos (switch activado y no capturados).
- Estado persistente en `$PokemonGlobal`: `roamPokemon` (capturado por índice), `roamPosition` (mapa
  actual de cada roamer), `roamEncounter`, `roamPokemonCaught`. `pbResetAllRoamers` revierte los
  derrotados-no-capturados.
- El **combate** del roamer se dispara vía el handler `:on_calling_wild_battle` ([[battles-runtime]]); su
  movimiento por mapas lo gestiona este archivo. Para añadir un roamer, edita `Settings::ROAMING_SPECIES`
  (es config del proyecto) y dale su switch.

## Dungeons procedurales (`037/009`)

`009_Overworld_RandomDungeons.rb` arma un mapa al vuelo a partir de `DungeonParameters` y
`DungeonTileset` (definidos en PBS, [[pbs-data]] → `026/020`-`021`). Para una mazmorra nueva, define sus
parámetros/tileset en el PBS y marca el mapa como dungeon en su metadata; el algoritmo hace el resto. Para
cambiar el **algoritmo** de generación, parchea con `alias_method` en `[ZBOX]`.
