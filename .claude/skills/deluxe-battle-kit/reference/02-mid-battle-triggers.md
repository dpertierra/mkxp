# Reference · Mid-Battle Scripting — Trigger Keys (cuándo)

Un midbattle script se monta con la regla `setBattleRule("midbattleScript", Hash)`. El `Hash` empareja
**Trigger Keys** (cuándo) → **Command Keys** (qué; ver `03-mid-battle-commands.md`).

```ruby
setBattleRule("midbattleScript", {
  "TurnEnd_1"   => { "speech" => "¡No te lo pondré fácil!" },
  "TargetHPLow" => { "useItem" => :FULLRESTORE, "speech" => "¡Aún no he terminado!" }
})
TrainerBattle.start(:LEADER_Brock, "Brock")
```

**Regla de nombres:** los Trigger Keys son strings que **empiezan en MAYÚSCULA**. Los Command Keys
empiezan en **minúscula**. La mayoría de triggers admiten **extensiones** con `_` (ver final).

## 4.A.1 Fases de la ronda
- `"RoundStartCommand"` — inicio de la fase de comandos (antes de elegir nada).
- `"RoundStartAttack"` — inicio de la fase de ataque (tras elegir, antes de ejecutar).
- `"RoundEnd"` — fin de la ronda (tras todo, antes de la siguiente fase de comandos).
> Requieren extensión numérica de turno y de "quién" (ver Extensions: User). Ej. `"RoundStartCommand_2"`.

## 4.A.2 Turnos del battler
- `"TurnStart"` — inicio del turno de un battler (antes de sus comandos).
- `"TurnEnd"` — fin del turno de un battler.
> Extensión: número de turno (`"TurnEnd_4"`), o species/type ID (`"TurnStart_CUBONE"`, `"TurnEnd_ROCK"`).

## 4.A.3 Uso de objetos
- `"BeforeItemUse"` / `"AfterItemUse"` — antes/después de que un entrenador use un objeto.
> Extensión: item ID (`"BeforeItemUse_POTION"`).

## 4.A.4 Captura (salvajes)
- `"BeforeCapture"` / `"AfterCapture"` / `"FailedCapture"` — antes de lanzar Ball / captura exitosa / fallo.
> Extensión: species o type ID (`"AfterCapture_PIKACHU"`, `"FailedCapture_ELECTRIC"`).

## 4.A.5 Cambios (switch)
- `"BeforeSwitchOut"` — antes de retirar manualmente un Pokémon.
- `"BeforeSwitchIn"` / `"BeforeLastSwitchIn"` — antes de enviar uno nuevo / el último de la reserva del lado.
- `"AfterSwitchIn"` / `"AfterLastSwitchIn"` — tras enviarlo (por cambio).
- `"AfterSendOut"` / `"AfterLastSendOut"` — como los anteriores pero para CUALQUIER envío (incluido el
  inicial al empezar el combate), no solo por cambio.
> Extensión: species o type ID (`"BeforeSwitchOut_MEOWTH"`, `"AfterSwitchIn_DARK"`).

## 4.A.6 Megas y Primals
- `"BeforeMegaEvolution"` / `"AfterMegaEvolution"`.
- `"BeforePrimalReversion"` / `"AfterPrimalReversion"`.
> Extensión: species o type ID.

## 4.A.7 Uso de movimientos
- `"BeforeMove"`, `"BeforeDamagingMove"`, `"BeforePhysicalMove"`, `"BeforeSpecialMove"`, `"BeforeStatusMove"`.
- `"AfterMove"`, `"AfterDamagingMove"`, `"AfterPhysicalMove"`, `"AfterSpecialMove"`, `"AfterStatusMove"`.
> Extensión: species o type ID (`"BeforePhysicalMove_MACHOP"`, `"AfterSpecialMove_GENGAR"`). **Solo**
> `"BeforeMove"`/`"AfterMove"` admiten además move ID (`"BeforeMove_TACKLE"`, `"AfterMove_GROWL"`).

## 4.A.8 Resultados de daño
Del **usuario** del movimiento: `"UserDealtDamage"`, `"UserDamagedSub"`, `"UserBrokeSub"`,
`"UserDealtCriticalHit"`, `"UserMoveEffective"` (supereficaz), `"UserMoveResisted"`, `"UserMoveNegated"`
(inmunidad/Protect), `"UserMoveDodged"` (fallo), `"UserHPHalf"` (≤50% tras moverse), `"UserHPLow"` (≤25%),
`"LastUserHPHalf"` / `"LastUserHPLow"` (sin aliados en reserva + ≤50%/≤25%).
Del **objetivo**: `"TargetTookDamage"`, `"TargetSubDamaged"`, `"TargetSubBroken"`, `"TargetTookCriticalHit"`,
`"TargetWeakToMove"` (supereficaz), `"TargetResistedMove"`, `"TargetNegatedMove"`, `"TargetDodgedMove"`,
`"TargetHPHalf"`, `"TargetHPLow"`, `"LastTargetHPHalf"`, `"LastTargetHPLow"`.
> Extensión: species, move o type ID (`"UserMoveEffective_EMBER"`, `"TargetTookCriticalHit_GRASS"`).

## 4.A.9 Condición del battler
`"BattlerHPRecovered"`, `"BattlerHPFull"`, `"BattlerHPReduced"`, `"BattlerHPCritical"` (≤25%),
`"BattlerFainted"`, `"LastBattlerFainted"` (último del lado), `"BattlerReachedHPCap"` (llegó al tope de
daño), `"BattlerStatusChange"`, `"BattlerStatusCured"`, `"BattlerConfusionStart"`/`"BattlerConfusionEnd"`,
`"BattlerAttractStart"`/`"BattlerAttractEnd"`, `"BattlerStatRaised"`/`"BattlerStatLowered"`,
`"BattlerMoveZeroPP"`.
> Extensiones: species ID (todas); type ID (las de HP/debilitamiento, `"BattlerHPCritical_NORMAL"`);
> status ID solo en `"BattlerStatusChange_BURN"`; stat ID solo en `"BattlerStatRaised_ATTACK"`/
> `"BattlerStatLowered"`; move/type ID solo en `"BattlerMoveZeroPP_HYPERBEAM"`/`"_ICE"`.

## 4.A.10 Fin de efectos
`"WeatherEnded"`, `"TerrainEnded"`, `"FieldEffectEnded"` (Trick Room, Gravity…), `"TeamEffectEnded"`
(Reflect, Light Screen…), `"BattlerEffectEnded"` (Disable, Encore…).
> Extensión: ID del efecto concreto (`"WeatherEnded_Rain"`, `"TeamEffectEnded_Tailwind"`). Solo efectos
> que cuentan turnos.

## 4.A.11 Fin del combate (sin extensiones)
`"BattleEnd"`, `"BattleEndWin"`, `"BattleEndLoss"`, `"BattleEndDraw"`, `"BattleEndForfeit"` (rendición
estilo Torre), `"BattleEndRun"` (huida del jugador), `"BattleEndFled"` (huye el salvaje),
`"BattleEndCapture"` (capturó al menos uno).

## 4.A.12 Variable midbattle
`"Variable_#"` (igual a #), `"VariableUp"`, `"VariableDown"`, `"VariableOver_#"`, `"VariableUnder_#"`.
> Solo admiten extensiones de Frequency. La variable se manipula con `setVariable`/`addVariable`/
> `multVariable` (ver commands). Útil para fases de jefe.

## 4.A.13 Elecciones (Choices)
`"Choice_<id>_#"` (eligió la opción # de la pregunta con ese id), `"ChoiceRight_<id>"` (acertó),
`"ChoiceWrong_<id>"` (falló). Sin extensiones. Las preguntas se montan con `setChoices` (ver commands).

## 4.A.14 Extensiones generales
Además de las específicas de cada grupo, los triggers se acotan con `_` + cualificador:
- **User/objetivo** (a quién aplica): `_player` (lado del jugador), `_foe`/`_opposing` (rival), y los
  símbolos relativos como índices. Determina quién dispara/recibe el trigger.
- **Turno** (número): `"RoundEnd_3"`, `"TurnStart_2"`.
- **Species / Type / Move / Status / Stat ID** según lo permitido por cada grupo (ver arriba).
- **Frequency** (cada cuánto): controla que un trigger se repita o solo dispare una vez.
- Se pueden **encadenar** varios cualificadores con `_`.

> Si dudas si una extensión concreta es válida para un trigger, revisa su grupo arriba (cada uno lista las
> extensiones compatibles) o consulta el GitBook `04_Mid-Battle Scripting/01_Trigger Keys`.
