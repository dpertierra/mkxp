---
name: ruby-essentials
description: >
  Ruby 3.1+ aplicado a Pokémon Essentials / MKXP-Z: sintaxis moderna, blocks/procs/lambdas
  (next vs return), metaprogramación segura (alias_method, define_method), Marshal, encoding,
  rendimiento del lenguaje y trampas típicas del motor. Usar al escribir o revisar cualquier
  código Ruby del proyecto. Triggers: dudas de sintaxis, "refactoriza", "optimiza este método",
  "por qué crashea este proc", revisión de estilo.
---

# Ruby 3.1 para Essentials

Reemplaza a la skill genérica "ruby-expert": aquí NO hay Rails, RSpec ni gems — el entorno es CRuby 3.1 embebido en MKXP-Z, sin Gemfile, con la stdlib disponible. El estilo del proyecto manda: leer código vecino antes de escribir (mucho del motor usa estilo clásico Essentials: `pbMetodo`, `snake_case`, módulos con `def self.`).

## Sintaxis moderna disponible (úsala con criterio)

```ruby
# Safe navigation — estándar del proyecto para dispose
@viewport&.dispose

# Endless methods — buenos para one-liners de consulta
def admin? = @role == :admin
def sx(val) = (val * SCALE_X).round

# Hash shorthand (3.1) y kwargs
user = { name:, level: }
def initialize(name:, level: 5)

# Pattern matching — útil para parsear datos heterogéneos
case datos
in { status: 200, body: }   then procesar(body)
in [first, *rest]           then ...
end

# Numbered params — solo en blocks cortos y obvios
party.map { _1.name }
```

Evitar por legibilidad en este codebase: Ractors, Fibers, RBS, refinements. Threads existen en MKXP-Z pero casi nunca son la respuesta correcta en un frame loop — preferir trabajo incremental por frame (`:on_frame_update` con contadores).

## Blocks, procs y lambdas — LA trampa del motor

Todo el sistema de handlers de Essentials usa `proc`. En un `proc`, `return` intenta salir del contexto que lo definió → **LocalJumpError** o salida inesperada. Regla R3:

```ruby
ItemHandlers::UseFromBag.add(:X, proc { |item|
  next 0 if condicion_fallo    # next = "return" del proc, con valor
  next 1
})
```

- `proc` ignora aridad sobrante; `lambda` la exige y su `return` sí es local. El motor usa `proc` — seguir el patrón del motor.
- En métodos normales, `return` normal. En blocks de iteración (`each`, `map`), `next`/`break` según semántica.

## Metaprogramación — solo la que usa el proyecto

```ruby
# alias_method: ÚNICO mecanismo de parcheo permitido (R5)
class Battle
  alias_method :_ZBOX_mip_orig_pbStartBattle, :pbStartBattle
  def pbStartBattle
    _ZBOX_mip_orig_pbStartBattle
  end
end

# attr_accessor para extender clases de datos persistidas
# define_method para familias de métodos repetitivos (con moderación)
# send/public_send para despacho dinámico verificado con respond_to?
```

Prohibido/peligroso aquí: `prepend` (R5), `method_missing` en clases del motor (rompe el debugging de otros), monkey-patches a clases Ruby core (ya existe `003_Technical/003_RubyUtilities.rb` — revisar qué añade antes de duplicar: `String`, `Array`, `Hash`, `Color`, CSV, etc.).

## Marshal y persistencia

Los saves y los `.dat` son `Marshal`. Implicaciones:

- Toda clase guardada en el save debe existir al cargar → si un plugin se desinstala, sus objetos en el save crashean la carga. Diseñar datos persistentes con tipos simples (Hash/Array/String/Symbol/Integer) cuando sea viable, o clase propia registrada con `ensure_class` en `SaveData.register`.
- No guardar en el save: bitmaps, sprites, viewports, procs, referencias a escenas. Solo datos puros.
- Atributos nuevos en clases ya persistidas llegan como `nil` desde saves viejos → inicialización defensiva (`@x ||= default`) o conversión en `SaveData` (`004_SaveData_Conversion.rb`).

## Encoding y archivos

- `.rb` en **UTF-8 sin BOM**; con acentos/ñ → `# encoding: utf-8` primera línea.
- Rutas con forward slash `/` siempre (multiplataforma; en Linux/macOS el FS es case-sensitive — respetar mayúsculas exactas de archivos de assets).
- I/O siempre con `begin/rescue` y log (`Console.echoln_li`, `PBDebug.log`).

## Rendimiento del lenguaje (frame loop a 60fps)

- Evitar crear objetos por frame: strings con `+`/interpolación, arrays temporales, `Color.new` repetidos → cachear en constantes o ivars.
- `each` sobre rangos/arrays preexistentes mejor que construir arrays nuevos (`select.map` encadenados crean intermedios; en hot paths usar un solo bucle).
- Comparar símbolos, no strings (`:FIRE`, no `"FIRE"`); GameData usa símbolos.
- `case/when` sobre tablas Hash de despacho cuando hay decenas de ramas calientes.
- No abusar de `rescue` como control de flujo: es caro y esconde bugs — validar con `nil?`/`respond_to?`/`try_get`.

## Estilo del proyecto

- 2 espacios, `snake_case` métodos/variables, `CamelCase` clases/módulos, `SCREAMING_SNAKE` constantes.
- Prefijo histórico `pb` en métodos públicos estilo motor (`pbStartScene`) — mantener coherencia con el código que se extiende; en módulos propios internos, nombres limpios sin `pb` también son válidos.
- Un método = una acción; sin duplicar bloques; early returns con guard clauses (`return if pkmn.nil?`).
- Comentarios solo donde el código no puede decirlo (constantes con unidades, invariantes, índices de switches).
