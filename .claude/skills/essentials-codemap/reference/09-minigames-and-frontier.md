# Reference · Minijuegos, modos alternos y Battle Frontier

Carpetas: `051_Minigames`, `053_Alternate battle modes`, `054_Battle Frontier`, `055_Battle Frontier rules`, `056_Battle Frontier generator`.

## 051_Minigames
Cada archivo es un minijuego autónomo (su propia escena + lógica). **Consulta si:** trabajas en ese minijuego concreto.

### 002_Minigame_Duel.rb
Duelo (minijuego de combate simplificado).
### 003_Minigame_TripleTriad.rb
Triple Triad (juego de cartas).
### 004_Minigame_SlotMachine.rb
Tragaperras del casino.
### 005_Minigame_VoltorbFlip.rb
Voltorb Flip.
### 006_Minigame_Lottery.rb
Lotería (comprobación de número ganador).
### 007_Minigame_Mining.rb
Minijuego de minería (excavar fósiles/items).
### 008_Minigame_TilePuzzles.rb
Puzzles de tiles (bloques deslizantes, etc.).

## 053_Alternate battle modes
Modos del overworld que envuelven combates con reglas especiales (trackers de pasos/tiempo + su UI).

### 002_SafariZone.rb
- **Core:** Zona Safari (contador de pasos/bolas, captura sin debilitar).
- **Encontrarás:** la sesión de Safari y su seguimiento.
- **Consulta si:** la Zona Safari (el combate en sí está en `035/002`).

### 003_BugContest.rb
- **Core:** Concurso de Captura de Bichos (tiempo, puntuación, mejor captura).
- **Encontrarás:** la sesión del concurso y la valoración de capturas.
- **Consulta si:** el Bug Catching Contest (el combate en `035/003`).

## 054_Battle Frontier
La maquinaria de retos competitivos (rachas, elección de rivales, combates encadenados). **Consulta si:** un facility tipo Torre de Combate / retos por rachas.

### 002_Challenge_BattleChallenge.rb
- **Core:** orquestador de un reto del Frontier (flujo de la racha).
- **Encontrarás:** inicio/continuación/fin de un reto y su estado.
- **Consulta si:** montar/entender un reto de batalla.

### 003_Challenge_Data.rb
- **Core:** estado/datos persistentes del reto (rachas, récords).
- **Encontrarás:** el almacenamiento de progreso del Frontier.
- **Consulta si:** guardar/leer rachas o récords.

### 004_Challenge_ChooseFoes.rb
- **Core:** selección de los rivales de la racha.
- **Encontrarás:** la elección de entrenadores rivales según el reto.
- **Consulta si:** cómo se eligen los oponentes.

### 005_Challenge_Battles.rb
- **Core:** ejecución de los combates del reto.
- **Encontrarás:** el lanzamiento encadenado de combates con las reglas del reto.
- **Consulta si:** cómo se corren los combates de la racha (ver skill `battles-runtime`).

### 006_UI_BattleSwap.rb
- **Core:** UI para elegir/intercambiar el equipo del reto (Pokémon de alquiler).
- **Encontrarás:** la pantalla de selección de equipo para el Frontier.
- **Consulta si:** la UI de elección de equipo competitivo.

## 055_Battle Frontier rules
Sistema de **reglas** de un reto (qué se permite, ajustes de nivel, reglas de combate).

### 002_Challenge_ChallengeRules.rb
- **Core:** contenedor de reglas de un reto.
- **Encontrarás:** cómo se ensamblan las reglas de un reto.
- **Consulta si:** definir el conjunto de reglas de un reto.

### 003_Challenge_Rulesets.rb
- **Core:** rulesets predefinidos.
- **Encontrarás:** combinaciones de reglas reutilizables.
- **Consulta si:** usar un ruleset estándar.

### 004_Challenge_EntryRestrictions.rb
- **Core:** restricciones de entrada (qué Pokémon pueden participar: nivel, especie, duplicados…).
- **Encontrarás:** los filtros de elegibilidad del equipo.
- **Consulta si:** limitar qué Pokémon pueden entrar.

### 005_Challenge_LevelAdjustment.rb
- **Core:** ajuste de nivel (igualar a 50/100, etc.).
- **Encontrarás:** la normalización de niveles del reto.
- **Consulta si:** combates a nivel fijo.

### 006_Challenge_BattleRules.rb
- **Core:** reglas de combate del reto (singles/doubles, clauses).
- **Encontrarás:** la traducción de un reto a reglas de `Battle` y clauses (ver `034/007`).
- **Consulta si:** aplicar reglas/clauses a los combates del Frontier.

## 056_Battle Frontier generator
Generación **procedural** de los equipos y entrenadores rivales a partir de las listas (`battle_facility_lists.txt`).

### 002_ChallengeGenerator_Data.rb
- **Core:** carga de los sets de Pokémon/entrenadores del Frontier.
- **Encontrarás:** el acceso a las listas definidas en PBS del Frontier.
- **Consulta si:** de dónde salen los datos competitivos (datos en skill `pbs-data` → metadata-misc).

### 003_ChallengeGenerator_Pokemon.rb
- **Core:** construcción de un Pokémon competitivo desde su set.
- **Encontrarás:** la instanciación de Pokémon de la lista (item/naturaleza/EVs/moves).
- **Consulta si:** cómo se arma un Pokémon del Frontier.

### 004_ChallengeGenerator_Trainers.rb
- **Core:** construcción de un entrenador rival competitivo.
- **Encontrarás:** la generación del `NPCTrainer` con su equipo según la lista.
- **Consulta si:** cómo se arma un rival del Frontier.

### 005_ChallengeGenerator_BattleSim.rb
- **Core:** simulación/equilibrado para emparejar rivales.
- **Encontrarás:** la lógica que ayuda a elegir rivales de dificultad adecuada.
- **Consulta si:** ajustar la dificultad/emparejamiento del Frontier.
