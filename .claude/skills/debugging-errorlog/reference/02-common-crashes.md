# Reference · Catálogo de crashes típicos

Formato: **clase + síntoma → causa probable → arreglo**. El arreglo respeta R2 (no tocar `Data/` salvo
petición) y R5/R11 (parche `[ZBOX]` con `alias_method`).

## NoMethodError

**`undefined method 'X' for nil:NilClass`** → algo era `nil` y se llamó un método sobre ello.
- Causa: un `get`/acceso que devolvió `nil` (especie/objeto/Pokémon/evento inexistente), o una variable
  no inicializada.
- Arreglo: validar antes (`return if obj.nil?`, R6) y usar `GameData::X.try_get` en vez de `get` cuando el
  dato puede no existir (R4). Identifica **qué** es `nil` mirando el método y el frame de abajo.

**`undefined method 'X' for #<Clase...>`** → el objeto no tiene ese método.
- Causa: typo en el nombre, versión de API distinta (firma cambió entre versiones de Essentials), o
  llamas a un método de otra clase.
- Arreglo: grep el método real en `Data/Scripts/` ([[essentials-codemap]]); nunca inventar firmas
  (proceso paso 2 de AGENTS.md).

## NameError

**`uninitialized constant Foo`** / **`undefined local variable or method 'bar'`**
- Causa A — **orden de carga**: tu código se ejecutó antes de que se definiera `Foo` (un plugin que carga
  después, o código a nivel de archivo en vez de dentro de un handler). En Essentials, el `NNN_` del
  nombre de carpeta/archivo fija el orden; los plugins cargan según `meta.txt`.
- Causa B — **plugin ausente/desactivado**: usas algo de un plugin que no está instalado.
- Arreglo: mueve el uso dentro de un handler/`EventHandlers` que corra tras la inicialización
  ([[plugin-architecture]]), o declara la dependencia del plugin en `meta.txt`. Si es un símbolo de
  GameData (`:MIESPECIE`), falta en el PBS o no se recompiló.

## Errores de PluginManager (al arrancar)

`003_Technical/006_PluginManager.rb` aborta con mensajes claros antes de entrar al juego:
- "El plugin 'A' requiere que el plugin 'B' sea la versión X o superior, pero la instalada es Y."
  → actualiza B o ajusta la dependencia/`:version` en el `meta.txt` de A.
- "...requiere que el plugin 'B'..." y B no está → instala B o quita la dependencia.
- Causa habitual tras añadir un plugin: `meta.txt` con `Requires`/`Dependencies` mal puestos, o carpetas
  de plugin desordenadas. Ver [[plugin-architecture]] (meta.txt, orden).

## Datos / PBS / GameData

**Crash al cargar o "no encuentra la especie/objeto/move"**
- Causa: **PBS sin recompilar** tras editarlo (el motor lee los `.dat` compilados, no el `.txt`), o un ID
  que no existe en el PBS.
- Arreglo: forzar recompilación (borrar los `.dat` de `Data/` correspondientes o el flag de compilación y
  rearrancar; el `Compiler.main` corre al inicio). Verifica que el ID exista y esté bien escrito en el PBS
  ([[pbs-data]]). Tras esto, los datos viejos en saves pueden requerir conversión (abajo).

**`Errno::ENOENT` / "Archivo X no encontrado"**
- Causa: falta un gráfico/audio/dato en la ruta esperada (nombre de archivo, carpeta o extensión).
- Arreglo: coloca el asset en la ruta correcta ([[assets-pipeline]]); revisa mayúsculas/espacios y
  convención de nombres por especie/forma/shiny.

## Saves incompatibles (Marshal)

**Crash al cargar una partida tras cambiar scripts/datos**, o `Marshal`/`TypeError`/`ArgumentError` al
deserializar.
- Causa: el save guardó objetos con una estructura que ya cambió (atributo nuevo/renombrado, clase
  distinta). Los saves de Essentials son `Marshal` de objetos vivos.
- Arreglo: añadir una **conversión de save** en el sistema de conversiones
  (`007_Save data/006_Game_SaveConversions.rb`) que migre el dato viejo, o (en desarrollo) empezar
  partida nueva. Nunca asumas que un save viejo aguanta un cambio de estructura sin migración.

## Trampas de Ruby (ver [[ruby-essentials]])

- **`LocalJumpError: unexpected return`** → usaste `return` dentro de un `proc`/bloque de handler. Usa
  `next` (R3).
- **`SystemStackError: stack level too deep`** → recursión infinita, a menudo por un `alias_method` mal
  hecho que se llama a sí mismo, o dos parches que se aliasan en bucle. Revisa la convención de alias
  (`_ZBOX_<abrev>_orig_<metodo>`, R5) y que no se cargue dos veces.
- **`can't modify frozen String`** → mutaste un literal congelado. Duplica (`.dup`) antes de modificar.
- **Encoding (`invalid byte sequence`, acentos raros)** → texto sin `force_encoding(Encoding::UTF_8)`;
  común al leer archivos. Ver [[ruby-essentials]].

## Alias / parches duplicados

**Un método se ejecuta dos veces, o `stack level too deep` tras añadir un parche.**
- Causa: el archivo del parche se cargó dos veces, o el `alias_method` no protege contra redefinición.
- Arreglo: un `alias` por método con nombre único (`_ZBOX_...`), guardado en `Plugins/[ZBOX] .../` con su
  `meta.txt`; nunca `prepend` (R5). Comprueba que no exista una copia del archivo en dos sitios.

## EventScriptError (Script de un evento)

Mensaje con **mapa + ID de evento**. Causa: error en la caja "Script" de ese evento (método inexistente,
`nil`, sintaxis). Arreglo: abre ese evento y corrige el script; si la lógica es grande, muévela a un
plugin `[ZBOX]` y deja `MiPlugin.metodo` en el evento ([[events-and-messages]], R8).

## Cuando el juego NO arranca (mirar `mkxp.log`, no `errorlog.txt`)

- Pantalla negra / cierre inmediato sin ventana roja → suele ser nivel **motor**: fuente que falta,
  `mkxp.json` mal formado, shader/OpenGL, asset de arranque corrupto. Busca líneas `[error]`/`[warning]`
  en `mkxp.log`. Ver [[mkxp-z-runtime]] (config `mkxp.json`, fuentes, runtime).

## Plantilla de respuesta del agente

1. **Qué pasó** (1 línea: clase + dónde).
2. **Por qué** (causa raíz, citando el frame culpable).
3. **Arreglo** (parche `[ZBOX]` o corrección concreta, respetando R1–R12).
4. **Verificación manual** (pasos para reproducir y confirmar que ya no peta).
