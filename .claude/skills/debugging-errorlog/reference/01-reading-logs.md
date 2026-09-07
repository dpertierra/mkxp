# Reference · Anatomía de los logs y lectura de trazas

Fuente: `004_Debugging/004_Errors.rb` (`pbPrintException`, `pbGetExceptionMessage`, `pbCriticalCode`),
`004_Debugging/002_PBDebug.rb`, `999_Main/999_Main.rb`.

## Los tres logs (no confundirlos)

| Archivo | Quién escribe | Contiene | Cuándo mirarlo |
|---|---|---|---|
| **`errorlog.txt`** (raíz del juego) | `pbPrintException` | Excepciones **Ruby del juego** | Lo normal: un crash con ventana de error |
| **`mkxp.log`** (raíz) | El runtime MKXP-Z | Arranque del motor, OpenGL, fuentes, audio, shaders, `[error]` de bajo nivel | "No abre", pantalla negra, fuente/textura que no carga, audio mudo |
| **`Data/debuglog.txt`** y `Data/dumplog.txt` | `PBDebug.log`/`dump` | Traza de depuración (scoring de IA, etc.) **solo** con `$DEBUG && $INTERNAL` | No es un crash; análisis de comportamiento |

## Anatomía de un bloque de `errorlog.txt`

El archivo se **abre en modo append** (`"ab"`): cada error nuevo se añade **al final**. Por eso **el
bloque de más abajo es el más reciente**. Formato real que escribe `pbPrintException`:

```
=================

[2026-06-23 18:40:12 +0200]
[Pokémon Essentials versión 21.1]
<texto de plugins de terceros, si lo añaden>
[LA BASE DE SKY versión 1.2.1.1]
[Pokémon Musgravita versión X.Y.Z]
Excepción: NoMethodError
Mensaje: undefined method `level' for nil:NilClass
<mensaje>

Traza:
Section043:112:in `pbStartBattle'
Section041:88:in `pbTrainerBattle'
...
```
- **Separador** `=================` + `[fecha]` marca el inicio de cada bloque.
- **`Excepción:`** = la clase del error (la pista nº 1). **`Mensaje:`** = el texto (la pista nº 2).
- **`Traza:`** = backtrace. Por defecto **10 líneas**; **25** si `$INTERNAL` (build interno). El **primer
  renglón es el culpable**; los siguientes, la cadena de llamadas que llevó hasta él.
- La ruta del log se **censura** (quita el directorio y reemplaza el usuario por `USERNAME`) en el
  mensaje que se muestra; en disco el archivo está en la raíz del juego (o `RTP.getSaveFileName` si `RTP`
  está definido).

## Mapear `Sección\d+` → archivo real

En las trazas, los scripts empaquetados aparecen como `SecciónNNN`/`SectionNNN`. `pbPrintException`
sustituye `Section(\d+)` por el **nombre del script** desde `$RGSS_SCRIPTS`. En La Base de Sky los scripts
están **extraídos** a `Data/Scripts/` (hay `scripts_extract.rb`), así que muchas trazas ya muestran rutas
o nombres de sección legibles. Para pasar de un nombre de sección/clase a su archivo concreto usa
[[essentials-codemap]] (responsabilidad por carpeta `NNN_`).

Un frame típico: `<script>:<línea>:in 'método'`. Abre ese archivo en esa línea: ahí está la instrucción
que petó. Lee 1-2 frames más abajo para entender con qué argumentos se llegó.

## Tipos de excepción especiales (`004_Errors.rb`)

- **`EventScriptError`** — error dentro del **Script de un evento** del mapa. Trae un `event_message` con
  el **mapa y el ID del evento**, sin traza Ruby cruda (más amable). Si el usuario pega esto: ve al
  evento indicado (skill [[events-and-messages]] / editor) y revisa su caja Script.
- **`Reset`** (`class Reset < Exception`) — **no es un fallo**: es F12 (reiniciar juego). Recarga la caché
  del sistema. Si aparece en un trace, suele ser ruido.
- **`Hangup`** — el script **tardó demasiado** (watchdog): "El script está tardando demasiado. El juego se
  reiniciará." Causa habitual: bucle infinito (un `loop`/`while` sin salida, o un `pbWait`/evento que
  nunca termina). Dispara un `pbEmergencySave`.
- **`Errno::ENOENT`** → "Archivo X no encontrado." Falta un asset/dato en la ruta esperada (ver
  [[assets-pipeline]] para la ruta correcta).

## `pbCriticalCode` y la captura global

Todo el juego corre dentro de `pbCriticalCode { mainFunctionDebug }` (`999_Main`). Cualquier excepción que
no sea `Reset`/`SystemExit` se enruta a `pbPrintException` (que escribe el log y muestra la ventana). Por
eso **casi cualquier crash acaba en `errorlog.txt`**: si el usuario vio la ventana roja de error, el
bloque está ahí.

Para envolver código propio arriesgado y que un fallo no tumbe todo, el patrón del motor es
`PBDebug.logonerr { ... }` (registra y continúa) — útil en handlers no críticos, pero no lo uses para
silenciar errores que el usuario debe ver.

## Pista al usuario

En la ventana de error, **mantener Ctrl al cerrar copia el mensaje completo al portapapeles**. Pídeselo así
cuando necesites la traza entera y no solo lo que se ve en pantalla.
