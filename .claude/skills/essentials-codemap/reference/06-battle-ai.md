# Reference · IA de combate

Carpetas: `032_AI`, `033_AI MoveEffects`. La IA decide las acciones de los battlers controlados por el ordenador (enemigos y, en auto-battle, el jugador). Su "nivel" depende del `SkillLevel` del tipo de entrenador.

## 032_AI
El cerebro de decisión.

### 002_Battle_AI.rb
- **Core:** clase de IA y bucle de decisión (elige qué hace un battler en su turno).
- **Encontrarás:** el orquestador que evalúa cambiar, usar item, Mega o atacar.
- **Consulta si:** cómo decide la IA su acción en general.

### 003_AI_Switch.rb
- **Core:** decisión de cambiar de Pokémon.
- **Encontrarás:** cuándo la IA prefiere relevar.
- **Consulta si:** lógica de cambio de la IA.

### 004_AI_UseItem.rb
- **Core:** decisión de usar un item (entrenadores con items).
- **Encontrarás:** cuándo y qué item usa la IA.
- **Consulta si:** uso de items por la IA.

### 005_AI_MegaEvolve.rb
- **Core:** decisión de Mega Evolucionar.
- **Encontrarás:** cuándo la IA hace Mega.
- **Consulta si:** Mega de la IA.

### 006_AI_ChooseMove.rb
- **Core:** elección de movimiento (bucle de scoring).
- **Encontrarás:** la evaluación de cada move candidato y la selección final.
- **Consulta si:** cómo puntúa y elige la IA su ataque.

### 007_AI_ChooseMove_GenericEffects.rb
- **Core:** scoring genérico por daño/efectividad.
- **Encontrarás:** los ajustes de puntuación comunes a todos los moves (daño esperado, tipo).
- **Consulta si:** la base del scoring de daño.

### 008_AI_ChooseMove_OtherScores.rb
- **Core:** otros ajustes de puntuación (situacionales).
- **Encontrarás:** modificadores de score por contexto de combate.
- **Consulta si:** matices del scoring que no son daño puro.

### 009_AI_Utilities.rb
- **Core:** utilidades de la IA (predicciones, consultas).
- **Encontrarás:** helpers compartidos por el módulo de IA.
- **Consulta si:** funciones de apoyo de la IA.

### 010_AITrainer.rb
- **Core:** visión que la IA tiene del entrenador y su equipo.
- **Encontrarás:** el modelo del entrenador para la toma de decisiones.
- **Consulta si:** datos de entrenador usados por la IA.

### 011_AIBattler.rb
- **Core:** visión que la IA tiene de un battler.
- **Encontrarás:** el modelo de un Pokémon activo para evaluar acciones.
- **Consulta si:** cómo la IA "ve" a un battler.

### 012_AIMove.rb
- **Core:** visión que la IA tiene de un movimiento (simulación sin aplicar).
- **Encontrarás:** la predicción de daño/efecto de un move sin ejecutarlo.
- **Consulta si:** cómo la IA estima el resultado de un move.

## 033_AI MoveEffects
Ajustes de puntuación **por tipo de efecto** de movimiento (espejan a `030_Move/006`-`014`). Cada archivo añade el scoring de su familia de efectos: la IA sabe valorar cada FunctionCode.

- **Core común:** dan a la IA el criterio para puntuar moves de cada categoría de efecto. **Encontrarás:** un ajuste de score por FunctionCode. **Consulta si:** creaste un move con efecto nuevo y quieres que la IA lo valore bien (añade su scoring aquí, en paralelo al efecto que pusiste en `030_Move`).

### 002_AI_MoveEffects_Misc.rb
Scoring de efectos misceláneos.
### 003_AI_MoveEffects_BattlerStats.rb
Scoring de moves que cambian stats.
### 004_AI_MoveEffects_BattlerOther.rb
Scoring de otros efectos sobre el battler (estados, etc.).
### 005_AI_MoveEffects_MoveAttributes.rb
Scoring de moves que alteran atributos del propio move.
### 006_AI_MoveEffects_MultiHit.rb
Scoring de moves multi-golpe.
### 007_AI_MoveEffects_Healing.rb
Scoring de moves de curación.
### 008_AI_MoveEffects_Items.rb
Scoring de moves relacionados con items.
### 009_AI_MoveEffects_ChangeMoveEffect.rb
Scoring de moves que cambian el efecto de otros moves.
### 010_AI_MoveEffects_SwitchingActing.rb
Scoring de moves de cambio/acción (relevo forzado, etc.).
