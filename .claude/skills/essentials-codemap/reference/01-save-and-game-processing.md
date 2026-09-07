# Reference · Guardado y procesamiento del juego

Carpetas: `007_Save data`, `009_Game processing`, `012_Switches and Variables`, `024_Data`.

## 007_Save data
Sistema de guardado. Define **qué** se persiste y **cómo** se migra entre versiones.

### 002_SaveData.rb
- **Core:** registro central del sistema de guardado y ruta del archivo.
- **Encontrarás:** el contenedor que lista todas las entradas guardables y la constante con la ruta del save.
- **Consulta si:** multisaves, cambiar la ruta del guardado, o entender qué bloque persiste cada cosa.

### 003_SaveData_Value.rb
- **Core:** definición de una entrada guardable.
- **Encontrarás:** la estructura de cada valor a guardar (procs de save/load/new_game).
- **Consulta si:** registras un dato global nuevo que debe guardarse (ver skill `plugin-architecture` § Persistencia).

### 004_SaveData_Conversion.rb
- **Core:** framework de migración de saves.
- **Encontrarás:** la maquinaria que transforma saves antiguos al formato actual.
- **Consulta si:** un save viejo debe seguir cargando tras un cambio de estructura.

### 005_Game_SaveValues.rb
- **Core:** registro concreto de las variables globales del juego.
- **Encontrarás:** la lista de globales que se guardan (`$player`, `$PokemonGlobal`, `$bag`, `$PokemonStorage`, etc.).
- **Consulta si:** quieres ver/añadir qué globales forman parte del save.

### 006_Game_SaveConversions.rb
- **Core:** conversiones concretas entre versiones de save.
- **Encontrarás:** las reglas de migración específicas registradas.
- **Consulta si:** debes añadir una migración para una global cambiada.

### 007_SaveFileCalls.rb
- **Core:** compatibilidad con saves legacy y llamadas de guardado.
- **Encontrarás:** el pegamento que carga formatos antiguos y dispara el guardado.
- **Consulta si:** problemas cargando partidas antiguas.

## 009_Game processing
Arranque, bucle del overworld, intérprete de eventos y el sistema de handlers.

### 002_StartGame.rb
- **Core:** carga inicial, pantalla de título y **`Game.save` / `Game.load`**.
- **Encontrarás:** el flujo de arranque, nueva partida/continuar, y las funciones de guardar/cargar partida.
- **Consulta si:** multisaves, cambiar el arranque, o el flujo de guardar/cargar.

### 003_Scene_Map.rb
- **Core:** bucle principal del overworld (`Scene_Map`).
- **Encontrarás:** el `update` que avanza jugador, eventos, pantalla y dispara encuentros/EventHandlers cada frame.
- **Consulta si:** algo debe ocurrir cada frame en el mapa o entender el ciclo del overworld.

### 004_Interpreter.rb
- **Core:** intérprete de comandos de evento.
- **Encontrarás:** la máquina que ejecuta las páginas de eventos del mapa.
- **Consulta si:** entender cómo se ejecuta un evento o un Common Event.

### 005_Interpreter_Commands.rb
- **Core:** implementación de cada comando de evento.
- **Encontrarás:** el código tras "Mostrar texto", "Condición", "Mover ruta", "Script", etc.
- **Consulta si:** un comando de evento se comporta de cierto modo o quieres añadir uno.

### 006_Event_Handlers.rb
- **Core:** motor del sistema `EventHandlers` (y demás colecciones de handlers).
- **Encontrarás:** la clase que implementa `add`/`trigger` de las colecciones de handlers.
- **Consulta si:** entender cómo funciona internamente un handler o crear una colección propia.

### 007_Event_HandlerCollections.rb
- **Core:** declaración de las colecciones de handlers concretas (`EventHandlers`, `MenuHandlers`…).
- **Encontrarás:** dónde se definen los ganchos globales y los menús registrables.
- **Consulta si:** buscas qué eventos `:on_...` existen o dónde se registran las entradas de menú (ver skill `menu-and-options`).

## 012_Switches and Variables
Estado del juego en memoria.

### 002_Game_Temp.rb
- **Core:** estado efímero no guardado (`$game_temp`).
- **Encontrarás:** flags temporales de sesión (en combate, en almacenamiento, reglas de batalla pendientes, etc.).
- **Consulta si:** necesitas un flag de "estado actual" que no debe persistir; muchas mecánicas leen de aquí.

### 003_Game_Switches.rb
- **Core:** switches del juego (`$game_switches`).
- **Encontrarás:** el contenedor de interruptores booleanos por ID.
- **Consulta si:** leer/escribir un switch (usar constante descriptiva, R12).

### 004_Game_Variables.rb
- **Core:** variables del juego (`$game_variables`).
- **Encontrarás:** el contenedor de variables por ID.
- **Consulta si:** leer/escribir una variable de juego.

### 005_Game_SelfSwitches.rb
- **Core:** self-switches de eventos (`$game_self_switches`).
- **Encontrarás:** el almacén de self-switches por mapa/evento.
- **Consulta si:** manipular el estado A/B/C/D de un evento desde código.

## 024_Data
### 002_GameData.rb
- **Core:** **registro base unificado `GameData`**.
- **Encontrarás:** los métodos comunes a todas las clases de datos (`register`, `get`, `try_get`, `each`, `keys`) y los mixins que comparten.
- **Consulta si:** entender cómo se consultan/registran datos del juego, o creas una clase `GameData` nueva. Las clases concretas están en `025`/`026` (ver `reference/03-gamedata-and-pbs.md`).
