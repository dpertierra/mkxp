# Reference · Overworld, visuales y disparo de combates

Carpetas: `037_Overworld`, `038_Overworld visuals`, `039_Battle triggering`.

## 037_Overworld
Mecánicas jugables del mapa.

### 002_Overworld.rb
- **Core:** mecánicas base del overworld (surf, salto de ledges, Rock Smash/Cut, etc.).
- **Encontrarás:** los flujos de las MOs/acciones de campo principales y utilidades de mapa.
- **Consulta si:** surf, ledges, romper rocas, cortar.

### 003_Overworld_Metadata.rb
- **Core:** acceso a la metadata del mapa/jugador en runtime.
- **Encontrarás:** helpers que leen `MapMetadata`/`Metadata` para el overworld.
- **Consulta si:** consultar propiedades del mapa actual desde código.

### 004_Overworld_Time.rb
- **Core:** sistema día-noche.
- **Encontrarás:** cálculo de hora del día y los periodos (mañana/día/tarde/noche).
- **Consulta si:** lógica que depende de la hora; horarios custom en `001_Settings/004`.

### 005_Overworld_FieldMoves.rb
- **Core:** **MOs/movimientos fuera de combate** (Vuelo, Fuerza, Destello, Buceo…).
- **Encontrarás:** la activación de cada movimiento de campo.
- **Consulta si:** una MO o movimiento usable en el overworld.

### 006_Overworld_Fishing.rb
- **Core:** pesca.
- **Encontrarás:** el flujo de cañas y el disparo del encuentro de pesca.
- **Consulta si:** pesca.

### 007_Overworld_BerryPlants.rb
- **Core:** plantas de bayas en el mapa.
- **Encontrarás:** plantar/regar/cosechar y el crecimiento por fases.
- **Consulta si:** crianza de bayas en el overworld.

### 008_Overworld_DayCare.rb
- **Core:** guardería/crianza (huevos).
- **Encontrarás:** depósito de Pokémon, generación de huevos y herencia.
- **Consulta si:** guardería, cría, huevos.

### 009_Overworld_RandomDungeons.rb
- **Core:** generación de dungeons procedurales.
- **Encontrarás:** el algoritmo que arma el mapa a partir de `DungeonParameters`/`DungeonTileset`.
- **Consulta si:** mazmorras generadas (datos en `026/020`-`021`).

### 010_Events Utilities.rb
- **Core:** **(custom, Zik)** extensiones de eventos por comentarios/nombre.
- **Encontrarás:** utilidades que amplían lo que un evento puede hacer mediante comentarios o su nombre.
- **Consulta si:** comportamiento extendido de eventos del mapa (custom).

### 011_Fancy Camera.rb
- **Core:** **(custom)** cámara suave/avanzada.
- **Encontrarás:** desplazamiento de cámara más fluido y efectos de seguimiento.
- **Consulta si:** comportamiento de la cámara del overworld.

## 038_Overworld visuals
Capa visual del overworld (no afecta la lógica).

### 002_Overworld_Weather.rb
- **Core:** clima visual del mapa (partículas, tiles, tono).
- **Encontrarás:** el renderizado del clima de overworld definido en `025/013`.
- **Consulta si:** apariencia del clima en el mapa.

### 003_Overworld_Overlays.rb
- **Core:** overlays/niebla sobre el mapa.
- **Encontrarás:** capas superpuestas (niebla, oscuridad, etc.).
- **Consulta si:** una capa visual sobre el mapa.

### 004_Overworld_MapTransitionAnims.rb
- **Core:** animaciones al cambiar de mapa.
- **Encontrarás:** las transiciones de entrada/salida de mapas.
- **Consulta si:** efecto al pasar de un mapa a otro.

### 005_Exit Arrows.rb
- **Core:** **(custom)** flechas de salida del mapa.
- **Encontrarás:** los indicadores de borde de mapa.
- **Consulta si:** flechas que marcan salidas.

### 006_Overworld Shadows EX.rb
- **Core:** **(custom)** sombras avanzadas de personajes.
- **Encontrarás:** sombras dinámicas mejoradas en el overworld.
- **Consulta si:** sombras del overworld (ver también `014/010`).

### 007_Barras entrenadores.rb
- **Core:** **(custom)** sensor de entrenadores / barras-VS al ser visto.
- **Encontrarás:** el indicador visual de cuando un entrenador detecta al jugador.
- **Consulta si:** señal/animación de "¡te han visto!".

## 039_Battle triggering
El puente overworld→combate: cómo se inician los combates y los encuentros.

### 002_Overworld_BattleStarting.rb
- **Core:** **arranque de combates** (`WildBattle.start`, `TrainerBattle.start`, `setBattleRule`, `prepare_battle`).
- **Encontrarás:** las clases que lanzan combates salvajes/de entrenador, la tabla de **reglas de batalla**, el montaje del entorno/clima/fondo y los resultados.
- **Consulta si:** iniciar/controlar un combate desde código, reglas de batalla (ver skill `battles-runtime`).

### 003_Overworld_BattleIntroAnim.rb
- **Core:** transición/animación previa al combate.
- **Encontrarás:** la elección de la transición según contexto y las animaciones VS especiales.
- **Consulta si:** la animación de entrada al combate.

### 004_Overworld_WildEncounters.rb
- **Core:** **encuentros salvajes** (probabilidad por paso, repelente, modificadores por habilidad, generación del Pokémon).
- **Encontrarás:** el algoritmo de aparición, la elección de especie/nivel y la creación del salvaje.
- **Consulta si:** encuentros salvajes, repeles, generación de salvajes (handlers `:on_wild_pokemon_created`).

### 005_Overworld_EncounterModifiers.rb
- **Core:** modificadores de encuentros.
- **Encontrarás:** ajustes que alteran qué/cómo aparece (eventos, condiciones especiales).
- **Consulta si:** forzar/alterar encuentros (p.ej. shiny garantizado, especie fija).

### 006_Overworld_RoamingPokemon.rb
- **Core:** Pokémon roaming (legendarios que vagan).
- **Encontrarás:** el registro de roamers, su movimiento por mapas y el disparo de su combate.
- **Consulta si:** Pokémon errantes.
