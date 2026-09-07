---
name: battle-effects
description: >
  Crear efectos de combate CUSTOM en Pokémon Essentials 21.1 / La Base de Sky a partir de una
  descripción en lenguaje natural, totalmente integrados: movimientos (function codes
  Battle::Move::<Code> y sus hooks), habilidades (Battle::AbilityEffects, ~70 triggers) y objetos en
  combate (Battle::ItemEffects), MÁS el scoring de la IA para que los use y reaccione a ellos
  (Battle::AI::Handlers: MoveEffectScore, AbilityRanking, ItemRanking). Cubre estado volátil custom
  (PBEffects), interacción con otros efectos (Mold Breaker/NonIgnorable, Protect/Substitute, inmunidad
  por tipo, supresión) y empaquetado como plugin [ZBOX]. Extraído 1:1 de 030_Move, 034_Other battle
  code y 032/033_AI. Para DISPARAR combates ver [[battles-runtime]]; para las entradas PBS ver
  [[pbs-data]]; para objetos Pokémon ver [[pokemon-api]]. Triggers: "crea un movimiento", "nueva
  habilidad", "efecto de objeto en combate", "function code", "AbilityEffects", "ItemEffects", "que la
  IA lo use", "pbEffectGeneral", "pbAdditionalEffect", "AddTarget", "berry custom", "que reaccione a".
---

# Efectos de combate custom

Fuentes (verificar firmas reales contra el juego — ver [[essentials-codemap]]):
`030_Move/002_Battle_Move.rb` (clase base + resolución de function code), `030_Move/003-014` (pipeline
de uso y efectos), `034_Other battle code/009_Battle_AbilityEffects.rb`,
`010_Battle_ItemEffects.rb`, `002_PBEffects.rb`, `032_AI/` y `033_AI MoveEffects/`.

> **Esta skill SÍ nombra métodos/clases** (al revés que [[essentials-codemap]]): para *crear* contenido
> hace falta la API exacta. Está anclada a **v21.1**; antes de usar un hook/trigger, confirma que sigue
> existiendo con un grep (process step 2 de AGENTS.md).

## Principio: integración en 3 capas

Un efecto "totalmente integrado" toca **tres** sistemas. Si falta alguno, el efecto funciona "a medias"
(p.ej. existe pero la IA nunca lo usa, o no respeta inmunidades):

1. **Mecánica** — el movimiento/habilidad/objeto *hace* lo que dice (hooks de `Battle::Move` /
   triggers de `AbilityEffects` / `ItemEffects`).
2. **Interacción** — coexiste bien con lo demás: inmunidad por tipo, Protect/Substitute/Magic Bounce,
   Mold Breaker (`NonIgnorable`), supresión de habilidad, clima/terreno, prioridad, dobles. Ver
   `reference/04-integration-and-ai.md`.
3. **IA** — la IA lo puntúa para usarlo con criterio y **reacciona** a habilidades/objetos nuevos del
   rival (`Battle::AI::Handlers::MoveEffectScore` / `AbilityRanking` / `ItemRanking`).

## Árbol de decisión: ¿movimiento, habilidad u objeto?

De la descripción del usuario:
- "Un **ataque/movimiento** que…" → **movimiento** (`reference/01-moves.md`). Lo activa el jugador al
  elegirlo; se define por su `FunctionCode`.
- "Un Pokémon que **pasivamente**… / cuando tiene la **habilidad**…" → **habilidad**
  (`reference/02-abilities.md`). Reacciona sola a eventos del combate.
- "Un **objeto/baya** que al equiparlo…" → **objeto en combate** (`reference/03-items-in-battle.md`).

Un mismo concepto puede repartirse: p.ej. "movimiento que sube ataque al rival que lo golpea" = el
movimiento (mecánica) + quizá una habilidad/efecto que reacciona.

## Workflow de creación (de la descripción al plugin)

1. **Clasificar** (árbol de arriba) y traducir la descripción a *cuándo* ocurre el efecto y *sobre
   quién*. Ese "cuándo" determina el **hook** (movimiento) o **trigger** (habilidad/objeto) exacto.
2. **Reusar antes que reinventar.** Para movimientos, mira si hereda de una **clase base** ya existente
   (`StatUpMove`, `HealingMove`, `TwoTurnMove`, `RecoilMove`, `ProtectMove`, `WeatherMove`,
   `TerrainMove`, `FixedDamageMove`…) o de un function code parecido. Para habilidades/objetos, copia el
   patrón de uno análogo (ver ejemplos en los reference).
3. **Implementar como plugin `[ZBOX]`** (R10/R11), nunca tocando `Data/` (R2):
   - Movimiento: `class Battle::Move::<MiCode> < Battle::Move` (o `< Battle::Move::ClaseBase`).
   - Habilidad/objeto: registro **aditivo** `Battle::AbilityEffects::<Trigger>.add(:MIHABILIDAD, proc { ... })`
     / `Battle::ItemEffects::<Trigger>.add(:MIOBJETO, proc { ... })`. Es aditivo, no parchea nada.
   - Texto al jugador con `_INTL` (R1), `next` dentro de procs (R3), validar `nil` (R6).
4. **Conectar el PBS** ([[pbs-data]]):
   - Movimiento → entrada en `moves.txt` con `FunctionCode = <MiCode>` (sin el prefijo `Battle::Move::`).
   - Habilidad → entrada en `abilities.txt` (el `id` es el símbolo `:MIHABILIDAD`).
   - Objeto → entrada en `items.txt`.
5. **Añadir scoring de IA** (`reference/04-integration-and-ai.md`):
   - Movimiento → `Battle::AI::Handlers::MoveEffectScore.add("<MiCode>", proc { ... })`
     (y `MoveFailureCheck` si puede fallar). Reusa con `.copy(...)` desde un code equivalente.
   - Habilidad/objeto → `AbilityRanking.add(:MIHABILIDAD, …)` / `ItemRanking.add(:MIOBJETO, …)` para que
     la IA valore tenerla y juegue alrededor de ella.
6. **Checklist de integración** + entrega con pasos de verificación manual (no ejecutas el juego).

## Checklist de integración (capa 2)

Antes de dar por hecho un efecto, revisa qué debe respetar (detalle en `reference/04-integration-and-ai.md`):
- **Inmunidad por tipo / `pbFailsAgainstTarget?`** — un movimiento de estado no debe "funcionar" sobre
  quien es inmune.
- **Protect / Substitute** — `def ignoresSubstitute?(user)`; efectos adicionales sobre el objetivo
  comprueban `target.damageState.substitute` y `target.affectedByAdditionalEffects?`.
- **Mold Breaker** — si tu habilidad *debe* ignorarse por Mold Breaker usa el trigger normal; si NO
  (tipo Full Metal Body), usa la variante `...NonIgnorable`. En código: `target.beingMoldBroken?`,
  `battler.hasActiveAbility?(:X)`.
- **Magic Bounce / Magic Coat** — movimientos de estado que rebotan: `def canMagicCoat?; true; end`.
- **Snatch** — movimientos que se pueden robar: `def canSnatch?; true; end`.
- **Dobles/triples** — `@target` correcto (GameData::Target) y efectos por objetivo vs generales.
- **Mensajes** — todo texto con `_INTL` y `{1}`/`{2}` (R1).

## Reference

| Archivo | Cubre |
|---|---|
| `reference/01-moves.md` | Movimientos: resolución `FunctionCode`→clase, catálogo de hooks override, clases base reutilizables |
| `reference/02-abilities.md` | Habilidades: catálogo completo de triggers de `AbilityEffects`, patrón `.add`, NonIgnorable |
| `reference/03-items-in-battle.md` | Objetos/bayas en combate: triggers de `ItemEffects`, patrón `.add`, consumo de bayas |
| `reference/04-integration-and-ai.md` | PBEffects (estado custom), interacción entre efectos y **scoring de IA** completo |

Skills relacionadas: [[battles-runtime]] (disparar combates, dar el Pokémon con la habilidad/objeto),
[[pbs-data]] (entradas en moves/abilities/items.txt), [[pokemon-api]] (objetos Pokémon),
[[plugin-architecture]] (empaquetado `[ZBOX]`, orden de carga, persistencia).
