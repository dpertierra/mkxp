# Reference · Pokémon, items, mochila y entrenadores

Carpetas: `041_Items`, `043_Pokemon`, `044_Pokemon-related`, `046_Trainers and player`. Para manipular Pokémon en runtime usa la skill `pokemon-api`; para datos PBS, `pbs-data`.

## 041_Items
Mochila e implementación de efectos de items.

### 002_Item_Utilities.rb
- **Core:** utilidades generales de items (añadir/quitar de `$bag`, consultas).
- **Encontrarás:** helpers de inventario y de elección de items.
- **Consulta si:** dar/quitar items o consultar la mochila.

### 003_Item_Effects.rb
- **Core:** **efecto de cada item fuera de combate** (sistema `ItemHandlers`).
- **Encontrarás:** los handlers que definen qué hace un item al usarlo en el campo o sobre un Pokémon.
- **Consulta si:** crear/editar el efecto de un item (patrón a copiar para `ItemHandlers`; ver skill `plugin-architecture`).

### 004_Item_BattleEffects.rb
- **Core:** efecto de items usados dentro del combate.
- **Encontrarás:** los handlers de uso de item en batalla (curas, X-items, etc.).
- **Consulta si:** un item con efecto en combate.

### 005_Item_Phone.rb
- **Core:** item Teléfono/Pokégear.
- **Encontrarás:** la lógica del item que abre el teléfono.
- **Consulta si:** integración del item del teléfono.

### 006_Item_PokeRadar.rb
- **Core:** Pokéradar.
- **Encontrarás:** el mecanismo de cadenas de encuentros del radar.
- **Consulta si:** Pokéradar/cadenas de hierba.

### 007_Item_Mail.rb
- **Core:** sistema de cartas (Mail).
- **Encontrarás:** crear/leer/transferir cartas.
- **Consulta si:** Mail.

### 008_Item_Sprites.rb
- **Core:** iconos de items.
- **Encontrarás:** carga del icono de un item (48×48).
- **Consulta si:** mostrar el icono de un item.

### 009_PokemonBag.rb
- **Core:** la clase de la Mochila (`$bag`) y sus bolsillos.
- **Encontrarás:** el almacenamiento por bolsillo, límites y orden.
- **Consulta si:** estructura de la mochila o bolsillos.

### 010_Pokevial.rb
- **Core:** **(custom)** Pokévial — curación rápida del equipo con cargas.
- **Encontrarás:** el item custom que cura el equipo y su persistencia.
- **Consulta si:** el sistema Pokévial.

## 043_Pokemon
La clase `Pokemon` (un Pokémon individual). Detalle de API en skill `pokemon-api`.

### 002_Pokemon.rb
- **Core:** **clase `Pokemon`** (stats, nivel/exp, IVs/EVs, naturaleza, habilidad, item, moves, género, shiny, felicidad, owner, evolución).
- **Encontrarás:** el constructor y casi todos los getters/setters de un Pokémon, más la maquinaria de evolución y recálculo de stats.
- **Consulta si:** crear o editar un Pokémon, o entender cualquier propiedad suya.

### 003_Pokemon_MegaEvolution.rb
- **Core:** Mega Evolución y Reversión Primal (como cambios de forma).
- **Encontrarás:** detección y cambio a/desde formas Mega/Primal.
- **Consulta si:** Mega/Primal de un Pokémon.

### 004_Pokemon_ShadowPokemon.rb
- **Core:** estado Oscuro de un Pokémon (medidor del corazón, Hiper Modo, purificación).
- **Encontrarás:** convertir en Shadow, gestionar el medidor y los movimientos oscuros.
- **Consulta si:** Pokémon Oscuros.

### 005_Pokemon_Move.rb
- **Core:** un movimiento conocido por el Pokémon (`Pokemon::Move`: PP, PP Ups).
- **Encontrarás:** la representación de un move aprendido y su delegación a los datos del move.
- **Consulta si:** PP, PP Ups o la instancia de un move conocido.

### 006_Pokemon_Owner.rb
- **Core:** datos del entrenador original (`Pokemon::Owner`).
- **Encontrarás:** ID/nombre/género/idioma del OT y su creación.
- **Consulta si:** propiedad de origen de un Pokémon (foráneo, OT).

## 044_Pokemon-related
Sistemas alrededor del Pokémon.

### 002_FormHandlers.rb
- **Core:** **`MultipleForms`** — comportamiento de formas alternativas (Castform, Rotom, Burmy…).
- **Encontrarás:** el registro de handlers de forma (`getForm`, `onSetForm`, `alterBitmap`…) y los casos por especie.
- **Consulta si:** una forma cambia según condiciones, o registrar una forma nueva.

### 003_ShadowPokemon_Other.rb
- **Core:** soporte adicional de Pokémon Oscuros (purificación, fuera de combate).
- **Encontrarás:** flujos de purificación y utilidades Shadow no ligadas a la clase Pokemon.
- **Consulta si:** cámara de purificación o lógica Shadow del overworld.

### 004_Pokemon_Sprites.rb
- **Core:** sprites/iconos de Pokémon en pantalla.
- **Encontrarás:** los sprites animados de Pokémon usados por UIs y combate.
- **Consulta si:** mostrar el sprite/icono de un Pokémon.

### 005_PokemonStorage.rb
- **Core:** **el PC / almacenamiento** (`$PokemonStorage`): cajas y operaciones.
- **Encontrarás:** estructura de cajas, depositar/retirar/borrar, límites.
- **Consulta si:** manipular el PC (ver skill `pokemon-api`).

### 006_FormChanger.rb
- **Core:** **(custom)** cambiador de forma regional desde evento.
- **Encontrarás:** el flujo de selección de party para alternar formas regionales.
- **Consulta si:** un NPC que cambia formas regionales.

### 007_Peinados_Furfrou.rb
- **Core:** **(custom)** peluquero de Furfrou.
- **Encontrarás:** el NPC que cambia el corte/forma de Furfrou.
- **Consulta si:** formas de Furfrou.

### 008_Trade_From_PC.rb
- **Core:** **(custom)** intercambio desde el PC.
- **Encontrarás:** el flujo de trade integrado en el almacenamiento.
- **Consulta si:** intercambios desde el PC.

## 046_Trainers and player
Clases de entrenador y del jugador.

### 002_Trainer_Class.rb
- **Core:** clase base `Trainer` y `NPCTrainer` (equipo, tipo, ID, items, helpers de party/PC).
- **Encontrarás:** construcción de entrenadores, consultas y manipulación de su party, y el helper de "combate espejo".
- **Consulta si:** crear un entrenador dinámico o operar sobre su equipo (ver skill `battles-runtime`).

### 003_Trainer_LoadAndNew.rb
- **Core:** cargar un entrenador del PBS y registrar nuevos (debug).
- **Encontrarás:** la conversión de datos PBS a entrenador de combate y los flujos de alta de entrenador.
- **Consulta si:** instanciar un entrenador definido en PBS o registrar uno nuevo en debug.

### 004_Trainer_Sprites.rb
- **Core:** sprites de entrenador (frontal/espalda/charset).
- **Encontrarás:** la carga de gráficos del tipo de entrenador.
- **Consulta si:** mostrar el sprite de un entrenador.

### 005_Player.rb
- **Core:** clase `Player` (`$player`): dinero, monedas, BP, medallas, outfit, flags, Pokédex.
- **Encontrarás:** los atributos del jugador y operaciones (recibir medalla, dinero, etc.).
- **Consulta si:** dinero/medallas/outfit/estado del jugador.

### 006_Player_Pokedex.rb
- **Core:** la Pokédex del jugador (visto/capturado, contadores).
- **Encontrarás:** registro de especies vistas/capturadas y estadísticas de dex.
- **Consulta si:** marcar/consultar visto-capturado o datos de Pokédex del jugador.
