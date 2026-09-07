# Reference · Integración entre efectos + scoring de IA

Esta es la capa que hace que un efecto custom quede **totalmente integrado**: que guarde su estado, que
coexista con el resto de mecánicas y que la IA lo entienda. Fuentes: `034_Other battle code/002_PBEffects.rb`,
`029_Battler/` (aplicación de efectos), `032_AI/` (motor de IA) y `033_AI MoveEffects/` (scores por
function code).

## 1. Estado volátil custom — `PBEffects`

Los efectos que duran dentro del combate se guardan en hashes de "effects" indexados por constantes del
módulo `PBEffects` (`034/002`). Hay **cuatro ámbitos**:

- **Battler** (`battler.effects[PBEffects::X]`) — constantes `0..133` (Confusion, Substitute, Taunt,
  FlashFire, ProtectRate, TwoTurnAttack…).
- **Posición** (`battler.pbOwnSide`/posición) — `700+` (FutureSight, Wish…).
- **Lado** (`battler.pbOwnSide.effects[...]` / `pbOpposingSide`) — `800+` (Reflect, LightScreen, Spikes,
  Tailwind, SeaOfFire…).
- **Campo / batalla** (`battle.field.effects[...]`) — `900+` (TrickRoom, Gravity, Terrain rooms…).

Para un efecto **nuevo**, reabre el módulo en tu plugin `[ZBOX]` con un valor libre (NO edites `Data/`):

```ruby
module PBEffects
  MiEfecto = 2000   # entero libre que no choque con los del motor
end
```
Inicialízalo donde el motor inicializa los demás (con un `alias_method`, R5) o trátalo como `nil`/`0` por
defecto leyendo con seguridad (R6). Lee/escribe: `battler.effects[PBEffects::MiEfecto] = 3` y decrementa
en fin de ronda si es un contador. Para ámbito de lado/campo, usa el hash correspondiente.

> Nota: tras un cambio de Pokémon, los effects de battler se resetean (salvo los marcados como
> persistentes). Si tu efecto debe sobrevivir al cambio, gestiónalo en `OnSwitchOut`/`OnSwitchIn`.

## 2. Interacción con otros efectos (qué respetar)

- **Inmunidad por tipo** — un movimiento de daño la aplica solo el motor; un movimiento de **estado**
  debe comprobar inmunidad en `pbFailsAgainstTarget?` (p.ej. no envenenar a tipo Acero/Veneno: usa los
  helpers `target.pbCanPoison?`, `pbCanBurn?`, `pbCanSleep?`, etc., que ya contemplan tipos, Veladura,
  Cuerpo Llama, etc.).
- **Substitute** — efectos adicionales sobre el objetivo: `return if target.damageState.substitute`.
  Si el movimiento debe atravesar Sustituto: `def ignoresSubstitute?(user); return true; end`.
- **Escudo Polvo (Shield Dust)** — corta efectos secundarios sobre el objetivo: comprueba
  `target.affectedByAdditionalEffects?` antes de aplicar en `pbAdditionalEffect`. Si el efecto secundario
  es sobre el **usuario**, marca `def additionalEffectAffectsUser?; return true; end` para que no lo corte.
- **Mold Breaker** — si tu habilidad defensiva debe ignorarse, usa el trigger normal; si NO, usa la
  variante `...NonIgnorable`. En movimientos, `target.hasMoldBreaker?`/`target.beingMoldBroken?`.
- **Magic Bounce / Magic Coat** — movimientos de estado que rebotan: `def canMagicCoat?; return true; end`.
- **Snatch** — movimientos auto-dirigidos robables: `def canSnatch?; return true; end`.
- **Protect-likes** — heredando de `ProtectMove` ya se gestiona la tasa de fallo consecutivo.
- **Clima / terreno** — para iniciar, hereda de `WeatherMove`/`TerrainMove`; para reaccionar, usa
  triggers `OnWeatherChange`/`EndOfRoundWeather`. Comprueba climas "fuertes" (HarshSun, HeavyRain,
  StrongWinds) que no se pueden sustituir.
- **Prioridad** — fija `priority` en `moves.txt`, o modula con `pbPriority(user)` / el trigger
  `PriorityChange` de habilidad.
- **Supresión de habilidad** — usa siempre `battler.hasActiveAbility?(:X)` (no `battler.ability == :X`)
  para respetar Gas Reactivo, Bilis, etc.

## 3. Scoring de IA — que la IA lo use y reaccione

Constantes (`032_AI/006_AI_ChooseMove.rb`, namespace `Battle::AI`):
`MOVE_FAIL_SCORE = 20`, `MOVE_USELESS_SCORE = 60`, `MOVE_BASE_SCORE = 100`. Mayor score = más probable
que la IA elija ese movimiento.

Los handlers viven en `module Battle::AI::Handlers` (`032_AI/002_Battle_AI.rb`):

| HandlerHash | Para qué | Clave |
|---|---|---|
| `MoveFailureCheck` | ¿el movimiento fallará? (lo descarta) | function code (String) |
| `MoveFailureAgainstTargetCheck` | ¿fallará contra ese objetivo? | function code |
| `MoveEffectScore` | puntuar el efecto del movimiento (general) | function code |
| `MoveEffectAgainstTargetScore` | puntuar el efecto contra un objetivo | function code |
| `MoveBasePower` | poder estimado para movimientos de poder variable | function code |
| `GeneralMoveScore` | ajuste de score aplicable a todos | (id libre) |
| `ShouldSwitch` / `ShouldNotSwitch` | decisiones de cambio | (id libre) |
| `AbilityRanking` | **cómo valora la IA una habilidad** (propia y rival) | símbolo de habilidad |
| `ItemRanking` | **cómo valora la IA un objeto** | símbolo de objeto |

### Movimientos: registrar score

```ruby
# Para un movimiento que sube el Ataque del usuario:
Battle::AI::Handlers::MoveFailureCheck.add("RaiseUserAttack1",
  proc { |move, user, ai, battle|
    next move.statusMove? &&
         !user.battler.pbCanRaiseStatStage?(move.move.statUp[0], user.battler, move.move)
  }
)
Battle::AI::Handlers::MoveEffectScore.add("RaiseUserAttack1",
  proc { |score, move, user, ai, battle|
    next ai.get_score_for_target_stat_raise(score, user, move.move.statUp)
  }
)
```
Firmas de proc:
- `MoveFailureCheck` → `|move, user, ai, battle|` (devuelve `true` si fallaría).
- `MoveEffectScore` → `|score, move, user, ai, battle|` (devuelve score ajustado).
- `MoveEffectAgainstTargetScore` → `|score, move, user, target, ai, battle|`.

La clave es el **function code** como String (igual que el nombre de la clase del movimiento). Reusa con
`.copy("FunctionCodeOrigen", "MiCode")` cuando tu movimiento puntúe igual que otro.

Helpers de IA útiles (en `032_AI/`, sobre `ai`/`move`/`user`/`target`): `ai.get_score_for_target_stat_raise`,
`ai.get_score_for_target_stat_drop`, `move.rough_damage`, `move.damagingMove?`, `target.hp`,
`user.battler.pbCanRaiseStatStage?`. Grep en `033_AI MoveEffects/` un function code parecido al tuyo y
copia su lógica de score (es la forma más fiable).

### Habilidades / objetos: que la IA reaccione

Para que la IA **juegue alrededor** de una habilidad/objeto nuevo (del rival o propio), registra su
ranking (handlers en `032_AI/009_AI_Utilities.rb`):

```ruby
Battle::AI::Handlers::AbilityRanking.add(:MIHABILIDAD,
  proc { |ability, score, ai, battler|
    next score + 10   # cuánto "vale" tener esta habilidad
  }
)
Battle::AI::Handlers::ItemRanking.add(:MIOBJETO,
  proc { |item, score, ai, battler|
    next score + 8
  }
)
```
Esto alimenta decisiones como elegir Mega/objeto y estimar amenazas. Para reacciones finas (p.ej. "no uses
movimientos de fuego contra un Pokémon con Absorbe Fuego"), la IA ya consulta los triggers de
`AbilityEffects`/`ItemEffects` al estimar daño y fallo: si implementaste bien la **capa mecánica** (1 y 2),
la IA hereda gran parte de la reacción automáticamente. El ranking afina el resto.

## Checklist final de "totalmente integrado"

1. Mecánica: hook/trigger correcto, con `_INTL` y `next`.
2. PBS: entrada en moves/abilities/items.txt ([[pbs-data]]).
3. Estado custom (si aplica): `PBEffects::MiEfecto` reabierto en el plugin.
4. Interacción: inmunidades, Substitute/Escudo Polvo, Mold Breaker (NonIgnorable), Magic Coat/Snatch
   según diseño, `hasActiveAbility?` para supresión.
5. IA: `MoveEffectScore` (+`MoveFailureCheck`) para movimientos; `AbilityRanking`/`ItemRanking` para
   habilidades/objetos.
6. Empaquetado `[ZBOX]` ([[plugin-architecture]]) sin tocar `Data/`.
7. Pasos de verificación manual para el usuario (probar en combate, mirar `errorlog.txt`).
