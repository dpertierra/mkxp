# Reference · Interpreter, script-calls, switches/variables y custom de eventos

Fuentes: `009_Game processing/004_Interpreter.rb` (clase `Interpreter`, helpers), `005_Interpreter_Commands.rb`
(implementación de los comandos de evento RPG Maker), `037_Overworld/002_Overworld.rb` (movimiento y
contenido), `058_Utilities/003_Utilities_Pokemon.rb` (`pbAddPokemon`), y `037_Overworld/010_Events Utilities.rb`
(custom Zik). Verificar firmas reales antes de usar.

## Referenciar personajes: `get_character`

Dentro de una caja "Script" de un evento, `self` es el `Interpreter`:
- `get_character(0)` → **este** evento (el que ejecuta el script). Atajo: `get_self`.
- `get_character(-1)` → el jugador (`$game_player`). Atajo: `get_player`.
- `get_character(id)` → el evento con ese `id` en el mapa. Atajo: `get_event(id)`.

```ruby
ev = get_character(0)
ev.turn_down            # mira hacia abajo
puts ev.x, ev.y         # posición en tiles
```
`pbGlobalLock` / `pbGlobalUnlock` congelan/descongelan todos los eventos (típico al inicio/fin de una
escena para que nadie se mueva).

## Mover eventos por script

```ruby
def pbMoveRoute(event, commands, waitComplete = false)   # 037/002
```
`commands` es un Array de comandos `PBMoveRoute::...` (Down, Left, Right, Up, Jump, Wait, TurnDown,
TurnTowardPlayer, SpeedDown, ThroughOn…). Con `waitComplete = true` bloquea hasta terminar.

```ruby
pbMoveRoute(get_self, [PBMoveRoute::Up, PBMoveRoute::Up, PBMoveRoute::TurnDown], true)
```
Otros helpers de movimiento (037/002): `pbWait(duration)` (espera en frames),
`pbTurnTowardEvent(event, otherEvent)`, `pbMoveTowardPlayer(event)`. Para "¡!" sobre un NPC:
`pbExclaim(events)` / `pbNoticePlayer(event)` (`058/002`).

## Switches, variables y self-switches

- **Switches** globales: `$game_switches[id]` (lee/escribe `true`/`false`).
- **Variables** globales: `$game_variables[id]`.
- **Self-switches** (por evento): `$game_self_switches[[map_id, event_id, "A"]] = true`, o desde el
  interpreter `pbSetSelfSwitch(event_id, "A", true, map_id = -1)` (mapa actual si `-1`). Tras cambiarlos
  conviene `$game_map.need_refresh = true` para que la página del evento se reevalúe.

```ruby
# Desde un plugin, usa SIEMPRE una constante descriptiva (R12), no el número:
$game_switches[MiPlugin::Settings::SW_INTRO_VISTA] = true
```
> R12: en código de plugin nunca uses el literal numérico del switch/variable; define una constante en el
> `Settings` del plugin. En el editor de eventos sí se usan por número.

## Common events y comandos del Interpreter

- Los **common events** se lanzan desde eventos (Call Common Event) o por script con la maquinaria del
  interpreter. Los autorun/parallel se evalúan según sus condiciones (switch/variable).
- Los comandos de evento (mensajes, condiciones, bucles, mover ruta, reproducir BGM/SE, transferir
  jugador, cambiar clima…) están implementados en `005_Interpreter_Commands.rb` como `command_NNN`
  (códigos RPG Maker). Normalmente no se llaman a mano: el evento los genera. Para replicar uno desde un
  plugin, busca su `command_NNN` y mira a qué helper global delega.

## Helpers de contenido frecuentes (verificados)

- `pbReceiveItem(item, quantity = 1)` (`037/002`) — da un objeto al jugador gestionando mochila llena y
  el mensaje de "obtuviste…".
- `pbItemBall(item, quantity = 1)` (`037/002`) — el patrón "Poké Ball en el suelo" (da el item y borra
  el evento con su self-switch).
- `pbAddPokemon(pkmn, level = 1, see_form = true)` (`058/003`) — añade un Pokémon al equipo/PC
  (`pkmn` puede ser símbolo de especie o un objeto `Pokemon`). Para construirlo a medida → [[pokemon-api]].
- Combates: `TrainerBattle.start(...)` / `WildBattle.start(...)` → [[battles-runtime]] (en eventos de
  entrenador se envuelve con `pbTrainerIntro`/`pbTrainerEnd`). **No** existe un `pbTrainerBattle` global
  en v21.1.

## Extensiones custom de eventos (Zik — `037/010_Events Utilities.rb`)

Amplían lo que un evento puede hacer **sin escribir scripts**, a través de su **nombre** o de
**comentarios** en la primera página. Útil saberlas para no reinventar:

Como parte del **nombre** del evento:
- `sizeblock(x,y)` — define un área de colisión sin necesidad de gráfico.

Como **comentario** en el evento (`s:...`):
- `s:Hitbox/X,Y` y `s:Hitbox_true/X,Y` — área de colisión (la variante `_true` gira con el evento).
- `s:Hitbox_radius/Rx,Ry` (+`_true`) — colisión por radio (basta un valor).
- `s:Offset/X,Y` — desplaza el gráfico visualmente (px).
- `s:Offset_shadow/X,Y` — desplaza la sombra (px).
- `s:Float` — animación de levitación suave.
- `s:doppelganger` (+`_up/_down/_left/_right`) — usa el gráfico actual del jugador.
- `s:pokemon_event/Nombre` y `s:pokemon_event_shiny/Nombre` (+ direcciones) — cambia el gráfico al del
  Pokémon indicado; al interactuar suena su cry.

Si una tarea pide "que un evento tenga hitbox custom / flote / muestre un Pokémon", esto ya existe: úsalo
en vez de programar un plugin. Para ver la lista completa y exacta, abre la cabecera de
`037/010_Events Utilities.rb`.

## Empaquetar lógica de evento en un plugin

Cuando la lógica crece (más que unas líneas de Script), muévela a un plugin `[ZBOX]` y deja en el evento
solo `MiPlugin.metodo` ([[plugin-architecture]], R8: handlers/eventos finos). Así el evento no carga
lógica de negocio.
