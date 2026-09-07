---
name: plugin-architecture
description: >
  Arquitectura de plugins para Pokémon Essentials 21.1 / La Base de Sky. Usar SIEMPRE al crear,
  modificar o parchear un plugin: estructura de carpetas, meta.txt, orden de carga, alias_method,
  persistencia (SaveData, PokemonGlobalMetadata), handlers (EventHandlers, ItemHandlers, MenuHandlers,
  MoveHandlers), anti-patrones y checklist de entrega. Triggers: "crea un plugin", "parchea",
  "sistema de X", "guarda datos", "añade un handler", "hook", "extiende el menú".
---

# Arquitectura de plugins — Essentials 21.1 / LBdS

Zona de trabajo: `..\LA BASE DE SKY\Plugins\`. Motor en `..\LA BASE DE SKY\Data\Scripts\` (READ-ONLY salvo petición explícita).

## Estructura de un plugin propio

```
Plugins/[ZBOX] NombrePlugin/
  meta.txt           → Obligatorio
  000_Settings.rb    → Solo constantes: layout, colores, rutas, switches/variables, opciones
  001_Core.rb        → Lógica de negocio. Sin sprites, sin registro de handlers
  002_Handlers.rb    → Solo EventHandlers / ItemHandlers / MenuHandlers (delegan a Core)
  003_Scene.rb       → Dibujo e input. Llama métodos públicos de Core
  004_DataManager.rb → Persistencia (SaveData / PokemonGlobalMetadata)
```

- Carga en orden alfabético-numérico; `000_` primero. Los nombres pueden adaptarse al proyecto y haber tantos archivos como hagan falta — la separación de responsabilidades es lo que no se negocia (R7, R8).
- Encoding: **UTF-8 sin BOM**. Si hay acentos/ñ en el archivo, primera línea: `# encoding: utf-8`.
- Assets del plugin: `Graphics/Plugins/NombrePlugin/` (ver skill assets-pipeline).

### meta.txt

```
Name        = Nombre del Plugin
Version     = 1.0.0
Essentials  = 21.1
Credits     = Zik
```

Opcionales: `Requires`, `Optional`, `Conflicts`, `First`, `Last`, `Priority`, `Disabled`.

- Sin dependencias → no poner `Priority`/`Requires`/`Optional`.
- Cargar **después** de otro plugin (para hacerle `alias_method`) → `Optional = NombreExacto` (o `Last = true`). `Requires = X` solo si sin X el plugin no funciona.
- `Conflicts` solo si el usuario lo especifica. Rango típico de `Priority` en el repo: 0–100.
- Alternativa válida que usan plugins existentes: `PluginManager.register({:name => "...", :version => "...", :credits => "..."})`. Respetarla en terceros; en plugins propios nuevos usar siempre `meta.txt`.

Prefijos en el repo: `[LBDS_CUST]` (propios nuevos — estándar del usuario), `[LBDSKY]`, `[LBDS]`, `[SV]`, `[ZA]`, `[TFR]`, `[LWN]`, carpetas sin prefijo (terceros/legacy). **No renombrar carpetas de terceros.**

## Parcheo — alias_method, nunca prepend

```ruby
class Battle
  alias_method :_LBDS_CUST_miplugin_orig_pbStartBattle, :pbStartBattle
  def pbStartBattle
    resultado = _LBDS_CUST_miplugin_orig_pbStartBattle
    MiPlugin.on_battle_start
    resultado
  end
end
```

- Convención: `_LBDS_CUST_<abreviatura>_orig_<metodo>` (evita colisiones entre plugins que parchean lo mismo).
- Con argumentos: alias igual, redefinir con la misma firma y pasarlos al original.
- Aplica al motor Y a plugins de terceros. Plugins `[LBDS_CUST]` propios se editan in-place, sin parche encima del propio código.
- `prepend` **prohibido** (a lo sumo sugerible en métodos no críticos, nunca prioridad).

## Persistencia

### Simple — extender PokemonGlobalMetadata

```ruby
class PokemonGlobalMetadata
  alias_method :_LBDS_CUST_mip_orig_initialize, :initialize
  def initialize
    _LBDS_CUST_mip_orig_initialize
    @mi_plugin_dato = nil
  end
  attr_accessor :mi_plugin_dato
end
# Acceso: $PokemonGlobal.mi_plugin_dato
```

Ojo: partidas guardadas ANTES de instalar el plugin tendrán `nil` en el atributo nuevo → siempre acceso defensivo (`$PokemonGlobal.mi_plugin_dato ||= valor_default`).

### Complejo — clase propia + SaveData.register

```ruby
class MiPluginData
  attr_accessor :nivel, :puntos, :flags
  def initialize
    @nivel  = 1
    @puntos = 0
    @flags  = {}
  end
end

SaveData.register(:mi_plugin_datos) do
  ensure_class :MiPluginData
  save_value     { $mi_plugin_data }
  load_value     { |val| $mi_plugin_data = val }
  new_game_value { MiPluginData.new }
end
```

Acceso SOLO vía método público en Core (`MiPlugin.datos`), nunca la global suelta desde fuera.

Referencias del motor: `007_Save data/002_SaveData.rb` (`SaveData::FILE_PATH`, `save_to_file`, `exists?`), `003_SaveData_Value.rb` (procs save/load/new_game), `004_SaveData_Conversion.rb` (migraciones entre versiones), `009_Game processing/002_StartGame.rb` (`Game.save(save_file = SaveData::FILE_PATH, safe: false)` y `Game.load(save_data)`).

## Handlers

### EventHandlers

```ruby
EventHandlers.add(:on_player_step_taken, :ZBOX_miplugin_pasos, proc {
  MiPlugin.on_paso
})
```

Lista completa (motor: `009_Game processing/006_Event_Handlers.rb` y `007_Event_HandlerCollections.rb`):

`:on_game_initialize`, `:on_new_game`, `:on_game_load`, `:on_game_map_setup`, `:on_new_spriteset_map`, `:on_frame_update`, `:on_leave_map`, `:on_enter_map`, `:on_map_or_spriteset_change`, `:on_player_change_direction`, `:on_leave_tile`, `:on_step_taken`, `:on_player_step_taken`, `:on_player_step_taken_can_transfer`, `:on_player_interact`, `:on_trainer_load`, `:on_wild_species_chosen`, `:on_wild_pokemon_created`, `:on_calling_wild_battle`, `:on_start_battle`, `:on_end_battle`, `:on_wild_battle_end`, `:on_badge_obtained`.

### ItemHandlers

```ruby
ItemHandlers::UseFromBag.add(:MI_OBJETO, proc { |item|
  next MiPlugin.usar_desde_mochila(item)   # next 0=falló, 1=usado, 2=usado y cierra mochila
})
ItemHandlers::UseInField.add(:MI_OBJETO, proc { |item| next MiPlugin.usar_en_campo(item) })
ItemHandlers::UseOnPokemon.add(:MI_OBJETO, proc { |item, qty, pkmn, scene|
  next false if pkmn.nil? || pkmn.egg?
  next MiPlugin.usar_en_pokemon(item, pkmn)
})
ItemHandlers::CanUseInBattle.add(:MI_OBJETO, proc { |item, pokemon, battler, move, firstAction, battle, scene, showMessages|
  next MiPlugin.puede_usar_en_batalla?(item, battler)
})
ItemHandlers::UseInBattle.add(:MI_OBJETO, proc { |item, battler, battle|
  MiPlugin.efecto_en_batalla(item, battler, battle)
})
```

### Batalla / movimientos

```ruby
BattleHandlers::DamageCalcUserItem.add(:MI_OBJETO, proc { |item, user, target, move, mults, power, type|
  mults[:attack_multiplier] *= 1.5 if move.physicalMove?
})

Battle::Move.register_move(:CODIGO_FUNCION) do
  def pbMoveFailed?(user, targets, show_message) = false
  def pbEffectAgainstTarget(user, target); end
  def pbAdditionalEffect(user, target); end
end
```

Habilidades: `Battle::AbilityEffects` en `034_Other battle code/009_Battle_AbilityEffects.rb` (~4000 líneas con TODOS los triggers: `SpeedCalc`, `DamageCalcFromUser`, `OnSwitchIn`, etc. — buscar ahí el trigger exacto antes de implementar).

### Menú debug (F9)

```ruby
MenuHandlers.add(:debug_menu, :mi_opcion, {
  "name"        => _INTL("Mi Opción"),
  "parent"      => :main,
  "description" => _INTL("..."),
  "effect"      => proc {
    MiPlugin.accion_debug
    next false
  }
})
```

También existen `:pause_menu`, `:party_menu`, `:pokegear_menu` y otros — verificar registros existentes con grep `MenuHandlers.add(:` antes de extender.

### Hook condicional a otro plugin

```ruby
if PluginManager.installed?("Quest System")   # nombre EXACTO del meta.txt
  EventHandlers.add(:on_end_battle, :ZBOX_mip_quest_hook, proc { |result|
    next if result != 1
    MiPlugin.notificar_victoria
  })
end
```

`PluginManager.installed?` a nivel de archivo se evalúa al cargar. Check dinámico → dentro del método/proc.

## Seguridad y errores

```ruby
return if !$player || $player.party.empty?
return if pkmn.nil? || pkmn.egg? || pkmn.fainted?

item_data = GameData::Item.try_get(:MI_OBJETO)
return unless item_data

begin
  datos = File.read("ruta")
rescue => e
  Console.echoln_li "MiPlugin ERROR: #{e.message}"
  return
end
```

Dispose seguro: `@viewport&.dispose`, `pbDisposeSpriteHash(@sprites)`.

## Anti-patrones (incorrecto → correcto)

- `return 1` en proc → `next 1` (R3).
- Lógica de negocio dentro del proc → `proc { next MiPlugin.metodo(args) }` (R8).
- `Viewport.new(0,0,512,384)` → `::Settings::SCREEN_WIDTH/HEIGHT` + `Layout` (R9). Siempre `::Settings::` con ruta completa para no chocar con `Settings` del plugin.
- `pbMessage("Hola")` → `pbMessage(_INTL("Hola"))`; `_INTL("Tienes #{n}")` → `_INTL("Tienes {1}", n)` (R1).
- `GameData::Item.get(:X)` dudoso → `try_get` + check (R4).
- `prepend` → `alias_method` (R5).
- Editar `Plugins/[LBDSKY] X/...` → plugin `[ZBOX]` aparte con alias (R11).
- `$game_switches[42]` → `$game_switches[MiPlugin::Settings::SW_QUEST]` (R12).
- `$mi_dato = true` global suelta → SaveData/clase en Core con acceso controlado.
- Mutar datos del juego desde la escena UI → la escena llama a Core.

## Globales frecuentes

`$player` (Player), `$bag`, `$PokemonGlobal`, `$PokemonStorage`, `$PokemonEncounters`, `$stats`, `$game_map`, `$game_player`, `$game_temp`, `$game_system`, `$game_switches`, `$game_variables`.

Resultado de batalla: `Battle::Outcome::WIN`(1), `LOSE`(2), `FLEE`(3), `CATCH`(4), `DRAW`(5).

Snippets: `pbMessage(_INTL("..."))`, `pbConfirmMessage(_INTL("¿Seguro?"))`, `pbSEPlay("GUI sel decision")`, `$bag.add(:OBJETO, qty)`, `pkmn.learn_move(:MOV)`, `pbChoosePokemon(1, 2)` (resultado en `$game_variables[1]`).

## Checklist de entrega

**Código:** `_INTL` en todo texto de jugador · sin `return` en procs · `try_get` donde aplique · `alias_method` (no `prepend`) · validación nil/estado · un archivo una responsabilidad · handlers delegando · sin números mágicos (UI ni switches) · interpolación `{1}` en `_INTL`.

**Plugin:** `meta.txt` completo · prefijo `[ZBOX]` · sin tocar `Data/` ni terceros sin petición · alias con convención · `Priority/Requires/Optional` correctos si hay dependencias.

**UI:** resolución vía `::Settings::` · layout en `000_Settings.rb` · dispose seguro · sin lógica de negocio en escena.

**Persistencia:** simple → `PokemonGlobalMetadata`; complejo → `SaveData.register` · acceso vía Core · defaults para partidas existentes.

**Archivos:** UTF-8 sin BOM · `# encoding: utf-8` si hay no-ASCII.

**Entrega al usuario:** rutas exactas de archivos creados/modificados + pasos de verificación manual (cargar partida → probar feature → guardar/recargar → partida nueva → revisar `errorlog.txt`/`mkxp.log`). El agente NO testea; el usuario sí.
