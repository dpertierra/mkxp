# Reference · Habilidades en combate (`Battle::AbilityEffects`)

Fuente: `034_Other battle code/009_Battle_AbilityEffects.rb`. Una habilidad custom NO es una clase: es
uno o más **handlers** registrados en los `HandlerHash` del módulo, más una entrada en `abilities.txt`
([[pbs-data]]). El `id` de la habilidad es el símbolo que pasas a `.add`.

## Patrón de registro

```ruby
# En un plugin [ZBOX]. Aditivo: no parchea nada.
Battle::AbilityEffects::DamageCalcFromUser.add(:MIHABILIDAD,
  proc { |ability, user, target, move, mults, power, type|
    mults[:attack_multiplier] *= 1.5 if type == :FIRE
  }
)
```
- El **primer argumento** del proc es siempre `ability` (el símbolo). El resto depende del trigger
  (ver firmas abajo).
- Usa `next` (no `return`) para salir antdes de tiempo (R3).
- Para clonar el comportamiento de otra habilidad: `Battle::AbilityEffects::<Trigger>.copy(:ORIG, :NUEVA)`.

## Ignorable vs `NonIgnorable` (Mold Breaker)

Casi todas las habilidades **defensivas** se ignoran cuando el atacante tiene Mold Breaker / Rompemoldes
(y similares). Por eso muchos triggers tienen dos versiones:
- versión normal → la habilidad se ignora bajo Mold Breaker.
- versión `...NonIgnorable` → **no** se ignora (p.ej. Full Metal Body, Comatose).

Elige según el diseño. En código puedes consultar `target.beingMoldBroken?` y
`battler.hasActiveAbility?(:X)` (que ya tiene en cuenta supresión por Neutralizing Gas, Gastro Acid, etc.).

## Catálogo de triggers (firma del proc → para qué)

Tras `ability`, el proc recibe estos argumentos. "→ devuelve" indica triggers cuyo retorno importa.

**Velocidad / peso**
- `SpeedCalc` `|ability, battler, mult|` → devuelve nuevo multiplicador de Velocidad (Clorofila, Impulso).
- `WeightCalc` `|ability, battler, weight|` → devuelve nuevo peso (Levitón).

**HP**
- `OnHPDroppedBelowHalf` `|ability, user, move_user, battle|` — al bajar de la mitad de PS por un golpe
  (Enjambre, Mar Llamas…, en versión "berserk"/refuerzo).

**Estado**
- `StatusCheckNonIgnorable` `|ability, battler, status|` — tratar al portador como si tuviera `status`
  (Comatose).
- `StatusImmunity` / `StatusImmunityNonIgnorable` / `StatusImmunityFromAlly`
  `|ability, battler, status|` → devuelve `true` para **impedir** ese estado.
- `OnStatusInflicted` `|ability, battler, user, status|` — reacción al ser causado un estado (Sincronía).
- `OnDealingStatus` `|ability, user, target, status|` — reacción al causar un estado.
- `StatusCure` `|ability, battler|` — cura/impide mantener estados (Cura Natural en cambio, Flama…).

**Cambios de stats**
- `StatLossImmunity` / `StatLossImmunityNonIgnorable` `|ability, battler, stat, battle, show_messages|`
  → `true` para impedir que **bajen** esa stat (Espíritu Vital, Full Metal Body).
- `StatLossImmunityFromAlly` `|ability, bearer, battler, stat, battle, show_messages|` (Velo Flor).
- `OnStatGain` `|ability, battler, stat, user|` / `OnStatLoss` `|ability, battler, stat, user|` —
  reacción a subir/bajar una stat (Acaloramiento, Competitivo…).
- `CopyStatChanges` `|ability, battler, battle|` (Coletazo/Opportunist-like).

**Prioridad / orden**
- `PriorityChange` `|ability, battler, move, priority|` → devuelve nueva prioridad (Cura Prioridad,
  Bromista).
- `PriorityBracketChange` `|ability, battler, battle|` → mover dentro del bracket (Rezagado/Stall).
- `PriorityBracketUse` `|ability, battler, battle|`.

**Fallos de movimiento / inmunidad a movimientos**
- `OnFlinch` `|ability, battler, battle|` (Impasible).
- `MoveBlocking` `|ability, bearer, user, targets, move, battle|` → bloquear un movimiento entrante.
- `MoveImmunity` `|ability, user, target, move, type, battle, show_message|` → `true` para ser **inmune**
  a ese movimiento (absorciones tipo Pararrayos, Absorbe Agua, Absorbe Fuego…).

**Uso del movimiento**
- `ModifyMoveBaseType` `|ability, user, move, type|` → devuelve nuevo tipo del movimiento (Piel Feérica,
  Refrigeración…).

**Precisión**
- `AccuracyCalcFromUser` / `AccuracyCalcFromAlly` / `AccuracyCalcFromTarget`
  `|ability, mods, user, target, move, type|` — modifica el hash `mods`
  (`mods[:accuracy_multiplier]`, `mods[:evasion_multiplier]`, `mods[:base_accuracy]`).

**Daño**
- `DamageCalcFromUser` / `DamageCalcFromAlly` / `DamageCalcFromTarget` /
  `DamageCalcFromTargetNonIgnorable` / `DamageCalcFromTargetAlly`
  `|ability, user, target, move, mults, power, type|` — modifica el hash `mults`:
  `mults[:power_multiplier]`, `mults[:attack_multiplier]`, `mults[:defense_multiplier]`,
  `mults[:final_damage_multiplier]`.
- `CriticalCalcFromUser` / `CriticalCalcFromTarget` `|ability, user, target, crit_stage, move|`
  → devuelve nuevo `crit_stage` (`-1` impide críticos, valores altos los facilitan).

**Al golpear / ser golpeado**
- `OnTargetedForHit` `|ability, user, target, move, hit_num, battle|` (Coraza Tera).
- `OnBeingHit` `|ability, user, target, move, battle|` — reacción al recibir un golpe (Elec. Estática,
  Mar de Llamas, contacto…). `user` = atacante, `target` = portador.
- `OnDealingHit` `|ability, user, target, move, battle|` — reacción al pegar (Punto Tóxico).

**Al terminar de usar un movimiento**
- `OnEndOfUsingMove` `|ability, user, targets, move, battle|`.
- `AfterMoveUseFromTarget` `|ability, target, user, move, switched_battlers, battle|`.

**Final de ronda**
- `EndOfRoundWeather` `|ability, weather, battler, battle|`.
- `EndOfRoundHealing` `|ability, battler, battle|` — curación al final de ronda.
- `EndOfRoundEffect` `|ability, battler, battle|` — efecto general de fin de ronda (Mudar, Cura…).
- `EndOfRoundGainItem` `|ability, battler, battle|` (Recogida).

**Cambios / debilitamiento / clima / terreno**
- `CertainSwitching` `|ability, switcher, battle|` / `TrappingByTarget` `|ability, switcher, bearer, battle|`
  — atrapar o garantizar cambio (Sombra Trampa, Cuerpo Imán…).
- `OnSwitchIn` `|ability, battler, battle, switch_in|` — al entrar al campo (Intimidación, Llovizna,
  Sequía, Veladura…). Muy usado.
- `OnSwitchOut` `|ability, battler, end_of_battle|` — al salir (Regeneración).
- `ChangeOnBattlerFainting` / `OnBattlerFainting` `|ability, battler, fainted, battle|` (Alma de Corazón).
- `OnWeatherChange` `|ability, battler, battle, old_weather, ability_changed|`.
- `OnTerrainChange` `|ability, battler, battle, old_terrain, ability_changed|` (Mímica).
- `OnIntimidated` `|ability, battler, battle|` — reacción a ser intimidado (Vigía, Irascible).

**Huida**
- `CertainEscapeFromBattle` `|ability, battler|` (Fuga).

## Ejemplos reales (de plantilla)

```ruby
# Potenciar tipo según PS bajos (estilo Mar de Llamas / Enjambre):
Battle::AbilityEffects::DamageCalcFromUser.add(:BLAZE,
  proc { |ability, user, target, move, mults, power, type|
    if user.hp <= user.totalhp / 3 && type == :FIRE
      mults[:attack_multiplier] *= 1.5
    end
  }
)

# Reaccionar al contacto: 30% de paralizar al atacante (Elec. Estática):
Battle::AbilityEffects::OnBeingHit.add(:STATIC,
  proc { |ability, user, target, move, battle|
    next if !move.pbContactMove?(user) || !user.affectedByContactEffect?
    next if user.paralyzed? || !user.pbCanParalyze?(target, false)
    next if battle.pbRandom(100) >= 30
    battle.pbShowAbilitySplash(target)
    user.pbParalyze(target)
    battle.pbHideAbilitySplash(target)
  }
)
```

Mensajes con `_INTL` (R1). Para que la IA valore/juegue alrededor de una habilidad nueva, añade un handler
en `Battle::AI::Handlers::AbilityRanking` (ver `04-integration-and-ai.md`).
