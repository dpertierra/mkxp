---
name: deluxe-battle-kit
description: >
  Usar el plugin Deluxe Battle Kit (DBK) y sus 10 add-ons en Pokémon Essentials 21.1 / La Base de Sky:
  Battle Rules nuevas (setBattleRule de DBK), mid-battle scripting COMPLETO (todos los Trigger Keys y
  Command Keys del hash "midbattleScript": speech/elecciones/speakers, audio/animación, mecánicas,
  atributos del battler, campo, variables), Wild Boss Attributes (:hp_level, :immunities), animaciones
  (Mega/Primal/Shadow), y los add-ons: Z-Power (Z-Moves/Ultra Burst), Dynamax/Gigantamax,
  Terastallization, SOS Battles, Raid Battles, Wonder Launcher, Improved Item AI, Enhanced Battle UI,
  Animated Pokemon System, Animated Trainer Intros. Cubre triggers/commands/reglas/PBS para ejecutar al
  100% cualquier escena de combate pedida. Para disparar combates base ver [[battles-runtime]]; para
  efectos custom de movimientos/habilidades ver [[battle-effects]]; para datos PBS ver [[pbs-data]].
  Triggers: "Deluxe Battle Kit", "DBK", "midbattleScript", "mid-battle", "combate con diálogo", "jefe",
  "boss", "raid", "SOS", "totem", "Z-Move", "Dynamax", "Gigantamax", "Terastal", "Wonder Launcher",
  "speech en combate", "el rival dice", "fase de jefe", "databoxStyle", "editWildPokemon".
---

# Deluxe Battle Kit (DBK)

DBK expande el sistema de combate de Essentials para dar control total sobre cómo se diseñan las batallas:
diálogo de entrenador en combate, cambiar música/fondo, animaciones, editar battlers/campo, jefes con
fases, raids, etc. Fuente: el GitBook en `Datos para skill/DBK`. Esta skill documenta **todos** los
triggers, commands, reglas y datos para acatar al 100% lo que se pida.

> **Relación con otras skills:** [[battles-runtime]] dispara el combate base (`WildBattle.start`/
> `TrainerBattle.start`) y trae el `setBattleRule` del **motor**; DBK **añade** reglas y el sistema
> midbattle encima. [[battle-effects]] escribe efectos custom de movimientos/habilidades/objetos.
> [[pbs-data]] define los datos PBS. [[pokemon-api]] construye los Pokémon.

## Los dos pilares de DBK

1. **Deluxe Battle Rules** — reglas nuevas para `setBattleRule` (modos, visuales, audio, editar jugador/
   salvaje, captura, Mega). Ver `reference/01-battle-rules.md`.
2. **Mid-Battle Scripting** — la regla especial `setBattleRule("midbattleScript", Hash)`. El `Hash`
   empareja **Trigger Keys** (cuándo ocurre algo) con **Command Keys** (qué ocurre). Es el corazón del
   plugin y lo más potente.

```ruby
setBattleRule("battleBGM", "Boss Theme")
setBattleRule("databoxStyle", :Long)
setBattleRule("midbattleScript", {
  "AfterSendOut_foe"   => "¡No tienes nada que hacer contra mí!",
  "TargetHPLow_foe"    => { "useItem" => :FULLRESTORE, "speech" => "¡Aún no!" },
  "BattleEndWin"       => { "speech" => "...has ganado. Por esta vez." }
})
TrainerBattle.start(:LEADER_Brock, "Brock")
```

## Cómo construir un midbattle script (procedimiento)

1. **Traducir la petición a momentos**: ¿cuándo debe pasar? → elige el/los **Trigger Key**
   (`02-mid-battle-triggers.md`). Acota con extensiones: `_player`/`_foe`, species/type/move/status/stat
   ID, número de turno, y frecuencia (`_repeat`/`_every_#`/`_random`).
2. **Traducir a acciones**: ¿qué debe pasar? → rellena el hash con **Command Keys**
   (`03-mid-battle-commands.md`). Para repetir un command, usa el sufijo `_A`/`_B` (orden de ejecución).
3. **Speech/elecciones/speakers** complejos → `04-advanced-scripting.md` (atajo de speech, símbolos
   `:Self`/`:Opposing`, `setChoices` + `Choice*`, variable midbattle, módulo `MidbattleScripts`,
   hardcodear con `MidbattleHandlers`, scripts globales).
4. **Jefes**: combina `editWildPokemon` (`:hp_level`/`:immunities` → `05-animations-boss-misc.md`),
   `battlerHPCap`/`battlerHP`, fases por `RoundEnd`/`TargetHPLow`/`BattlerReachedHPCap`. Patrones reales
   en `05` (sección Example Battles).
5. **Mecánica de add-on** (Z/Dynamax/Tera/Raid/SOS/Launcher): añade sus reglas y sus triggers/commands
   propios al mismo hash (`06`/`07`). Verifica que el add-on esté instalado.
6. **Texto siempre con la sintaxis DBK** (`{1}` battler, `{2}` su entrenador, `\\PN` jugador). Recompilar
   tras instalar cualquier add-on que añada PBS (`CTRL` al cargar; `SHIFT` reaplica datos).

## Reference

| Archivo | Cubre |
|---|---|
| `reference/01-battle-rules.md` | Deluxe Battle Rules: modos, visuales (databoxStyle/slide), audio, tempPlayer/Party/Bag, Mega, captura (raidStyleCapture), editWildPokemon |
| `reference/02-mid-battle-triggers.md` | **Todos** los Trigger Keys (14 grupos) + extensiones |
| `reference/03-mid-battle-commands.md` | **Todos** los Command Keys (texto, audio, utilidades, mecánicas, atributos, campo) + extensiones |
| `reference/04-advanced-scripting.md` | Speech avanzado, choices, variable midbattle, MidbattleScripts, MidbattleHandlers (hardcode), scripts globales |
| `reference/05-animations-boss-misc.md` | Animaciones, Wild Boss Attributes (:hp_level/:immunities), Misc (select_start, $stats, flags), Example Battles |
| `reference/06-addons-z-dynamax-tera.md` | Z-Power (Z-Moves/Ultra Burst), Dynamax/Gigantamax, Terastallization: reglas + mid-battle + PBS |
| `reference/07-addons-raid-sos-launcher.md` | Raid Battles (`RaidBattle.start`), SOS/Totem, Wonder Launcher: reglas + mid-battle + PBS |
| `reference/08-addons-ui-ai-animated.md` | Enhanced Battle UI, Improved Item AI (handlers), Animated Pokemon System, Animated Trainer Intros |

> **Convención de nombres clave:** Trigger Keys empiezan en MAYÚSCULA, Command Keys en minúscula. Un Hash
> no admite dos claves iguales → usa `_A`/`_B` para repetir un command. Antes de usar una mecánica de
> add-on, confirma que ese add-on está instalado en el proyecto.
