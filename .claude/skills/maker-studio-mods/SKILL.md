---
name: maker-studio-mods
description: >
  Crear mods JavaScript para el EDITOR Maker Studio (no para el juego): manifest.json + index.js,
  punto de entrada activate(ctx)/deactivate(), superficies de ctx (editor/map/tileset/events/ui/bus/
  fs/storage/i18n/selectors/projectData…), bus de eventos, registrar menús/paneles/herramientas/
  overlays/atajos/comandos/comandos de evento, dependencias (requires), comandos Tauri, publicar al
  Marketplace y troubleshooting. Usar al escribir, depurar o publicar un mod del editor.
  Triggers: "mod de Maker Studio", "manifest.json", "ctx.ui", "ctx.map", "registerMenuItem",
  "registerCommand", "panel del editor", "publicar mod", "Mod Manager", "ctx.bus".
---

# Mods de Maker Studio (JS)

Un mod **extiende el editor de escritorio**, no el juego. JS puro (ESM), corre en el contexto web del editor (Tauri). Para extender el JUEGO es el plugin Ruby → skill **maker-studio-plugin**. Para entender el editor → skill **maker-studio**.

Docs fuente: `..\Maker Studio\Docs for mods\` (`docs/api-reference.md`, `docs/quick-reference.md` y `docs/es/`, `docs/mod-api.d.ts`, `examples/mods/`, `TUTORIAL.md`, `PUBLISHING.md`, `SUBMISSION.md`, `schema/`). Referencia JS aplicada al sandbox: `references/javascript-for-mods.md`. **Verificar la API real en `mod-api.d.ts`/`api-reference.md` antes de usar cualquier método — no inventar firmas.** API actual: **1.0.0**.

## Estructura mínima

```
<modId>/
  manifest.json   # metadatos
  index.js        # código (entry = campo "main")
  README.md       # opcional
```

Ubicaciones (el editor escanea al abrir proyecto o al pulsar Reload):
- **Proyecto**: `<game>/Plugins/MakerStudio/003_Editor/Mods/<modId>/` (viaja con el juego).
- **Global**: `%APPDATA%/maker-studio/Mods/<modId>/` (todos los proyectos). En macOS `~/Library/Application Support/maker-studio/Mods/`, Linux `~/.local/share/maker-studio/Mods/`.
- Proyecto **eclipsa** a global con el mismo id (insignia: azul = proyecto, púrpura = global).

## manifest.json

```json
{
  "id": "com.autor.mi-mod",
  "name": "Mi Mod",
  "version": "1.0.0",
  "apiVersion": "1.0.0",
  "main": "index.js",
  "authors": [{ "name": "Autor", "url": "https://..." }],
  "description": "Una frase.",
  "permissions": ["ui.toasts", "fs.write.project"],
  "requires": []
}
```

Requeridos: `id` (reverse-DNS, único, `^[a-zA-Z0-9._-]+$`), `name`, `version` (semver), `apiVersion` (mayor debe coincidir con la del editor o no carga), `main`. `permissions` declara cada capacidad que usa el código (los revisores del Marketplace lo cotejan). `requires` → ver Dependencias abajo.

## Punto de entrada

```js
export function activate(ctx) {
  // obligatorio. corre una vez al cargar el mod.
  ctx.log.info("cargado");
  ctx.bus.on("map.loaded", (e) => ctx.ui.showToast({ message: `Mapa ${e.mapId}` }));
}
export function deactivate() {
  // opcional. los Disposables registrados vía ctx se auto-descartan.
}
```

- ESM puro. **Sin npm/paquetes externos** (specifiers desnudos fallan). Multi-archivo: `import { x } from './utils.js'` — solo relativos `./`/`../`, **extensión `.js` obligatoria**.
- Casi todo lo registrado por `ctx` devuelve un `Disposable` y se limpia solo al descargar; no cerrar paneles en `deactivate` (el hueco persiste a propósito).

## Superficies de `ctx`

`editor` (estado: mapa/capa/herramienta activos, viewport, tema) · `map` (leer/escribir tiles, capas, selección, undo groups) · `tileset` · `shadow` · `fog` / `panorama` / `layerGroups` (grupos de capa de imagen) · `events` (eventos RMXP + comandos personalizados) · `tools` · `menu` · `commands` (registro entre mods) · `ui` (paneles/diálogos/toasts/overlays/menús contextuales/atajos) · `bus` · `fs` (FS con scope) · `storage` (K/V por mod) · `clipboard` (texto del SO) · `stats` · `keybinds` · `i18n` · `selectors` (pickers modales) · `projectData` (listas RPG de solo lectura) · `mods` / `plugins` (consultar instalados) · `log` · `lifecycle`.

## Patrones comunes

```js
// Toast / menú / atajo
ctx.ui.showToast({ message: "Hecho", level: "info" });       // level: info|warn|error
ctx.menu.registerMenuItem({ menu: "Mods", label: "Acción",
  shortcut: "Ctrl+Shift+D",   // binding REAL y reasignable — NO llamar también registerShortcut
  icon: "database", handler: () => {} });

// Eventos del editor
ctx.bus.on("map.tile.changed", (e) => {/* {mapId,layer,x,y,oldId,newId} */});
ctx.bus.on("save.before", async (e) => ({ cancel: true, reason: "no listo" })); // cancelable

// Tiles (pasan por undo/redo del editor)
const id = ctx.map.readTile(mapId, layer, x, y);
ctx.map.writeTile(mapId, layer, x, y, id, "pintar");
ctx.map.beginUndoGroup("Op"); /* writes */ ctx.map.endUndoGroup();

// Panel acoplable (DOM vanilla; usar var(--token) del tema del editor)
ctx.ui.registerPanel({ id: "mi.panel", title: "Panel", defaultPosition: "right",
  render(host) { host.innerHTML = `<div style="color:var(--text-primary)">hola</div>`;
                 return () => {/* cleanup */}; } });

// Overlay de canvas (CSS vars NO aplican aquí → ctx.editor.theme())
ctx.ui.registerOverlay({ id: "mi.ov", zOrder: 10, render(c, info) {
  const px = (tx*info.tileSize - info.viewportX)*info.zoom; c.fillRect(px, py, ...); } });

// Herramienta / menú contextual
ctx.tools.registerTool({ id: "mi.tool", label: "Tool", icon: "✦", onPointerDown(ev) {} });
ctx.ui.registerContextMenuItem({ context: "map-tile", label: "X", handler: (info) => {} });
// contextos: map-tile|map-event|tile-palette|tile-palette-extra|layer|map-tree|event-editor

// Selectors (pickers nativos; resuelven a valor o null)
const item = await ctx.selectors.pickItem({ title: "Recompensa" });
const g = await ctx.selectors.pickGraphic("Characters", { fields: ["direction","pattern"] });

// Persistencia / archivos (paths relativos; .. y absolutos → PermissionDeniedError)
await ctx.storage.set("k", v); const v = await ctx.storage.get("k", def);
const txt = await ctx.fs.readModFile("data.json");        // carpeta del mod
const m = await ctx.fs.readProjectFile("Data/Map001.rxdata"); // raíz del juego (permiso fs.project)

// i18n: keys = cadenas fuente en inglés
ctx.i18n.addTranslations("es", { "Done": "Hecho" });
ctx.ui.showToast({ message: ctx.i18n.t("Done") });

// Comandos entre mods (ids globales → namespacéalos con tu id)
ctx.commands.register("mi-mod.x", async (a) => {});
await ctx.commands.execute("otro-mod.y", arg);

// Consultar mods/plugins en runtime (deps blandas / feature detection)
if (ctx.mods.isActive("com.x.core")) {}
const fp = ctx.plugins.get("Following Pokemon EX"); // meta.txt del plugin Ruby

// Comando Tauri directo (capacidades fuera de ctx)
const bytes = await window.__TAURI__.core.invoke("read_binary_file", { path });
```

## Bus de eventos (escuchar vía `ctx.bus.on`)

`map.loaded`/`unloaded` · `map.tile.changed` · `map.batch.changed` · `tool.activated` · `selection.changed` · `save.before`(cancelable)/`save.after`/`save.failed` · `mod.loaded`/`unloaded` · `panel.opened`/`closed` · `layer.changed` · `viewport.changed` · `hover.changed` · `clipboard.changed` · `brush.changed` · `tileset.changed` · `undo`/`redo` · `event.changed` · `paste.before`(cancelable) · `stats.changed` · `keybind.changed` · `locale.changed`. Cancelables: devolver `{ cancel: true, reason }` (puede ser async; timeout 5 s/handler). Wrappers de conveniencia: `ctx.lifecycle.*`.

## Comandos de evento personalizados (`ctx.events.registerCommand`)

Añade un comando que los map makers insertan en páginas de evento (página de mod con icono 🧩). **Sin dispatcher de runtime**: el formulario es un *constructor de un comando Script* — `script(params)` devuelve Ruby literal que se guarda como comando Script **código 355** y corre en el juego como cualquier script. Esto mantiene el round-trip del `.rxdata` y pasa `validateEvent`. `parse(text)` recupera params para reeditar; `summary(params)` etiqueta la línea. Campos: `number, text, select, checkbox, switch, variable, coordinate, record, event, graphic, audio` (omitir `fields` → textarea de script libre). El Ruby que emitas debe existir/funcionar en el juego (lo provee el plugin o tu parche `[ZBOX]`) → skill **maker-studio-plugin**.

## Tema y CSS

Paneles y diálogos custom renderizan en el DOM del editor → las variables de tema cascadean. Usar `var(--bg-primary|--text-primary|--border|--accent|--danger|…)` y `font-family: inherit` (13px) para igualar y cambiar claro/oscuro solo. En overlays de canvas las CSS vars NO aplican: ramificar con `ctx.editor.theme()`.

## Dependencias (`manifest.requires`)

Array unificado, discriminado por `type`. Mod: `{ "type": "mod", "id": "…", "version": "^1.2.0" }` (orden topológico; id ausente bloquea; rango registrado, no forzado en v1). Plugin Ruby de Essentials: `{ "type": "plugin", "name": "…", "enforcement": "plugin"|"pluginAndVersion"|"none", "version": "…", "versionCheck": "greaterOrEqual"|"exact"|"compatible", "url": "…" }`. Para deps **blandas** usar `ctx.mods`/`ctx.plugins` en runtime, no `requires`.

## Publicar al Marketplace (flujo)

1. Repo **público** en GitHub. 2. `manifest.version` = tag (sin `v`). Tag `vX.Y.Z`. 3. Release con asset zip `<modId>-<version>.zip` (la plantilla `templates/publish.yml` lo zipea, hashea SHA-256 y publica). 4. PR a `Toskan4134/maker-studio-mods` añadiendo la entrada a `index.json` con `version`, `assetName`, `sha256` (obligatorios — el editor verifica el hash y rehúsa si no coincide), `permissions`, `apiVersion`. 5. CI valida schema/duplicados/asset; merge → live en ~1 h (caché del índice). El pin SHA-256 es la frontera de seguridad: una release nueva no llega a usuarios hasta el merge del PR. Detalle: `TUTORIAL.md` (paso a paso) + `PUBLISHING.md` + `SUBMISSION.md`.

## Probar y depurar

- **Reload** en la fila del mod (Mod Manager) reescanea sin reiniciar. Estado `error` → expandir la fila para el log; consola completa con `Ctrl+Shift+I`.
- Errores típicos: JSON inválido (coma final), falta `export function activate`, `apiVersion` errónea, importar paquetes (solo ESM relativo + `window.__TAURI__`), nombres de evento case-sensitive (`map.loaded`), id de comando/herramienta sin namespace (colisión), path traversal `..`. Detalle: `docs/es/troubleshooting.md`.
- Para autocompletado: copiar `mod-api.d.ts` junto al mod y `/** @param {import("./mod-api").ModContext} ctx */`.

## Relación con el plugin Ruby

Mods que persisten datos en el mapa (grupos de capa custom vía `ctx.layerGroups`, comandos de evento que emiten Ruby) dependen del plugin `MakerStudio` para verse/correr en el juego. Los grupos de capa custom se guardan en el `.rxdata` y los renderiza el plugin **aunque el mod no esté instalado**. Si el Ruby que emite un comando de evento no existe en el motor, hace falta agregarlo al plugin → skill **maker-studio-plugin**.
