# Enumeraciones cerradas + métodos de Evolución

Valores hardcoded en `025_Hardcoded data/`. Son los **únicos** valores válidos para los campos `e`/`*e` que los referencian. Para añadir uno nuevo hay que registrarlo en el `.rb` correspondiente (no en PBS).

---

## GrowthRate (002) — campo `GrowthRate`
`Medium` (=Medium Fast), `Erratic`, `Fluctuating`, `Parabolic` (=Medium Slow), `Fast`, `Slow`. Cada uno define `exp_values` (tabla nivel→exp) y `exp_formula`. `max_level` = `Settings::MAXIMUM_LEVEL`.

## GenderRatio (003) — campo `GenderRatio`
`female_chance` = umbral 0–255 (random < chance → hembra).

| ID | female_chance |
|---|---|
| `AlwaysMale` | — (solo macho) |
| `AlwaysFemale` | — (solo hembra) |
| `Genderless` | — (sin género) |
| `FemaleOneEighth` | 32 |
| `Female25Percent` | 64 |
| `Female50Percent` | 128 |
| `Female75Percent` | 192 |
| `FemaleSevenEighths` | 224 |

## EggGroup (004) — campo `EggGroups`
`Undiscovered`, `Monster`, `Water1`, `Bug`, `Flying`, `Field`, `Fairy`, `Grass`, `Humanlike`, `Water3`, `Mineral`, `Amorphous`, `Water2`, `Ditto`, `Dragon`.

## BodyShape (005) — campo `Shape`
`Head`, `Serpentine`, `Finned`, `HeadArms`, `HeadBase`, `BipedalTail`, `HeadLegs`, `Quadruped`, `Winged`, `Multiped`, `MultiBody`, `Bipedal`, `MultiWinged`, `Insectoid`. (`icon_position` 0–13 en `Graphics/UI/Pokedex/icon_shapes.png`.)

## BodyColor (006) — campo `Color`
`Red`, `Blue`, `Yellow`, `Green`, `Black`, `Brown`, `Purple`, `Gray`, `White`, `Pink`.

## Habitat (007) — campo `Habitat`
`None`, `Grassland`, `Forest`, `WatersEdge`, `Sea`, `Cave`, `Mountain`, `RoughTerrain`, `Urban`, `Rare`.

## Stat (009) — orden y abreviaturas
`type`: `main` (solo HP), `main_battle` (Atk/Def/SpA/SpD/Spe), `battle` (Accuracy/Evasion). `pbs_order` define el orden en PBS:

| ID | pbs_order | type |
|---|---|---|
| `HP` | 0 | main |
| `ATTACK` | 1 | main_battle |
| `DEFENSE` | 2 | main_battle |
| `SPEED` | 3 | main_battle |
| `SPECIAL_ATTACK` | 4 | main_battle |
| `SPECIAL_DEFENSE` | 5 | main_battle |
| `ACCURACY` | — | battle |
| `EVASION` | — | battle |

⚠️ En PBS el orden es **HP, Atk, Def, Spd, SpAtk, SpDef** (Velocidad 4ª).

## Nature (010) — campo `Nature`
25 naturalezas. `stat_changes` = `[[stat, ±%], …]`. Las neutras no tienen cambios: `HARDY`, `DOCILE`, `SERIOUS`, `BASHFUL`, `QUIRKY`.
Con cambios (sube +10% / baja −10%): `LONELY`(+Atk/−Def), `BRAVE`(+Atk/−Spe), `ADAMANT`(+Atk/−SpA), `NAUGHTY`(+Atk/−SpD), `BOLD`(+Def/−Atk), `RELAXED`(+Def/−Spe), `IMPISH`(+Def/−SpA), `LAX`(+Def/−SpD), `TIMID`(+Spe/−Atk), `HASTY`(+Spe/−Def), `JOLLY`(+Spe/−SpA), `NAIVE`(+Spe/−SpD), `MODEST`(+SpA/−Atk), `MILD`(+SpA/−Def), `QUIET`(+SpA/−Spe), `RASH`(+SpA/−SpD), `CALM`(+SpD/−Atk), `GENTLE`(+SpD/−Def), `SASSY`(+SpD/−Spe), `CAREFUL`(+SpD/−SpA).

## Status (011) — estados
`NONE`, `SLEEP`, `POISON`, `BURN`, `PARALYSIS`, y según `Settings::FREEZE_EFFECTS_CAUSE_FROSTBITE`: `FROZEN` (off) **o** `FROSTBITE` (on). `icon_position` en `Graphics/UI/statuses.png`.

## TerrainTag (012) — tags de tile (id_number = el "Terrain Tag" en RMXP)
Propiedades booleanas por tag: `can_surf`, `waterfall`, `waterfall_crest`, `can_fish`, `can_dive`, `deep_bush`, `shows_grass_rustle`, `shows_water_ripple`, `land_wild_encounters`, `double_wild_encounters`, `battle_environment` (símbolo Environment), `ledge`, `ice`, `bridge`, `shows_reflections`, `must_walk`, `must_walk_or_run`, `ignore_passability`.

| ID | nº | Notas |
|---|---|---|
| `None` | 0 | |
| `Ledge` | 1 | ledge |
| `Grass` | 2 | rustle, encuentros, env Grass |
| `Sand` | 3 | env Sand |
| `Rock` | 4 | env Rock |
| `DeepWater` | 5 | surf, fish, dive, env MovingWater |
| `StillWater` | 6 | surf, fish, env StillWater, reflejos, ripple |
| `Water` | 7 | surf, fish, env MovingWater |
| `Waterfall` | 8 | surf, waterfall |
| `WaterfallCrest` | 9 | surf, fish, crest |
| `TallGrass` | 10 | deep_bush, encuentros, dobles, env TallGrass, must_walk |
| `UnderwaterGrass` | 11 | encuentros |
| `Ice` | 12 | env Ice, ice, must_walk_or_run |
| `Neutral` | 13 | ignore_passability |
| `SootGrass` | 14 | rustle, encuentros, env Grass (añade hollín) |
| `Bridge` | 15 | bridge |
| `Puddle` | 16 | env Puddle, reflejos, ripple |
| `NoEffect` | 17 | |

## Weather (013) — clima de overworld — campo `Weather` (map_metadata)
`category` (∈ None/Rain/Hail/Sandstorm/Sun/Fog) afecta evoluciones, encuentros y bayas.

| ID | nº | category |
|---|---|---|
| `None` | 0 | None |
| `Rain` | 1 | Rain |
| `Storm` | 2 | Rain |
| `Snow` | 3 | Hail |
| `Blizzard` | 4 | Hail |
| `Sandstorm` | 5 | Sandstorm |
| `HeavyRain` | 6 | Rain |
| `Sun` | 7 | Sun |
| `Fog` | 8 | Fog |

(`graphics`, `particle_delta_*`, `tile_delta_*`, `tone_proc` controlan la animación.)

## EncounterType (014) — tipos de encuentro (encounters.txt)
`type` interno ∈ land/cave/water/fishing/contest/none. `trigger_chance` por defecto:

| ID | type | trigger_chance |
|---|---|---|
| `Land` / `LandDay` / `LandNight` / `LandMorning` / `LandAfternoon` / `LandEvening` | land | 21 |
| `PokeRadar` | land | 20 |
| `Cave` / `CaveDay` / `CaveNight` / `CaveMorning` / `CaveAfternoon` / `CaveEvening` | cave | 5 |
| `Water` / `WaterDay` / `WaterNight` / `WaterMorning` / `WaterAfternoon` / `WaterEvening` | water | 2 |
| `OldRod` / `GoodRod` / `SuperRod` | fishing | 0 |
| `RockSmash` | none | 50 |
| `HeadbuttLow` / `HeadbuttHigh` | none | 0 |
| `BugContest` | contest | 21 |

## Environment (015) — entorno de batalla — campo `Environment`
`None`, `Grass`, `TallGrass`, `MovingWater`, `StillWater`, `Puddle`, `Underwater`, `Cave`, `Rock`, `Sand`, `Forest`, `ForestGrass`, `Snow`, `Ice`, `Volcano`, `Graveyard`, `Sky`, `Space`, `UltraSpace`. (`battle_base` = nombre del gráfico de base para algunos.)

## BattleWeather (016) — clima en combate
`None`, `Sun`, `Rain`, `Sandstorm`, `Hail`, `Snowstorm`, `HarshSun`, `HeavyRain`, `StrongWinds`, `ShadowSky`. (Se usa en `setBattleRule "weather", X` — ver skill [[battles-runtime]].)

## BattleTerrain (017) — terreno en combate
`None`, `Electric`, `Grassy`, `Misty`, `Psychic`. (`setBattleRule "terrain", X`.)

## Target (018) — objetivo de movimiento — campo `Target` (moves.txt)
Propiedades: `num_targets` (0/1/2+), `targets_foe`, `targets_all`, `affects_foe_side`, `long_range`.

`None`, `User`, `NearAlly`, `UserOrNearAlly`, `AllAllies`, `UserAndAllies`, `NearFoe`, `RandomNearFoe`, `AllNearFoes`, `Foe`, `AllFoes`, `NearOther`, `AllNearOthers`, `Other`, `AllBattlers`, `UserSide`, `FoeSide`, `BothSides`.

---

# Métodos de Evolución (008_Evolution.rb) — campo `Evolution`

Formato en PBS (pokemon.txt / pokemon_forms.txt): `Evolution = ESPECIE,MÉTODO,PARÁMETRO`. El tipo del parámetro lo define cada método (`parameter`): `Integer`, `:Item`, `:Move`, `:Type`, `:Species`, `String`, o ninguno.

`any_level_up` = true significa "necesita subir de nivel" (no un nivel mínimo). El parámetro entonces no es un nivel.

### Por subida de nivel (`level_up_proc`)
| Método | Param | Condición |
|---|---|---|
| `Level` | Integer | nivel ≥ param |
| `LevelMale` / `LevelFemale` | Integer | nivel + género |
| `LevelDay` / `LevelNight` / `LevelMorning` / `LevelAfternoon` / `LevelEvening` | Integer | nivel + hora |
| `LevelNoWeather` | Integer | nivel + sin clima |
| `LevelSun` / `LevelRain` / `LevelSnow` / `LevelSandstorm` | Integer | nivel + categoría de clima |
| `LevelCycling` / `LevelSurfing` / `LevelDiving` | Integer | nivel + bici/surf/buceo |
| `LevelDarkness` | Integer | nivel + mapa oscuro |
| `LevelDarkInParty` | Integer | nivel + un Pokémon Siniestro en el equipo |
| `LevelCoins` | Integer | monedas ≥ param (las resta) |
| `AttackGreater` / `AtkDefEqual` / `DefenseGreater` | Integer | nivel + relación Atk vs Def (Hitmon-) |
| `Silcoon` / `Cascoon` | Integer | nivel + bits del personalID (Wurmple) |
| `Ninjask` | Integer | nivel (Nincada→Ninjask) |
| `Shedinja` | Integer | (no evoluciona; duplica el Pokémon si hay hueco + Poké Ball) |
| `LevelRandForm` | Integer | nivel; 1% prob. forma 1 |
| `LevelBattle` | Integer | nivel **subiendo en combate** (`battle_level_up_proc`) |

### Felicidad / belleza (any_level_up)
| Método | Param | Condición |
|---|---|---|
| `Happiness` / `HappinessMale` / `HappinessFemale` / `HappinessDay` / `HappinessNight` | — | felicidad ≥ 220 (160 con soft cap) + condición |
| `HappinessMove` | :Move | felicidad + conoce el move |
| `HappinessMoveType` | :Type | felicidad + conoce move de ese tipo |
| `HappinessHoldItem` | :Item | felicidad + lleva el item (lo consume) |
| `MaxHappiness` | — | felicidad == `MAX_HAPPINESS` |
| `Beauty` | Integer | belleza ≥ param (Feebas) |

### Items en mano (any_level_up, consumen el item)
`HoldItem`, `HoldItemMale`, `HoldItemFemale`, `DayHoldItem`, `NightHoldItem`, `HoldItemHappiness` — param `:Item`.

### Conocer movimiento / tener especie / ubicación (any_level_up)
| Método | Param | Condición |
|---|---|---|
| `HasMove` | :Move | conoce el move |
| `HasMoveRandForm` | :Move | conoce el move; 1% forma 1 |
| `HasMoveType` | :Type | conoce move de ese tipo |
| `HasInParty` | :Species | hay esa especie en el equipo |
| `Location` | Integer | está en ese map_id |
| `LocationFlag` | String | el mapa tiene ese flag |
| `Region` | Integer | está en esa región del town map |
| `Counter` | Integer | `evolution_counter` ≥ param (lo resetea) |

### Tras combate (`after_battle_proc`)
| Método | Param | Condición |
|---|---|---|
| `AfterBattleCounter` | Integer | `evolution_counter` ≥ param tras combate (resetea) |
| `AfterBattleCounterMakeReady` | Integer | marca `ready_to_evolve` (no resetea) |
| `AfterBattleCritCounter` | Integer | `crit_counter` ≥ param (Farfetch'd → Sirfetch'd) |

### Por usar item (`use_item_proc`)
`Item`, `ItemMale`, `ItemFemale`, `ItemDay`, `ItemNight`, `ItemHappiness` — param `:Item` (piedra evolutiva u otro).

### Por intercambio (`on_trade_proc`)
`Trade`, `TradeMale`, `TradeFemale`, `TradeDay`, `TradeNight` (sin param); `TradeItem` (`:Item`, consume); `TradeSpecies` (`:Species`, el otro Pokémon, sin Everstone).

### Por evento (`event_proc`) — param Integer (nº de evento)
`Event` (valor == param), `EventReady` (valor == param y `ready_to_evolve`). Disparar con `pbEvolutionEvent(numero)`. Eventos usados por defecto: 1 = Kubfu→Urshifu, 2 = Yamask Galar→Runerigus.

### Métodos NUEVOS de La Base de Sky (no vanilla)
| Método | Param | Condición |
|---|---|---|
| `CableLinkItem` | :Item | usar `:LINKINGCORD` llevando el item (lo consume) — evolución por intercambio sin intercambiar |
| `CollectItems` | :Item | tener 999 del item en la Mochila (los resta) |
| `LevelWithPartner` | Integer | nivel ≥ param y hay partner (`$PokemonGlobal.partner`) |
| `LevelUseMoveCount` | :Move | usar el move 20 veces (`evo_move_count`) |
| `LevelDefeatItsKindWithItem` | :Item | derrotar 3 de su especie llevando el item (Leader's Crest, `evo_crest_count`) |
| `LevelRecoilDamage` | Integer | acumular `evo_recoil_count` ≥ param de daño por retroceso |
| `LevelRecoilDamageForm0` | Integer | igual; además fija forma según género si era 0 |
| `LevelWalk` | Integer | caminar `evo_step_count` ≥ param pasos |

`None` = sin evolución (placeholder). Para disparar/forzar evoluciones desde código (`check_evolution_*`, `pbEvolutionEvent`), ver skill [[pokemon-api]].
