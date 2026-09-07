# Reference · Mid-Battle Scripting — Advanced (4.C)

## Atajo de speech
Si lo único que quieres en un trigger es speech, **no** hace falta el hash: pon el string/array
directamente como valor del Trigger Key.
```ruby
"BeforeMove_TRICKROOM_foe" => "¡A poner todo patas arriba!"
"BattlerFainted_CHARIZARD_foe" => ["¡N-no! ¡Charizard!", "¡Esto no ha terminado!"]
```
Solo funciona para speech; cualquier otra cosa necesita el hash de commands.

## Speakers (4.C.1.C)
- **Múltiples speakers en un array de speech**: intercala índices o símbolos antes de cada línea.
  `"speech" => [1, "Ríndete.", 0, "¡Jamás!", 1, "Ja, ja..."]`.
- **Símbolos de speaker** (relativos a quien disparó el trigger): `:Self`, `:Ally`, `:Ally2`,
  `:Opposing`, `:OpposingAlly`, `:OpposingAlly2` (con fallbacks razonables). Úsalos cuando no sepas qué
  índice estará disponible (triggers sin extensión de lado).
- **Speakers ajenos al combate** con `setSpeaker`: un trainer type (`:ROCKETBOSS` desliza ese sprite) o
  un species ID (`:MEWTWO`), o Array `[species, forma, género(0/1), shiny]` (`[:CHERRIM, 1, 1, true]`).
  El género fija también el windowskin por defecto.
- `editSpeaker` cambia el sprite del speaker **actual** (revelar identidad, frames de emoción):
  `"editSpeaker" => :TEAMROCKET_M`. Acepta los mismos argumentos que `setSpeaker`.
- `editWindow` cambia nombre/windowskin de la caja: String (nombre), Array `[nombre_o_nil, "windowskin"]`
  (de `Graphics/Windowskins`; o número: 0 azul/masc, 1 rojo/fem, otro = neutro), `:Hide`/`:Show`.
  Combínalo con `setSpeaker` para nombre+skin custom: `"editWindow" => ["Giovanni", "speech hgss 10"]`.
  Funciona incluso sobre el jugador (simular a Prof. Oak comentando off-screen).

## Choices (4.C.1.B)
1. Monta con `setChoices => [:id, indice_correcto_o_nil, "Op1", "Op2", ...]` (o Hash de opción→reacción).
2. Muéstralas poniendo `:Choices` dentro del array de `speech`/`text`, tras la pregunta.
3. Reacciona con triggers:
   - `"ChoiceRight_<id>"` / `"ChoiceWrong_<id>"` si hay respuesta correcta.
   - `"Choice_<id>_#"` para una opción concreta (# = índice 1-based) cuando no hay correcta (`nil`).
```ruby
"RoundStartCommand_foe" => {
  "setChoices" => [:weather, nil, "¡Sol!", "¡Lluvia!", "¡Nieve!"],
  "speech"     => ["¿Tu clima favorito?", :Choices]
},
"Choice_weather_1" => { "changeWeather" => :Sun },
"Choice_weather_2" => { "changeWeather" => :Rain },
"Choice_weather_3" => { "changeWeather" => :Hail }
```

## Variable midbattle (4.C.2)
Empieza en 0 cada combate, **nunca baja de 0**. Manipula con `setVariable`/`addVariable`/`multVariable`
(commands). **Solo `addVariable` y `multVariable` disparan** los triggers de variable (`setVariable` no).
Triggers: `"Variable_#"` (= exacto), `"VariableUp"`/`"VariableDown"`, `"VariableOver_#"`/`"VariableUnder_#"`.
Útil para contadores/temporizadores y fases de jefe.

## Extensiones de frecuencia (`_repeat`, `_random`, `_every_#`)
Acotan **cada cuánto** dispara un trigger (en el sistema de hash; al hardcodear no aplican — ahí todo se
repite siempre):
- sin extensión → dispara **una sola vez**.
- `_repeat` → cada vez que se cumpla la condición (`"RoundStartCommand_foe_repeat"`).
- `_every_#` → cada N veces (`"RoundEnd_foe_every_4"`).
- `_random` → al azar.
Se combinan con las demás extensiones de la `02-mid-battle-triggers.md`.

## Guardar scripts reutilizables — módulo `MidbattleScripts` (4.C.3)
Crea un archivo `.rb` en los scripts del plugin con:
```ruby
module MidbattleScripts
  MIDBATTLE_EXAMPLE_SCRIPT = {
    "RoundStartCommand_1_foe" => { "speech" => "¡No me ganarás esta vez!" },
    "BattleEndWin"            => { "speech" => "¡No! ¡Imposible!" }
  }
end
```
Y llámalo por símbolo (sin meter el hash entero en el evento):
```ruby
setBattleRule("midbattleScript", :MIDBATTLE_EXAMPLE_SCRIPT)
```
Ejemplos del propio plugin: `[002] Midbattle Scripting/[003] MidbattleScripts Module`.

## Hardcodear — `MidbattleHandlers` (4.C.4)
Para control total (Ruby). Handler con `case trigger`:
```ruby
MidbattleHandlers.add(:midbattle_scripts, :midbattle_example_script,
  proc { |battle, idxBattler, idxTarget, trigger|
    scene = battle.scene
    case trigger
    when "RoundStartCommand_1_foe"
      scene.pbStartSpeech(1)
      battle.pbDisplayPaused(_INTL("¡No me ganarás esta vez!"))
    end
  }
)
setBattleRule("midbattleScript", :midbattle_example_script)
```
Args del proc: `battle` (clase Battle), `idxBattler` (quién disparó), `idxTarget` (objetivo, en
movimientos), `trigger` (la Trigger Key). IDs en minúscula para no confundir con las constantes de
`MidbattleScripts`. Ejemplos: `[002] Midbattle Scripting/[004] MidbattleHandlers Scripts`.

API útil al hardcodear:
- **Battle**: `pbTriggerActivated?(*triggers)` (¿ya disparó alguno?), `pbPauseAndPlayBGM(track)`,
  `pbResumeBattleBGM`.
- **Battle::Battler**: `@damageThreshold` (= `battlerHPCap`), `wild_flee(fleeMsg=nil)`.
- **Battle::Scene**: `pbStartSpeech(speaker)`, `pbGetSpeaker(speaker)`, `pbHideSpeaker`,
  `pbShowSpeaker(speaker)`, `pbUpdateSpeakerSprite(speaker)`, `pbShowSpeakerWindows(name, windowskin)`,
  `pbHideSpeakerWindows(speech)`, `pbInCinematicSpeech?`, `pbForceEndSpeech`. (`speaker` = índice, objeto
  battler, species ID, trainer type ID, o array `[species, forma, género, shiny]`.)

## Scripts globales (4.C.5)
Para que algo ocurra en **todos** los combates (o según un switch/mapa) sin poner la regla. **Solo** se
hacen hardcodeando, con el handler `:midbattle_global` (misma estructura que `:midbattle_scripts`). Se
apilan sobre los scripts normales sin sobrescribirlos. (El propio plugin usa uno para la música de HP
bajo: `:low_hp_music`.)
