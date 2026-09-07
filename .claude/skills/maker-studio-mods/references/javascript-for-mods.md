# JavaScript para mods de Maker Studio

Referencia rápida del JS que realmente importa al escribir mods del editor. El sandbox es **ESM moderno en el contexto web de un Tauri** (Chromium). Condensado y orientado al entorno; base general inspirada en [33-js-concepts](https://github.com/leonardomso/33-js-concepts) (skill original en `..\Maker Studio\Skills\javascript-mastery\`). El agente es Ruby-first — esto cubre las diferencias que muerden.

## El entorno

- **ESM puro**: `export function activate(ctx)`, `import { x } from './u.js'`. NADA de `require`, npm ni CDN. Specifiers solo relativos (`./`, `../`) y con extensión `.js`.
- **Navegador, no Node**: hay `window`, `document`, `fetch`, `localStorage`, `console`, `setTimeout`. NO hay `fs`, `process`, `Buffer`. Para disco/SO: `ctx.fs`, `ctx.storage` o `window.__TAURI__.core.invoke(...)`.
- **CommonJS** (`module.exports`) solo funciona en mod de **un archivo** (fallback `new Function`); para multi-archivo, ESM obligatorio.

## Trampas Ruby → JS

| Ruby | JS |
|------|-----|
| `nil` | `null` **y** `undefined` (dos cosas) |
| `==` | usar **`===`** (sin coerción). `==` coacciona: `"5" == 5` true |
| `elsif` | `else if` |
| `puts` | `console.log` / `ctx.log.info` |
| `"#{x}"` | `` `${x}` `` (template literals, backticks) |
| símbolos `:x` | strings `"x"` |
| `arr.each` | `arr.forEach` / `for..of` |
| `hash.each` | `Object.entries(o).forEach(([k,v]) => …)` |
| último valor = retorno | hay que `return` explícito (salvo arrow de una expresión) |
| `nil?`/`&.` | `x == null` / `x?.prop` (optional chaining) |
| `a || b` (nil/false) | `a ?? b` solo cubre null/undefined; `||` también `0`/`""`/`false` |

Falsy en JS (8): `false, 0, -0, 0n, "", null, undefined, NaN`. Ojo: `[]` y `{}` son **truthy**.

## Funciones y `this`

```js
const f = (a, b) => a + b;            // arrow, retorno implícito de UNA expresión
const g = (x) => { return x * 2; };   // con bloque → return explícito
```

- **Arrow** no tiene `this` propio (lo hereda del scope léxico). En callbacks de `ctx`/DOM usa arrows para no perder `this`.
- `function () {}` tiene `this` dinámico según cómo se llame — fuente de bugs. Evítalo en handlers salvo que necesites el `this` del llamador.

## Closures

Una función recuerda el scope donde se definió. Patrón para estado privado en un mod:

```js
export function activate(ctx) {
  let contador = 0;                       // vive mientras el mod esté cargado
  ctx.menu.registerMenuItem({ menu: "Mods", label: "Sumar",
    handler: () => ctx.ui.showToast({ message: `${++contador}` }) });
}
```

`let`/`const` (block-scoped) siempre; nunca `var`. `const` = no reasignable (pero el objeto sigue mutable).

## Async — el modelo

El editor es asíncrono. Muchos métodos devuelven `Promise` (`ctx.selectors.*`, `ctx.fs.*`, `ctx.storage.*`, `ctx.ui.show*Dialog`, `invoke`).

```js
async function run(ctx) {
  const item = await ctx.selectors.pickItem();   // pausa hasta resolver
  if (!item) return;                             // null = canceló
  await ctx.fs.writeModFile("last.json", JSON.stringify(item));
}
```

- `await` solo dentro de `async`. `activate` puede ser `async`.
- Errores: `try { await … } catch (e) { ctx.log.error(e); }`. Una Promise rechazada sin catch revienta silenciosa.
- Paralelo: `const [a, b] = await Promise.all([p1, p2]);`.
- Handlers de bus cancelables (`save.before`/`paste.before`) **pueden ser async** y devolver `{ cancel, reason }` (timeout 5 s).
- Microtasks (Promises) corren antes que macrotasks (`setTimeout`). No asumas orden entre `setTimeout(0)` y `.then`.

## Datos

```js
arr.map(x => x.id); arr.filter(x => x.age >= 18); arr.reduce((s,x) => s+x, 0);
arr.find(x => x.name === "a"); arr.some(...); arr.every(...);
const { id, name = "?" } = obj;            // destructuring + default
const copia = { ...obj, extra: 1 };        // spread (copia superficial)
Object.keys(o); Object.values(o); Object.entries(o);
JSON.parse(str); JSON.stringify(val);      // .rxdata/config van por JSON
```

## DOM (paneles y diálogos custom)

`render(host)` recibe un `HTMLElement`. DOM vanilla — cualquier framework o ninguno.

```js
render(host) {
  host.innerHTML = `<button class="b">OK</button>`;
  const btn = host.querySelector(".b");
  const onClick = () => ctx.ui.showToast({ message: "click" });
  btn.addEventListener("click", onClick);
  return () => btn.removeEventListener("click", onClick); // cleanup opcional
}
```

- Igualar el tema con `var(--token)` (skill maker-studio-mods → Tema y CSS). `font-family: inherit`.
- `innerHTML` con datos de usuario = riesgo XSS; para texto usa `el.textContent` o escapa.
- En overlays de canvas el `ctx` del render es un `CanvasRenderingContext2D` (no confundir con el `ctx` del mod) y las CSS vars NO aplican.

## Errores comunes en el sandbox

- Importar `@tauri-apps/api` → usar `window.__TAURI__.core.invoke`.
- Olvidar `await` → trabajar con una `Promise` en vez del valor (`[object Promise]`).
- Nombres de evento del bus son case-sensitive: `"map.loaded"`, no `"Map.Loaded"`.
- `==` accidental, o `||` donde querías `??` (pierde `0`/`""`).
- Mutar un objeto compartido del editor en vez de copiarlo con `{...}` / `[...]`.
