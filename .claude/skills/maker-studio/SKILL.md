---
name: maker-studio
description: >
  Qué es Maker Studio y cómo funciona: editor de mapas de escritorio (Tauri) que reemplaza el editor
  de RPG Maker XP leyendo/escribiendo .rxdata directo, MÁS el plugin Ruby MakerStudio que renderiza
  sus extras en el juego. Modelo de datos (capas nativas vs extendidas, @extended_layers, sombras,
  fog/panorama), UI, herramientas, map versions, editor de eventos/tilesets/scripts, simulador, y
  funciones exclusivas MS. Usar para entender el programa o decidir qué subsistema toca una tarea.
  Triggers: "Maker Studio", "MakerStudio", "editor de mapas", "capa extendida", "map version",
  "sombra del mapa", "fog/panorama", "qué hace el editor", "abre Maker Studio".
---

# Maker Studio — el programa

Editor de mapas moderno (reemplazo del editor de RPG Maker XP) por **Toskan4134**. Dos piezas separadas:

1. **App de escritorio (el editor)** — Tauri v2 + React 19 + TS + Rust. Lee y **escribe `.rxdata` directo** (vía `alox-48`, la lib Marshal de Rust). No bundleada con el plugin: se instala desde [Releases](https://github.com/Toskan4134/maker-studio/releases/latest) y se autoactualiza.
2. **Plugin Ruby `MakerStudio`** — corre en el juego (Essentials/LBdS). Lee los datos extra que el editor incrusta en el `.rxdata` y los renderiza in-game. Sin él, RMXP/Essentials estándar ignora los extras (el mapa sigue cargando, solo sin capas extendidas/sombras/fog/etc.). Detalle: skill **maker-studio-plugin**.

Para crear/extender **mods del editor** (JS) → skill **maker-studio-mods**. Para tocar el **plugin Ruby** → skill **maker-studio-plugin**.

## Mapa de la documentación (rutas relativas a `Essentie/`)

| Recurso | Ruta |
|---------|------|
| Docs del editor (en + `es/`) | `..\Maker Studio\Docs Maker Studio\docs\` |
| Docs de mods (API, ejemplos, schema, plantillas) | `..\Maker Studio\Docs for mods\` |
| Fuente del plugin Ruby (versión más completa) | `..\Maker Studio\MakerStudio\` |
| Skills externas de referencia (architect, javascript-mastery) | `..\Maker Studio\Skills\` |
| Integración empaquetada de ejemplo `[LBDS1.2.0]` | `..\Maker Studio\Docs Maker Studio\Integrations\` |

Docs del editor en español: `docs\es\` (getting-started, interface-guide, tools, layers, shadows, events-editor, game-simulator, tileset-editor, map-management, map-versions, database, scripts, marketplace, keyboard-shortcuts). Leer la doc real antes de afirmar comportamiento — no inventar.

## Modelo de datos — clave

- **Mapas**: `Data/MapXXX.rxdata` (RPG::Map Marshal estándar de RMXP). El editor lo parchea in-place.
- **Capas nativas (0–2)**: `Table3` estándar de RMXP, tileset del mapa, IDs de tile normales. Lo único que RMXP estándar lee y renderiza.
- **Capas extendidas (3+)**: ilimitadas, incrustadas como string JSON `@extended_layers` **dentro del mismo `.rxdata`**. Invisibles para RMXP; el plugin las lee. Soportan: tiles entre tilesets, autotiles con nombre (más allá de las 8 ranuras de RMXP), efectos por tile (opacity, rotation, hue, saturation, lighting, flip H/V) y override de paso/prioridad/terreno por tile.
- **Autotiles expandidos**: `@expanded_autotiles` (JSON) en objetos de `Tilesets.rxdata`.
- **Sombras**: solo tiles fuente + config (sin PNG). Bitmap **generado en runtime**; las sombras de autotiles animados se animan.
- **Fog / Panorama / grupos de capa personalizados**: pilas de capas de imagen guardadas en el mapa; fog encima de los tiles (prio 3000), panorama debajo (prio -1000), grupos de mod a prioridad propia. Persisten en el `.rxdata` → se renderizan in-game **aunque el mod que los creó no esté instalado** (los lee el plugin).
- **Map versions**: varios estados del mismo mapa (mismo `map_id`, mismo tamaño) guardados como adiciones en el mismo archivo; se cambian in-game con un switch/variable. Detalle abajo.
- **Backups**: cada guardado copia el original a `Data/map-backups/` como `<archivo>.<timestamp>.backup` (10 más recientes por archivo). Escrituras atómicas (temp + swap). Autoguardado cada 5 min en perfil de usuario (nunca toca los archivos reales) + recuperación tras fallo.
- **Scripts**: el editor de Scripts lee/escribe `Data/Scripts.rxdata` (backup a `.bak`). Si el proyecto ya extrajo scripts a `Data/Scripts/`, usar "Open in VSC".

## Funciones exclusivas de MS (requieren el plugin in-game) — insignia **MS**

Capas extendidas · efectos por tile · autotiles con nombre/extra · grupos fog/panorama/personalizados · sombras · **map versions** · battleback por-versión · comandos de evento de fog (Change Map Settings tipo Fog, Fog Color/Opacity) · acciones **Set Frame** de move route. Sin el plugin, el juego las ignora; el `.rxdata` sigue abriendo en RMXP estándar. Las capas nativas y todo lo estándar de RMXP NO llevan insignia. Conmutar con View → Show MS-Exclusive Indicators.

## Interfaz (resumen)

- **Menús**: File, Edit (incl. Advanced Clipboard + Cross-Project Clipboard), View (Panels, Show Grid/Collision/Events, Dim, Language, Layout), Map (New/Duplicate/Versions/Resize-Shift/Change Tileset/Change Battleback/Export/Import), Tools (herramientas, Brush Editor, Database, Scripts), Mods (Mod Manager + aportes de mods), Help.
- **Paneles acoplables** (arrastrables/flotantes): Maps (árbol), Tile Palette, Layers, Tile Properties, Tile Info, Events, Minimap. View → Layout → Refresh Layout restaura.
- **Herramientas**: Brush(B) · Eraser(E) · Fill(F) · Rectangle(R) · Eyedropper(I) · Select(S) · Pan(Espacio). Estampas multi-tile, Custom Shape Brush, pintado de línea (Shift+clic), bloqueo por eje (Ctrl+arrastrar). En la capa de eventos solo Brush/Eraser/Select/Pan.
- **Idioma**: English / Español (mods pueden añadir más). Términos técnicos en inglés a propósito (tile, tileset, autotile, script, mod, BGM/SE).

## Subsistemas (cada uno con su doc en `docs/es/`)

| Subsistema | Qué es |
|------------|--------|
| **Layers** | Nativas 0–2 (RMXP) vs extendidas 3+; panel de capas con visibilidad/opacidad/orden; grupos fog/panorama/custom |
| **Shadows** | Sombras dinámicas desde tiles seleccionados; Direction/Height/Tint/Opacity/Offset, 3D shadow, animadas; runtime, sin PNG |
| **Events editor** | CRUD estilo RMXP multipágina, ~45 comandos con formularios tipados, bloques auto-cerrados, Set Move Route con acciones Frame (MS) |
| **Tileset editor** | Edita passage/priority/bush/counter/terrain tag por tile (escribe en `Tilesets.rxdata`); autotiles aparte; terrain tags 0–17 + custom de mods |
| **Map versions** | Estados intercambiables del mismo mapa por switch/variable; in-place, sin Transfer Player |
| **Map management** | New/Duplicate/Resize-Shift (anclaje 9 pos, wrap toroidal)/Change Tileset/Panorama+Battleback/Export JSON·PNG·GIF·WebP/Import JSON/Set Player Start/Run |
| **Scripts editor** | Ver/editar `Scripts.rxdata` con resaltado Ruby, buscar/reemplazar global; o abrir en VS Code |
| **Database** | Pestañas Tilesets/Switches/Variables (+ editor de tilesets) |
| **Game simulator** | "Sim Map" — corre el mapa en el editor con input del jugador para probar sin lanzar el juego |
| **Marketplace** | Mods → Mod Manager → Marketplace; instala mods firmados por SHA-256 desde el registro |

## Map versions — detalle

- Mismo `map_id` y tamaño que el base; todo (tiles, eventos, fog, panorama, sombras) cambia in-place.
- Cada versión enlaza a **Switch ON** o **Variable == valor**. Resolución por número (v1, v2…); gana la primera cuyo selector coincida; si ninguna, base (v0). Solape → aviso ⚠.
- Estado vive en el switch/variable → se guarda en la partida del jugador. Self-switches A–D se **comparten** entre versiones (mismo map_id). Eventos autorun/parallel de la versión vieja se detienen al cambiar.
- Runtime soportado en familia v21.1 (LBDS 1.1/1.2 y Essentials vanilla v21.1). Editar versiones funciona siempre.

## Flujo de trabajo típico

1. Abrir proyecto (carpeta con `Game.exe` + `Data/`). 2. Abrir mapa (doble clic en el árbol). 3. Elegir tile + capa, pintar. 4. **Ctrl+S** (escribe `.rxdata`, backup automático). 5. Probar: **Run** (lanza `Game.exe` en debug) o **Sim Map**.

In-game (debug, F9): **"Maker Studio..." → "Open Maker Studio"** lanza la app con el proyecto actual; **"Reload Map Data"** hot-recarga el mapa (limpia caché, relee `.rxdata` + capas extendidas, refresca renderer) sin reiniciar. Al cargar partida (Continue), el plugin refresca el mapa desde disco automáticamente.

## Compatibilidad

`.rxdata` editado por Maker Studio sigue abriendo en RPG Maker XP estándar (los extras son adiciones puras invisibles). Las funciones MS solo se ven in-game con el plugin instalado. El editor edita esos extras independientemente de qué build distribuyas.
