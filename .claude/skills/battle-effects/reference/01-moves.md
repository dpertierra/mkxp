# Reference · Movimientos (function codes y hooks)

Fuentes: `030_Move/002_Battle_Move.rb` (base + resolución), `003_Move_Usage.rb` y
`004_Move_UsageCalculations.rb` (pipeline de uso/daño), `005_Move_BaseEffects.rb` (clases base
reutilizables), `006-014_MoveEffects_*.rb` (los function codes concretos del motor — úsalos de
plantilla).

## Cómo un movimiento obtiene su efecto

El campo `FunctionCode` de `moves.txt` se resuelve a una clase (`Battle::Move.from_pokemon_move`):

- code que **empieza por dígito** → `Battle::Move::Effect<code>` (p.ej. `FunctionCode = 0F4` → `Effect0F4`).
- code alfabético → `Battle::Move::<code>` (p.ej. `FunctionCode = HealUserFully` → `Battle::Move::HealUserFully`).
- si la clase no existe → `Battle::Move::Unimplemented` (los de daño solo pegan; los de estado fallan).

Por eso un movimiento custom = **una entrada en `moves.txt`** con `FunctionCode = MiCode` + **una clase**
`class Battle::Move::MiCode < Battle::Move` en un plugin `[ZBOX]`. El nombre de la clase ES el function
code (sin prefijo). No reutilices como function code los nombres de las clases base de `005` (ese archivo
lo advierte en su cabecera).

```ruby
# Plugin [ZBOX]: un movimiento que cura 1/2 de los PS máximos del usuario.
class Battle::Move::HealUserHalfOfTotalHP < Battle::Move::HealingMove
  def pbHealAmount(user)
    return (user.totalhp / 2.0).round
  end
end
```
En `moves.txt`: `FunctionCode = HealUserHalfOfTotalHP`.

## Hooks override (qué redefinir y cuándo dispara)

La clase base define muchos métodos vacíos/por defecto pensados para sobreescribir. Los más usados, en
orden aproximado de ejecución dentro de un turno:

**Antes de usar / fallos**
- `pbOnStartUse(user, targets)` — al empezar a usar el movimiento, antes de nada. Inicializa estado del
  turno (p.ej. `@calcCategory`, banderas).
- `pbMoveFailed?(user, targets)` — devuelve `true` para fallar el movimiento entero (suele mostrar
  "¡Pero ha fallado!"). Para condiciones globales (sin ally, ya activo, clima incompatible…).
- `pbMoveFailedLastInRound?(user, showMessage=true)` — para Protect-likes (falla si no va último).
- `pbFailsAgainstTarget?(user, target, show_message)` — falla **contra ese objetivo** concreto
  (devuelve `true`). Para inmunidades específicas o estados no aplicables.
- `pbAddTarget(targets, user)` / `pbModifyTargets(targets, user)` — añadir/cambiar objetivos (Counter,
  Dragon Darts…).

**Tipo, poder, daño**
- `pbBaseType(user)` — tipo real del movimiento (para tipos dinámicos).
- `pbBaseDamage(baseDmg, user, target)` / `pbBaseDamageMultiplier(mult, user, target)` — modular el poder.
- `pbCalcType(user)` y `pbCalcTypeModSingle(moveType, defType, user, target)` — efectividad custom.
- `pbNumHits(user, targets)` — número de golpes (multi-hit).
- `pbCritialOverride(user, target)` — fuerza (`1`) o impide (`-1`) crítico; `0` = normal.
- `pbFixedDamage(user, target)` — daño fijo (heredando de `FixedDamageMove`).

**Efectos (el grueso)**
- `pbEffectGeneral(user)` — efecto que ocurre **una vez**, no ligado a un objetivo (clima, estado de
  campo, subir stats propios…).
- `pbEffectAgainstTarget(user, target)` — efecto **por cada objetivo** afectado (status moves, bajar
  stats del rival…).
- `pbAdditionalEffect(user, target)` — efecto **secundario probabilístico** (el `effect_chance` del
  PBS); respeta Escudo Polvo/Substitute. Comprueba `target.affectedByAdditionalEffects?`.
- `pbEffectWhenDealingDamage(user, target)` — justo al causar daño.
- `pbEffectAfterAllHits(user, target)` — tras todos los golpes (recoil, Struggle…).
- `pbEndOfMoveUsageEffect(user, targets, numHits, switchedBattlers)` — al final del uso.

**Banderas/propiedades** (redefinir devolviendo `true`/`false`)
- `ignoresSubstitute?(user)`, `worksWithNoTargets?`, `canMagicCoat?`, `canSnatch?`,
  `additionalEffectAffectsUser?` (si el efecto secundario es sobre el usuario; Escudo Polvo no lo corta),
  `healingMove?`, `recoilMove?`, `chargingTurnMove?`, `multiHitMove?`, `nonLethal?(user, target)`
  (False Swipe), `hitsFlyingTargets?`/`hitsDiggingTargets?`/`hitsDivingTargets?`.
- Banderas que en realidad leen `flags` del PBS (no redefinir, **ponerlas en `moves.txt`**):
  `contactMove?` (`Contact`), `canProtectAgainst?` (`CanProtect`), `soundMove?` (`Sound`),
  `punchingMove?` (`Punching`), `bitingMove?` (`Biting`), `bombMove?` (`Bomb`), `pulseMove?` (`Pulse`),
  `danceMove?` (`Dance`), `slicingMove?` (`Slicing`), `windMove?` (`Wind`), `powderMove?` (`Powder`),
  `highCriticalRate?` (`HighCriticalHitRate`), `thawsUser?` (`ThawsUser`).

## Clases base reutilizables (`005_Move_BaseEffects.rb`)

Hereda de estas en vez de reimplementar. **No** uses estos nombres como function code; son superclases.
La subclase concreta suele fijar sus instance vars (`@statUp`, `@statDown`, `@weatherType`, etc.) en su
propio `initialize` (o ya viene seteado por el function code base del motor del que copies).

- `Battle::Move::StatUpMove` — sube **una** stat propia. Usa `@statUp = [:ATTACK, 2]`. Implementa
  `pbMoveFailed?`/`pbEffectGeneral`/`pbAdditionalEffect`.
- `Battle::Move::MultiStatUpMove` — sube **varias** stats propias. `@statUp = [:ATTACK, 1, :DEFENSE, 1]`.
- `Battle::Move::StatDownMove` — baja stats propias (al causar daño).
- `Battle::Move::TargetStatDownMove` — baja **una** stat del objetivo. `@statDown = [:SPEED, 1]`.
  (`canMagicCoat?` ya devuelve `true`.)
- `Battle::Move::TargetMultiStatDownMove` — baja varias del objetivo (gestiona Mirror Armor/Contrary).
- `Battle::Move::FixedDamageMove` — daño fijo: implementa `pbFixedDamage(user, target)`.
- `Battle::Move::HealingMove` — cura al usuario: implementa `pbHealAmount(user)`. `healingMove?`/
  `canSnatch?` ya son `true`; `pbMoveFailed?` ya falla a PS llenos.
- `Battle::Move::RecoilMove` — daño de retroceso: implementa `pbRecoilDamage(user, target)`
  (ya exime a Roca Cabeza y a quien no recibe daño indirecto).
- `Battle::Move::ProtectMove` — Protect-likes. Fija `@effect` (un `PBEffects::...`) y opcional
  `@sidedEffect`. Gestiona la tasa de fallo por uso consecutivo.
- `Battle::Move::WeatherMove` — inicia clima: fija `@weatherType` (p.ej. `:Rain`).
- `Battle::Move::TerrainMove` — inicia terreno: fija `@terrainType`.
- `Battle::Move::TwoTurnMove` — dos turnos (carga + ataque). Sobrescribe
  `pbChargingTurnMessage`/`pbAttackingTurnEffect`/`pbChargingTurnEffect`; respeta Hierba Única
  (`POWERHERB`).
- `Battle::Move::PledgeMove` — movimientos combinados tipo Pledge.

## Patrones frecuentes

```ruby
# 1) Movimiento de daño que SIEMPRE baja la Velocidad del objetivo (no probabilístico):
class Battle::Move::LowerTargetSpeed1 < Battle::Move::TargetStatDownMove
  def initialize(battle, move); super; @statDown = [:SPEED, 1]; end
end

# 2) Movimiento de estado que duerme al objetivo si no es inmune:
class Battle::Move::SleepTarget < Battle::Move
  def canMagicCoat?; return true; end
  def pbFailsAgainstTarget?(user, target, show_message)
    return !target.pbCanSleep?(user, show_message, self)
  end
  def pbEffectAgainstTarget(user, target)
    target.pbSleep
  end
end

# 3) Daño con efecto secundario al 30% de quemar (effect_chance=30 en moves.txt):
class Battle::Move::BurnTargetChance < Battle::Move
  def pbAdditionalEffect(user, target)
    return if target.pbHasType?(:FIRE)
    target.pbBurn(user) if target.pbCanBurn?(user, false, self)
  end
end
```
Para el daño base, naturaleza física/especial, efectividad y demás cálculos automáticos NO hay que hacer
nada: el motor los aplica según `Type`/`Category`/`Power` del PBS. Solo se redefine lo que se desvía del
comportamiento estándar.

Para el **scoring de IA** de cada movimiento nuevo, ver `04-integration-and-ai.md`.
