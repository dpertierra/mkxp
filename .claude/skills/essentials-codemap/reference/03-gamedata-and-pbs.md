# Reference · Datos del juego (GameData) y compilador PBS

Carpetas: `025_Hardcoded data`, `026_PBS data`, `065_Compiler`. Para el **formato de los PBS y los campos exactos** usa la skill `pbs-data`; aquí se documenta en qué archivo del motor vive cada clase de datos.

## 025_Hardcoded data
Enumeraciones cerradas (no se editan desde PBS; se registran en código). Cada archivo define una clase `GameData` con sus valores fijos.

- **Core común:** cada uno controla un conjunto de valores válidos para un campo del juego. **Consulta si:** necesitas la lista de valores válidos de ese concepto o añadir uno nuevo (hay que registrarlo aquí, no en PBS).

### 002_GrowthRate.rb
Curvas de experiencia (tablas nivel→exp y fórmulas). Consulta para ritmos de crecimiento.
### 003_GenderRatio.rb
Proporciones de género (umbral de probabilidad). Consulta para ratios macho/hembra.
### 004_EggGroup.rb
Grupos huevo de cría. Consulta para compatibilidad de cría.
### 005_BodyShape.rb
Formas corporales (y su icono en Pokédex). Consulta para búsqueda por forma.
### 006_BodyColor.rb
Colores corporales. Consulta para búsqueda por color.
### 007_Habitat.rb
Hábitats. Consulta para clasificación de hábitat.
### 008_Evolution.rb
**Todos los métodos de evolución** (procs de condición por subida de nivel, item, intercambio, evento, etc., incluidos los custom de Sky). Consulta para añadir/entender un método de evolución (detalle en skill `pbs-data`).
### 009_Stat.rb
Definición de stats, su orden en PBS y abreviaturas. Consulta para el orden de stats o stats de batalla.
### 010_Nature.rb
Naturalezas y sus modificadores de stats. Consulta para efectos de naturaleza.
### 011_Status.rb
Estados alterados (sueño, veneno, quemadura…) e iconos. Consulta para estados.
### 012_TerrainTag.rb
Terrain tags y sus propiedades (surf, pesca, encuentros, hielo…). Consulta para comportamiento de tiles.
### 013_Weather.rb
Climas de overworld (categoría, partículas, tono). Consulta para clima de mapa.
### 014_EncounterType.rb
Tipos de encuentro y su probabilidad base. Consulta para tablas de encuentros.
### 015_Environment.rb
Entornos de batalla. Consulta para el fondo/entorno de combate.
### 016_BattleWeather.rb
Climas dentro del combate. Consulta para clima de batalla.
### 017_BattleTerrain.rb
Terrenos de combate (Eléctrico, Hierba…). Consulta para terrenos.
### 018_Target.rb
Objetivos de movimientos. Consulta para a quién apunta un move.

## 026_PBS data
Clases `GameData` cuyos valores se cargan desde los archivos PBS (texto plano → `.dat`). Cada archivo define el esquema (`SCHEMA`) y la clase de un tipo de dato.

- **Core común:** cada uno controla un tipo de dato editable por PBS. **Encontrarás:** el `SCHEMA` (campos y formatos) y los métodos de la clase. **Consulta si:** necesitas saber qué campos acepta ese PBS o cómo se usa el dato en código (formato exacto: skill `pbs-data`).

### 002_MiscPBSData.rb
Cachés de datos varios (dexes regionales, animaciones de batalla, map infos). Consulta para datos auxiliares cargados de `.dat`.
### 003_TownMap.rb
Datos del mapa de región (`town_map.txt`). Consulta para puntos del mapa regional.
### 004_Type.rb
Tipos elementales y su tabla de eficacias. Consulta para debilidades/resistencias/inmunidades.
### 005_Ability.rb
Datos de habilidades (nombre/descripción/flags). El efecto real está en `034`. Consulta para definir una habilidad.
### 006_Move.rb
Datos de movimientos (tipo, categoría, potencia, target, FunctionCode, flags). Consulta para definir/leer un move.
### 007_Item.rb
Datos de items (bolsillo, precio, usos, flags). El efecto está en `041`. Consulta para definir un item.
### 008_BerryPlant.rb
Fases de crecimiento de bayas. Consulta para crianza de bayas.
### 009_Species.rb
**Clase `Species` (masiva)**: todos los datos de una especie/forma. Consulta para cualquier propiedad de un Pokémon definida en PBS.
### 010_Species_files.rb
Resolución de archivos de especie (sprites front/back/icon/shiny/shadow/egg, gritos, sombra, footprint, super-shiny). Consulta para rutas/nombres de gráficos de Pokémon (ver skill `assets-pipeline`).
### 011_SpeciesMetrics.rb
Posiciones del sprite en batalla y sombra. Consulta para ajustar dónde aparece un Pokémon en combate.
### 012_ShadowPokemon.rb
Datos de Pokémon Oscuros (moveset oscuro, medidor). Consulta para Shadow Pokémon.
### 013_Ribbon.rb
Datos de cintas. Consulta para ribbons.
### 014_Encounter.rb
Tablas de encuentros por mapa/versión. Consulta para encuentros salvajes (lógica en `039`).
### 015_TrainerType.rb
Tipos de entrenador (título, género, dinero, BGM, Poké Ball). Consulta para definir un tipo de entrenador.
### 016_Trainer.rb
Entrenadores individuales y su `to_trainer` (construye el `NPCTrainer` de combate). Consulta para definir un entrenador o instanciarlo (ver skill `battles-runtime`).
### 017_Metadata.rb
Metadata global (dinero inicial, BGMs por defecto, almacenamiento). Consulta para ajustes globales del juego.
### 018_PlayerMetadata.rb
Metadata por personaje jugable (charsets, tipo, inicio). Consulta para apariencia/inicio del jugador.
### 019_MapMetadata.rb
Metadata por mapa (clima, exterior, healing spot, fondo de batalla…). Consulta para propiedades de un mapa.
### 020_DungeonTileset.rb
Tilesets para dungeons procedurales. Consulta para generación procedural (lógica en `037`).
### 021_DungeonParameters.rb
Parámetros de generación de dungeons. Consulta para tamaño/forma de dungeons.
### 022_PhoneMessage.rb
Diálogos telefónicos. Consulta para el teléfono/rematches.

## 065_Compiler
Convierte los PBS de texto a `.dat` y los reescribe. Es la **fuente de verdad de qué campos existen**.

### 002_Compiler.rb
- **Core:** orquestador del compilado.
- **Encontrarás:** el flujo que dispara la compilación de todos los PBS y la generación de `.dat`.
- **Consulta si:** entender cuándo/cómo se recompila o añadir un PBS nuevo al pipeline.

### 003_Compiler_CompilePBS.rb
- **Core:** **parseo y validación de los PBS** (interpreta los códigos de `SCHEMA`).
- **Encontrarás:** cómo se leen las secciones, qué significa cada código de formato y las validaciones que abortan con error.
- **Consulta si:** dudas sobre qué acepta exactamente un campo PBS o por qué el compilador rechaza algo (ver skill `pbs-data` → schema-format).

### 004_Compiler_WritePBS.rb
- **Core:** reescritura de los PBS desde los datos (Debug → "Write all PBS").
- **Encontrarás:** cómo se serializan los datos de vuelta a texto y qué valores se omiten por ser default.
- **Consulta si:** entender cómo se exporta el PBS o qué se considera valor por defecto.

### 005_Compiler_MapsAndEvents.rb
- **Core:** compilado de mapas, conexiones y eventos-entrenador desde comentarios.
- **Encontrarás:** la conversión de comentarios de evento en eventos-entrenador y el procesado de `map_connections`.
- **Consulta si:** generación de eventos de entrenador por comentarios o conexiones de mapa.
