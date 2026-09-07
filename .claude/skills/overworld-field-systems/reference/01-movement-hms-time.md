# Reference · MOs, terreno, día/noche, metadata y seguidores

Fuentes: `037_Overworld/005_Overworld_FieldMoves.rb` (MOs), `002_Overworld.rb` (terreno),
`004_Overworld_Time.rb` (día/noche), `003_Overworld_Metadata.rb`, `011_Game classes/011_Game_Follower.rb`
(+`012_Game_FollowerFactory.rb`).

## Movimientos de campo / MOs — `HiddenMoveHandlers`

Cada MO (Surf, Fuerza, Vuelo, Corte, Destello, Buceo, Excavar…) se implementa con **handlers**
registrados por el `id` del movimiento. Tres ganchos:

```ruby
module HiddenMoveHandlers
  CanUseMove     = ...   # ¿se puede usar aquí/ahora?  proc { |move, pkmn, showmsg| ... }  → true/false
  ConfirmUseMove = ...   # confirmación previa          proc { |move, pkmn| ... }
  UseMove        = ...   # ejecutar el efecto            proc { |move, pokemon| ... }
end
```
Entradas globales: `pbCanUseHiddenMove?(pkmn, move, showmsg = true)`, `pbConfirmUseHiddenMove(pkmn, move)`,
`pbUseHiddenMove(pkmn, move)`.

Registro (patrón de Corte, real):
```ruby
HiddenMoveHandlers::CanUseMove.add(:CUT, proc { |move, pkmn, showmsg|
  # comprobar insignia, terreno frente al jugador, etc.  next false si no procede
})
HiddenMoveHandlers::UseMove.add(:CUT, proc { |move, pokemon|
  pbCut   # el helper que hace la animación + quita el tile
})
```

### Crear una MO nueva
1. Define el movimiento en `moves.txt` ([[pbs-data]]) — su efecto **en combate** es aparte
   ([[battle-effects]]); aquí va el efecto **de campo**.
2. En un plugin `[ZBOX]`, registra `CanUseMove`/`UseMove` (y `ConfirmUseMove` si pide confirmación) con
   `.add(:MIMOVIMIENTO, proc { ... })`. Es aditivo (R8), no parchea nada.
3. Si se activa desde el menú de party o un objeto (MT/MO), engánchalo donde corresponda
   ([[menu-and-options]] para el comando de party).

Helpers de campo existentes que puedes reusar: `pbCut`, `pbSurfacing`, `pbCanFly?(pkmn, show_messages)`,
`pbFlyToNewLocation(pkmn, move)`. Reusa antes de reescribir (filosofía ponytail).

## Mecánicas de terreno (`002_Overworld.rb`)

Surf, salto de ledges, puentes, Rock Smash y el disparo de Corte viven aquí, junto con utilidades de mapa.
Los **terrain tags** del tileset deciden el comportamiento (agua, hierba, ledge…); para editarlos, el
editor de terrain tags está en debug (`061_Editor screens`) o en Maker Studio ([[maker-studio]]). Para
parchear una mecánica base (p.ej. cómo se salta un ledge) usa `alias_method` en `[ZBOX]` (R5).

`pbMoveRoute(event, commands, waitComplete)` y `pbWait(duration)` también están aquí, pero para scripting
de eventos ver [[events-and-messages]].

## Día/noche (`004_Overworld_Time.rb`)

```ruby
PBDayNight.isMorning?    # 5:00–9:59
PBDayNight.isDay?        # 5:00–19:59
PBDayNight.isAfternoon?  # 14:00–16:59
PBDayNight.isEvening?    # 17:00–19:59
PBDayNight.isNight?      # 20:00–4:59
```
- `pbGetTimeNow` → la hora actual del juego (puede estar fijada por evento/switch para forzar horarios).
- `PBDayNight.getTone`/`getShade` → el tono de color aplicado al mapa según la hora (`HOURLY_TONES`).
- Lógica que dependa de la hora (encuentros nocturnos, evoluciones por hora, eventos) usa estos
  predicados. Horarios/configuración custom en `001_Settings` ([[pbs-data]]/configuración del proyecto).

## Metadata de mapa y jugador (`003_Overworld_Metadata.rb`)

Acceso en runtime a las propiedades definidas en el PBS de metadata (música, clima de entrada, tipo de
mapa, posición de inicio, bicicleta permitida…). Lee `GameData::MapMetadata`/`GameData::PlayerMetadata`.
Los **valores** se definen en `metadata.txt`/`map_metadata.txt` ([[pbs-data]] → metadata-misc); este
archivo solo los **consulta**. Para que un mapa cambie de música/clima/tipo, edita su metadata PBS, no el
código.

## Seguidores / Pokémon que te sigue (`011/011`, `011/012`)

La Base de Sky trae **followers** en el core: `Game_Follower < Game_Event` (un evento dependiente que
sigue al líder). API clave: `follow_leader(leader, instant = false, leaderIsTrueLeader = true)`,
movimiento `move_fancy`/`jump_fancy`/`fancy_moveto`, `turn_towards_leader`. La fábrica
`Game_FollowerFactory` los crea/gestiona.
- Para un **Pokémon que sigue al jugador** (overworld pet) se usa este sistema de dependent events; suele
  haber un plugin/ajuste que registra al primer Pokémon del equipo como follower. Si la tarea es "que mi
  Pokémon me siga", revisa primero si ya está montado (extender antes que duplicar) y engancha el sprite
  del Pokémon ([[assets-pipeline]] para los gráficos de overworld).
- Para NPCs que siguen al jugador en una escena, crea el follower con la fábrica y muévelo con
  `follow_leader` o rutas ([[events-and-messages]]).
