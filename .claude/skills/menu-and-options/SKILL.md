---
name: menu-and-options
description: >
  Construir menús del juego vía MenuHandlers (debug F9, pausa, party, PC, debug de Pokémon/batalla)
  y opciones del menú de Opciones en los DOS sistemas de La Base de Sky: el antiguo
  (019_UI_Options, clases EnumOption/SliderOption...) y el v22 con pestañas
  (020_UI_Options_v22, PageHandlers + tipos símbolo). Categorías reales, parents válidos, tipos de
  opción configurables, dónde anidar cada cosa y persistencia en PokemonSystem. Usar al añadir una
  entrada de debug, una opción configurable, o un comando a cualquier menú. Triggers: "menú debug",
  "opción de opciones", "MenuHandlers", "categoría del debug", "F9", "pestaña de opciones", "slider".
---

# Menús (MenuHandlers) y Opciones — La Base de Sky

Todos los menús del juego (debug, pausa, party, PC, opciones) se construyen con **`MenuHandlers.add(:menu, :clave, hash)`**. El motor `MenuHandlers` vive en `009_Game processing/007_Event_HandlerCollections.rb` (líneas ~103-159): `add`, `each_available` (ordena por `"order"`, evalúa `"condition"`), `call`.

**Regla de oro antes de añadir una entrada: el `"parent"` debe existir.** Si apuntas a un parent no registrado, la entrada **no aparece** (no crashea, simplemente se pierde). El menú dibuja una clave como submenú solo si otra entrada la usa como `"parent"` (`hasSubMenu?`). Por eso `:plugins_menu` **NO existe** en el debug menu de LBdS — usarlo como parent oculta la entrada. (El plugin Box Auto-Sort tiene este bug latente; sus entradas debug nunca salen.)

---

## 1. Debug menu (F9) — `:debug_menu`

Renderizado por `pbDebugMenu` en `063_Debug menus/002_Debug_Menus.rb`. Registros del core en `063_Debug menus/003_Debug_MenuCommands.rb`. Se abre con F9 en el mapa o desde `:pause_menu → :debug`.

### Hash de una entrada

```ruby
MenuHandlers.add(:debug_menu, :mi_clave, {
  "name"        => _INTL("Texto visible"),     # string o proc
  "parent"      => :main,                       # DEBE existir (ver categorías abajo)
  "description" => _INTL("Ayuda mostrada abajo"),# opcional; string o proc
  "always_show" => false,                        # opcional; false = oculta en modo "no mostrar todo"
  "effect"      => proc { MiPlugin.accion; next }# acción; en debug_menu el retorno se ignora
})
```

- `"effect"` se invoca con `MenuHandlers.call(:debug_menu, cmd, "effect")` — **sin args y el valor de retorno se ignora** (salvo el caso especial `:warp`, que recibe `sprites, viewport` y retorna truthy para salir del menú). Aun así usar `next`, nunca `return` (R3).
- Una entrada **sin `"effect"`** cuya clave sea usada como `"parent"` por otras = se convierte en **submenú** automáticamente.

### Categorías reales (parents válidos)

Raíz: `:main`. Submenús de primer nivel ya registrados (todos `"parent" => :main`):

| Clave | Nombre | Contenido |
|-------|--------|-----------|
| `:field_menu` | Opciones de campo... | warp, interruptores, variables, PC, guardería, Safari/Concurso |
| `:battle_menu` | Opciones de combate... | testear combates, reglas, errante, Rep.Exp, logging |
| `:pokemon_menu` | Opciones de Pokémon... | curar, añadir, llenar/vaciar cajas, almacenamiento |
| `:shadow_pokemon_menu` | (bajo `:pokemon_menu`) | PokéCepo, Cámara de Purificación |
| `:items_menu` | Opciones de Objetos... | añadir objeto, llenar/vaciar mochila |
| `:player_menu` | Opciones del Jugador... | dinero, medallas, Pokédex, Pokégear, outfit, nombre |
| `:pbs_editors_menu` | Editores de archivos PBS... | editores de especies/moves/items/etc. |
| `:editors_menu` | Otros editores... | editores varios, Tileset Rearranger |
| `:files_menu` | Opciones de archivos... | compilar, recargar caché, exportar |

Añadidos por features: `:event_report_menu` (`060_Debug/002_Event Reporting`), `:item_ball_printer` (`060_Debug/009_ItemBallPrinter.rb`).

### Patrón correcto para entradas de plugin

Guardar siempre con `if $DEBUG` para que no aparezca en release. Dos opciones:

```ruby
# A) Colgar de una categoría existente (lo más simple)
if $DEBUG
  MenuHandlers.add(:debug_menu, :zbox_colormatch, {
    "name"        => _INTL("Color Match"),
    "parent"      => :pokemon_menu,            # parent que SÍ existe
    "description" => _INTL("Lanza el minijuego de mezclar colores"),
    "effect"      => proc { ColorMatch.run(5); next }
  })
end

# B) Crear primero TU propio submenú, luego colgarle entradas
if $DEBUG
  MenuHandlers.add(:debug_menu, :zbox_menu, {       # <- el parent que faltaba
    "name"        => _INTL("Plugins [ZBOX]..."),
    "parent"      => :main,
    "description" => _INTL("Herramientas de depuración de mis plugins")
  })
  MenuHandlers.add(:debug_menu, :zbox_colormatch, {
    "name"        => _INTL("Color Match"),
    "parent"      => :zbox_menu,                     # ahora sí existe
    "description" => _INTL("Lanza el minijuego de mezclar colores"),
    "effect"      => proc { ColorMatch.run(5); next }
  })
end
```

El error que dio el agente antes (`"parent" => :plugins_menu`) era la opción B **sin registrar el submenú padre**. Solución: registrar `:zbox_menu` con `"parent" => :main` primero, o usar la opción A.

### Otros menús debug (cada uno su lista propia)

| Menú | Renderiza | Firma del `"effect"` | Parents propios | Notas |
|------|-----------|----------------------|-----------------|-------|
| `:pokemon_debug_menu` | `pbPokemonDebug` (party/PC) — `008_Debug_PokemonCommands.rb` | `\|pkmn, pkmnid, heldpoke, settingUpBattle, screen\|` → `next true` cierra, `next false` sigue | `:hp_status_menu`, `:level_stats`, `:moves`, `:cosmetic`, `:species_and_form`, `:shadow_pkmn` | Acción sobre 1 Pokémon |
| `:battle_debug_menu` | `pbBattleDebug` — `005_Debug_BattleCommands.rb` | `\|battle\|` | `:battlers`, `:trainers`, `:field` | Debug en combate |
| `:battle_pokemon_debug_menu` | `pbBattlePokemonDebug` — `006_Debug_BattlePkmnCommands.rb` | `\|pkmn, battler, screen\|` | — | Clave `"usage" => :pokemon`/`:battler` filtra disponibilidad |

---

## 2. Menús del juego (no debug)

Mismo `MenuHandlers.add`, distinto primer símbolo. Verificar registros existentes con grep antes de extender.

| Menú | Registro / render | Para qué |
|------|-------------------|----------|
| `:pause_menu` | `048_UI/002_UI_PauseMenu.rb` | Menú de pausa (Pokédex, Equipo, Mochila, Pokégear, Guardar, Opciones, Salir). Hash con `"name"`, `"order"`, `"condition"`, `"effect"` |
| `:party_menu` | `048_UI/007_UI_Party.rb` + plugins | Comandos al elegir un Pokémon del equipo |
| `:pc_menu` | `049_Non-interactive UI/007_UI_HallOfFame.rb` y otros | Menú principal del PC |
| `:pokegear_menu` | Pokégear | Apps del Pokégear |

Las firmas de `"effect"` varían por menú: leer el render correspondiente (`pbShowCommands`/loop que hace `MenuHandlers.call(:menu, cmd, "effect", *args)`) para saber qué args recibe.

---

## 3. Opciones — DOS sistemas según `Settings::USE_NEW_OPTIONS_UI`

Definido en `001_Settings/002_Settings.rb` (en LBdS = **`true`**). Ambos sistemas registran en `:options_menu`, pero el formato del hash difiere y cada bloque va guardado con `if Settings::USE_NEW_OPTIONS_UI` / `if !...`.

El valor de cada opción se persiste en la clase **`PokemonSystem`** (`$PokemonSystem`, se guarda aparte del save de partida). Para una opción nueva: reabrir `PokemonSystem`, añadir `attr_accessor` y default vía alias de `initialize` (patrón de persistencia, ver plugin-architecture).

```ruby
class PokemonSystem
  alias_method :_ZBOX_mip_orig_initialize, :initialize
  def initialize
    _ZBOX_mip_orig_initialize
    @mi_opcion = 0          # default; saves viejos lo verán nil → acceso defensivo
  end
  attr_accessor :mi_opcion
end
```

### 3a. Sistema NUEVO v22 (pestañas) — `020_UI_Options_v22.rb`

Páginas (pestañas) vía `PageHandlers` (`007_Event_HandlerCollections.rb` línea ~169). Páginas por defecto: `:gameplay`, `:audio`, `:graphics`, `:plugins`. La pestaña **`:plugins` aparece sola** cuando hay ≥1 opción con `"page" => :plugins` (su `:condition` es `PageHandlers.has_any?`). **Para opciones de plugin, usar `"page" => :plugins`.**

```ruby
if Settings::USE_NEW_OPTIONS_UI
  MenuHandlers.add(:options_menu, :mi_opcion, {
    "page"        => :plugins,                 # :gameplay | :audio | :graphics | :plugins
    "name"        => _INTL("Mi Opción"),
    "order"       => 10,
    "type"        => :toggle,                   # ver tabla de tipos
    "parameters"  => proc { [_INTL("Sí"), _INTL("No")] },
    "description" => _INTL("Qué hace esta opción."),
    "condition"   => proc { next true },        # opcional: ocultar según estado
    "disabled_proc" => proc { next false },     # opcional: gris/no editable
    "get_proc"    => proc { next $PokemonSystem.mi_opcion },
    "set_proc"    => proc { |value, screen| $PokemonSystem.mi_opcion = value }
  })
end
```

Para crear tu propia pestaña: `PageHandlers.add(:options_menu, :mi_pagina, { :name => proc { next _INTL("Mi Plugin") }, :order => 60, :description => proc { next _INTL("...") } })`.

**Tipos `"type"` (símbolo) del v22** — todos verificados en el render (`update_value`/`draw`):

| Tipo | UI | `"parameters"` | `get`/`set` value |
|------|----|----------------|--------------------|
| `:array` | Lista cíclica (← →), texto pasa por `_INTL` | `[a, b, c]` o proc | índice (Integer) |
| `:array_one` | Igual a `:array` pero sin ciclar al borde | idem | índice |
| `:toggle` | On/Off | `[_INTL("Sí"), _INTL("No")]` | 0/1 |
| `:number_type` | Entero discreto | `1..N` (Range) | Integer en rango |
| `:number_slider` | Slider | `[min, max, intervalo]` | Integer |
| `:arrow_option` | Flechas con valores libres | array o proc | índice |
| `:multiselect` | Marcar varios (Enter alterna) | `[a, b, c, ...]` | Array de índices |
| `:control` | Reasignar tecla/botón | constante `Input::X` | usa `"use_proc"` |
| `:use` | Botón de acción (no valor) | — | usa `"use_proc"` (sin get/set) |
| `:submenu` | Abre subpestañas | sub-páginas | usa `"submenu_pages"` |

Claves extra del hash v22: `"on_select"` (proc al posarse en la opción), `"disabled_proc"`, `"visible_proc"`, `"default_value"`, `"use_proc"` (para `:use`/`:control`), `"submenu_pages"` (para `:submenu`), `"multi_options"` (genera varias entradas dinámicas).

### 3b. Sistema ANTIGUO — `019_UI_Options.rb`

Activo solo si `USE_NEW_OPTIONS_UI = false`. Sin páginas. El `"type"` es una **clase**, no símbolo:

```ruby
if !Settings::USE_NEW_OPTIONS_UI
  MenuHandlers.add(:options_menu, :mi_opcion, {
    "name"        => _INTL("Mi Opción"),
    "order"       => 70,
    "type"        => EnumOption,                # clase (ver tabla)
    "parameters"  => [_INTL("Sí"), _INTL("No")],
    "description" => _INTL("Qué hace."),
    "condition"   => proc { next true },         # opcional
    "on_select"   => proc { |scene| ... },       # opcional
    "get_proc"    => proc { next $PokemonSystem.mi_opcion },
    "set_proc"    => proc { |value, scene| $PokemonSystem.mi_opcion = value }
  })
end
```

| Clase | Equivale (v22) | `"parameters"` |
|-------|----------------|----------------|
| `EnumOption` | `:array` | array de strings |
| `ArrowOption` (hija de Enum) | `:arrow_option` | array (o proc que lo retorna) |
| `NumberOption` | `:number_type` | `Range` o `[min, max]` |
| `SliderOption` | `:number_slider` | `[min, max, intervalo]` |
| `ButtonOption`→`:use`, `ToggleOption`→`:toggle`, `SubmenuOption`→`:submenu` (mapeados en `OPTION_TYPE_MAP`) | | |

### 3c. Compatibilidad y qué formato usar

El v22 **lee el formato antiguo** (convierte clase→símbolo con `OPTION_TYPE_MAP` y auto-asigna página por palabras clave del nombre con `auto_detect_page`). El antiguo **NO** entiende símbolos. Por tanto:

- LBdS usa `USE_NEW_OPTIONS_UI = true` → **escribir formato v22** (símbolo + `"page"`). Es lo recomendado.
- Para soportar ambos pase lo que pase, registrar las dos versiones con el guard `if Settings::USE_NEW_OPTIONS_UI` / `else` como hacen `020` y `019`.

---

## Checklist al tocar menús/opciones

- [ ] `"parent"` (debug) o `"page"` (opciones v22) **existe** — si es propio, registrarlo primero.
- [ ] Entradas de debug guardadas con `if $DEBUG`.
- [ ] `_INTL` en `"name"` y `"description"` (R1); en procs, `next` no `return` (R3).
- [ ] Opciones: `attr_accessor` + default en `PokemonSystem` vía alias; acceso defensivo para saves viejos.
- [ ] Opciones de plugin → `"page" => :plugins` (la pestaña aparece sola).
- [ ] Formato acorde a `USE_NEW_OPTIONS_UI` (LBdS = v22) o doble registro con guard.
- [ ] `"order"` coherente para posición; `"condition"`/`"disabled_proc"` para estados.
- [ ] No registrar lógica de negocio en el proc: delegar a Core (R8).
