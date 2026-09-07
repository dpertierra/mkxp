# moves.txt · items.txt · abilities.txt · types.txt

Clases: `026_PBS data/006_Move.rb`, `007_Item.rb`, `005_Ability.rb`, `004_Type.rb`.

---

## moves.txt

Cabecera `[SIMBOLO]` (`"m"`). Ej. `[FIREBLAST]`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Sin nombre"` | Nombre visible. |
| `Type` | `e` :Type | `:NONE` | Tipo del movimiento. |
| `Category` | `e` `["Physical","Special","Status"]` | `2` (Status) | 0=Physical, 1=Special, 2=Status. |
| `Power` | `u` | `0` | Potencia base (0 = no daña / variable). |
| `Accuracy` | `u` | `100` | Precisión (0 = no falla nunca). |
| `TotalPP` | `u` | `5` | PP base (sin PP Ups). |
| `Target` | `e` :Target | `:None` | A quién apunta. Valores en enums-and-evolution.md (Target). |
| `Priority` | `i` | `0` | Prioridad (-6 a +6 típico). |
| `FunctionCode` | `s` | `"None"` | Enlaza con `Battle::Move::<FunctionCode>` (efecto real en `030_Move effects`). |
| `Flags` | `*s` | `[]` | Etiquetas (ver abajo). |
| `EffectChance` | `u` | `0` | % de efecto secundario (0 = siempre, según FunctionCode). |
| `Description` | `q` | `"???"` | Descripción visible. |

**Flags habituales de moves** (consultados por el motor; se combinan libremente):
`Physical`/`Special` (si `MOVE_CATEGORY_PER_MOVE`), `Contact`, `CanProtect`, `CanMirrorMove`, `ThawsUser`, `HighCriticalHitRate`, `Biting`, `Punching`, `Sound`, `Powder`, `Bomb`, `Pulse`, `Dance`, `KingsRockUtility`, `CanMetronome`, `Recharge`, `Charge`, `TramplesMinimize`, `IgnoreSubstitute`, `Bypassismunity`, `OHKO`/`ohko...` (cualquier flag que empiece por `ohko`). El motor reconoce estos por nombre; añade los tuyos y conságuelos con `move.has_flag?("X")`.

**FunctionCodes con propiedades dinámicas** (el motor muestra valores modificados si `Settings::SHOW_MODIFIED_MOVE_PROPERTIES`): `TypeDependsOnUserIVs` (Hidden Power), `TypeAndPowerDependOnUserBerry` (Natural Gift), `TypeDependsOnUserPlate`/`Memory`/`Drive`, `TypeIsUserFirstType`, `TypeDependsOnUserOgerponForm`, `TypeDependsOnUserTaurosForm…`, `ThrowUserItemAtTarget` (Fling), `PowerHigherWithUserHP`/`PowerLowerWithUserHP`, `PowerHigherWithUserHappiness`/`PowerLowerWithUserHappiness`, `PowerHigherWithLessPP`. Estos dependen de flags de item como `Fling_N` y `NaturalGift_TIPO_N`.

```
[FIREBLAST]
Name = Llamarada
Type = FIRE
Category = Special
Power = 110
Accuracy = 85
TotalPP = 5
Target = NearOther
FunctionCode = BurnTarget
Flags = CanProtect,CanMirrorMove,CanMetronome
EffectChance = 10
Description = Ataca con una llama intensa que puede causar quemaduras.
```

---

## items.txt

Cabecera `[SIMBOLO]` (`"m"`). Ej. `[POTION]`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Unnamed"` | Nombre singular. |
| `NamePlural` | `s` | `"Unnamed"` | Nombre plural. |
| `PortionName` | `s` | nil | Nombre de una unidad (opcional). |
| `PortionNamePlural` | `s` | nil | Nombre de varias unidades (opcional). |
| `Pocket` | `v` | `1` | Bolsillo de la Mochila (índices definidos en Settings; LBdS custom en `004_Settings_Extra_Base.rb`). |
| `Price` | `u` | `0` | Precio de compra. |
| `SellPrice` | `u` | `Price / ITEM_SELL_PRICE_DIVISOR` | Precio de venta. |
| `BPPrice` | `u` | `1` | Precio en Puntos de Batalla. |
| `FieldUse` | `e` `{OnPokemon:1, Direct:2, TM:3, HM:4, TR:5}` | `0` | Uso fuera de combate. |
| `BattleUse` | `e` `{OnPokemon:1, OnMove:2, OnBattler:3, OnFoe:4, Direct:5}` | `0` | Uso en combate. |
| `Flags` | `*s` | `[]` | Etiquetas (ver abajo). |
| `Consumable` | `b` | (true si no es importante) | Si se consume al usarlo. |
| `ShowQuantity` | `b` | (true si no es importante) | Si la Mochila muestra cantidad. |
| `Move` | `e` :Move | nil | Movimiento enseñado por MT/MO/TR. |
| `Description` | `q` | `"???"` | Descripción visible. |

**Flags de items reconocidos por código** (`is_X?` correspondientes):
`Mail`, `IconMail`, `PokeBall`, `SnagBall`, `Berry`, `KeyItem`, `EvolutionStone`, `Fossil`, `Apricorn`, `TypeGem`, `Mulch`, `MegaStone` (no incluye Red/Blue Orb), `Scent`, `Unlosable`, `Healing`, `Vitamin`, `XItem`, `StatBoost`.
Flags con parámetro: `Fling_N` (potencia de Lanzamiento), `NaturalGift_TIPO_N` (tipo y potencia de Don Natural).

`is_important?` = Key Item, MT u MO → no consumible, no mostrable cantidad, no se puede tener en mano.
Icono: `Graphics/Items/SIMBOLO.png` (**48×48**). MT/MO/TR usan `machine`/`machine_hm`/`machine_tr` + tipo del move. Efecto del item: `ItemHandlers` (skill [[plugin-architecture]]).

```
[POTION]
Name = Poción
NamePlural = Pociones
Pocket = 1
Price = 200
FieldUse = OnPokemon
BattleUse = OnPokemon
Flags = Healing
Consumable = true
Description = Restaura 20 PS de un Pokémon.
```

---

## abilities.txt

Cabecera `[SIMBOLO]` (`"m"`).

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Sin nombre"` | Nombre visible. |
| `Description` | `q` | `"???"` | Descripción visible. |
| `Flags` | `*s` | `[]` | Etiquetas libres. |

El **efecto real** de la habilidad NO está en PBS: vive en `034_Other battle code/009_Battle_AbilityEffects.rb` (y la IA en `033_AI MoveEffects`). Registrar un efecto nuevo = añadir a `Battle::AbilityEffects.<Hook>.add(:MIABILIDAD, proc {...})`. Ver skill [[essentials-codemap]].

```
[OVERGROW]
Name = Espesura
Description = Potencia los movimientos de tipo Planta cuando le quedan pocos PS.
```

---

## types.txt

Cabecera `[SIMBOLO]` (`"m"`). Ej. `[FIRE]`.

| Campo PBS | Formato | Default | Significado |
|---|---|---|---|
| `Name` | `s` | `"Sin nombre"` | Nombre visible. |
| `IconPosition` | `u` | `0` | Fila del icono en `Graphics/UI/types.png` (icono 64×28). |
| `IsSpecialType` | `b` | `false` | Si es tipo especial (afecta a la categoría físico/especial cuando `MOVE_CATEGORY_PER_MOVE` está off). |
| `IsPseudoType` | `b` | `false` | Tipo "pseudo" (p.ej. para mecánicas especiales, no aparece como tipo normal). |
| `Weaknesses` | `*m` | `[]` | Tipos ante los que este tipo es **débil** (recibe ×2). |
| `Resistances` | `*m` | `[]` | Tipos que **resiste** (recibe ×0.5). |
| `Immunities` | `*m` | `[]` | Tipos ante los que es **inmune** (×0). |
| `Flags` | `*s` | `[]` | Etiquetas libres. |

Eficacias (módulo `Effectiveness`): INEFFECTIVE=0 (×0), NOT_VERY_EFFECTIVE=1 (×0.5), NORMAL=2 (×1), SUPER=4 (×2), HYPER=8 (×4). Multiplicador = valor/2.

`Weaknesses`/`Resistances`/`Immunities` se leen desde el punto de vista **del defensor**: lista los tipos atacantes. Para "Fuego es débil a Agua" → en `[FIRE]`, `Weaknesses = WATER,GROUND,ROCK`.

```
[FIRE]
Name = Fuego
IconPosition = 9
Weaknesses = WATER,GROUND,ROCK
Resistances = FIRE,GRASS,ICE,BUG,STEEL,FAIRY
Immunities =
```
