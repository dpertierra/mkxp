# metadata.txt · player_metadata.txt · map_metadata.txt · town_map.txt · berry_plants.txt · dungeon_*.txt · phone.txt · Battle Frontier

Clases: `026_PBS data/017_Metadata.rb`, `018_PlayerMetadata.rb`, `019_MapMetadata.rb`, `003_TownMap.rb`, `008_BerryPlant.rb`, `020_DungeonTileset.rb`, `021_DungeonParameters.rb`, `022_PhoneMessage.rb`.

---

## metadata.txt — global

Cabecera `[0]` (`"u"`; siempre la sección 0 para datos globales).

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `StartMoney` | `u` | `3000` | Dinero inicial. |
| `StartItemStorage` | `*e` :Item | `[]` | Items ya en el PC al empezar. |
| `Home` | `vuuu` | nil | map_id, x, y, dirección a donde va el jugador tras perder sin Centro visitado. |
| `StorageCreator` | `s` | nil | Nombre del creador del Almacenamiento ("PC de XXX"). Default "Bill". |
| `WildBattleBGM` | `s` | nil | BGM combate salvaje por defecto. |
| `TrainerBattleBGM` | `s` | nil | BGM combate entrenador. |
| `WildVictoryBGM` | `s` | nil | BGM victoria salvaje. |
| `TrainerVictoryBGM` | `s` | nil | BGM victoria entrenador. |
| `WildCaptureME` | `s` | nil | ME al capturar. |
| `SurfBGM` | `s` | nil | BGM surfeando. |
| `BicycleBGM` | `s` | nil | BGM en bici. |

---

## player_metadata.txt — por personaje jugable

Cabecera `[N]` (`"u"`; 1, 2, 3… = cada personaje jugable). El PBS y la clase comparten archivo de origen pero el `DATA_FILENAME` es `player_metadata.dat`.

| Campo PBS | Formato | Significado |
|---|---|---|
| `TrainerType` | `e` :TrainerType | Tipo de entrenador del jugador. |
| `WalkCharset` | `s` | Charset caminando. |
| `RunCharset` | `s` | Corriendo (fallback: WalkCharset). |
| `CycleCharset` | `s` | En bici (fallback: RunCharset). |
| `SurfCharset` | `s` | Surfeando (fallback: CycleCharset). |
| `DiveCharset` | `s` | Buceando (fallback: SurfCharset). |
| `FishCharset` | `s` | Pescando (fallback: WalkCharset). |
| `SurfFishCharset` | `s` | Pescando mientras surfea (fallback: FishCharset). |
| `Home` | `vuuu` | map_id, x, y, dirección de inicio. |

---

## map_metadata.txt — por mapa

Cabecera `[ID_MAPA]` (`"u"`).

| Campo PBS | Formato | Significado |
|---|---|---|
| `Name` | `s` | Nombre del mapa visible (puede diferir del de RMXP). Soporta `\PN` = nombre del jugador. |
| `Outdoor` | `b` | Exterior: se colorea según hora del día. |
| `ShowArea` | `b` | Muestra el nombre del mapa al entrar. |
| `LocationSign` | `s` | Archivo en `Graphics/UI/Location/` para el letrero. |
| `Bicycle` | `b` | Permite usar bici. |
| `BicycleAlways` | `b` | Bici automática e indesmontable. |
| `HealingSpot` | `vuu` | map_id, x, y del Centro Pokémon de esta ciudad. |
| `Weather` | `eu` :Weather | Tipo de clima + intensidad/probabilidad (0–100). |
| `MapPosition` | `uuu` | región, x, y en el town map. |
| `DiveMap` | `v` | ID del mapa submarino (solo si hay agua profunda). |
| `DarkMap` | `b` | Mapa oscuro (círculo de luz; Destello lo amplía). |
| `SafariMap` | `b` | Forma parte de la Zona Safari. |
| `SnapEdges` | `b` | No centra al jugador en los bordes. |
| `StillReflections` | `b` | Reflejos sin estiramiento horizontal. |
| `Dungeon` | `b` | Mapa generado proceduralmente (dungeon aleatorio). |
| `BattleBack` | `s` | Base de los fondos de batalla (`XXX_bg`, `XXX_base0/1`, `XXX_message`). |
| `WildBattleBGM` / `TrainerBattleBGM` / `WildVictoryBGM` / `TrainerVictoryBGM` / `WildCaptureME` | `s` | BGM/ME específicos del mapa. |
| `MapSize` | `us` | ancho (celdas town map) + string de cuadrícula. |
| `Environment` | `e` :Environment | Entorno de batalla por defecto del mapa. |
| `Flags` | `*s` | Etiquetas. `CertainCaptures` hace que toda captura tenga éxito. |

---

## town_map.txt — mapa de región

Cabecera `[N]` (`"u"`; índice de región).

| Campo PBS | Formato | Significado |
|---|---|---|
| `Name` | `s` | Nombre de la región. |
| `Filename` | `s` | Imagen del mapa de región. |
| `Point` | `^uusSUUUU` (repetible) | x, y, Nombre, NombreInterno(op), + 4 enteros opcionales (flags/posición de evento). Cada línea `Point = ...` es un punto. |
| `Flags` | `*s` | Etiquetas. |

---

## berry_plants.txt — fases de bayas

Cabecera `[SIMBOLO_BAYA]` (`"m"`). Constantes: `NUMBER_OF_REPLANTS = 9`, `NUMBER_OF_GROWTH_STAGES = 4`, `NUMBER_OF_FULLY_GROWN_STAGES = 4`. Regaderas: `SPRAYDUCK, SQUIRTBOTTLE, WAILMERPAIL, SPRINKLOTAD`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `HoursPerStage` | `v` | `3` | Horas por cada fase de crecimiento. |
| `DryingPerHour` | `u` | `15` | Cuánto se seca la tierra por hora. |
| `Yield` | `uv` | `[2,5]` | Cosecha mínima y máxima. |

---

## dungeon_tilesets.txt — tilesets de dungeons procedurales

Cabecera `[ID]` (`"u"`). Define qué tiles del tileset son suelo/pared/vacío para la generación procedural.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Autotile` | `^um` (repetible) | — | (índice_autotile, tipo): `Autotile = 0,floor`. |
| `Tile` | `^um` (repetible) | — | (índice_tile, tipo): `Tile = 12,walls`. |
| `SnapToLargeGrid` | `b` | false | Ajusta a rejilla 2×2. |
| `LargeVoidTiles` | `b` | false | Tiles de vacío 2×2. |
| `LargeWallTiles` | `b` | false | Tiles de pared grandes. |
| `LargeFloorTiles` | `b` | false | Tiles de suelo 2×2. |
| `DoubleWalls` | `b` | false | Paredes dobles. |
| `FloorPatchUnderWalls` | `b` | false | Parche de suelo bajo las paredes. |
| `ThinNorthWallOffset` | `i` | `0` | Offset de tile para paredes norte finas. |
| `Flags` | `*s` | `[]` | Etiquetas. |

Tipos de tile (segundo valor): `floor`, `walls`, `void`, `decoration`, y los derivados de pared (`wall_1..9`, `wall_in_*`, `upper_wall_*`) que el código genera automáticamente desde `walls`.

---

## dungeon_parameters.txt — parámetros de generación

Cabecera `[AREA]` o `[AREA,Versión]` (`"mV"`).

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `DungeonSize` | `vv` | `5,5` | celdas X, Y. |
| `CellSize` | `vv` | `10,10` | ancho, alto de celda. |
| `MinRoomSize` | `vv` | `5,5` | tamaño mínimo de sala. |
| `MaxRoomSize` | `vv` | `cell-1` | tamaño máximo de sala. |
| `CorridorWidth` | `v` | `2` | ancho de pasillo. |
| `ShiftCorridors` | `b` | nil | desplaza pasillos aleatoriamente. |
| `NodeLayout` | `m` | `:full` | distribución de nodos (ver abajo). |
| `RoomLayout` | `m` | `:full` | distribución de salas. |
| `RoomChance` | `v` | `70` | % de nodos que se vuelven sala. |
| `ExtraConnections` | `u` | `2` | conexiones extra. |
| `FloorPatches` | `vvu` | `3,75,25` | radio, %, suavizado. |
| `FloorDecorations` | `uu` | `50,200` | densidad normal, densidad grande. |
| `VoidDecorations` | `uu` | `50,200` | densidad normal, grande. |
| `RNGSeed` | `u` | nil | semilla fija (reproducible). |
| `Flags` | `*s` | `[]` | Etiquetas. |

Layouts (`NodeLayout`/`RoomLayout`): `:full`, `:no_corners`, `:ring`, `:antiring`, `:plus`, `:diagonal_up` (/), `:diagonal_down` (\), `:cross` (x), `:quadrants`.

---

## phone.txt — diálogos telefónicos

Clase `PhoneMessage`. Cabecera `Default` (set por defecto) o `[TIPO,Nombre,Versión]` (`"q"`). Todos los campos son `^q` (repetibles: cada línea es una opción que se elige al azar).

| Campo PBS | Significado |
|---|---|
| `Intro` | Saludo genérico. |
| `IntroMorning` / `IntroAfternoon` / `IntroEvening` | Saludo según hora. |
| `Body` | Cuerpo del mensaje. |
| `Body1` / `Body2` | Variantes de cuerpo. |
| `BattleRequest` | Pide rematch. |
| `BattleRemind` | Recordatorio de rematch. |
| `End` | Despedida. |

---

## Battle Frontier (battle_facility_lists.txt + sets)

`battle_facility_lists.txt` lista conjuntos de Pokémon/entrenadores para retos del Frontier. Cada sección es un set:

```
[DefaultTrainerList]
Trainers = battle_tower_trainers.txt
Pokemon = battle_tower_pokemon.txt
#-------------------------------
[TrainerList]
Trainers = cup_poke_trainers.txt
Pokemon = cup_poke_pkmn.txt
Challenges = pokecupsingle,pokecupdouble
```

**Archivo de Pokémon** (una línea por Pokémon, separadores `;`):
`ESPECIE;ITEM;NATURALEZA;EVs;MOVE1,MOVE2,MOVE3,MOVE4`
```
SUNKERN;LAXINCENSE;RELAXED;HP,SA;MEGADRAIN,HELPINGHAND,SUNNYDAY,LIGHTSCREEN
MAGIKARP;FOCUSBAND;HARDY;HP,ATK;FLAIL,,,
```
(EVs se indican por abreviatura de stat: `HP,ATK,DEF,SPD,SA,SD`; los huecos vacíos son moves sin definir.)

**Archivo de entrenadores** (secciones `[NNN]` numéricas):
| Campo | Significado |
|---|---|
| `Type` | Tipo de entrenador. |
| `Name` | Nombre. |
| `BeginSpeech` | Frase inicial. |
| `EndSpeechWin` | Frase si gana. |
| `EndSpeechLose` | Frase si pierde. |
| `PokemonNos` | Índices (de la lista de Pokémon) que puede usar. |

Para definir un reto de batalla en código (`pbBattleChallenge`…) ver skill [[battles-runtime]].

---

## regional_dexes.txt / map_connections.txt

- `regional_dexes.txt`: listas de especies por Pokédex regional (orden de aparición). Se carga vía `pbLoadRegionalDexes` → `Data/regional_dexes.dat`.
- `map_connections.txt`: conexiones entre mapas (editor visual en Debug). No tiene clase GameData; lo procesa el compilador a `Data/map_connections.dat`.
