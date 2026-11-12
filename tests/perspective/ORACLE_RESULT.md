# Oráculo de la tabla 2.5D — fase 0 de `PLAN_PERSPECTIVA_25D.md`

Script: [`oracle.rb`](oracle.rb) — Ruby 3 puro, sin motor ni juego.

```bash
ruby tests/perspective/oracle.rb
```

## Qué se ha copiado (y de dónde)

Todo sale de leer las fuentes, no de inventarlas:

| Símbolo | Origen | Qué es |
|---|---|---|
| `TILT_DEGREES`, `MAX_TILT_DEGREES` | `[001] Settings.rb:40-41` | inclinación de la cámara (se clampea a 50°) |
| `VANISHING_STRENGTH` | `[001] Settings.rb:58` | convergencia lateral |
| `HORIZON_CURVE` | `[001] Settings.rb:91` | caída del horizonte (px) |
| `MIN/MAX_CAMERA_ZOOM` | `[001] Settings.rb:106-117` | 60 % … 250 % (el menú debug deja elegirlo) |
| `MAX_MARGIN_COLUMNS/ROWS` | `[001] Settings.rb:123-124` | freno de la rejilla ampliada (12) |
| `VERTICAL_GAIN`, `TILE_LIFT`, `CHARACTER_GAIN` | `[001] Settings.rb:182-195` | elementos de pie |
| `SHAPE_FLOOR = 0.15` | `[002] Core.rb:1032` | horizonte del perfil |
| `raw_project_y` / `shape_integral` / `shape_at` | `[002] Core.rb:1084-1090`, `1110-1115`, `1047-1057` | Y proyectada |
| `horizon_curve` | `[002] Core.rb:329-340` | caída extra arriba (`t` acotado a 1 → se planta en `y = 0`) |
| `width_scale_at` / `min_width_scale` | `[002] Core.rb:1069-1073`, `1077-1081` | escala horizontal |
| `vertical_unit_at` / `character_unit_at` | `[002] Core.rb:1147-1158` | alto de lo que está de pie |
| `y_shift`, `centred_shift`, `refresh_margins`, `raw_unproject` | `[002] Core.rb:884-944`, `1808-1816` | encuadre ampliado y márgenes |
| `DISPLAY_TILE_WIDTH/HEIGHT = 32` | `Data/Scripts/016_Map renderer/002_TilemapRenderer.rb:14-15` | tamaño de tile |

Valores de la tabla (como en el plan):

- `F(y) = raw_project_y(y) + horizon_curve(y) - y_shift`
- `W(y) = width_scale_at(y)`
- `U(y) = vertical_unit_at(y) / 32`

## Método

- **Rango de Y plana**: el que cubre la rejilla ampliada, es decir la pantalla
  extendida por los `margin_rows` que calcula `refresh_margins`
  (`y_min = unprojected_edge(0) - margen·32`, `y_max = unprojected_edge(alto) + margen·32`).
  Con `tilt 50 / zoom 60` el rango llega a `-768 … 868` px (1636 px); con
  `tilt 50 / zoom 100` son `-213 … 538` px.
- **Barrido**: 2 resoluciones (512×384 y 640×440) × tilt {0, 25, 50, 75, 100} ×
  zoom {60, 80, 100, 130} × horizonte {0, 14} × {tilt clampeado a 50, tilt 75/100
  sin clampear como estrés} × {blend 1.0, blend 0.5 (fundido Q a medias)} = **128 configs**.
- **Métricas** (px de pantalla): `errF` = |F interpolada − F exacta|;
  `errX_borde` = (ancho/2)·|ΔW| (error de la x en el borde);
  `altoPared` = 32·|ΔU| (gain = 1) y `altoPersonaje` = 32·|0.6·ΔW + 0.4·ΔU|
  (gain = `CHARACTER_GAIN`).
- Los puntos de prueba son la rejilla fina (0.5 px) + los quiebres de F + los
  puntos medios de cada tramo de la tabla (donde el error es máximo).

## Resultado (peor caso de los 128 configs)

| esquema | errF peor | errX borde peor | alto de pie peor | **max global** | ¿< 0.25 px? |
|---|---|---|---|---|---|
| uniforme N=32 | 1.3839 | 1.3253 | 0.1519 | **1.3839** | NO |
| uniforme N=48 | 0.5877 | 0.7466 | 0.0850 | **0.7466** | NO |
| uniforme N=64 | 0.8024 | 0.6610 | 0.0734 | **0.8024** | NO |
| uniforme N=96 | 0.5512 | 0.4129 | 0.0459 | **0.5512** | NO |
| uniforme N=128 | 0.4138 | 0.2665 | 0.0369 | **0.4138** | NO |
| curvo N=32 | 0.2731 | 0.0000 | 0.0000 | **0.2731** | NO |
| curvo N=48 | 0.1315 | 0.0000 | 0.0000 | **0.1315** | SI |
| **curvo N=64** | **0.0683** | **0.0000** | **0.0000** | **0.0683** | **SI** |
| curvo N=96 | 0.0268 | 0.0000 | 0.0000 | **0.0268** | SI |
| curvo N=128 | 0.0151 | 0.0000 | 0.0000 | **0.0151** | SI |

Peor config de cada métrica: `512x384 tilt50 zoom60 hor14` (errF),
`640x440 tilt50 zoom60 hor0` (errX), `640x440 tilt50 zoom60 hor0` (alto).

Con `N = 64` uniforme fallan **44 de los 128 configs**.

## Por qué falla el reparto uniforme

El error no se reparte: **se concentra en `y = 0`**, donde la curva de horizonte
se planta (`[002] Core.rb:337-338`, `t` acotado a 1). Ahí la derivada de `F` salta
de golpe (`2·HORIZON_CURVE/(alto/2) = 0.1458`), y una interpolación lineal no
puede representar un salto de pendiente: el error crece como `|Δs|·h/4`, o sea
** lineal en el paso `h`**, no cuadrático.

Diagnóstico del peor config (`512x384 tilt50 zoom60 hor14`, rango `-768 … 868`):

```
uniforme N=64: y=0.0 err=0.8024 | y=-0.5 err=0.7714 | y=0.5 err=0.7609 | ...
curvo   N=64: y=168.0 err=0.0616 | y=88.0 err=0.0616 | y=184.0 err=0.0616 | ...
```

El `errX` del reparto uniforme tiene otro origen: `W(y)` es **lineal a trozos**
con un quiebre en el clamp del perfil (`SHAPE_FLOOR`) y otro en el suelo de
convergencia (`min_width_scale`). Uniforme no pone ninguna muestra ahí, así que
la escala se deforma; con la tabla *curva* el error de `W` es **exactamente 0**
(mismo motivo: `U = vertical_gain · W` con `TILE_LIFT = 0`, así que el alto de
lo que está de pie también es exacto).

Para llegar a 0.25 px con reparto uniforme harían falta **≈ 240 muestras**
(`h ≤ 6.9 px`): por encima del mínimo garantizado de 128 `vec4` del vertex
shader en GLES2. **El reparto uniforme no es viable.**

## Recomendación

> **N = 64 con reparto no uniforme (curvo) — peor error 0.068 px (3,7× de holgura
> bajo el objetivo de 0.25 px).**

Cómo se reparte (lo mismo que hace el script, `Table#curved_positions`):

1. Se parte el rango en los **quiebres** de `F`/`W`:
   `y = 0` (la curva de horizonte se planta), `y = alto/2` (se anula),
   `y = limit = (SHAPE_FLOOR−1)·alto/tan(tilt)` (clamp del perfil) y
   `y` donde `W` toca `min_width_scale` (suelo de convergencia).
2. Dentro de cada tramo las muestras se espacian `h ∝ 1/√|F''|`, que es el
   reparto que iguala el error (`error ≈ |F''|·h²/8`).

Como el reparto por tramos es **uniforme dentro de cada tramo**, la tabla sigue
siendo un puñado de sub-tablas uniformes: al shader le basta con saber, por
tramo, `(inicio, paso)`. Con 4 tramos son 4 pares `vec2` extra — de sobra en el
presupuesto de 128 `vec4` que el plan ya reserva (64 `vec4` de muestras + 4 de
tramos).

Consecuencia para el plan: la API `viewport.perspective_set(y_start, y_step, table)`
del §"API Ruby propuesta" **no vale tal cual** (una sola `y_step` implica tabla
uniforme). Hay que cambiar el `setter` a algo del estilo
`perspective_set(segments, table)` donde cada segmento lleva su `y_start`/`y_step`,
o guardar las posiciones de las 64 muestras y buscar el tramo en el shader
(4 comparaciones).

Alternativas si se quiere tocar menos:

- **N = 48 curvo** también cumple (0.1315 px), pero deja solo 1.9× de margen.
- **Uniforme N = 64 recortado a la pantalla visible** (sin márgenes) da 0.2096 px:
  pasa por poco, pero deja el margen de la rejilla ampliada sin cubrir (la tabla
  solo habría que extrapolarlo) y no hay margen para lo que no se ha barrido.

## Lo que este oráculo NO cubre

- Solo el **modelo clásico** (`Settings::PROJECTION_MODEL = :classic`). El modelo
  `:camera` (integral logarítmica) tiene otra curvatura: si se va a usar, hay que
  repetir la medición con esas fórmulas.
- La **cuantización de los vértices** del quad subdividido (modo 1 con
  `perspective_slices`): el error de la curva dentro de un tile es del renderer,
  no de la tabla, y ya existe en la ruta Ruby actual.
- El `blend` intermedio del fundido Q solo se ha medido en 0.5; el resto de
  valores se interpola linealmente entre el estado 2D (error 0) y el 2.5D final.
