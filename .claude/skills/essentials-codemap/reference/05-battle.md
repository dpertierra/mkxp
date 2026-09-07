# Reference · Batalla (mecánica, battlers, movimientos, escena, efectos)

Carpetas: `028_Battle`, `029_Battler`, `030_Move`, `031_Scene`, `034_Other battle code`, `035_Other battle types`. La IA está en `reference/06-battle-ai.md`. Para lanzar combates desde código, skill `battles-runtime`.

## 028_Battle
La clase `Battle` (estado y bucle del combate, dividida por fases).

### 002_Battle.rb
- **Core:** clase **`Battle`**: estado global del combate (bandos, reglas, clima/terreno, contador de turnos).
- **Encontrarás:** los atributos del combate y las consultas sobre su estado.
- **Consulta si:** propiedades del combate, reglas activas, clima/terreno de batalla.

### 003_Battle_StartAndEnd.rb
- **Core:** inicio y fin del combate (saludos, recompensas de dinero, salida).
- **Encontrarás:** el arranque del combate, el reparto de dinero y el cierre.
- **Consulta si:** qué ocurre al empezar/terminar un combate, recompensas.

### 004_Battle_ExpAndMoveLearning.rb
- **Core:** ganancia de EXP/EVs, subir de nivel, aprender movimientos y evolución post-combate.
- **Encontrarás:** el reparto de experiencia, el aprendizaje de moves y el disparo de evoluciones.
- **Consulta si:** EXP/EVs, aprender moves al subir nivel, evolución tras combate.

### 005_Battle_ActionAttacksPriority.rb
- **Core:** orden de acciones (prioridad, velocidad).
- **Encontrarás:** el cálculo del orden de turno de los battlers.
- **Consulta si:** quién actúa primero, prioridades.

### 006_Battle_ActionSwitching.rb
- **Core:** cambiar de Pokémon (acción de relevo).
- **Encontrarás:** la lógica de cambio voluntario/forzado con sus triggers.
- **Consulta si:** relevos de Pokémon.

### 007_Battle_ActionUseItem.rb
- **Core:** usar un item en combate (acción de Mochila).
- **Encontrarás:** el flujo de usar item desde la batalla.
- **Consulta si:** acción de item del jugador en combate.

### 008_Battle_ActionRunning.rb
- **Core:** huir.
- **Encontrarás:** la lógica de escape (probabilidad, bloqueos).
- **Consulta si:** huida del combate.

### 009_Battle_ActionOther.rb
- **Core:** otras acciones (Mega Evolución, llamar a Pokémon Oscuro, etc.).
- **Encontrarás:** acciones especiales del menú de comando.
- **Consulta si:** Mega en batalla u otras acciones no estándar.

### 010_Battle_CommandPhase.rb
- **Core:** fase de comandos (elegir acción de cada battler).
- **Encontrarás:** el bucle que pide acción a jugador e IA.
- **Consulta si:** cómo se eligen las acciones del turno.

### 011_Battle_AttackPhase.rb
- **Core:** fase de ataque (ejecución de las acciones del turno).
- **Encontrarás:** el orden de ejecución y disparo de movimientos/cambios/items.
- **Consulta si:** cómo se resuelve el turno.

### 012_Battle_EndOfRoundPhase.rb
- **Core:** fase de fin de ronda (clima, estados, objetos residuales, Perish, etc.).
- **Encontrarás:** todos los efectos que ocurren al cerrar la ronda.
- **Consulta si:** daño de clima/estado al final del turno, efectos periódicos.

## 029_Battler
`Battle::Battler` — un Pokémon **activo** en combate (estado de batalla, distinto del objeto `Pokemon`).

### 002_Battle_Battler.rb
- **Core:** clase `Battler` (estado individual en combate).
- **Encontrarás:** atributos del battler (HP, stats efectivos, efectos activos).
- **Consulta si:** propiedades de un Pokémon en combate.

### 003_Battler_Initialize.rb
- **Core:** inicialización del battler al entrar.
- **Encontrarás:** la puesta en pista de un Pokémon (lectura desde el objeto Pokemon).
- **Consulta si:** qué pasa cuando un Pokémon sale a combatir.

### 004_Battler_ChangeSelf.rb
- **Core:** cambios sobre el propio battler (forma, HP, transformaciones).
- **Encontrarás:** modificaciones de HP/forma del battler en combate.
- **Consulta si:** cambiar HP/forma de un battler.

### 005_Battler_Statuses.rb
- **Core:** estados alterados e inmunidades en combate.
- **Encontrarás:** aplicar/curar estados y comprobar inmunidades.
- **Consulta si:** infligir/curar sueño, veneno, quemadura, etc.

### 006_Battler_StatStages.rb
- **Core:** niveles de stat (boosts/drops ±6).
- **Encontrarás:** subir/bajar stats en combate y sus límites.
- **Consulta si:** cambios de Ataque/Defensa/etc. por etapas.

### 007_Battler_AbilityAndItem.rb
- **Core:** disparo de habilidad/item del battler.
- **Encontrarás:** los puntos donde se activan habilidades e items (delegan a `034`).
- **Consulta si:** cuándo se evalúa la habilidad/objeto de un battler.

### 008_Battler_UseMove.rb
- **Core:** uso de un movimiento (flujo principal).
- **Encontrarás:** PP, intención, fallo, ejecución del move.
- **Consulta si:** cómo se usa un movimiento de principio a fin.

### 009_Battler_UseMoveTargeting.rb
- **Core:** selección/redirección de objetivos.
- **Encontrarás:** cómo se eligen y redirigen los targets (Seguidme, etc.).
- **Consulta si:** targeting de movimientos.

### 010_Battler_UseMoveSuccessChecks.rb
- **Core:** comprobaciones de éxito/precisión.
- **Encontrarás:** chequeos de fallo, inmunidad y acierto.
- **Consulta si:** por qué un move falla o acierta.

### 011_Battler_UseMoveTriggerEffects.rb
- **Core:** efectos disparados tras pegar (retroceso, efectos secundarios…).
- **Encontrarás:** los gatillos post-impacto.
- **Consulta si:** efectos que ocurren después de que un move golpea.

## 030_Move
`Battle::Move` y la implementación de cada efecto de movimiento.

### 002_Battle_Move.rb
- **Core:** clase base de movimiento en combate (ligada al FunctionCode).
- **Encontrarás:** la base de la que heredan todos los efectos de move.
- **Consulta si:** entender cómo un FunctionCode se conecta a su clase.

### 003_Move_Usage.rb
- **Core:** flujo de uso de un move (a nivel de la clase Move).
- **Encontrarás:** orquestación de golpes, multi-target, fallos.
- **Consulta si:** el ciclo de aplicación de un movimiento.

### 004_Move_UsageCalculations.rb
- **Core:** **fórmula de daño y precisión**.
- **Encontrarás:** el cálculo completo de daño (STAB, tipos, críticos, modificadores) y de acierto.
- **Consulta si:** cómo se calcula el daño/precisión o tocar la fórmula.

### 005_Move_BaseEffects.rb
- **Core:** efectos base reutilizables.
- **Encontrarás:** clases base para movimientos con efectos comunes.
- **Consulta si:** patrón base de un efecto.

### 006_MoveEffects_Misc.rb … 014_MoveEffects_SwitchingActing.rb
- **Core:** **implementación de cada FunctionCode** (un archivo por familia de efecto: misceláneos `006`, stats del battler `007`, otros del battler `008`, atributos del move `009`, multi-golpe `010`, curación `011`, items `012`, cambiar efecto del move `013`, switching/acción `014`).
- **Encontrarás:** la clase concreta que implementa el comportamiento de cada move (el `FunctionCode` de moves.txt apunta aquí).
- **Consulta si:** crear/editar el efecto de un movimiento; busca un FunctionCode análogo en estos archivos y copia el patrón (ver skill `pbs-data` para el dato).

## 031_Scene
La parte **visual** del combate.

### 002_Battle_Scene.rb
- **Core:** clase de la escena de batalla (viewports, orquestación visual).
- **Encontrarás:** el contenedor de todos los elementos gráficos del combate.
- **Consulta si:** estructura visual de la batalla.

### 003_Scene_Initialize.rb
- **Core:** montaje inicial de sprites de batalla.
- **Encontrarás:** la creación de data boxes, bases, sprites de Pokémon/entrenador.
- **Consulta si:** qué se crea al iniciar la escena de combate.

### 004_Scene_ChooseCommands.rb
- **Core:** control del cursor de los menús de combate (comando/lucha/target).
- **Encontrarás:** la lógica de elegir Luchar/Mochila/Pokémon/Huir, mover y target.
- **Consulta si:** el menú de comandos o de selección de movimiento/objetivo.

### 005_Scene_PlayAnimations.rb
- **Core:** reproducción de animaciones de movimiento/efecto.
- **Encontrarás:** el disparo de las animaciones de ataque (ver también `034/008`).
- **Consulta si:** mostrar la animación de un move.

### 006_Battle_Scene_Menus.rb
- **Core:** los menús de la barra inferior de combate.
- **Encontrarás:** las clases de menú de comando/lucha/target.
- **Consulta si:** apariencia/comportamiento de los menús de batalla.

### 007_Battle_Scene_Objects.rb
- **Core:** objetos visuales (data boxes, sprites de battler, bases).
- **Encontrarás:** los paneles de info de Pokémon y los sprites en pista.
- **Consulta si:** el data box o el sprite de un Pokémon en combate.

### 008_Battle_Scene_BaseAnimation.rb
- **Core:** base del sistema de animaciones de escena.
- **Encontrarás:** la maquinaria base para animaciones (poké balls, desmayo, etc.).
- **Consulta si:** crear una animación de escena de combate.

### 009_Battle_Scene_Animations.rb
- **Core:** animaciones concretas (lanzar bola, capturar, retirar, Mega…).
- **Encontrarás:** las animaciones específicas de eventos de combate.
- **Consulta si:** una animación concreta (captura, relevo, Mega).

### 010_Battle_DebugScene.rb
- **Core:** escena de batalla en modo texto (sin gráficos).
- **Encontrarás:** una `Scene` ligera para depurar la mecánica.
- **Consulta si:** depurar la lógica de combate sin visuales.

### 011_Battle_Scene_Arceus_Transform.rb
- **Core:** transformación visual de Arceus/Silvally (cambio de tipo/forma).
- **Encontrarás:** la animación de cambio de placa/memoria.
- **Consulta si:** efecto visual de Arceus/Silvally.

### 012_Fondos Terrenos.rb
- **Core:** **(custom)** fondos/visuales de terreno y hazards en combate.
- **Encontrarás:** sprites de terreno (Eléctrico/Hierba…) y de trampas (Púas, etc.) en la escena.
- **Consulta si:** visual de terrenos o entry hazards en batalla.

### 013_ContinuousWeatherAnimations.rb
- **Core:** **(custom)** animaciones de clima continuas durante el combate.
- **Encontrarás:** clima animado de forma continua en lugar de en ráfagas.
- **Consulta si:** el clima visual de la batalla.

## 034_Other battle code
Código de combate transversal: efectos de campo, captura, clauses, animaciones, y los grandes catálogos de efectos.

### 002_PBEffects.rb
- **Core:** constantes de **efectos** (de battler, bando y campo).
- **Encontrarás:** los IDs/nombres de todos los efectos volátiles (Confusión, Pantalla Luz, Trampa Rocas…).
- **Consulta si:** referenciar un efecto de combate por su constante.

### 003_Battle_ActiveField.rb
- **Core:** estado del campo entero (clima/terreno/efectos globales).
- **Encontrarás:** la representación del campo de batalla compartido.
- **Consulta si:** efectos que afectan a todo el campo.

### 004_Battle_DamageState.rb
- **Core:** estado del daño de un golpe (cuánto, crítico, efectividad).
- **Encontrarás:** el contenedor con el resultado de un cálculo de daño.
- **Consulta si:** inspeccionar el resultado de un golpe.

### 005_Battle_Peers.rb
- **Core:** puente entre el combate y datos externos (party, almacenamiento).
- **Encontrarás:** los métodos para que el combate acceda a la party/PC del jugador.
- **Consulta si:** cómo el combate lee/escribe la party del jugador.

### 006_Battle_CatchAndStoreMixin.rb
- **Core:** **mecánica completa de captura** y almacenamiento del capturado.
- **Encontrarás:** el cálculo de captura, las sacudidas y el envío a party/PC.
- **Consulta si:** capturas, tasa de captura, qué pasa al atrapar.

### 007_Battle_Clauses.rb
- **Core:** **cláusulas de torneo** (Sleep/Freeze/Evasion/OHKO/Species…).
- **Encontrarás:** los parches que aplican cada clause cuando está activa.
- **Consulta si:** reglas de torneo/Frontier (ver skill `battles-runtime`).

### 008_BattleAnimationPlayer.rb
- **Core:** reproductor de las animaciones de batalla (formato de archivo de animación).
- **Encontrarás:** el motor que ejecuta las animaciones de move/efecto fotograma a fotograma.
- **Consulta si:** cómo se reproduce una animación de batalla (editor en `062`).

### 009_Battle_AbilityEffects.rb
- **Core:** **TODAS las habilidades en batalla** (catálogo enorme, sistema `Battle::AbilityEffects`).
- **Encontrarás:** un handler por habilidad agrupado por tipo de trigger.
- **Consulta si:** crear/editar el efecto de una habilidad; busca un trigger análogo y copia el patrón (dato en skill `pbs-data`).

### 010_Battle_ItemEffects.rb
- **Core:** efectos de items **en combate** (objetos en mano: bayas, Vidasfera…).
- **Encontrarás:** un handler por item de combate, por trigger.
- **Consulta si:** efecto de un objeto equipado en batalla.

### 011_Battle_PokeBallEffects.rb
- **Core:** modificadores de captura por Poké Ball.
- **Encontrarás:** el multiplicador/efecto de cada tipo de bola.
- **Consulta si:** una Poké Ball nueva o su tasa (ver skill `battles-runtime`).

## 035_Other battle types
Modos de combate especiales.

### 002_SafariBattle.rb
Combate Safari (sin debilitar; bolas Safari, cebo/barro). Consulta para la Zona Safari.
### 003_BugContestBattle.rb
Combate del Concurso de Captura. Consulta para el Bug Catching Contest.
### 004_BattlePalaceBattle.rb
Modo Palacio (los Pokémon actúan solos según naturaleza). Consulta para Battle Palace.
### 005_BattleArenaBattle.rb
Modo Arena (juicio por puntos en pocos turnos). Consulta para Battle Arena.
### 006_RecordedBattle.rb
Grabación/reproducción de combates. Consulta para repeticiones de batalla.
