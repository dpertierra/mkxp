# Reference · Objetos y bayas en combate (`Battle::ItemEffects`)

Fuente: `034_Other battle code/010_Battle_ItemEffects.rb`. Igual que las habilidades: un objeto custom es
uno o más **handlers** en los `HandlerHash` del módulo + una entrada en `items.txt` ([[pbs-data]]). El
`id` es el símbolo del objeto. Solo aplica al objeto **equipado** (held item); los objetos de mochila
usables sobre un Pokémon son otra cosa (ItemHandlers — ver [[plugin-architecture]] y [[pbs-data]]).

## Patrón de registro

```ruby
# Plugin [ZBOX]: cura 1/16 de PS al final de cada ronda (estilo Restos).
Battle::ItemEffects::EndOfRoundHealing.add(:MIOBJETO,
  proc { |item, battler, battle|
    next if !battler.canHeal?
    battle.pbCommonAnimation("UseItem", battler)
    battler.pbRecoverHP(battler.totalhp / 16)
    battle.pbDisplay(_INTL("¡{1} recuperó PS con {2}!", battler.pbThis, battler.itemName))
  }
)
```
Primer arg siempre `item` (símbolo). `.copy(:ORIG, :NUEVO)` para reutilizar. `next`, no `return` (R3).

## Catálogo de triggers (firma del proc → para qué)

Tras `item`:

**Velocidad / peso**
- `SpeedCalc` `|item, battler, mult|` → nuevo multiplicador de Velocidad.
- `WeightCalc` `|item, battler, weight|` → nuevo peso (Piedra Pómez).

**HP / estado / stats**
- `HPHeal` `|item, battler, battle, forced|` — curación reactiva por umbral de PS (Baya Zidra, Restos
  de mochila…). `forced` = consumida por efecto externo (Acróbata, Bocado…).
- `StatusCure` `|item, battler, battle, forced|` — cura un estado (Baya Meloc, Cura, Antiquemar…).
- `StatLossImmunity` `|item, battler, stat, battle, show_messages|` → `true` para impedir bajada (Claw
  Fossil-likes/Clear Amulet).
- `OnStatLoss` `|item, battler, stat, user|` — reacción a una bajada (Maraca Asalto/Adrenaline Orb-likes).
- `CopyStatChanges` `|item, battler, battle|`.

**Prioridad**
- `PriorityChange` `|item, battler, move, priority|` → nueva prioridad (Quick Claw via bracket).
- `PriorityBracketChange` / `PriorityBracketUse` `|item, battler, battle|`.

**Fallos / precisión / daño / crítico**
- `OnMissingTarget` `|item, user, target, move, battle|` — al fallar un golpe (Pundonor/Blunder Policy).
- `AccuracyCalcFromUser` / `AccuracyCalcFromTarget` `|item, mods, user, target, move, type|`.
- `DamageCalcFromUser` / `DamageCalcFromTarget` `|item, user, target, move, mults, power, type|` —
  modifica `mults` (mismas claves que las habilidades: `:power_multiplier`, `:attack_multiplier`,
  `:defense_multiplier`, `:final_damage_multiplier`). Tipo Vida, herbolarios de tipo, gafas…
- `CriticalCalcFromUser` / `CriticalCalcFromTarget` `|item, user, target, crit_stage, move|` → nuevo
  crit_stage (Garra Afilada, Lente Dichosa).

**Al ser golpeado**
- `OnBeingHit` `|item, user, target, move, battle|` — reacción del portador al recibir (Globo Aéreo
  estalla, Cuerno/Casco Dentado…).
- `OnBeingHitPositiveBerry` `|item, user, target, move, battle|` — bayas que se activan al ser golpeado
  con efecto **beneficioso** (bayas resistencia tipo: Zreza, Caoca…). Separadas para que Bocado/Picotazo
  e Inquietud las traten bien.

**Al terminar de usar un movimiento**
- `AfterMoveUseFromTarget` / `AfterMoveUseFromUser` `|item, ...|`.
- `OnEndOfUsingMove` `|item, battler, battle, forced|` — Baya Zanama (restaura PP), etc.
- `OnEndOfUsingMoveStatRestore` — Hierba Blanca (revierte bajadas).

**Exp / EV / clima / terreno**
- `ExpGainModifier` `|item, ...|` (Huevo Suerte) / `EVGainModifier` (Plumas/Macho-bands de EV).
- `WeatherExtender` / `TerrainExtender` `|item, ...|` — alargan duración (Roca Calor, etc.).
- `OnWeatherChange` / `OnTerrainChange` `|item, battler, battle, ...|`.

**Final de ronda / cambios / huida**
- `EndOfRoundHealing` `|item, battler, battle|` — curación de fin de ronda (Restos).
- `EndOfRoundEffect` `|item, battler, battle|` — efecto de fin de ronda (Toxiesfera, Nudo Hierba…).
- `CertainSwitching` `|item, switcher, battle|` (Malla Mudar) / `TrappingByTarget`.
- `OnSwitchIn` `|item, battler, battle, ...|` (Globo Aéreo anuncia al entrar).
- `OnIntimidated` `|item, battler, battle|` (Adrenaline Orb).
- `CertainEscapeFromBattle` `|item, battler|` (Bola Humo).

## Bayas: consumo

- Tras activarse, una baya/objeto se consume con `battler.pbConsumeItem`. Para bayas, el motor llama a
  los checks de objeto en los momentos correctos (umbral de PS, fin de ronda, al ser golpeado…).
- Un **movimiento** puede impedir que el objetivo consuma su baya curativa redefiniendo
  `preventsBattlerConsumingHealingBerry?(battler, targets)` (Bocado/Picotazo la roban antes).
- Recuperar el objeto consumido (Reciclaje, Cosecha) usa el registro interno de objeto consumido del
  battler; no hace falta gestionarlo a mano para un objeto estándar.

## Ejemplo real (Restos)

```ruby
Battle::ItemEffects::EndOfRoundHealing.add(:LEFTOVERS,
  proc { |item, battler, battle|
    next if !battler.canHeal?
    battle.pbCommonAnimation("UseItem", battler)
    battler.pbRecoverHP(battler.totalhp / 16)
    battle.pbDisplay(_INTL("¡{1} ha recuperado unos pocos PS con {2}!",
       battler.pbThis, battler.itemName))
  }
)
```

Para que la IA valore equipar/jugar alrededor de un objeto nuevo, añade un handler en
`Battle::AI::Handlers::ItemRanking` (ver `04-integration-and-ai.md`).

## Poké Balls custom (mención)

Las Poké Balls se registran en `Battle::PokeBallEffects` (`034/011_Battle_PokeBallEffects.rb`):
`IsUnconditional`, `ModifyCatchRate`, `OnCatch`, `OnFailCatch` con `.add(:MIBALL, proc {...})`. Para
crear una ball nueva ver también [[battles-runtime]] (capturas) y [[pbs-data]] (items.txt como Poké Ball).
