# trainer_types.txt · trainers.txt · encounters.txt · ribbons.txt · shadow_pokemon.txt

Clases: `026_PBS data/015_TrainerType.rb`, `016_Trainer.rb`, `014_Encounter.rb`, `013_Ribbon.rb`, `012_ShadowPokemon.rb`. Para crear entrenadores/combates **en código** (sin PBS) ver skill [[battles-runtime]].

---

## trainer_types.txt

Cabecera `[SIMBOLO]` (`"m"`). Ej. `[CAMPER]`, `[LEADER_Misty]`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Unnamed"` | Título visible (Mochilero, Líder de Gimnasio…). |
| `Gender` | `e` `{Male:0, Female:1, Unknown/Mixed:2}` | `2` | Género del tipo. Acepta `Male/male/M/m/0`, `Female/…/1`, `Unknown/Other/Mixed/X/2`. |
| `BaseMoney` | `u` | `30` | El jugador gana `BaseMoney × nivel del Pokémon más alto`. |
| `SkillLevel` | `u` | `= BaseMoney` | Nivel de habilidad de la IA. |
| `PokeBall` | `e` :Item | nil | Poké Ball por defecto de todos los Pokémon de este tipo. |
| `Flags` | `*s` | `[]` | Etiquetas (p.ej. para que la IA o eventos los traten distinto). |
| `IntroBGM` | `s` | nil | BGM antes del combate. |
| `BattleBGM` | `s` | nil | BGM del combate. |
| `VictoryBGM` | `s` | nil | BGM de victoria. |

Sprites: `Graphics/Trainers/SIMBOLO.png` (frontal), `SIMBOLO_back.png` (espalda, para players y partners), charset `Graphics/Characters/trainer_SIMBOLO.png`. Players usan sufijo `_outfit` (`SIMBOLO_0.png`).

```
[LEADER_Misty]
Name = Líder de Gimnasio
Gender = Female
BaseMoney = 100
BattleBGM = Battle Gym Leader
VictoryBGM = Battle victory leader
```

---

## trainers.txt

Cabecera `[TIPO,Nombre]` o `[TIPO,Nombre,Versión]` (`"esU"`). La **versión** distingue rematches (mismo tipo+nombre). Ej. `[CAMPER,Liam]`, `[TEAMROCKET_M,Grunt,1]`.

### Propiedades del entrenador (antes del primer `Pokemon =`)

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Items` | `*e` :Item | `[]` | Items que el entrenador usa en combate: `Items = FULLRESTORE,FULLRESTORE`. |
| `LoseText` | `q` | `"..."` | Frase al ser derrotado. |
| `LoseText_F` | `q` | `= LoseText` | **(Custom Sky)** Frase alternativa si el jugador es chica. |
| `Pokemon` | `ev` :Species | — | `Pokemon = ESPECIE,nivel`. Inicia un nuevo Pokémon. |

### Subpropiedades por Pokémon (SUB_SCHEMA — tras un `Pokemon =`, aplican al último)

| Campo PBS | Formato | Significado |
|---|---|---|
| `Form` | `u` | Número de forma. |
| `Name` | `s` | Apodo del Pokémon. |
| `Moves` | `*e` :Move | Movimientos exactos (si se omite, se calculan por nivel). |
| `Ability` | `e` :Ability | Habilidad concreta (por ID). |
| `AbilityIndex` | `u` | Índice de habilidad (0/1 natural, 2+ oculta). |
| `Item` | `e` :Item | Objeto en mano. |
| `Gender` | `e` `{male:0, female:1}` | Género forzado. |
| `Nature` | `e` :Nature | Naturaleza. |
| `IV` | `uUUUUU` | IVs (HP obligatorio + 5 opcionales): `IV = 20,20,20,20,20,20`. |
| `EV` | `uUUUUU` | EVs (mismo orden de stats). |
| `Happiness` | `u` | Felicidad. |
| `Shiny` | `b` | Shiny forzado. |
| `SuperShiny` | `b` | **(Custom Sky)** Super Shiny forzado. |
| `Shadow` | `b` | Pokémon Oscuro (Shadow). |
| `Ball` | `e` :Item | Poké Ball del Pokémon. |

Si no se define IV/EV/Nature, `to_trainer` los deriva: IV = `min(nivel/2, 31)`, EV = `min(nivel*3/2, 252/6)`, naturaleza pseudoaleatoria pero estable (según índice de especie + tipo de entrenador).

```
[LEADER_Brock,Brock]
Items = FULLRESTORE,FULLRESTORE
LoseText = Muy bien.
Pokemon = GEODUDE,12
    Gender = male
    Moves = DEFENSECURL,HEADSMASH,ROCKPOLISH,ROCKTHROW
    AbilityIndex = 0
    IV = 20,20,20,20,20,20
Pokemon = ONIX,14
    Name = Rocky
    Gender = male
    Shiny = true
    Item = SITRUSBERRY
    Ball = HEAVYBALL
```

`GameData::Trainer.get(tipo, nombre, versión).to_trainer` construye un `NPCTrainer` listo para combate (instancia los Pokémon, aplica formas, Mega, naturaleza, etc.). Ver skill [[battles-runtime]].

---

## encounters.txt

Clase `Encounter`. **No tiene SCHEMA estándar**: el compilador lo procesa de forma especial. Cabecera `[ID_MAPA]` o `[ID_MAPA,Versión]` (versión = `encounter_version` del juego, para alternar tablas).

Estructura: por cada tipo de encuentro, una línea `TipoEncuentro` o `TipoEncuentro,probabilidad_de_paso`, seguida de líneas indentadas `peso,ESPECIE,nivel_min[,nivel_max]`.

```
[002]
Land,21
    40,PIDGEY,11,14
    40,RATTATA,11,14
    10,SPEAROW,12,14
LandNight,21
    39,RATTATA,10,14
Water,2
    60,TENTACOOL,14,19
OldRod
    100,MAGIKARP,16,19
```

- `probabilidad_de_paso` por defecto la define el `trigger_chance` del tipo (ver enums-and-evolution.md → EncounterType).
- `peso` es relativo (la suma no tiene que ser 100).
- Tipos válidos (todos los `EncounterType`): `Land`, `LandDay/Night/Morning/Afternoon/Evening`, `PokeRadar`, `Cave(+variantes horarias)`, `Water(+variantes)`, `OldRod`, `GoodRod`, `SuperRod`, `RockSmash`, `HeadbuttLow`, `HeadbuttHigh`, `BugContest`. Lista completa + tipo interno en enums-and-evolution.md.
- Lógica runtime: `039_Battle triggering/004_Overworld_WildEncounters.rb` (`$PokemonEncounters`, `pbGenerateWildPokemon`). Ver skill [[battles-runtime]] para generar encuentros desde código.

---

## ribbons.txt

Cabecera `[SIMBOLO]` (`"m"`).

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Unnamed"` | Nombre visible. |
| `IconPosition` | `u` | `0` | Posición del icono en `Graphics/UI/ribbons.png`. |
| `Description` | `q` | `"???"` | Descripción visible. |
| `Flags` | `*s` | `[]` | Etiquetas libres. |

---

## shadow_pokemon.txt

Clase `ShadowPokemon` (`OPTIONAL = true`: el archivo puede no existir). Cabecera `[ESPECIE]` o `[ESPECIE,NumForma]` (`"eV"`). Define el moveset oscuro y el medidor del corazón.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `GaugeSize` | `v` | `4000` (`HEART_GAUGE_SIZE`) | Tamaño del medidor del corazón a purificar. |
| `Moves` | `*e` :Move | `[]` | Movimientos oscuros disponibles (los primeros `MAX_MOVES`). |
| `Flags` | `*s` | `[]` | Etiquetas libres. |

Si una especie no tiene entrada, usa `:SHADOWRUSH` como único movimiento oscuro por defecto. Mecánica runtime (makeShadow, heart_gauge, purificación) en skill [[pokemon-api]].

```
[TEDDIURSA]
GaugeSize = 3000
Moves = SHADOWRUSH,SHADOWBLAST
```
