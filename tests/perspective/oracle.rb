#!/usr/bin/env ruby
# frozen_string_literal: true

# =============================================================================
# Oráculo de la tabla de proyección 2.5D — fase 0 de PLAN_PERSPECTIVA_25D.md
#
# Script puro de Ruby 3 (sin motor, sin juego). Copia las fórmulas del modelo
# clásico del plugin "[ROYAL] Visual 2.5D HD" y mide el error de interpolar
# linealmente N muestras de F/W/U frente a la fórmula exacta.
#
# Fuentes (leídas, no inventadas):
#   [001] Settings.rb  -> Visual25D::Config
#   [002] Core.rb      -> Visual25D (proyección clásica)
#   Data/Scripts/016_Map renderer/002_TilemapRenderer.rb -> constantes de tile
#
# Lo que se mide (todo en px de pantalla):
#   F  : Y proyectada            = raw_project_y(y) + horizon_curve(y) - y_shift
#   W  : escala horizontal        = width_scale_at(y)
#   U  : alto por tile de pie     = vertical_unit_at(y) / DISPLAY_TILE_HEIGHT
#   x  : borde de pantalla        = |x-cx| * W  ->  error = half_width * |dW|
#   alto de pie: unidad * 32      ->  gain=1 (paredes) y gain=CHARACTER_GAIN
#
# Uso: ruby tests/perspective/oracle.rb
# =============================================================================

module OracleConfig
  #--- [001] Settings.rb (Visual25D::Config) ---------------------------------
  TILT_DEGREES       = 50        # Settings.rb:40
  MAX_TILT_DEGREES   = 50        # Settings.rb:41
  PROJECTION_MODEL   = :classic  # Settings.rb:49
  VANISHING_STRENGTH = 0.3       # Settings.rb:58
  HORIZON_CURVE      = 14        # Settings.rb:91
  CAMERA_ZOOM        = 100       # Settings.rb:106
  MIN_CAMERA_ZOOM    = 60        # Settings.rb:117
  MAX_CAMERA_ZOOM    = 250       # Settings.rb:107
  MAX_MARGIN_COLUMNS = 12        # Settings.rb:123
  MAX_MARGIN_ROWS    = 12        # Settings.rb:124
  VERTICAL_GAIN      = 0.2       # Settings.rb:182
  TILE_LIFT          = 0         # Settings.rb:187
  CHARACTER_GAIN     = 0.4       # Settings.rb:195

  #--- [002] Core.rb ---------------------------------------------------------
  SHAPE_FLOOR = 0.15             # Core.rb:1032

  #--- 002_TilemapRenderer.rb:14-15 (Game_Map::TILE_WIDTH = 32) ------------
  DISPLAY_TILE_WIDTH  = 32
  DISPLAY_TILE_HEIGHT = 32
end

# =============================================================================
# Copia de la proyección clásica de [002] Core.rb (una instancia = un estado).
# =============================================================================
class Projection
  attr_reader :width, :height, :perspective, :zoom, :tilt, :blend
  attr_reader :horizon_curve_px, :margin_rows, :margin_columns, :y_shift

  def initialize(width:, height:, tilt:, zoom_percent:, horizon_curve_px:, blend: 1.0)
    @width = width
    @height = height
    @blend = blend
    @horizon_curve_px = horizon_curve_px.to_f
    # Core.rb:236-240 — el tilt se clampea a MAX_TILT_DEGREES.
    @tilt = [tilt.to_f, OracleConfig::MAX_TILT_DEGREES.to_f].min
    @camera_zoom = zoom_percent.to_f
    rebuild_render_params!
  end

  # Core.rb:217-227 — la perspectiva es la tangente del tilt; el zoom escala
  # el resultado del fundido (Q).
  def rebuild_render_params!
    degrees = @tilt * @blend
    @perspective = Math.tan(degrees * Math::PI / 180.0)
    target_z = @camera_zoom / 100.0
    @zoom = 1.0 + ((target_z - 1.0) * @blend)
  end

  # Core.rb:1039-1042
  def tilt_normal
    return 1.0 + (@perspective / 2.0)
  end

  # Core.rb:1047-1057 — perfil de la perspectiva (1 en el centro de pantalla).
  def shape_at(y)
    return 1.0 if @perspective == 0
    raw = 1.0 + (@perspective * y / @height)
    raw = OracleConfig::SHAPE_FLOOR if raw < OracleConfig::SHAPE_FLOOR
    return raw / tilt_normal
  end

  # Core.rb:1110-1115 — integral de shape_at (dos filas contiguas comparten
  # borde exactamente). Tramo lineal por debajo del horizonte.
  def shape_integral(y)
    limit = (OracleConfig::SHAPE_FLOOR - 1.0) * @height / @perspective
    return y + (@perspective * y * y / (2.0 * @height)) if y >= limit
    base = limit + (@perspective * limit * limit / (2.0 * @height))
    return base + (OracleConfig::SHAPE_FLOOR * (y - limit))
  end

  # Core.rb:1084-1090
  def raw_project_y(y)
    return @zoom * y if @perspective == 0
    return @zoom * shape_integral(y) / tilt_normal
  end

  # Core.rb:931-944 — inversa exacta de raw_project_y.
  def raw_unproject(raw_value)
    return raw_value / @zoom if @perspective == 0
    target = raw_value * tilt_normal / @zoom
    limit = (OracleConfig::SHAPE_FLOOR - 1.0) * @height / @perspective
    base  = limit + (@perspective * limit * limit / (2.0 * @height))
    return limit + ((target - base) / OracleConfig::SHAPE_FLOOR) if target < base
    disc = 1.0 + (2.0 * @perspective * target / @height)
    return limit if disc < 0
    return (Math.sqrt(disc) - 1.0) * @height / @perspective
  end

  # Core.rb:329-340 — caída extra en lo alto de la pantalla. Se planta por
  # encima de y=0 (t acotado a 1) y se anula por debajo del centro.
  def horizon_curve(y)
    amt = @horizon_curve_px
    return 0.0 if amt == 0.0 || @perspective == 0
    b = @blend
    return 0.0 if b <= 0.0
    half = @height / 2.0
    return 0.0 if half <= 0
    t = (half - y) / half
    return 0.0 if t <= 0.0
    t = 1.0 if t > 1.0
    return (amt * b) * t * t
  end

  # Core.rb:1077-1081 — suelo de la convergencia (ancho mínimo de una fila).
  def min_width_scale
    half = @width / 2.0
    return half / (half + ((OracleConfig::MAX_MARGIN_COLUMNS - 1) * OracleConfig::DISPLAY_TILE_WIDTH))
  end

  # Core.rb:1069-1073
  def width_scale_at(y)
    value = @zoom * (1.0 + ((shape_at(y) - 1.0) * OracleConfig::VANISHING_STRENGTH))
    floor = min_width_scale
    return (value < floor) ? floor : value
  end

  # Core.rb:1137-1143 — cuanto se ponen de pie los elementos verticales.
  def vertical_gain
    rt = @tilt * @blend
    return 1.0 if rt <= 0 || OracleConfig::VERTICAL_GAIN <= 0
    upright = 1.0 / Math.cos(rt * Math::PI / 180.0)
    return 1.0 + ((upright - 1.0) * OracleConfig::VERTICAL_GAIN)
  end

  # Core.rb:1147-1150 — alto en pantalla de UN tile de alto de pie sobre "y".
  def vertical_unit_at(y)
    return ((OracleConfig::DISPLAY_TILE_HEIGHT * vertical_gain) + OracleConfig::TILE_LIFT) *
           width_scale_at(y)
  end

  # Core.rb:1155-1158
  def character_unit_at(y)
    flat = OracleConfig::DISPLAY_TILE_HEIGHT * width_scale_at(y)
    return flat + ((vertical_unit_at(y) - flat) * OracleConfig::CHARACTER_GAIN)
  end

  # Core.rb:1808-1811
  def centred_shift
    half = @height / 2
    return raw_project_y(half) - half
  end

  # Core.rb:926-928
  def unprojected_edge(screen_y)
    return raw_unproject(screen_y + @y_shift.to_f)
  end

  # Core.rb:884-920 — encuadre ampliado: márgenes de filas/columnas y y_shift.
  def refresh_margins!
    tile_w = OracleConfig::DISPLAY_TILE_WIDTH
    tile_h = OracleConfig::DISPLAY_TILE_HEIGHT
    @y_shift = centred_shift
    top    = unprojected_edge(0)
    bottom = unprojected_edge(@height)
    above = (top < 0) ? (-top.to_f / tile_h).ceil : 0
    base_rows = (@height.to_f / tile_h).ceil + 1
    below = ((bottom.to_f / tile_h).ceil + 1) - base_rows
    @margin_rows = [[above, below].max + 1, 1].max
    @margin_rows = OracleConfig::MAX_MARGIN_ROWS if @margin_rows > OracleConfig::MAX_MARGIN_ROWS
    # Si la rejilla no llega a donde pide la proyección, se corre hacia abajo.
    @y_shift = [centred_shift, raw_project_y(-@margin_rows * tile_h)].max
    half = @width / 2.0
    extra = (half / width_scale_at(unprojected_edge(0))) - half
    @margin_columns = (extra <= 0) ? 1 : (extra / tile_w).ceil + 1
    @margin_columns = OracleConfig::MAX_MARGIN_COLUMNS if @margin_columns > OracleConfig::MAX_MARGIN_COLUMNS
  end

  # --- Valores de la tabla (F, W, U) en una Y plana -------------------------

  # F = Y proyectada (Core.rb:1117-1120, sin el redondeo a entero).
  def f_at(y)
    return raw_project_y(y) + horizon_curve(y) - @y_shift
  end

  # U = alto por tile de pie, dividido por el alto del tile (para el shader).
  def u_at(y)
    return vertical_unit_at(y) / OracleConfig::DISPLAY_TILE_HEIGHT
  end

  # --- Curvatura (para el reparto no uniforme) ------------------------------

  # d2/dy2 de raw_project_y: constante en el tramo cuadrático, 0 bajo el
  # clamp de SHAPE_FLOOR.
  def raw_second_derivative(y)
    return 0.0 if @perspective == 0
    limit = (OracleConfig::SHAPE_FLOOR - 1.0) * @height / @perspective
    return 0.0 if y < limit
    return @zoom * @perspective / (@height * tilt_normal)
  end

  # d2/dy2 de horizon_curve: constante solo en (0, half).
  def horizon_second_derivative(y)
    amt = @horizon_curve_px
    return 0.0 if amt == 0.0 || @perspective == 0
    half = @height / 2.0
    return 0.0 unless y > 0.0 && y < half
    return 2.0 * amt * @blend / (half * half)
  end

  def second_derivative(y)
    return raw_second_derivative(y) + horizon_second_derivative(y)
  end

  # Puntos donde la derivada de F salta (quiebres de la función):
  #   y = 0    -> la curva de horizonte se planta (Core.rb:337-338)
  #   y = half -> la curva de horizonte se anula (Core.rb:336)
  #   y = limit-> el perfil de la perspectiva se clampea (Core.rb:1055)
  #   y = floor-> la escala de ancho toca su suelo (Core.rb:1071-1072)
  def breakpoints
    pts = [0.0, @height / 2.0]
    return pts if @perspective == 0
    limit = (OracleConfig::SHAPE_FLOOR - 1.0) * @height / @perspective
    pts << limit
    # Quiebre de W por el suelo de convergencia (si se alcanza antes del
    # clamp del perfil, el del clamp ya lo cubre).
    if OracleConfig::VANISHING_STRENGTH > 0
      raw = tilt_normal * (1.0 + ((min_width_scale / @zoom - 1.0) / OracleConfig::VANISHING_STRENGTH))
      y_floor = ((raw - 1.0) * @height / @perspective)
      pts << y_floor if y_floor > limit
    end
    return pts.uniq
  end

  # Rango de Y plana que cubre la rejilla ampliada: la pantalla extendida por
  # los márgenes de filas que calcula refresh_margins.
  def domain
    refresh_margins! if @margin_rows.nil?
    th = OracleConfig::DISPLAY_TILE_HEIGHT
    top = unprojected_edge(0)
    bottom = unprojected_edge(@height)
    y_lo = top - (@margin_rows * th)
    y_hi = bottom + (@margin_rows * th)
    return [y_lo, y_hi]
  end

  # Rango de Y plana que se ve en pantalla (sin márgenes).
  def visible_domain
    refresh_margins! if @margin_rows.nil?
    return [unprojected_edge(0), unprojected_edge(@height)]
  end
end

# =============================================================================
# Tabla de muestras: posiciones + valores (F, W, U) e interpolación lineal.
# =============================================================================
class Table
  attr_reader :positions

  def initialize(proj, n, uniform: true, y_lo: nil, y_hi: nil)
    @proj = proj
    dom_lo, dom_hi = proj.domain
    @y_lo = y_lo || dom_lo
    @y_hi = y_hi || dom_hi
    @positions = uniform ? uniform_positions(n, @y_lo, @y_hi) : curved_positions(n, @y_lo, @y_hi)
    @values = @positions.map { |y| [proj.f_at(y), proj.width_scale_at(y), proj.u_at(y)] }
  end

  # Reparto uniforme: y_step = (y_hi - y_lo) / (N-1).
  def uniform_positions(n, y_lo, y_hi)
    return [y_lo] if n <= 1
    step = (y_hi - y_lo) / (n - 1).to_f
    return Array.new(n) { |i| y_lo + (i * step) }
  end

  # Reparto no uniforme: se parte en los quiebres de F y dentro de cada
  # tramo se espacia h ∝ 1/sqrt(|F''|), que es el reparto que iguala el error
  # de interpolación (error ≈ |F''| h² / 8).
  def curved_positions(n, y_lo, y_hi)
    edges = [y_lo] + @proj.breakpoints.select { |b| b > y_lo && b < y_hi }.sort + [y_hi]
    segs = edges.each_cons(2).map do |a, b|
      mid = (a + b) / 2.0
      [a, b, @proj.second_derivative(mid)]
    end
    weights = segs.map { |a, b, c| (b - a) * Math.sqrt(c.abs) }
    total = weights.sum
    # Reparto de los N-1 intervalos por resto mayor (redondeo a entero).
    intervals = weights.map { |w| total > 0 ? ((w / total) * (n - 1)).floor : 0 }
    intervals.each_index do |i|
      intervals[i] = 1 if intervals[i] < 1
    end
    # Ajusta al total (n-1) quitando/añadiendo intervalos del tramo más largo.
    while intervals.sum > n - 1
      idx = intervals.each_index.max_by { |i| intervals[i] }
      break if intervals[idx] <= 1
      intervals[idx] -= 1
    end
    while intervals.sum < n - 1
      idx = intervals.each_index.max_by { |i| (segs[i][1] - segs[i][0]) }
      intervals[idx] += 1
    end
    positions = [y_lo]
    segs.each_with_index do |(a, b, _c), i|
      k = intervals[i]
      k.times { |j| positions << (a + ((b - a) * (j + 1) / k.to_f)) }
    end
    return positions
  end

  # Interpolación lineal con extrapolación por la pendiente del extremo (como
  # el viewport: PLAN_PERSPECTIVA_25D.md, "Fuera de [...] se extrapola").
  def interpolate(y)
    pos = @positions
    if y <= pos.first
      i = 0
    elsif y >= pos.last
      i = pos.size - 2
    else
      lo = 0
      hi = pos.size - 1
      while hi - lo > 1
        mid = (lo + hi) / 2
        if pos[mid] <= y
          lo = mid
        else
          hi = mid
        end
      end
      i = lo
    end
    y0 = pos[i]
    y1 = pos[i + 1]
    t = (y1 == y0) ? 0.0 : (y - y0) / (y1 - y0)
    return [
      @values[i][0] + ((@values[i + 1][0] - @values[i][0]) * t),
      @values[i][1] + ((@values[i + 1][1] - @values[i][1]) * t),
      @values[i][2] + ((@values[i + 1][2] - @values[i][2]) * t)
    ]
  end
end

# =============================================================================
# Medición del error de interpolación.
# =============================================================================
Result = Struct.new(:err_f, :err_x, :err_wall, :err_char, :err_f_vis)

def measure(proj, table)
  y_lo, y_hi = proj.domain
  v_lo, v_hi = proj.visible_domain
  half = proj.width / 2.0
  gain_char = OracleConfig::CHARACTER_GAIN
  tile_h = OracleConfig::DISPLAY_TILE_HEIGHT

  # Puntos de prueba: rejilla fina + quiebres + medios de tramo de la tabla.
  points = test_points(proj, table)

  res = Result.new(0.0, 0.0, 0.0, 0.0, 0.0)
  points.each do |yy|
    fi, wi, ui = table.interpolate(yy)
    d_f = fi - proj.f_at(yy)
    d_w = wi - proj.width_scale_at(yy)
    d_u = ui - proj.u_at(yy)
    e_f = d_f.abs
    e_x = (half * d_w).abs
    # gain=1 (paredes): unit = U.  gain=CHARACTER_GAIN: unit = (1-g)W + gU.
    e_wall = (tile_h * d_u).abs
    e_char = (tile_h * ((1.0 - gain_char) * d_w + gain_char * d_u)).abs
    res.err_f = e_f if e_f > res.err_f
    res.err_x = e_x if e_x > res.err_x
    res.err_wall = e_wall if e_wall > res.err_wall
    res.err_char = e_char if e_char > res.err_char
    if yy >= v_lo && yy <= v_hi && e_f > res.err_f_vis
      res.err_f_vis = e_f
    end
  end
  return res
end

# Puntos de prueba: rejilla fina + quiebres de F + medios de tramo de la
# tabla (donde el error de un tramo parabólico es maximo).
def test_points(proj, table)
  y_lo, y_hi = proj.domain
  points = []
  y = y_lo
  while y <= y_hi
    points << y
    y += 0.5
  end
  points << y_hi
  points.concat(proj.breakpoints.select { |b| b >= y_lo && b <= y_hi })
  table.positions.each_cons(2) { |a, b| points << ((a + b) / 2.0) }
  points.uniq
end

# Los K puntos con más error de F (para localizar el origen del error).
def worst_points(proj, table, k = 5)
  list = test_points(proj, table).map do |yy|
    fi, = table.interpolate(yy)
    [yy, (fi - proj.f_at(yy)).abs]
  end
  list.sort_by { |(_, e)| -e }.first(k)
end

# Error máximo de F en un subrange (para tablas recortadas a la pantalla).
def max_err_f(proj, table, y_lo, y_hi)
  worst = 0.0
  y = y_lo
  while y <= y_hi
    fi, = table.interpolate(y)
    e = (fi - proj.f_at(y)).abs
    worst = e if e > worst
    y += 0.25
  end
  worst
end

# =============================================================================
# Barrido de parámetros.
# =============================================================================
RESOLUTIONS = [[512, 384], [640, 440]].freeze
TILTS       = [0, 25, 50, 75, 100].freeze
ZOOMS       = [60, 80, 100, 130].freeze
HORIZONS    = [0, OracleConfig::HORIZON_CURVE].freeze

def build_configs
  configs = []
  RESOLUTIONS.each do |(w, h)|
    TILTS.each do |tilt|
      ZOOMS.each do |zoom|
        HORIZONS.each do |horizon|
          clamped = [tilt, OracleConfig::MAX_TILT_DEGREES].min
          configs << {
            w: w, h: h, tilt: tilt, eff_tilt: clamped, zoom: zoom, horizon: horizon,
            blend: 1.0, kind: :normal,
            label: "#{w}x#{h} tilt#{tilt} zoom#{zoom} hor#{horizon}"
          }
          # Tilt por encima del tope de Settings: no lo permite el plugin, pero
          # sirve como estrés de la tabla.
          if tilt > OracleConfig::MAX_TILT_DEGREES
            configs << {
              w: w, h: h, tilt: tilt, eff_tilt: tilt, zoom: zoom, horizon: horizon,
              blend: 1.0, kind: :stress,
              label: "#{w}x#{h} tilt#{tilt}(stress) zoom#{zoom} hor#{horizon}"
            }
          end
          # Fundido Q a medias: la tabla se rehace en cada frame del fundido.
          if tilt == OracleConfig::MAX_TILT_DEGREES
            configs << {
              w: w, h: h, tilt: tilt, eff_tilt: clamped, zoom: zoom, horizon: horizon,
              blend: 0.5, kind: :fade,
              label: "#{w}x#{h} tilt#{tilt} zoom#{zoom} hor#{horizon} blend0.5"
            }
          end
        end
      end
    end
  end
  return configs
end

def make_projection(cfg)
  proj = Projection.new(
    width: cfg[:w], height: cfg[:h],
    tilt: cfg[:eff_tilt], zoom_percent: cfg[:zoom],
    horizon_curve_px: cfg[:horizon], blend: cfg[:blend]
  )
  # Core.rb:236-240 — el clamp real ocurre en el setter; aquí se aplica
  # directamente el tilt efectivo.
  proj
end

def table_row(cells, widths)
  return "| " + cells.map.with_index { |c, i| c.to_s.ljust(widths[i]) }.join(" | ") + " |"
end

def print_table(title, headers, rows)
  puts
  puts title
  widths = headers.each_with_index.map do |h, i|
    lens = rows.map { |r| r[i].to_s.length }
    [h.to_s.length, lens.empty? ? 0 : lens.max].max
  end
  puts table_row(headers, widths)
  puts table_row(widths.map { |w| '-' * w }, widths)
  rows.each { |r| puts table_row(r, widths) }
end

def main
  configs = build_configs
  puts "Oráculo tabla 2.5D — modelo clásico (Settings PROJECTION_MODEL=#{OracleConfig::PROJECTION_MODEL})"
  puts "Config: VANISHING_STRENGTH=#{OracleConfig::VANISHING_STRENGTH} HORIZON_CURVE=#{OracleConfig::HORIZON_CURVE} " \
       "VERTICAL_GAIN=#{OracleConfig::VERTICAL_GAIN} CHARACTER_GAIN=#{OracleConfig::CHARACTER_GAIN} " \
       "MAX_MARGIN_ROWS=#{OracleConfig::MAX_MARGIN_ROWS} MAX_MARGIN_COLUMNS=#{OracleConfig::MAX_MARGIN_COLUMNS}"
  puts "Configs: #{configs.size} (tilt se clampea a #{OracleConfig::MAX_TILT_DEGREES}; 'stress' = sin clampear)"

  schemes = []
  [32, 48, 64, 96, 128].each { |n| schemes << { n: n, uniform: true, name: "uniforme N=#{n}" } }
  [32, 48, 64, 96, 128].each { |n| schemes << { n: n, uniform: false, name: "curvo   N=#{n}" } }

  # Cache de proyecciones (una por config) y de tablas por (config, esquema).
  projections = {}
  configs.each { |cfg| projections[cfg[:label]] = make_projection(cfg) }

  results = {}   # [scheme][label] => Result
  schemes.each do |scheme|
    results[scheme[:name]] = {}
    configs.each do |cfg|
      proj = projections[cfg[:label]]
      table = Table.new(proj, scheme[:n], uniform: scheme[:uniform])
      results[scheme[:name]][cfg[:label]] = measure(proj, table)
    end
  end

  # --- Tabla 0: rango de Y plana por config (contexto) ---------------------
  rows = []
  configs.each do |cfg|
    proj = projections[cfg[:label]]
    y_lo, y_hi = proj.domain
    rows << [cfg[:label], proj.margin_rows, proj.margin_columns,
             format('%.1f', y_lo), format('%.1f', y_hi), format('%.1f', y_hi - y_lo)]
  end
  print_table(
    "TABLA 0 — rango de Y plana que cubre la rejilla ampliada",
    %w[config filas_margen columnas_margen y_min y_max largo], rows
  )

  # --- Tabla A: peor caso por esquema --------------------------------------
  rows = []
  schemes.each do |scheme|
    res = results[scheme[:name]]
    argmax = lambda do |key|
      best = configs.max_by { |cfg| res[cfg[:label]].send(key) }
      [res[best[:label]].send(key), best[:label]]
    end
    fv, fl = argmax.call(:err_f)
    xv, xl = argmax.call(:err_x)
    wv, wl = argmax.call(:err_wall)
    cv, _cl = argmax.call(:err_char)
    worst = [fv, xv, wv, cv].max
    rows << [
      scheme[:name],
      format('%.4f', fv), fl,
      format('%.4f', xv), xl,
      format('%.4f', wv), wl,
      format('%.4f', worst),
      worst < 0.25 ? 'SI' : 'NO'
    ]
  end
  print_table(
    "TABLA A — peor caso global sobre todos los configs (px de pantalla)",
    %w[esquema errF_peor cfgF errX_peor cfgX errAlto_peor cfgA max_global bajo_0.25],
    rows
  )

  # --- Tabla B: detalle por config con N=64 uniforme ------------------------
  ref = 'uniforme N=64'
  rows = []
  configs.each do |cfg|
    r = results[ref][cfg[:label]]
    rows << [
      cfg[:label],
      format('%.4f', r.err_f),
      format('%.4f', r.err_x),
      format('%.4f', r.err_wall),
      format('%.4f', r.err_char),
      format('%.4f', r.err_f_vis),
      ([r.err_f, r.err_x, r.err_wall, r.err_char].max < 0.25) ? 'SI' : 'NO'
    ]
  end
  print_table(
    "TABLA B — detalle por config con #{ref} (px de pantalla)",
    %w[config errF errX_borde altoPared altoPersonaje errF_soloPantalla bajo_0.25],
    rows
  )

  # --- Tabla C: configs que fallan con 64 uniforme -------------------------
  failing = configs.reject do |cfg|
    r = results[ref][cfg[:label]]
    r.err_f < 0.25 && r.err_x < 0.25 && r.err_wall < 0.25 && r.err_char < 0.25
  end
  puts
  puts "Configs que NO bajan de 0.25 px con #{ref}: #{failing.size} de #{configs.size}"
  failing.each do |cfg|
    r = results[ref][cfg[:label]]
    puts format('  %-44s errF=%.4f errX=%.4f alto=%.4f', cfg[:label], r.err_f, r.err_x, r.err_wall)
  end

  # --- Diagnóstico: dónde se Concentra el error -----------------------------
  worst_cfg = configs.max_by { |cfg| results[ref][cfg[:label]].err_f }
  puts
  puts "Diagnóstico del peor config con #{ref}: #{worst_cfg[:label]}"
  proj = projections[worst_cfg[:label]]
  y_lo, y_hi = proj.domain
  puts format('  rango de Y plana: %.1f .. %.1f (margen de %d filas)',
              y_lo, y_hi, proj.margin_rows)
  puts "  quiebres de F en el rango: " \
       "#{proj.breakpoints.select { |b| b > y_lo && b < y_hi }.map { |b| format('%.1f', b) }.join(', ')}"
  [['uniforme N=64', true], ['curvo   N=64', false]].each do |name, uniform|
    tab = Table.new(proj, 64, uniform: uniform)
    pts = worst_points(proj, tab, 5)
    puts "  #{name}: puntos con más error de F -> " \
         "#{pts.map { |(y, e)| format('y=%.1f err=%.4f', y, e) }.join(' | ')}"
  end
  # ¿Basta con recortar la tabla a la pantalla visible (sin márgenes)?
  v_lo, v_hi = proj.visible_domain
  [[true, 'uniforme'], [false, 'curvo']].each do |uniform, name|
    tab = Table.new(proj, 64, uniform: uniform, y_lo: v_lo, y_hi: v_hi)
    puts "  #{name} N=64 SOLO en la pantalla visible (#{format('%.1f..%.1f', v_lo, v_hi)}): " \
         "errF=#{format('%.4f', max_err_f(proj, tab, v_lo, v_hi))}"
  end
  # N estimado que necesitaría el reparto uniforme para llegar a 0.25 px:
  # el error del quiebre escala como |ds|*h/4 y el de la curvatura como
  # |F''|*h²/8.
  ds = (proj.horizon_curve_px > 0) ? (2.0 * proj.horizon_curve_px / (proj.height / 2.0)) : 0.0
  h_kink = (ds > 0) ? (4.0 * 0.25 / ds) : Float::INFINITY
  f2_max = proj.breakpoints.map { |b| proj.second_derivative(b) }.push(
            proj.second_derivative((y_lo + y_hi) / 2.0)).map(&:abs).max
  h_quad = (f2_max > 0) ? Math.sqrt((8.0 * 0.25) / f2_max) : Float::INFINITY
  h_need = [h_kink, h_quad].min
  n_need = ((y_hi - y_lo) / h_need).ceil + 1
  puts format('  N uniforme estimado para 0.25 px: ~%d (h<=%.1f px; salto de derivada %.4f, F''<=%.6f)',
              n_need, h_need, ds, f2_max)

  # --- Recomendación -------------------------------------------------------
  target = 0.25
  max_err = lambda do |name|
    results[name].values.map { |r| [r.err_f, r.err_x, r.err_wall, r.err_char].max }.max
  end
  ok_schemes = schemes.select { |s| max_err.call(s[:name]) < target }
  # Recomendado: el menor N que cumple el objetivo con margen x2 (holgura
  # para lo que no se ha barrido: modelo :camera, blend intermedio, etc.).
  recommended = schemes.select { |s| max_err.call(s[:name]) <= target / 2.0 }
                      .min_by { |s| s[:n] }
  recommended ||= ok_schemes.min_by { |s| s[:n] }
  minimum = ok_schemes.min_by { |s| s[:n] }
  puts
  puts "=== Recomendación ==="
  if recommended
    name = recommended[:name]
    res = results[name]
    puts "N = #{recommended[:n]} (#{name.strip}) — peor error #{format('%.4f', max_err.call(name))} px " \
         "(objetivo < #{target} px)."
    puts format('  peor errF=%.4f  errX_borde=%.4f  altoPared=%.4f  altoPersonaje=%.4f',
                res.values.map(&:err_f).max,
                res.values.map(&:err_x).max,
                res.values.map(&:err_wall).max,
                res.values.map(&:err_char).max)
    puts "  N mínimo que cumple: #{minimum[:n]} (#{minimum[:name].strip}, #{format('%.4f', max_err.call(minimum[:name]))} px)"
    uniform_ok = ok_schemes.select { |s| s[:uniform] }.min_by { |s| s[:n] }
    if uniform_ok
      puts "  Reparto uniforme: N = #{uniform_ok[:n]} es el menor que cumple " \
           "(#{format('%.4f', max_err.call(uniform_ok[:name]))} px)"
    else
      best_u = schemes.select { |s| s[:uniform] }.max_by { |s| max_err.call(s[:name]).abs * -1 }
      puts "  Reparto uniforme: NINGÚN N probado cumple (mejor: #{best_u[:name].strip}, " \
           "#{format('%.4f', max_err.call(best_u[:name]))} px)"
    end
    puts "  Referencia del plan (uniforme N=64): #{format('%.4f', max_err.call('uniforme N=64'))} px"
  else
    puts "Ningun esquema probado baja de #{target} px."
  end
  puts
end

main if $PROGRAM_NAME == __FILE__
