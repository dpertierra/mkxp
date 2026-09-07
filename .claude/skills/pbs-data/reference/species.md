# Especies: pokemon.txt · pokemon_forms.txt · pokemon_metrics.txt

Clases: `026_PBS data/009_Species.rb`, `011_SpeciesMetrics.rb`. El SCHEMA de Species es **dinámico**: `self.schema(compiling_forms)` devuelve un set distinto para `pokemon.txt` (base) y para `pokemon_forms.txt` (formas). Abajo se indica en qué archivo aplica cada campo.

## pokemon.txt — campos (en orden del SCHEMA)

Cabecera: `[SIMBOLO]` (`"m"`). Ej. `[BULBASAUR]`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Unnamed"` | Nombre visible. Solo en pokemon.txt (no en formas). |
| `FormName` | `q` | nil | Nombre de la forma (texto visible). |
| `Types` | `*e` :Type | `[:NORMAL]` | 1 o 2 tipos: `Types = GRASS,POISON`. |
| `BaseStats` | `vvvvvv` | todos 1 | 6 enteros: **HP,Atk,Def,Spd,SpAtk,SpDef**. ¡Velocidad 4ª! |
| `GenderRatio` | `e` :GenderRatio | `:Female50Percent` | Solo pokemon.txt. Valores en enums-and-evolution.md. |
| `GrowthRate` | `e` :GrowthRate | `:Medium` | Solo pokemon.txt. Medium/Erratic/Fluctuating/Parabolic/Fast/Slow. |
| `BaseExp` | `v` | `100` | Exp base al derrotarlo. |
| `EVs` | `*ev` :Stat | `{}` (0) | Pares STAT,cantidad: `EVs = SPECIAL_ATTACK,1`. |
| `CatchRate` | `u` | `255` | 0–255. |
| `Happiness` | `u` | `70` | Felicidad base 0–255. |
| `Abilities` | `*e` :Ability | `[]` | Hasta 2 naturales: `Abilities = OVERGROW,CHLOROPHYLL` (índices 0 y 1). |
| `HiddenAbilities` | `*e` :Ability | `[]` | Ocultas (índices 2+). |
| `Moves` | `*ie` (int, :Move) | `[]` | Pares nivel,MOVE: `Moves = 1,TACKLE,3,VINEWHIP`. Nivel 0 = se conoce al nacer. |
| `TutorMoves` | `*e` :Move | `[]` | Movimientos por MT/MO/Tutor. |
| `EggMoves` | `*e` :Move | `[]` | Movimientos huevo. |
| `EggGroups` | `*e` :EggGroup | `[:Undiscovered]` | Grupos huevo (cría). |
| `HatchSteps` | `v` | `1` | Pasos para eclosionar el huevo. |
| `Incense` | `e` :Item | nil | Solo pokemon.txt. Incienso que un padre debe llevar para producir esta cría. |
| `Offspring` | `*s` (pokemon.txt) / `*e` :Species (forms) | `[]` | Especies posibles de la cría. Vacío = solo esta especie. |
| `Height` | `f` | `1` | Altura en metros (`0.7`). Internamente se guarda ×10 (decímetros). |
| `Weight` | `f` | `1` | Peso en kg (`6.9`). Internamente ×10 (hectogramos). |
| `Color` | `e` :BodyColor | `:Red` | Color para búsqueda en Pokédex. |
| `Shape` | `e` :BodyShape | `:Head` | Forma corporal. |
| `Habitat` | `e` :Habitat | `:None` | Hábitat. |
| `Category` | `s` | `"???"` | "Categoría" Pokédex (`Semilla`). |
| `Pokedex` | `q` | `"???"` | Texto de la entrada Pokédex. |
| `Generation` | `i` | `0` | Generación de debut. |
| `Flags` | `*s` | `[]` | Etiquetas libres (ver más abajo). |
| `WildItemCommon` | `*e` :Item | `[]` | Item llevado en salvaje (común). Si hay varios, uno al azar. |
| `WildItemUncommon` | `*e` :Item | `[]` | Item llevado (poco común). |
| `WildItemRare` | `*e` :Item | `[]` | Item llevado (raro). |
| `Evolutions` | `*ses` / `*ees` | `[]` | Forma compilada; **no se escribe a mano**. |
| `Evolution` | `^seS` / `^eeS` | — | Línea repetible: `Evolution = ESPECIE,MÉTODO,PARÁMETRO`. Métodos en enums-and-evolution.md. |
| `HideFromDex` | `b` | `false` | Oculta la especie en la Pokédex. |
| `SuperShinyHue` | `*i` | `[]` | **(Custom Sky)** Tonos (hue 0–360) para la versión Super Shiny, separados por coma. |

## pokemon_forms.txt — campos extra/diferentes

Cabecera: `[ESPECIE,NumForma]` (`"ev"`). Ej. `[CHARIZARD,1]`. Solo se escriben los campos que **difieren** de la forma base; lo no definido se hereda de la especie base.

Campos **adicionales** que solo existen en pokemon_forms.txt:

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `PokedexForm` | `u` | = NumForma | Qué forma se muestra en la Pokédex para esta entrada. |
| `Region` | `q` | `""` | Nombre de región de debut (pensado para formas regionales). |
| `MegaStone` | `e` :Item | nil | Piedra que activa esta Mega forma. |
| `MegaMove` | `e` :Move | nil | Movimiento que activa esta Mega forma (alternativa a la piedra, p.ej. Rayquaza). |
| `UnmegaForm` | `i` | `-2` | Forma a la que revierte al terminar el combate. `-2` = vuelve a la forma previa. |
| `MegaMessage` | `u` | `0` | 0 = mensaje Mega estándar; 1 = mensaje tipo Rayquaza. |

En pokemon_forms.txt, `GenderRatio`, `GrowthRate`, `Name` e `Incense` **no** están en el SCHEMA (se heredan de la base). `Offspring` y `Evolution(s)` usan enums de Species en vez de strings.

## Flags conocidos de Species (campo `Flags`)

Reconocidos por código (mayúsculas/minúsculas indistintas vía `has_flag?`):
- `DefaultForm_N` — la forma por defecto de la especie es la N (no la 0).
- `ExcludeFromBaseForms` — no cuenta como "forma base" (listados, Pokédex de formas).
- `cantMegaEvolve` — bloquea Mega Evolución de esta forma.
- `Paradox` — marca Pokémon Paradoja (Booster Energy lo trata como inseparable).
- `UltraBeast` — Ultrente; altera tasas de captura (Beast Ball ×5, resto ×0.1).
- Otros flags libres que tu propio código puede consultar con `species_data.has_flag?("MiFlag")`.

## Mega Evolución (resumen de datos)

Una Mega forma es una **forma alternativa** en pokemon_forms.txt con `MegaStone` (o `MegaMove`). El código (`mega_form?`, `getMegaForm`) la detecta. La lógica runtime (makeMega/makeUnmega) está en la skill [[pokemon-api]].

## pokemon_metrics.txt — posiciones de sprite en batalla

Clase `SpeciesMetrics`. Ajusta dónde se dibuja el sprite en combate y su sombra. Cabecera `[ESPECIE]` o `[ESPECIE,NumForma]` (`"eV"`).

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `BackSprite` | `ii` | `[0,0]` | Offset (x,y) del sprite de espalda (Pokémon del jugador). |
| `FrontSprite` | `ii` | `[0,0]` | Offset (x,y) del sprite frontal (rival). |
| `FrontSpriteAltitude` | `i` | `0` | Altura sobre el suelo del sprite frontal (lo sube). |
| `ShadowX` | `i` | `0` | Offset horizontal de la sombra. |
| `ShadowSize` | `u` | `2` | Tamaño de sombra (índice del gráfico de sombra). |

Los offsets se **duplican** al aplicarse (multiplicados ×2). Si una forma >0 no tiene su propia entrada, hereda la de la especie base.

## Sprites y gráficos (convención de nombres)

`GameData::Species.check_graphic_file` busca el PNG combinando especie/forma/género/shiny/shadow. Carpetas (`Graphics/Pokemon/`):
- `Front/`, `Back/`, `Icons/`, `Front shiny/`, `Back shiny/`, `Icons shiny/`, `Shadow/`, `Footprints/`, `Eggs/`.
- **(Custom Sky)** super shiny: subcarpetas `Front supershiny/`, `Back supershiny/`, `Icons supershiny/`. Si no existe el gráfico dedicado, usa el sprite **normal** (no el shiny) como base y le aplica el hue de `SuperShinyHue`.
- Nombre base: `ESPECIE` (símbolo), con sufijos `_NumForma`, `_female`, `_shadow`. Fallback en cascada hasta `000`.
- Cries: `Audio/SE/Cries/ESPECIE.wav` (o `ESPECIE_NumForma`). Detalle de assets en skill [[assets-pipeline]].

## Ejemplo real

```
[BULBASAUR]
Name = Bulbasaur
Types = GRASS,POISON
BaseStats = 45,49,49,45,65,65
GenderRatio = FemaleOneEighth
GrowthRate = Parabolic
BaseExp = 64
EVs = SPECIAL_ATTACK,1
CatchRate = 45
Happiness = 50
Abilities = OVERGROW
HiddenAbilities = CHLOROPHYLL
Moves = 1,TACKLE,1,GROWL,3,VINEWHIP
TutorMoves = AMNESIA,ATTRACT
EggMoves = AMNESIA,CHARM
EggGroups = Monster,Grass
HatchSteps = 5120
Height = 0.7
Weight = 6.9
Color = Green
Shape = Quadruped
Habitat = Grassland
Category = Semilla
Pokedex = Nada más nacer, ya tiene una semilla en el lomo.
Generation = 1
Evolution = IVYSAUR,Level,16
```

Forma alternativa (pokemon_forms.txt):
```
[CHARIZARD,1]
FormName = Mega-Charizard X
Types = FIRE,DRAGON
BaseStats = 78,130,111,100,130,85
MegaStone = CHARIZARDITEX
Abilities = TOUGHCLAWS
```
