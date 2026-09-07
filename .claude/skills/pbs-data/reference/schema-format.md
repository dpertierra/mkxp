# Códigos de formato del SCHEMA

Cada entrada de un `SCHEMA` es `"ClavePBS" => [:attr, "formato", *args]`. El string `"formato"` describe, carácter a carácter, qué espera el compilador después del `=`. Extraído 1:1 de los `SCHEMA` reales de `026_PBS data/`. El parser canónico es `065_Compiler/` (`SchemaHelper`/`pbGetCsvRecord`).

## Caracteres individuales

| Código | Significado | Ejemplo de valor PBS |
|---|---|---|
| `i` | Entero **con signo** (Integer, admite negativos). | `Priority = -6` |
| `u` | Entero **sin signo** ≥ 0. | `Power = 90` |
| `v` | Entero **positivo** ≥ 1 (no cero). | `TotalPP = 5` |
| `f` | Número decimal (Float). | `Height = 0.7` |
| `x` | Número hexadecimal. | (raro en estos PBS) |
| `s` | String de una "celda": texto hasta la siguiente coma. | `Name = Bulbasaur` |
| `q` | String del **resto de la línea** (puede contener comas, puntuación). | `Description = Ataca y puede paralizar.` |
| `m` | Símbolo/identificador → se convierte a `Symbol`. **No** valida contra GameData. | `[BULBASAUR]` |
| `n` | Nombre tipo nombre-de-archivo (sin comas). | (BGM, charsets) |
| `b` | Booleano. Acepta `true`/`false`, `yes`/`no`, etc. | `Consumable = true` |
| `e` | **Enum.** El 3.er elemento del array define los valores válidos (ver abajo). | `Type = FIRE` |

**Mayúscula = opcional.** `I`, `U`, `V`, `S`, `N` (y `Q` donde aplique) significan "puede ir en blanco" → produce `nil`. Por eso en `"uUUUUU"` (IV/EV) el primer stat es obligatorio y los otros cinco opcionales.

## El código `e` (enum) y sus tres formas

El 3.er (y a veces 4.º) elemento del array de SCHEMA dice de dónde salen los valores:

1. **Símbolo de GameData** → `["e", :Type]`: el valor debe ser un ID existente en `GameData::Type`. El compilador aborta si no existe. Ej.: `Type = FIRE`, `Ability = OVERGROW`, `Move = TACKLE`, `Item = POTION`, `Nature = ADAMANT`.
2. **Array de strings** → `["e", ["Physical", "Special", "Status"]]`: el valor escrito se mapea a su **índice** (0, 1, 2…). Ej. `Category = Special` → guarda `1`.
3. **Hash** → `["e", {"male"=>0, "Male"=>0, "m"=>0, ...}]`: el valor escrito se mapea al **valor del hash**. Ej. `Gender = female` → guarda `1`. (Trainer Gender, TrainerType Gender, Item FieldUse/BattleUse.)

## Prefijos de repetición

| Prefijo | Significado |
|---|---|
| `*` | El patrón que sigue se **repite, separado por comas, en una sola línea** → produce un Array. |
| `^` | La clave puede aparecer en **varias líneas**; cada línea es un sub-registro y se acumulan en un Array. |

Ejemplos de listas (`*`):
- `"*s"` → lista de strings: `Flags = Consumable,Fling_30`.
- `"*m"` → lista de símbolos: `Weaknesses = WATER,GROUND,ROCK`.
- `"*e", :Type` → lista de enums GameData: `Types = GRASS,POISON`.
- `"*i"` → lista de enteros: `SuperShinyHue = 120,200`.
- `"*ev", :Stat` → lista de **pares** (enum, valor): `EVs = SPECIAL_ATTACK,1,SPEED,2`.
- `"*ie", nil, :Move` → lista de **pares** (entero, enum): `Moves = 1,TACKLE,3,GROWL` (nivel,MOVE).
- `"*ees", :Species, :Evolution` → tríos (enum, enum, string): forma de `Evolutions` ya compilada.

Ejemplos de líneas repetidas (`^`):
- `"^q"` (phone.txt) → cada línea `Intro = ...` es una opción posible.
- `"^uusSUUUU"` (town_map Point) → cada línea `Point = ...` es un punto del mapa.
- `"^eeS", :Species, :Evolution` / `"^seS", nil, :Evolution` (Species `Evolution`) → cada línea `Evolution = ESPECIE,MÉTODO,PARÁMETRO`.
- `"^um"` (dungeon_tilesets `Tile`/`Autotile`) → cada línea es (entero, símbolo).

## Patrones compuestos (varios códigos juntos = varias celdas)

Cuando el formato son varias letras sin `*`/`^`, son celdas fijas separadas por comas en esa línea:

| Formato | Dónde | Significado de cada celda |
|---|---|---|
| `vvvvvv` | Species `BaseStats` | 6 enteros positivos: HP, Atk, Def, **Spd**, SpAtk, SpDef |
| `uUUUUU` | Trainer `IV`/`EV` | 1.º obligatorio + 5 opcionales (mismo orden de stats) |
| `vuuu` | Metadata/PlayerMetadata `Home` | map_id, x, y, dirección |
| `vuu` | MapMetadata `HealingSpot` | map_id, x, y |
| `uuu` | MapMetadata `MapPosition` | región, x, y (en el town map) |
| `eu`, :Weather | MapMetadata `Weather` | tipo de clima (enum) + intensidad/probabilidad (0–100) |
| `us` | MapMetadata `MapSize` | ancho (celdas) + string de cuadrícula |
| `ii` | SpeciesMetrics `BackSprite`/`FrontSprite` | offset x, offset y |
| `v`,`uv` | BerryPlant `HoursPerStage`/`Yield` | ver `reference/metadata-misc.md` |

## Cabeceras (`SectionName`) especiales

El `SectionName` no siempre es un símbolo simple; su formato varía por clase:

- `"m"` → símbolo simple: `[BULBASAUR]`, `[POTION]`, `[FIRE]`.
- `"u"` → número: `[5]` (map_metadata, metadata, player_metadata, town_map).
- `"ev", :Species` (compilando formas) → `[ESPECIE,NumForma]`: `[CHARIZARD,1]`.
- `"eV", :Species` (species_metrics, shadow_pokemon) → `[ESPECIE]` o `[ESPECIE,NumForma]` (forma opcional).
- `"esU", :TrainerType` (trainers) → `[TIPO,Nombre]` o `[TIPO,Nombre,Versión]`.
- `"mV"` (dungeon_parameters) → `[AREA]` o `[AREA,Versión]`.
- `"q"` (phone) → texto libre o `[TIPO,Nombre,Versión]`.

## Notas de lectura inversa (`get_property_for_PBS`)

Varias clases definen `get_property_for_PBS` para **escribir** de vuelta el PBS (Debug → "Write all PBS"). Ahí se ve qué valores se omiten por ser el default (p.ej. `SkillLevel` no se escribe si es igual a `BaseMoney`; `Power`/`Priority`/`EffectChance` no se escriben si son 0). Útil para saber qué es "valor por defecto" sin leer el `initialize`.
