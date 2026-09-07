---
name: debugging-errorlog
description: >
  Triage de errores en Pokémon Essentials 21.1 / La Base de Sky a partir de lo que el USUARIO pega:
  leer un stack trace de errorlog.txt o mkxp.log, identificar el archivo/línea reales (mapeo
  Sección\d+ → nombre de script), reconocer los patrones de crash típicos (NoMethodError sobre nil,
  undefined method/constant por orden de carga o plugin ausente, EventScriptError en un evento, PBS sin
  compilar/GameData inexistente, save incompatible por Marshal, alias duplicado, return dentro de proc,
  stack level too deep) y devolver causa + arreglo + pasos de verificación. El agente NO ejecuta el
  juego: diagnostica sobre el log pegado y la fuente. Basado en 004_Debugging, 999_Main y
  003_Technical/006_PluginManager. Para localizar el archivo culpable ver [[essentials-codemap]]; para
  arreglar con plugin ver [[plugin-architecture]]. Triggers: "me sale este error", "errorlog", "mkxp.log",
  "crashea", "se cierra el juego", "NoMethodError", "undefined method", "uninitialized constant", "stack
  trace", "Exception", "no compila el PBS", "se corrompió el save".
---

# Triage de errores (errorlog / mkxp.log)

Fuentes: `004_Debugging/004_Errors.rb` (`pbPrintException`, formato del log), `004_Debugging/002_PBDebug.rb`
(`Data/debuglog.txt`), `999_Main/999_Main.rb` (`pbCriticalCode`, captura global),
`003_Technical/006_PluginManager.rb` (errores de plugins). Verificar el archivo culpable con
[[essentials-codemap]].

> **El agente no ejecuta el juego** (regla de alcance de AGENTS.md). Aquí trabajas sobre **el log que el
> usuario pega** + la fuente. Si falta el log, pídelo (`errorlog.txt` está en la raíz del juego).

## Flujo de diagnóstico

1. **Identificar el log.** Hay tres orígenes distintos (detalle en `reference/01-reading-logs.md`):
   - **`errorlog.txt`** (raíz del juego) — errores **Ruby del juego**: lo más común. Se **añade al final**
     con separador `=================` + `[fecha]`; el bloque **de abajo del todo** es el más reciente.
   - **`mkxp.log`** — log del **runtime MKXP-Z** (motor): arranque, OpenGL, fuentes, audio, shaders. Para
     "no abre / pantalla negra / falta una fuente". Ver [[mkxp-z-runtime]].
   - **`Data/debuglog.txt`** / `dumplog.txt` — salida de `PBDebug` (IA de combate, etc.), solo con
     `$DEBUG && $INTERNAL`. No es un crash; es traza de depuración.
2. **Leer la cabecera del bloque:** `Excepción: <Clase>` + `Mensaje: <texto>`. La **clase** ya orienta el
   tipo de fallo (`NoMethodError`, `NameError`, `ArgumentError`, `RuntimeError`, `EventScriptError`…).
3. **Leer la Traza (primer frame = culpable).** El primer renglón de `Traza:` es **dónde** ocurrió.
   Convierte `Sección\d+` o la ruta a un archivo de `Data/Scripts/` (mapeo en
   `reference/01-reading-logs.md`) y ábrelo en esa línea. Los frames siguientes son la cadena de llamada.
4. **Casar con un patrón conocido** (`reference/02-common-crashes.md`): la clase + el texto + el archivo
   casi siempre encajan en un patrón típico con causa y arreglo concretos.
5. **Proponer el arreglo** respetando las reglas (R1–R12): si el fallo está en el motor o en un plugin de
   terceros, **parche `[ZBOX]` con `alias_method`** ([[plugin-architecture]]), nunca editar `Data/`.
6. **Entregar pasos de verificación manual** para que el usuario reproduzca y confirme (cargar partida,
   repetir la acción, revisar que el `errorlog.txt` ya no crece).

## Reglas de lectura rápida

- **El primer frame manda.** Si el primer frame está en un plugin de terceros pero el *Mensaje* habla de
  un dato/método tuyo, el origen real suele ser tu código llamado desde ahí: sigue la traza hacia abajo
  hasta el primer frame **tuyo**.
- **`NoMethodError ... for nil`** = algo era `nil` cuando no debía → falta un `return if x.nil?` (R6) o el
  `try_get` en vez de `get` (R4). Mira qué recibió el `nil`.
- **`uninitialized constant` / `undefined method` para algo que SÍ existe** = problema de **orden de
  carga** (tu código corrió antes de definirse lo que usa) o el plugin no está instalado/activo.
- **Tras editar PBS o datos:** si el crash aparece al cargar, sospecha **PBS sin recompilar** o **save de
  una versión anterior** (incompatibilidad de `Marshal`).
- Pide siempre el bloque **completo** (cabecera + Mensaje + Traza entera), no solo la primera línea.

## Reference

| Archivo | Cubre |
|---|---|
| `reference/01-reading-logs.md` | Anatomía de `errorlog.txt`, `mkxp.log` y `debuglog.txt`; mapeo `Sección\d+`→script; `EventScriptError`, `Reset`, `Hangup`; cómo leer un backtrace |
| `reference/02-common-crashes.md` | Catálogo de crashes típicos: clase + mensaje → causa → arreglo, por categoría |

Skills relacionadas: [[essentials-codemap]] (localizar el archivo del primer frame), [[plugin-architecture]]
(arreglar con parche `[ZBOX]`/`alias_method`), [[ruby-essentials]] (trampas de Ruby: `next` vs `return`,
encoding, `Marshal`), [[pbs-data]] (recompilar/validar PBS), [[mkxp-z-runtime]] (errores de `mkxp.log`).
