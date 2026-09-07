---
name: battles-runtime
description: >
  Disparar y controlar combates desde código en Pokémon Essentials 21.1 / La Base de Sky: WildBattle.start
  y TrainerBattle.start (todas las combinaciones de argumentos), construir entrenadores DINÁMICAMENTE sin
  PBS (NPCTrainer.new, GameData::Trainer.to_trainer, battle_self), uso EXHAUSTIVO de setBattleRule (las 28
  reglas), reglas de torneo/clauses, Battle.new directo, resultados (Battle::Outcome), partners, generar
  salvajes (pbGenerateWildPokemon), capturas/Poké Balls y retos del Frontier. Extraído 1:1 de 039_Battle
  triggering, 046_Trainers and player y 034_Other battle code. Para los datos PBS ver [[pbs-data]]; para
  editar los Pokémon ver [[pokemon-api]]. Triggers: "inicia un combate", "TrainerBattle", "WildBattle",
  "setBattleRule", "combate doble", "entrenador dinámico", "regla de batalla", "clause", "combate contra
  tu propio equipo".
---

# Combates en runtime

Fuentes: `039_Battle triggering/002_Overworld_BattleStarting.rb` (clave), `046_Trainers and player/002_Trainer_Class.rb` + `003_Trainer_LoadAndNew.rb`, `034_Other battle code/007_Battle_Clauses.rb` + `011_Battle_PokeBallEffects.rb`. Verificar firmas reales antes de usar — ver [[essentials-codemap]].

**Flujo general:** (1) opcionalmente `setBattleRule(...)` una o varias veces, (2) llamar `WildBattle.start(...)` o `TrainerBattle.start(...)`. Las reglas se aplican **solo al siguiente combate** y se limpian después.

## Combate salvaje — `WildBattle.start`

```ruby
WildBattle.start(*args, can_override: false)
```
`args` puede mezclar:
- **especie, nivel** (secuencia): `WildBattle.start(:PIKACHU, 15)`.
- **varios** para doble/triple: `WildBattle.start(:ZUBAT, 8, :ZUBAT, 8)`.
- **Array `[especie, nivel]`**: se genera con `pbGenerateWildPokemon` (item salvaje, shiny boosts, etc.).
- **objeto `Pokemon`** ya creado: `WildBattle.start(mi_pkmn)` (control total del Pokémon salvaje).

`can_override: true` permite que `:on_calling_wild_battle` redirija (roamers, Safari, Concurso). Devuelve `true` salvo si el jugador pierde/empata.

```ruby
pkmn = Pokemon.new(:GYARADOS, 30)
pkmn.shiny = true
pkmn.item = :SITRUSBERRY
setBattleRule("cannotrun")
WildBattle.start(pkmn)
```
Internamente: `WildBattle.start_core` → `Battle.new(scene, player_party, foe_party, player_trainers, nil)`.

## Combate de entrenador — `TrainerBattle.start`

```ruby
TrainerBattle.start(*args)
```
`args` describe uno o más entrenadores, mezclando:
- **tipo, nombre** → carga de PBS: `TrainerBattle.start(:CAMPER, "Dave")`.
- **tipo, nombre, versión**: `TrainerBattle.start(:HIKER, "Ford", 42)`.
- **varios entrenadores** (combate doble por bando): `TrainerBattle.start(:CAMPER, "Dave", :HIKER, "Ford", 42)`.
- **objeto `NPCTrainer`** (entrenador dinámico, ver abajo): `TrainerBattle.start(trainer)`.
- **`[tipo, nombre, versión, frase]`** (frase de derrota opcional sobreescribe `LoseText`).

Devuelve `true` si el jugador gana. Internamente: `Battle.new(scene, player_party, foe_party, player_trainers, foe_trainers)`.

`TrainerBattle.start` también gestiona el caso de dos eventos-entrenador que ven al jugador a la vez (los empareja en doble). Por eso en eventos se envuelve en `pbTrainerIntro(:TIPO)` … `pbTrainerEnd`.

## Construir un entrenador DINÁMICAMENTE (sin PBS)

### Desde cero con `NPCTrainer`
```ruby
trainer = NPCTrainer.new("Dave", :CAMPER, version = 0)
trainer.party = [Pokemon.new(:MACHOP, 15), Pokemon.new(:GEODUDE, 14)]
trainer.items = [:POTION, :POTION]        # items que usa en combate
trainer.lose_text = "¡Has ganado!"
trainer.id = $player.make_foreign_ID      # opcional
TrainerBattle.start(trainer)
```
`NPCTrainer < Trainer`. Atributos: `name`, `trainer_type`, `id`, `language`, `party` (Array de Pokemon), `version`, `items`, `lose_text`, `win_text`.

### Cargar uno del PBS y modificarlo
```ruby
trainer = pbLoadTrainer(:HIKER, "Ford", 0)        # = GameData::Trainer...to_trainer
# o:
trainer = GameData::Trainer.get(:HIKER, "Ford", 42).to_trainer
trainer.items.push(:FULLRESTORE)
trainer.party.first.shiny = true
TrainerBattle.start(trainer)
```
`to_trainer` instancia un `NPCTrainer` con su party ya construida (aplica forma, Mega, naturaleza, IV/EV, apodo, Shadow, Poké Ball del tipo…). Ver [[pbs-data]] → trainers-encounters.md.

### Combatir contra tu propio equipo (helper Sky `battle_self`)
```ruby
$player.battle_self("Rival", :POKEMONTRAINER_Red)
```
Clona el equipo del jugador (`Marshal`), lo asigna a un `NPCTrainer` y lanza `TrainerBattle.start`. Buen patrón para "espejos".

### Crear el party "a mano" con `Pokemon.new`
Cada Pokémon se construye y edita con la API de [[pokemon-api]] (IVs, naturaleza, moves, item, forma, shiny…). El `owner` puede dejarse en el trainer: `Pokemon.new(:species, level, trainer, false)`.

## `setBattleRule` — uso exhaustivo

```ruby
setBattleRule("double")                 # regla sin valor
setBattleRule("weather", "Rain")        # regla con valor (el valor va DESPUÉS)
setBattleRule("environ", "Cave", "outcome", 25)   # se pueden encadenar
```
Reglas que **requieren un valor a continuación**: `terrain`, `weather`, `environment`/`environ`, `backdrop`/`battleback`, `base`, `outcome`/`outcomevar`. Las demás son flags sueltos. Llamar varias veces acumula reglas. Alternativa de bajo nivel: `$game_temp.add_battle_rule(regla, valor=nil)`.

**Las 28 reglas (hash `BATTLE_RULES`)** — lista completa, valores y efecto en `reference/setbattlerule-and-clauses.md`. Resumen por categoría:
- Tamaño: `single` `1v1` `1v2` `2v1` `1v3` `3v1` `double` `2v2` `2v3` `3v2` `triple` `3v3`.
- Visual: `backdrop`/`battleback` (valor), `base` (valor), `anims`/`noanims`.
- Entorno/clima/terreno: `environment`/`environ` (valor), `weather` (valor), `terrain` (valor).
- Acciones/partner: `canrun`/`cannotrun`, `canswitch`/`cannotswitch`, `switchstyle`/`setstyle`, `disablebag`, `disablepokeballs`, `autobattle`, `nopartner`.
- Roamers: `roamerflees`.
- Captura: `certaincapture`, `forcecatchintoparty`.
- Resultado: `canlose`/`cannotlose`, `outcome`/`outcomevar` (valor = nº de Game Variable).
- Recompensas: `noexp`, `nomoney`.

## Resultado del combate — `Battle::Outcome`
`UNDECIDED = 0`, `WIN = 1`, `LOSE = 2`, `FLEE = 3`, `CATCH = 4`, `DRAW = 5`. Se guarda en la Game Variable del `outcome` (1 por defecto). `Battle::Outcome.should_black_out?(outcome)` indica si el jugador "cae derrotado".

`WildBattle.start` → true salvo derrota/empate. `TrainerBattle.start` → true solo si `WIN`.

## Combates dobles / triples y partners
- Comprobar antes: `pbCanDoubleBattle?` / `pbCanTripleBattle?` (2+ / 3+ Pokémon útiles, o partner).
- Partner: `$PokemonGlobal.partner = [tipo, nombre, id, party]`. Si hay partner y el combate lo permite, se auto-pone `double`. Regla `nopartner` lo excluye. Ver `reference/setbattlerule-and-clauses.md` y skill [[pbs-data]] (Partner trainer).
- En eventos se valida con `!pbCanDoubleBattle?` → mensaje + Exit Event Processing.

## `Battle.new` directo (control total)
```ruby
scene = Battle::Scene.new
battle = Battle.new(scene, player_party, foe_party, player_trainers, foe_trainers)
battle.party1starts = [...]; battle.party2starts = [...]
battle.items = foe_items; battle.ally_items = ally_items
# prepare_battle aplica setBattleRule; setBattleMode, defaultWeather, defaultTerrain, environment, backdrop...
outcome = battle.pbStartBattle
```
Normalmente NO hace falta: usa `WildBattle.start`/`TrainerBattle.start`, que ya montan la escena, BGM, transición y limpieza. Propiedades útiles del `battle`: `rules` (Hash), `environment`, `defaultWeather`, `defaultTerrain`, `backdrop`, `controlPlayer` (auto-battle), `sendToBoxes`, `turnCount`, `time` (0 día/1 tarde/2 noche-cueva), `adjust_levels`/`adjust_levels_reset_moves`.

## Reglas de torneo (clauses) y capturas
Las "clauses" (Sleep Clause, Species Clause, OHKO Clause, etc.) y los modificadores de Poké Ball se aplican vía `battle.rules["..."] = true` (no por `setBattleRule`, que tiene su whitelist). Lista completa en `reference/setbattlerule-and-clauses.md`.

## Generar Pokémon salvajes y encuentros
- `pbGenerateWildPokemon(species, level, isRoamer=false)` → un `Pokemon` con item salvaje, boost shiny (Amuleto Iris, nº batallados), Pokérus, género/naturaleza según habilidad líder. 
- `pbEncounter(enc_type, only_single=true)` → elige de la tabla del mapa y lanza el combate.
- `$PokemonEncounters.choose_wild_pokemon(enc_type, chance_rolls=1)` → `[especie, nivel]`.
- Tipos de encuentro y tablas: [[pbs-data]] → trainers-encounters.md + enums-and-evolution.md.
- Roamers: `039_Battle triggering/006_Overworld_RoamingPokemon.rb` (`$PokemonGlobal.roamPokemon`). Combate vía `:on_calling_wild_battle`.

## Retos del Battle Frontier
Sets de Pokémon/entrenadores en `battle_facility_lists.txt` + archivos asociados ([[pbs-data]] → metadata-misc.md). Se lanzan con la maquinaria del Frontier (`pbBattleChallenge` / `BattleChallenge`), que arma entrenadores aleatorios de esas listas y aplica reglas del reto (singles/doubles, nivel, clauses). El reto define `Challenges = ...` y el código del facility selecciona el set.
