# Reference · Mid-Battle Scripting — Command Keys (qué)

El valor de cada Trigger Key es un Hash de **Command Keys** (qué ocurre). Empiezan en **minúscula**. Se
ejecutan **en orden**. El "battler foco" por defecto es quien disparó el trigger; cámbialo con `setBattler`.

**Índices/símbolos de battler** (donde se admiten): `:Self`, `:Ally`, `:Ally2`, `:Opposing`,
`:OpposingAlly`, `:OpposingAlly2`, o un índice entero (0 = primer Pokémon del jugador, 1 = primer rival…).

## 4.B.1 Texto y diálogo
- `"text"` → String o Array. Mensajes de combate. `{1}` = nombre del battler, `{2}` = su entrenador,
  `\\PN` = nombre del jugador. En Array puedes anteponer un índice/símbolo de battler para cambiar el foco
  (`[0, "¡{1} no se rinde!"]`). Incluye `:Choices` para mostrar elecciones (montadas con `setChoices`).
- `"speech"` → String o Array. Como `"text"` pero con **animación cinemática**: barras negras, UI oculta,
  y el sprite del entrenador rival aparece a hablar. En salvajes el speaker rival es el propio Pokémon.
- `"setChoices"` → Array. Define elecciones ramificadas para `text`/`speech`. Formato:
  `[:id, indice_correcto_o_nil, "Op1", "Op2", ...]`. Las opciones pueden ser un Hash para dar respuesta:
  `[:colors, 3, {"Rojo"=>"No...", "Azul"=>"¡Correcto!"}]`. El `:id` lo usan los triggers `Choice_*`.
- `"setSpeaker"` → Integer/Symbol/Array. Cambia quién habla sin reiniciar la animación. Índice o símbolo
  relativo; `:Battler` (que hable el Pokémon aunque tenga dueño); `:Hide` (oculta el sprite); un trainer
  type ID (`:LEADER_Brock` desliza a Brock aunque no esté en el combate); un species ID (sprite del
  Pokémon), o Array `[species, forma, género(0/1), shiny(bool)]`.
- `"editSpeaker"` → como `setSpeaker` pero **sin** deslizar uno nuevo: solo actualiza el gráfico actual
  (cambiar de "frame"/emoción del entrenador, o mini-animaciones).
- `"editWindow"` → String/Array/Symbol. Personaliza nombre y windowskin del speaker. String = nombre
  mostrado; Array `[nombre_o_nil, "windowskin"]` (de `Graphics/Windowskins`, ajusta color del texto);
  `:Hide` (oculta placas/cajas, texto sobre la barra negra); `:Show` (restaura).
- `"endSpeech"` → Boolean. `true` termina la animación cinemática antes de tiempo.

## 4.B.2 Audio y animación
- `"playSE"` → String. Reproduce un SE (de `Audio/SE`).
- `"playCry"` → Integer/Symbol. Grito de un Pokémon: índice/símbolo de battler, o species ID (aunque no
  esté en combate).
- `"changeBGM"` → String o Array `[archivo, fade_seg, volumen_o_nil, tono_o_nil]`.
- `"endBGM"` → Float. Fade-out del BGM en N segundos (`0.0` = inmediato). El combate sigue en silencio.
- `"pauseAndPlayBGM"` → String. Pausa el BGM actual (recuerda su posición) y reproduce otro.
- `"resumeBGM"` → Boolean. Termina el actual y retoma el pausado con `pauseAndPlayBGM`.
- `"playAnim"` → String/Symbol/Array. String = animación común (`"StatUp"`); move ID = animación del
  movimiento; Array `[anim, user_idx, target_idx]` para fijar usuario/objetivo (índices o símbolos).

## 4.B.3 Utilidades
- `"setBattler"` → Integer/Symbol. Cambia el battler foco de los comandos siguientes.
- `"wait"` → Integer/Float. Congela la escena N segundos (`0.5` = medio segundo).
- `"pause"` → Integer/Float. Retrasa las acciones siguientes N segundos.
- `"ignoreUntil"` → String/Array de Trigger Keys. Bloquea los keys siguientes del hash hasta que se
  dispare uno de esos triggers.
- `"ignoreAfter"` → String/Array. Bloquea los keys siguientes **después** de dispararse ese trigger.
- `"toggleSwitch"` → Integer. Conmuta el game switch indicado (on↔off).
- `"setVariable"` → Integer o Array (aleatorio). Fija la variable midbattle.
- `"addVariable"` → Integer o Array. Suma (negativo = resta) a la variable midbattle.
- `"multVariable"` → Integer/Float. Multiplica la variable (float `0.5` = dividir, redondeado).

## 4.B.4 Mecánicas de combate
- `"useItem"` → Symbol o Array. Fuerza a usar un objeto sobre el battler (no necesita tenerlo ni se
  consume). Las Poké Balls las lanza siempre el jugador. Array = lista para elegir uno al azar.
- `"useMove"` → Integer/Symbol/String/Array. Fuerza un movimiento del moveset este turno. Integer =
  índice; Symbol = move ID. Array `[move, target_idx]`. String = categoría general (elige al azar entre
  los que cumplan): `"Damage"` (+`_foe`/`_ally`), `"Heal"` (+`_self`/`_ally`/`_foe`), `"Status"`
  (+`_self`/`_ally`/`_foe`). Falla si ya se movió, eligió otra acción, está bloqueado (Encore/Disable/
  Choice), o su movimiento natural es Z-Move/Dynamax.
- `"switchOut"` → Integer/Symbol/Array. Fuerza cambio. Integer = índice de party; Symbol = species ID;
  Array `[destino, "mensaje {1}"]`. Símbolos: `:Choose` (elige el entrenador/IA), `:Random`, `:Forced`
  (como Random pero con mensaje de forzado tipo Rugido).
- `"megaEvolve"` → Boolean o String (mensaje). Fuerza Mega en cualquier momento (incluso salvaje con su
  Mega Piedra/Mega Move).
- `"disableMegas"` → Boolean. Activa/desactiva Mega para el dueño del battler.
- `"disableBalls"` → Boolean. Activa/desactiva lanzar Poké Balls.
- `"disableItems"` → Boolean. Activa/desactiva objetos para todos los entrenadores.
- `"disableControl"` → Boolean. `true` = la IA controla al jugador; `false` = devuelve el control.
- `"endBattle"` → Integer. Termina el combate: `1` victoria, `2` derrota, `3` huida/rendición, `4` victoria
  (=1), `5` empate (=2 en general).
- `"wildFlee"` → Boolean o String (mensaje, `{1}` = salvaje). Solo en salvajes: lo hace huir, terminando.

## 4.B.5 Atributos del battler
- `"battlerName"` → String. Cambia el nombre del battler.
- `"battlerHP"` → Integer o Array. % de PS máx: positivo cura, negativo daña, `0` lo deja en 1 PS. Array
  `[valor, "mensaje {1}"]`.
- `"battlerHPCap"` → Integer. Tope de daño por ataques directos: a `25` no baja de 25% PS; `100` = inmune
  a daño de ataque; `0` = sobrevive con 1 PS. (No afecta daño indirecto.) Clave para jefes por fases.
- `"battlerStatus"` → Symbol o Array. Estado: `:BURN`, `:TOXIC`, `:CONFUSION`…; `:NONE` cura (incluye
  confusión/atracción). Array de estados = aleatorio. Array `[estado(s), true]` muestra mensajes de
  éxito/fallo.
- `"battlerType"` → Symbol o Array. Cambia tipo(s): `:WATER` o `[:FIRE, :DRAGON]`; `:Reset` restaura.
  Falla en Multitype/RKS System o si está Terastalizado.
- `"battlerForm"` → Integer/Symbol/Array. Forma: número, `:Random`, `:Cycle`. No incluye Mega/Primal.
  Array `[forma, "mensaje" o true]`. Falla bajo Mega/Ultra Burst/Dynamax/Tera o semi-invulnerable.
- `"battlerSpecies"` → Symbol o Array. Cambia especie (¡permanente en los del jugador!). Array
  `[especie, "mensaje"]` (`{1}` battler, `{2}` nueva especie, `{3}` dueño).
- `"battlerEvolve"` → Symbol o Array. Evoluciona forzado sin requisitos: `:Next`, `:Random`, o species ID
  (cualquiera, incluso imposible). Array `[especie, forma]` para una forma concreta (`[:RAICHU, 1]`).
- `"battlerAbility"` → Symbol o Array. Cambia habilidad; `:Reset` restaura. Array de IDs = aleatorio;
  `[id(s), true o "mensaje"]` para avisar.
- `"battlerItem"` → Symbol o Array. Cambia objeto; `:Remove` quita. Array de IDs = aleatorio;
  `[id(s), true o "mensaje"]`.
- `"battlerMoves"` → Symbol o Array. move ID lo añade (desplaza el primero si está lleno); `:Reset`
  restaura los de su nivel. Array de IDs reemplaza por índice; `nil` en un índice borra ese movimiento.
- `"battlerStats"` → Array o Symbol. `[:ATTACK, 2, :DEFENSE, -3]` (pares stat/etapas). `:Random` como
  stat. `:Reset`/`:ResetRaised`/`:ResetLowered`.
- `"battlerEffects"` → Array `[:Efecto, valor, "mensaje opcional"]`, o array de arrays para varios.
  Ej. `[[:Rage, true], [:PerishSong, 3, "¡Caerá en 3 turnos!"]]`.
- `"battlerWish"` → Boolean/Integer/Array. Pone Deseo en la **posición** (no en el battler): `true` (2
  turnos, cura 50% PS del que lo puso); Integer = turnos; Array `[turnos, PS_curados]`.

## 4.B.6 Condiciones del campo
- `"teamEffects"` → Array `[:Efecto, valor, "mensaje"]` (o array de arrays). Un **lado**: `:StickyWeb`,
  `:Spikes`, `:Tailwind`, `:Reflect`…
- `"fieldEffects"` → Array igual, todo el **campo**: `:Gravity`, `:TrickRoom`, `:MagicRoom`, `:PayDay`…
- `"changeWeather"` → Symbol. Weather ID, `:Random`, `:None`. (Climas Primal acaban si no hay la habilidad.)
- `"changeTerrain"` → Symbol. Terrain ID, `:Random`, `:None`.
- `"changeEnvironment"` → Symbol. Environment ID, `:Random`, `:None`.
- `"changeBackdrop"` → String o Array `["fondo", "bases"]` (raíz del nombre, sin `_bg`/`_base`). Cambiar
  bases puede cambiar el environment.
- `"changeDataboxes"` → Symbol o Array. Estilo de databox (`:Basic`/`:Long`); `nil` restaura. En salvajes,
  Array `[estilo, "título1", ...]` para títulos por fase de jefe.

## 4.B.7 Extensiones de Command Keys (claves duplicadas)
Un Hash no admite dos claves iguales. Para repetir un Command Key, añade `_<lo que sea>` para hacerlo
único: `"speech_A"`, `"speech_B"`, `"setBattler_A"`, `"battlerStats_A"`… Se ejecutan **en orden de
escritura**. Convención: letras en orden alfabético.

```ruby
"UserDealtCriticalHit_player" => {
  "speech_A"       => [:Opposing, "¡Qué suerte...!"],
  "setBattler_A"   => 1,
  "battlerStats_A" => [:ATTACK, 1, :SPEED, 1],
  "speech_B"       => "¡Mi {1} no se rendirá!"
}
```

Speech avanzado (speakers múltiples, opciones complejas), variables, guardar scripts reutilizables,
hardcodear y scripts globales → `04-advanced-scripting.md`.
