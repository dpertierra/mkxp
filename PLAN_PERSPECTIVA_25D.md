# Plan: proyección 2.5D en la GPU (opción 2)

## Objetivo

Sacar de Ruby el trabajo por tile y por frame del plugin `[ROYAL] Visual 2.5D HD`. El motor aplica la perspectiva en el vertex shader; Ruby deja cada sprite en su posición plana (como el motor vanilla) y solo marca, una vez por rebind, si el sprite es suelo o está de pie.

Hoy, al andar, Ruby visita ~1300 tiles por frame con ~25-40 llamadas cada uno y dibuja ~600 sprites extra de franjas de suelo. Con este plan el coste Ruby del scroll vuelve a ser el de un juego 2D y las franjas desaparecen (la curva la hace la malla subdividida en la GPU).

## Decisión clave: tabla, no fórmulas en GLSL

Toda la proyección del modelo clásico depende solo de la **Y plana de pantalla**:

- `F(y)` = `raw_project_y(y) + horizon_curve(y) - y_shift` (Y proyectada)
- `W(y)` = `width_scale_at(y)` (escala horizontal)
- `U(y)` = `vertical_unit_at(y) / DISPLAY_TILE_HEIGHT` (alto por píxel de algo de pie)

Ninguna depende del scroll: la tabla solo cambia al tocar tilt, zoom, horizonte, blend (fundido Q) o márgenes.

Por eso **Ruby sigue siendo la única fuente de las fórmulas**: calcula `F/W/U` en double, en 64 muestras, y las sube al viewport. El shader interpola linealmente. Ventajas frente a portar la matemática a GLSL:

- No hay dos implementaciones que mantener (ni el modelo `:camera` con su integral logarítmica que portar aparte).
- Sin costuras: dos tiles vecinos comparten vértices con las mismas coordenadas planas enteras, el shader da exactamente la misma salida para los dos y el rasterizador cierra la junta. El redondeo por borde que hace Ruby hoy (y sus rayas negras) deja de hacer falta.
- Compatible con GLES2: 64 `vec4` de uniform (F, W, U, libre) caben en el mínimo garantizado de 128 `vec4` del vertex shader. No requiere texturas float.

Validación: un oráculo Ruby (fase 0) mide el error de interpolar 64 muestras frente a la fórmula exacta. Objetivo < 0,25 px en todo el rango; si no se llega, se sube a 96 muestras o se reparten de forma no uniforme.

## API Ruby propuesta

### Viewport

```ruby
viewport.perspective_set(segments, table)  # segments: 1..4 × [y_start, y_step, count] (Σcount = 64); table: 64*3 Float [F0,W0,U0, ...]
viewport.perspective_center_x = 256               # cx de project_x (por defecto rect.width / 2)
viewport.perspective_clear                        # vuelve al estado sin perspectiva
viewport.perspective?                             # => true/false
```

- Fase 0 (`tests/perspective/ORACLE_RESULT.md`): con reparto uniforme el error llega a 0,8 px por el quiebre de pendiente en `y = 0`; con 64 muestras en hasta 4 tramos uniformes (cortes en los quiebres de F/W) baja a 0,068 px. De ahí los tramos.
- Fuera del rango total se extrapola con la pendiente del extremo (la rejilla ampliada tiene filas por encima y por debajo de la pantalla).
- El setter valida tipos, longitud y finitud antes de tocar el estado. No guarda `VALUE` de Ruby.

### Sprite

| Propiedad | Tipo | Defecto | Uso |
|---|---|---|---|
| `perspective_mode` | Integer | `0` | `0` = sin warp (comportamiento actual), `1` = suelo, `2` = de pie |
| `perspective_anchor` | Float | `0.0` | Modo 2: Y plana de la línea de pies, relativa a `sprite.y` |
| `perspective_gain` | Float | `1.0` | Modo 2: `1.0` paredes y tiles con prioridad, `CHARACTER_GAIN` personajes |
| `perspective_lift` | Float | `0.0` | Modo 2: píxeles extra ya proyectados (Cliff, Shelf) |
| `perspective_slices` | Integer | `4` | Modo 1: filas de vértices del quad (curva del suelo) |

El warp solo se aplica si el sprite tiene `mode != 0` **y** su viewport tiene tabla. No toca `taper`: si ambos están activos, el modo 1/2 ignora `taper` (el plugin deja de usarlo en la ruta GPU).

### Matemática del vertex shader

Sobre la posición plana del vértice en coordenadas lógicas del viewport, antes de `projMat`:

```glsl
// Suelo (modo 1)
y' = F(y);
x' = cx + (x - cx) * W(y);

// De pie (modo 2): f = sprite.y + anchor (pies, plano)
unit = W(f) + (U(f) - W(f)) * gain;
y' = F(f) - (f - y) * unit - lift;
x' = cx + (x - cx) * W(f);
```

Es la misma cuenta que `vertical_unit_at` y `character_unit_at` en `[002] Core.rb`: `gain = 1` da la pared y `gain = CHARACTER_GAIN` el personaje.

## Cambios en el motor (mkxp)

| Archivo | Cambio |
|---|---|
| `src/display/viewport.h/.cpp` | Struct trivial `PerspectiveTable` (64×vec4, y_start, y_step, cx, enabled, generation). Neutra por defecto. |
| `binding/viewport-binding.cpp` | `perspective_set`, `perspective_clear`, `perspective_center_x=`, `perspective?`. |
| `src/display/sprite.h/.cpp` | Atributos de la tabla anterior, con setters con guardia de no-op como los existentes. En `Sprite::draw`: si `mode == 0` o el viewport no tiene tabla, **el camino actual sin cambios**; si no, variante de shader 2.5D. |
| `binding/sprite-binding.cpp` | Bind de las cinco propiedades (plantilla: cómo entró `taper` en `458d022d`). |
| `shader/sprite25d.vert` (nuevo) | Vertex shader con la tabla y los dos modos. Se combina con los fragment existentes (`simple`, `simpleAlpha`, `sprite`) para cubrir opacidad, tono/color, flash y bush (los personajes en hierba usan bush). |
| `src/display/gl/shader.h/.cpp`, `shader/meson.build` | Registrar las variantes 2.5D. Compilación perezosa en el primer uso. |
| `src/display/gl/` (nuevo helper) | Malla de N filas por quad para el modo 1, UV interpolada recta, buffer reutilizado. `Vertex`/VAO globales sin cambios. |
| `src/display/sprite.cpp:424-460` | Culling: con modo ≠ 0, caja conservadora (ampliada por el máximo de `W` y el `lift`) o sin culling de caja. El plugin ya oculta lo que queda fuera. |

**Fuera de alcance:** `Tilemap` nativo, `Plane`, `Window` y `Bitmap#stretch_blt`. Essentials 21.1 dibuja el mapa con el `TilemapRenderer` de Ruby (sprites), que es lo que el plugin parchea, así que el `Tilemap` nativo no interviene. Snapshots (`Graphics.snap_to_bitmap`) capturan el resultado ya proyectado, sin reproyectar.

## Impacto en juegos sin 2.5D

Ninguno visible si se respeta el diseño opt-in:

- Por defecto todos los sprites tienen `mode = 0` y ningún viewport tiene tabla: `Sprite::draw` sigue exactamente el mismo shader, VBO, uniforms y draw call de hoy. El único coste añadido es una comparación entera por sprite dibujado (nanosegundos).
- Los shaders actuales no ganan uniforms ni ramas: la perspectiva va en programas aparte que se compilan solo si alguien los usa.
- API Ruby: solo métodos y propiedades nuevos. Ningún juego existente los llama; `Sprite`, `Viewport` y RGSS1/2/3 no cambian.
- El riesgo real está en los bugs del código compartido que se toca (`Sprite::draw`, culling). Se cubre con capturas de referencia con la función apagada (fase 1) y comparando contra el binario anterior.
- ABI C++: cambia el layout de `Sprite`/`Viewport`, así que extensiones nativas compiladas contra el fork tendrían que recompilarse. Los scripts Ruby no se ven afectados.

Para el propio plugin: si el binario no trae la API (mkxp-z estándar, o el futuro port 3DS, ver `PLAN_PORT_3DS.md`), se usa la ruta Ruby actual. La detección es la misma que ya se hace con `TAPER_SUPPORTED`.

## Cambios en el plugin

Nuevo archivo `[017] GPU Projection.rb`, más ramas cortas en los hooks existentes:

```ruby
GPU_PROJECTION = Viewport.method_defined?(:perspective_set) &&
                 Sprite.method_defined?(:perspective_mode=)
```

**En la ruta GPU:**

1. **Tabla:** al cambiar los parámetros (donde hoy se pone `@projection_dirty` y en cada frame del fundido Q), muestrear `F/W/U` con las funciones de `[002] Core.rb` y llamar a `perspective_set` en el viewport del mapa (`Spriteset_Map` `@viewport1`). Ruby sigue siendo la fuente de verdad.
2. **Tiles nativos:** en `refresh_tile` (rebind), fijar `mode`/`anchor` según `geom_priority` (pri 0 → suelo, pri N → de pie con pies en `cy + N`). `refresh_tile_coordinates` y `refresh_tile_z` vuelven a ser los del motor más la Z plana que ya calcula el plugin. Desaparece la proyección por tile al hacer scroll.
3. **Personajes:** `Sprite_Character` en modo 2 con `anchor` en los pies y `gain = CHARACTER_GAIN`; `perspective_lift` lleva `character_lift`/`event_tag_lift`. La Z sigue en CPU, igual que ahora.
4. **Capas MakerStudio:** igual que los tiles nativos, en el bind del pool.

**Lo que deja de usarse en la ruta GPU** (se conserva para el fallback): `GroundStrip` y franjas (`place_ground_strips`, `build_row_slices`, `ensure_ground_slice`), `project_tile`, `project_tile_scroll_x/_y_flat`, `yflat_geom`, las caches de `ground_edge`/zoom y `taper`.

**Lo que se queda igual:** toda la lógica de Z (espacio plano), márgenes de rejilla, culling por fila, atmósfera, opciones/debug y el puente de oscuridad (pocos puntos, puede seguir con las fórmulas Ruby).

**Casos especiales, en fase posterior:** muros Cliff (`WallField`), `InteriorWall` y `Shelf`. Mientras tanto se quedan en `mode = 0` con la proyección Ruby actual. Mezclar los dos caminos puede dar costuras de 1 px en la frontera; los sellos actuales (`WALL_EDGE_SEAL`) deberían taparlas.

## Fases

Cada fase se puede entregar y probar por separado.

0. **Oráculo (sin motor ni juego).** Script en `tests/perspective/oracle.rb` que copia las fórmulas de `Core.rb`, construye la tabla de 64 muestras y mide el error de interpolación para varios tilt/zoom/horizon/márgenes. Fija el número de muestras.
1. **Viewport + Sprite modo 1, shader simple.** Escena de prueba en `tests/perspective/` (rejilla de tiles de colores). Capturas con la función apagada idénticas al binario anterior; con la función encendida, sin costuras entre tiles vecinos.
2. **Modo 2 y familias de shader.** Opacidad, tono/color/flash, bush, wave, rotación. Personaje sobre fila con `gain`, columna de pri 1..4, `lift`.
3. **Culling y hi-res.** Bounds conservadores; `enableHires`/`framebufferScalingFactor`, viewports parciales y snapshots.
4. **Plugin, tiles nativos y personajes.** Ruta GPU con fallback automático. Opción de debug para alternar GPU/Ruby y comparar FPS con la sonda (`pbVisual25DPerfProbe`).
5. **Plugin, MakerStudio y muros.** Pool extendido, Cliff, InteriorWall, Shelf con `lift`.
6. **Limpieza.** Medir y decidir qué partes de la ruta Ruby se mantienen solo como fallback.

## Riesgos

- **Error de interpolación** de la tabla en los extremos (fila del horizonte con curva fuerte). Lo mide la fase 0.
- **Coordenadas de juego**: `Sprite#x/y`, ratón e hit tests siguen en plano. Hoy el plugin ya trabaja así para la lógica; solo afecta a quien lea `sprite.x` esperando la posición proyectada (revisar `ScreenPosHelper` en `[009]`).
- **Mezcla de rutas** (fase 4 con muros aún en Ruby): costuras de 1 px en la frontera.
- **Vértices**: modo 1 con 4 filas son 15 vértices por tile en vez de 4. Con ~400 tiles de suelo visibles son ~6000 vértices: despreciable para la GPU, y sustituye a ~600 sprites de franjas.
- **Port 3DS**: no hay GLSL en PICA200; esa plataforma usaría el fallback Ruby salvo que se implemente aparte.

## Verificación manual (cuando se implemente)

1. Juego sin 2.5D (o con el plugin desactivado): menús, combate, mapa, animaciones y transiciones idénticos al binario anterior.
2. Con 2.5D: andar en las cuatro direcciones, buscar costuras entre tiles, pri 0/pri >0, personajes en hierba (bush), fundido Q.
3. Alternar GPU/Ruby desde el menú debug y comparar FPS con la sonda.
4. Revisar `errorlog.txt` y `mkxp.log`.
