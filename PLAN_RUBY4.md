# Plan: migrar MKXP-Z de Ruby 3.1 a Ruby 4.0

## Objetivo

Enlazar MKXP-Z con Ruby 4.0.x (última versión de mantenimiento) para mejorar el rendimiento de La Base de Sky, con YJIT disponible en Linux y macOS.

**Decisiones tomadas:**

| # | Decisión |
|---|----------|
| D1 | Versión destino: Ruby 4.0.x (último tag `v4.0.N` de `ruby/ruby`; al redactar, `v4.0.9`). |
| D2 | YJIT se compila en Linux y macOS y se activa con `"YJITEnable"` en `mkxp.json`, **apagado por defecto** hasta que la Fase 5 lo apruebe. |
| D3 | La Fase 0 (perfilado) es una puerta de decisión obligatoria: si no se cumple el criterio, la migración se detiene. |
| D4 | El plan cubre el motor (este repo) y el juego (checklist en la Fase 4; los cambios se hacen en el repo del juego). |
| D5 | El fork de Ruby vive en un fork propio de `mkxp-z/ruby`, rama `mkxp-z-4.0.x`. |

## Estado (2026-11-05)

Detalle por tarea en `odd/tasks/ruby4-migration.md`. Los cambios del juego están en la rama `feat/ruby4-compat` de La Base de Sky, no en este repo.

| Fase | Estado |
|------|--------|
| 0 Perfilado | Pendiente: lo mide el usuario jugando. |
| 1 Fork | Hecho: `dpertierra/ruby`, rama `mkxp-z-4.0.x` (9cd02826), release `mkxp-z-4.0.9-2`. |
| 2 Build | Linux hecho. CI Ubuntu verde con artefacto (run 42089759520). macOS y Windows: pendiente de confirmar en esa misma run. Windows msvcrt sin verificar. |
| 3 Binding | Hecho: MJIT ignorado en Ruby 3.3+ (679e007b). Pendiente: comentario de `YJITEnable` en `mkxp.json` (líneas 515-521). |
| 4 Juego | Parcial: `Dir.exist?` y `Set` (174bf6d4), `Ruby Library 4.0.0` (9267b0bd). Pendiente: saves con `Set`, avisos de deprecación y partida de humo completa. |
| 5 YJIT | Pendiente: requiere la medición del usuario. |

**Hechos nuevos:**
- Rendimiento 2.5D: el cuello era `Sprite#z=`, que reinsertaba la escena en O(n) en cada cambio de z (stackprof: 22,5 % del tiempo). Orden perezoso en 38bf7d60 (perf) y 01df0e08 (test), en `main` y en esta rama (merge 4e0bdac1).
- Medido por el usuario en Pokémon World Frontier (Linux): la peor zona bajaba de 30 FPS y pasa a ~42 FPS con Ruby 3.1 y 4.0 sin YJIT. Con YJIT en 4.0, 30 → 60 FPS estable; esa medición es previa al arreglo de z-sort.
- Juego en Linux: arranca, carga y guarda saves de 3.1 y es jugable en 4.0 (L6). Necesita `"preloadScript": ["preload.rb"]` en `mkxp.json` y `MKXP.puts` → `System.puts` en `preload.rb`.
- Ruby se descarga como tarball de release, sin BASERUBY (d0490571; detalle en Fase 2.1).
- YJIT: `configure` lo activa solo si hay `rustc` ≥ 1.58 en Linux/macOS (`configure.ac:3936`), así que no se pasa `--enable-yjit` (bf622a16). En Windows/mingw no hay JIT y `--yjit` solo avisa; `"YJITEnable": true` en un `mkxp.json` compartido es seguro.
- CI: nombres de artefacto saneados (13aac8d1). Ubuntu: se quitan los plugins bfd `LLVMgold-*.so`, que rompían `ar` con objetos Rust de YJIT (65ce210e, 39916e49). macOS: se quita el parche de socket obsoleto, `--without-gmp` y `rb_cv_symbol_prefix=_` (03e176c1, 7922b053). Windows: OpenSSL sin tests con `make -j1` y Defender en tiempo real desactivado (2bf8da4f, 78716d96, eba9d09a).
- Build local contra 3.1: pasar `-Dmri_includes=<ruby-3.1.0>` y `-Dcpp_args=-I<ruby-3.1.0/x86_64-linux>` por separado. Si no, se cuela `/usr/include/ruby/config.h` (Ruby 4.0 del sistema) y el juego crashea al arrancar.

## Hechos revisados

- Ruby se compila desde el fork `mkxp-z/ruby`, rama `mkxp-z-3.1.3`:
  `linux/Makefile:387`, `windows/Makefile:355`, `macos/Dependencies/common.make:355`.
- Versión 3.1 fijada en: `meson_options.txt:1` (`mri_version`), `linux/Makefile:368` (`libruby.so.3.1`),
  `windows/Makefile:336` (`x64-msvcrt-ruby310.dll`), `macos/config/mkxp.xcconfig:10` (`MRI_VERSION = 3.1.0`)
  y `macos/mkxp-z.xcodeproj/project.pbxproj` (`libruby.3.1.dylib`).
- El binding usa `TypedData` y `rb_define_alloc_func`; no se encontraron APIs C eliminadas (taint, `rb_cData`, `rb_iterate`).
  Las comprobaciones de versión usan `RAPI_FULL` (`binding/binding-util.h:44`), que para 4.0.9 vale 409.
- `binding/binding-mri.cpp:1191-1216` ya pasa `--yjit` a `ruby_options` si `"YJITEnable"` está activo.
  También pasa `--mjit` con `"JITEnable"`; **MJIT se eliminó en Ruby 3.3**, así que esa rama falla con 4.0.
- El fork aplica dos commits sobre 3.1.3 (`c2ac5cc` "apply mkxp-z patches" y `4d85560` arreglo de `clock_gettime`). Parches:

| Archivo | Cambio | Tipo |
|---------|--------|------|
| `vm_eval.c`, `vm_insnhelper.c` | Desactiva comprobación `private`/`protected` | **Semántico**: Essentials y plugins dependen de ello |
| `class.c`, `vm_insnhelper.c` | Ignora "superclass mismatch" al reabrir clases | **Semántico** |
| `sprintf.c` | No avisa de "too many arguments for format string" | Compatibilidad |
| `include/ruby/win32.h`, `thread_win32.c`, `win32/win32.c` | Conflicto con el macro `Sleep` (crash al arrancar) | Windows |
| `win32/win32.c` | `CREATE_NO_WINDOW` al lanzar procesos | Windows |
| `configure.ac` | Enlace estático de `gcc`, `pthread`, `z`, `ffi` en mingw | Windows |
| `4d85560` | Redefinición de `clock_gettime`/`clock_getres` | Windows |

- La Base de Sky (`/home/diego/Documents/GitHub/La-Base-de-Sky/LA BASE DE SKY/`): 49 carpetas en `Data/Scripts`, 4 plugins.
  - `Dir.exists?` en `Data/Scripts/060_Debug/006_Map Exporter.rb:148` (eliminado en Ruby 3.2).
  - Copia propia de `Set` en `Data/Scripts/058_Utilities/002_Libraries/001_set.rb`. En Ruby 4.0 `Set` es clase del núcleo implementada en C.
  - `Data/Scripts/058_Utilities/007_Search_Sort.rb:1` añade `Data/Ruby Library 3.3.0` (106 entradas de stdlib) al `$LOAD_PATH`.
  - Sin usos de `File.exists?`, `Fixnum`, `Bignum`, `taint`/`untaint` ni `$SAFE`.

**Pendiente de confirmar:** que Ruby 4.0 siga compilando con el toolchain mingw **msvcrt** que usa `windows/Makefile` (RubyInstaller usa UCRT desde 3.1). Si no, la Fase 2 incluye pasar el toolchain a UCRT64.

---

## Fase 0 — Perfilado (puerta de decisión)

Pregunta a responder: **¿qué parte del tiempo de CPU del hilo principal se gasta dentro de Ruby?**
Si Ruby es poco, cambiar de versión de Ruby no mejora los FPS: el coste está en C++ (Bitmap, Tilemap) o en la GPU.

El build de Linux enlaza Ruby como biblioteca compartida (`libruby.so.3.1`), así que `perf` puede separar el tiempo por biblioteca (DSO) sin instrumentar nada.

### Paso a paso (Linux)

1. **Instalar perf** y permitir perfilar procesos propios:
   ```sh
   sudo dnf install perf
   sudo sysctl kernel.perf_event_paranoid=1
   ```
2. **Compilar el motor actual** (Ruby 3.1) en release, como siempre:
   ```sh
   cd linux && make everything && cd ..
   meson setup build && ninja -C build
   ```
   Copiar el ejecutable al juego (`Game_Linux`) igual que en una entrega normal.
3. **Configurar `mkxp.json` del juego** solo para medir (guardar una copia del original):
   ```jsonc
   "vsync": false,
   "syncToRefreshrate": false,
   "frameSkip": false,
   "printFPS": true
   ```
   `perf` solo cuenta tiempo en CPU; las esperas para mantener el framerate no aparecen, así que no hace falta quitar el límite de FPS.
4. **Preparar una partida guardada por escena.** Escenas mínimas:
   - **E1** Overworld: el mapa con más eventos, caminando 30 s.
   - **E2** Combate doble salvaje o entrenador, con animaciones, durante 2-3 turnos.
   - **E3** Turno de IA: combate contra entrenador con equipo completo (selección de movimientos de la IA).
   - **E4** UI: abrir y navegar Pokédex, resumen del Pokémon y PC.
   - **E5** La escena que el jugador reporta como más lenta, si existe.
5. **Grabar cada escena** (juego abierto, situado en la escena):
   ```sh
   PID=$(pgrep -f Game_Linux | head -1)
   perf record -F 999 -g -p "$PID" -o e1.data -- sleep 30
   ```
   Durante los 30 s, jugar la escena. Repetir para `e2.data` … `e5.data`.
6. **Leer el reparto por hilo y biblioteca:**
   ```sh
   perf report -i e1.data --sort comm,dso --stdio | head -40
   ```
   - Fijarse solo en las filas del **hilo principal** (`comm` = nombre del ejecutable). Los hilos de audio de SDL/OpenAL salen aparte.
   - `libruby.so.3.1` → tiempo en el intérprete de Ruby (**esto es lo que mejora la migración y YJIT**).
   - Ejecutable `Game_Linux`/`mkxp-z` → C++ del motor (Bitmap, Sprite, Tilemap). La migración no lo cambia.
   - `libGL*`, `*mesa*`, `*nvidia*`, `libc`, `libSDL2` → driver de vídeo, sistema y SDL. La migración no lo cambia.
7. **Ver qué métodos Ruby dominan** (opcional, útil para la Fase 5):
   ```sh
   perf report -i e3.data --dsos libruby.so.3.1 --sort sym --stdio | head -30
   ```
   Si predomina `vm_exec_core` o `vm_call_*`, es ejecución de bytecode: YJIT es justo lo que más ayuda ahí.
   Si predomina `gc_*`, el coste es del recolector: 4.0 ayuda algo, pero conviene además reducir objetos creados por frame.
8. **Anotar la línea base** en una tabla (se reutiliza en la Fase 5):

   | Escena | % hilo principal en `libruby` | % motor C++ | % driver/sistema | FPS medio (`printFPS`) |
   |--------|------|------|------|------|
   | E1 | | | | |

### Criterio de decisión

- **Seguir** si en al menos dos escenas, una de ellas E2 o E3, `libruby` es **≥ 30 %** del tiempo de CPU del hilo principal.
- **Detener** si `libruby` es < 30 % en todas: el cuello de botella está en el render. Abrir en su lugar un plan para optimizar el binding o el renderer, guiado por el `perf report` del ejecutable.
- Si la decisión es dudosa (entre 20 y 30 %), seguir solo con las Fases 1-2 y medir de nuevo antes de la Fase 3.

**Salida:** tabla de línea base, archivos `e*.data` guardados y decisión anotada en este documento.

---

## Fase 1 — Fork de Ruby 4.0

1. Hacer un fork de `mkxp-z/ruby` en GitHub personal.
2. Clonar el fork, añadir `ruby/ruby` como remoto y crear la rama:
   ```sh
   git remote add upstream https://github.com/ruby/ruby
   git fetch upstream tag v4.0.N --no-tags
   git checkout -b mkxp-z-4.0.x v4.0.N
   ```
3. Portar los parches de la tabla de *Hechos revisados*. **No usar `cherry-pick` a ciegas**: el código de 4.0 cambió de forma.
   Para cada parche, localizar la función equivalente en 4.0 y reaplicar la intención:
   - **Visibilidad**: en 4.0 la comprobación existe en `vm_eval.c` (`rb_method_call_status`) y en `vm_insnhelper.c` (`vm_call_method`). Buscar también rutas nuevas que comprueben `METHOD_VISI_PRIVATE`/`METHOD_VISI_PROTECTED` (`grep -rn METHOD_VISI_ *.c`), como el despacho de `send`/`public_send` y los fast paths de llamadas.
   - **Superclass mismatch**: `class.c` (`rb_define_class`, `rb_define_class_id_under`) y `vm_insnhelper.c` (`vm_check_if_class`).
   - **sprintf**: bloque "too many arguments for format string" en `sprintf.c`.
   - **Windows**: `Sleep`, `CREATE_NO_WINDOW`, `configure.ac` mingw, `clock_gettime` (comprobar si 4.0 ya incluye el arreglo de `4d85560` antes de reaplicarlo).
4. Un commit por parche, con mensaje que nombre el parche original. Así un rebase a `v4.0.N+1` se hace por commit.
5. Script de prueba de semántica en Ruby puro (dentro del fork o en `tests/`), ejecutable con el `ruby` recién compilado:
   ```ruby
   class A; private def priv = :ok; protected def prot = :ok; end
   raise "private" unless A.new.priv == :ok
   raise "protected" unless A.new.prot == :ok
   class B < Object; end
   class B < String; end # no debe lanzar TypeError
      puts "patches OK"
   ```
   Ejecutarlo **dos veces**: `./ruby test.rb` y `./ruby --yjit --yjit-call-threshold=1 test.rb`. El segundo modo comprueba que YJIT respeta el parche de visibilidad (YJIT compila sus propios caminos de llamada; ver Fase 3).

**Salida:** rama `mkxp-z-4.0.x` en el fork propio, con parches portados y el script pasando sin y con YJIT.

---

## Fase 2 — Build del motor contra 4.0

1. **Fuente del fork configurable** en los tres Makefiles, para poder volver a upstream:
   ```make
   RUBY_REPO   ?= $(GITHUB)/<usuario>/ruby
   RUBY_BRANCH ?= mkxp-z-4.0.x
   ```
   Sustituir la línea `$(CLONE) $(GITHUB)/mkxp-z/ruby ... mkxp-z-3.1.3` por `$(CLONE) $(RUBY_REPO) ... --branch $(RUBY_BRANCH)`
   en `linux/Makefile:387`, `windows/Makefile:355` y `macos/Dependencies/common.make:355`.
   **Actualización:** compilar Ruby desde git exige un BASERUBY >= 3.1 con openssl (rompía macOS en CI). Los tres Makefiles
   descargan ahora un tarball de release (`RUBY_TARBALL ?=`, release `mkxp-z-4.0.9-2` de `dpertierra/ruby`) que ya trae
   `configure`, los fuentes generados (prism, `parse.c`) y las gemas bundled, así que no hace falta BASERUBY.
   Regenerarlo desde el fork: `ruby tool/make-snapshot -srcdir=. -archname=ruby-4.0.9-mkxpz-N -packages=gzip <outdir> branches/mkxp-z-4.0.x`,
   subirlo con su `.sha256` como asset de una release nueva (`gh release create`) y subir el tag en `RUBY_TARBALL` en cada cambio del fork.
2. **Nombres de biblioteca**:
   - `linux/Makefile:368-370`: `libruby.so.3.1` → `libruby.so.4.0`.
   - `windows/Makefile:336-338`: `$(RB_PREFIX)-ruby310.dll` → `$(RB_PREFIX)-ruby400.dll`.
   - `macos/config/mkxp.xcconfig:10`: `MRI_VERSION = 4.0.0`; en `project.pbxproj`, las referencias a `libruby.3.1.dylib` → `libruby.4.0.dylib`.
   - `meson_options.txt:1`: valor por defecto de `mri_version` → `4.0`.
3. **YJIT en el build** (Linux y macOS): no se pasa `--enable-yjit` (eliminado de `RUBY_CONFIGURE_ARGS` en bf622a16). `configure` activa YJIT solo si encuentra `rustc` ≥ 1.58 (`configure.ac:3936`), así que CI instala `rustc` (e01910e6). En `windows/Makefile` no aplica.
4. **Flags de C**: las notas de `linux/Makefile:374-375` y `windows/Makefile:342-343` indican que `-std=gnu99` ya no es necesario para versiones válidas en C23 ("3.5.0+"). Probar sin `-std=gnu99` y quitarlo si compila.
5. **Revisar `RUBY_CONFIGURE_ARGS`**: `--disable-rubygems` sigue siendo válido en 4.0. Revisar qué extensiones de `--with-static-linked-ext` cambian de nombre o pasan a bundled gems (comprobar en el `NEWS.md` de 4.0, por ejemplo `fiddle`). Si el juego las usa (Fase 4), incluirlas en el build.
6. **Windows**: compilar con el toolchain actual (msvcrt). Si falla por msvcrt, pasar el `windows/Makefile` a UCRT64 (`RB_PREFIX` → `x64-ucrt`) y anotar el cambio.
7. Compilar los tres targets y arrancar el juego sin cambios en los scripts para ver los primeros errores.

**Salida:** binarios de Linux, Windows y macOS enlazados con 4.0 que arrancan hasta el primer error del juego, o hasta el título.

---

## Fase 3 — Cambios en el binding C++

1. **MJIT eliminado** (`binding/binding-mri.cpp:1191-1210`): envolver la rama `conf.jit.enabled` en `#if RAPI_FULL < 330`. Para 3.3+, registrar un aviso en el log si `"JITEnable"` está activo e ignorarlo.
2. **YJIT**: la rama `conf.yjit.enabled` ya existe. Actualizar el comentario de `mkxp.json:515-520` ("experimental", "Ruby 3.1") e indicar que en Windows no tiene efecto.
   Opcional: exponer `--yjit-exec-mem-size` (ya hay un `TODO` en `binding-mri.cpp:1214`) si la Fase 5 muestra problemas de memoria.
3. **YJIT y el parche de visibilidad**: si el script de la Fase 1 falla con `--yjit`, localizar en `yjit/src/codegen.rs` las comprobaciones de `METHOD_VISI_PRIVATE`/`PROTECTED` (en el despacho de `send`) y hacer que llamen igualmente. Hasta resolverlo, `"YJITEnable"` no se activa.
4. Compilar con `-Wdeprecated-declarations` visible y corregir los avisos de APIs C obsoletas en `binding/`.
5. Mantener la compatibilidad con 3.1 mediante `RAPI_FULL` (los guards ya existen), para poder volver atrás.

**Salida:** binding sin avisos nuevos; `JITEnable` inofensivo en 4.0; `YJITEnable` funcional en Linux y macOS.

---

## Fase 4 — Checklist del juego (repo de La Base de Sky)

Los cambios se hacen en el repo del juego (`/home/diego/Documents/GitHub/La-Base-de-Sky/LA BASE DE SKY/`), no en este.

- [x] `Data/Scripts/060_Debug/006_Map Exporter.rb:148`: `Dir.exists?` → `Dir.exist?` (174bf6d4).
- [x] `Data/Scripts/058_Utilities/002_Libraries/001_set.rb`: en 4.0 `Set` es clase del núcleo. Cargar la copia solo cuando `RUBY_VERSION < "4.0"` (envolver el archivo en ese `if`).
- [ ] **Saves con `Set`**: buscar si algún dato guardado contiene objetos `Set` (`grep -rn "Set.new\|to_set\|Set\[" Data/Scripts Plugins`). Si los hay, cargar un save hecho con 3.1 en el build 4.0 y comprobar que esos datos se leen bien. Si fallan, añadir un `marshal_load` de compatibilidad.
- [x] `Data/Ruby Library 3.3.0`: sustituir (hecho en 9267b0bd, en la rama del juego) por la stdlib de 4.0 (`lib/` del build del fork) en `Data/Ruby Library 4.0.0`, y actualizar `Data/Scripts/058_Utilities/007_Search_Sort.rb:1`. Ver primero qué se `require` realmente desde esa carpeta; si es poco, copiar solo eso.
- [ ] Repetir la búsqueda de APIs eliminadas en **todos** los plugins de terceros antes de cada entrega:
  `File.exists?`, `Dir.exists?`, `Fixnum`, `Bignum`, `taint`/`untaint`/`tainted?`, `$SAFE`, `Struct.new(... keyword_init: true)` (aviso), `Kernel#=~` sobre objetos no `String`.
- [ ] Activar `Warning[:deprecated] = true` temporalmente en `preload.rb`, jugar y revisar `mkxp.log`: anotar los avisos de "chilled string" (strings literales que se modifican). En 4.0 solo avisan; corregirlos con `+"texto"` o `.dup`.
- [ ] Partida completa de humo: título → cargar save de 3.1 → mapa → combate → captura → PC → guardar → recargar.

**Salida:** juego que arranca, carga saves antiguos y guarda sin errores en 4.0.

---

## Fase 5 — Medición y activación de YJIT

1. Repetir la Fase 0 (mismas escenas y partidas) con el build 4.0 en tres configuraciones:
   - **A**: Ruby 3.1 (línea base ya medida).
   - **B**: Ruby 4.0, `"YJITEnable": false`.
   - **C**: Ruby 4.0, `"YJITEnable": true`.
2. Comparar FPS medio y % en `libruby` por escena. En C, el código compilado por YJIT aparece en `perf` como `[JIT]` o sin símbolo; contarlo como tiempo Ruby.
3. Medir memoria residente (`ps -o rss= -p $PID`) tras 10 minutos de juego en B y C.
4. **Criterio para activar YJIT por defecto**: C mejora a B en E2/E3, ninguna escena empeora, el script de la Fase 1 pasa con `--yjit` y la memoria extra es aceptable para el juego.
5. Anotar resultados en este documento.

**Salida:** tabla A/B/C y decisión sobre `"YJITEnable"`.

---

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Rutas nuevas de 4.0 comprueban la visibilidad y el parche queda incompleto | Script de la Fase 1, `grep METHOD_VISI_`, partida de humo completa |
| YJIT no respeta el parche de visibilidad | Prueba con `--yjit-call-threshold=1`; YJIT apagado hasta resolverlo |
| Ruby 4.0 no compila con mingw msvcrt | Pasar a UCRT64 en la Fase 2 |
| Plugins de terceros con APIs eliminadas | Checklist de la Fase 4 antes de cada entrega |
| La ganancia no compensa | Puerta de decisión de la Fase 0 |
| Rebase del fork en cada `v4.0.N` | Un commit por parche (Fase 1.4) |
