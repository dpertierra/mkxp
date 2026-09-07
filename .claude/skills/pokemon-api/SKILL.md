---
name: pokemon-api
description: >
  Manipulación de objetos Pokémon en RUNTIME (no PBS): crear con Pokemon.new, leer/escribir cada
  atributo (especie, forma, nivel, exp, IVs/EVs, naturaleza, habilidad, item, movimientos, género,
  shiny/super-shiny, felicidad, pokerus, ribbons, owner), curar, evolucionar, cambiar de forma,
  Mega/Primal, Pokémon Oscuros (Shadow), añadir al equipo/PC, y registrar formas con MultipleForms.
  Extraído 1:1 de 043_Pokemon y 044_Pokemon-related. Para los DATOS en texto plano ver [[pbs-data]];
  para crear/usar Pokémon en combates ver [[battles-runtime]]. Triggers: "crea un Pokémon", "edita el
  Pokémon", "dale al jugador un Pokémon", "cambia la forma/IVs/naturaleza", "Pokemon.new", "makeShadow",
  "evoluciona", "MultipleForms".
---

# Pokémon en runtime — la clase `Pokemon`

Clase: `043_Pokemon/002_Pokemon.rb` (+ `003_MegaEvolution`, `004_ShadowPokemon`, `005_Move`, `006_Owner`) y `044_Pokemon-related/` (FormHandlers, FormChanger, Storage, Trade). Las instancias son Pokémon individuales; el equipo del jugador es `$player.party` (Array). Verificar firmas reales antes de usar — ver skill [[essentials-codemap]].

## Crear un Pokémon

```ruby
Pokemon.new(species, level, owner = $player, withMoves = true, recheck_form = true)
```
- `species`: símbolo/String/GameData::Species (`:MAGIKARP`).
- `level`: 1..`GameData::GrowthRate.max_level`.
- `owner`: `$player`, un `NPCTrainer`, un `Pokemon::Owner`, u otro → se genera Owner. Sin owner válido → Owner anónimo `(0,"",2,2)`.
- `withMoves`: si calcula los movimientos por nivel (`reset_moves`).
- `recheck_form`: si llama a `MultipleForms "getFormOnCreation"`.

El constructor: fija nivel/exp, cura estado, IVs aleatorios (0–31), EVs a 0, naturaleza/habilidad/género/shiny sin fijar (se calculan al vuelo desde el `personalID`), `poke_ball = :POKEBALL`, `obtain_method`/`obtain_map`/`obtain_level` según contexto, `personalID` aleatorio de 32 bits, y `calc_stats`.

### Constantes de la clase
`IV_STAT_LIMIT = 31`, `EV_LIMIT = 510` (total), `EV_STAT_LIMIT = 252` (por stat), `MAX_NAME_SIZE = 11` (**Sky lo subió de 10**), `MAX_MOVES = 4`.

## Dárselo al jugador (helpers de evento)

| Helper | Qué hace |
|---|---|
| `pbAddPokemon(species, level)` o `pbAddPokemon(pkmn)` | Lo añade al equipo o, si está lleno, al PC. Devuelve true/false. Lo trata como propio del jugador. |
| `pbAddToParty(species, level)` / `pbAddToParty(pkmn)` | Solo al equipo (falla si está lleno). |
| `pbAddPokemonSilent` / `pbAddToPartySilent` | Igual pero sin mensajes. |
| `pbAddForeignPokemon(species, level, ot_name, nickname=nil, ot_gender=0)` | Pokémon **regalo** (OT distinto). Solo al equipo. Acepta también un `pkmn` ya creado. |
| `pbGenerateEgg(species, text="")` | Crea un huevo. |

Todos devuelven true/false → úsalos en una Conditional Branch. Tras añadir, el último está en `$player.last_party`.

```ruby
pkmn = Pokemon.new(:MAGIKARP, 10)
pkmn.shiny = true
pbAddPokemon(pkmn)
```

Pokémon "de alquiler" (no cuenta para la Pokédex): `pkmn.owner.id = $player.make_foreign_ID`.

## Lectura/escritura de atributos

Tras **cualquier** edición que afecte stats, llamar `pkmn.calc_stats`. Setters que ya lo hacen solos: `species=`, `form=`, `nature=`, `nature_for_stats=`, `level=`.

### Especie y forma
- `species` / `species=(:NEWSPECIES)` — cambia especie y recalcula (resetea forma/habilidad/contadores).
- `isSpecies?(:X, ...)` — true si es alguna de esas especies.
- `form` — forma efectiva (consulta MultipleForms salvo en combate/PC). `form=(n)` la fija (recalcula, registra en Pokédex, llama `onSetForm`).
- `form_simple` / `form_simple=` — forma "cruda" sin recálculo de MultipleForms.
- `forced_form` — si se define, ignora a MultipleForms.
- `setForm(n) { ... }` — como `form=` pero con bloque intermedio (para mensajes antes de `onSetForm`).
- `species_data` — el `GameData::Species` de su especie+forma.

### Nivel / experiencia
- `level` / `level=(n)` (1..max) · `exp` / `exp=(n)` · `exp_fraction` (0.0–1.0 del nivel actual) · `growth_rate` · `base_exp`.
- `egg?` (true si `steps_to_hatch > 0`) · `steps_to_hatch`.

### PS / estado
- `hp` / `hp=(n)` (clamp 0..totalhp; al llegar a 0 cura estado y resetea contadores de evo) · `totalhp`.
- `status` (`:NONE/:SLEEP/:POISON/:BURN/:PARALYSIS/:FROZEN|:FROSTBITE`) · `status=(:X)` · `statusCount` (turnos sueño / flag toxic).
- `give_status(:X)` (respeta inmunidades) · `can_get_status?(:X)`.
- `able?` (vivo y no huevo) · `fainted?`.
- `heal` (PS+PP+estado) · `heal_HP` · `heal_status` · `heal_PP(idx=-1)`.

### Stats / IVs / EVs
- `stat(:ATTACK)` o `attack/defense/spatk/spdef/speed/totalhp`.
- `iv` (Hash por stat) · `ivMaxed` (Hash bool, Hyper Training) · `ev` (Hash). Edición directa: `pkmn.iv[:ATTACK] = 31` → luego `calc_stats`.
- `calcIV` (efectivos con Hyper Training) · `fill_ivs` · `add_evs(hash)` (respeta límites 252/stat y 510 total).
- `baseStats` · `base_stat_total` (vía species_data) · `evYield`.

### Naturaleza / habilidad / item
- `nature` / `nature_id` / `nature=(:ADAMANT)` · `nature_for_stats` / `nature_for_stats=` (mints Gen 8) · `hasNature?`.
- `ability` (GameData::Ability) · `ability_id` · `ability=(:X)` · `ability_index` / `ability_index=(n)` (0/1 natural, 2+ oculta) · `hasAbility?(:X)` · `hasHiddenAbility?` · `getAbilityList`.
- `item` (GameData::Item) · `item_id` · `item=(:X)` · `hasItem?(:X)` · `wildHoldItems` · `mail` / `mail=`.

### Movimientos (`pkmn.moves` = Array de `Pokemon::Move`)
- `learn_move(:X)` (silencioso; descarta el 1º si supera 4) · `forget_move(:X)` · `forget_move_at_index(i)` · `forget_all_moves` · `reset_moves` (moveset por nivel).
- `hasMove?(:X)` · `numMoves` · `find_move(:X)` · `getMoveList` · `compatible_with_move?(:X)` · `can_relearn_move?`.
- `first_moves` (para el recordador) · `record_first_moves` · `add_first_move` / `remove_first_move`.
- Un `Pokemon::Move` tiene `id`, `pp`, `ppup` (cada PP Up +20%), `total_pp`, y delega `power/type/category/accuracy/...` a `GameData::Move`.

### Género / shiny
- `gender` (0 macho / 1 hembra / 2 sin género) · `gender=(0|1)` · `makeMale` / `makeFemale` · `male?/female?/genderless?` · `singleGendered?` · `changeGender(recheck_form=true)`.
- `shiny?` · `shiny=(bool)` · `super_shiny?` · `super_shiny=(bool)` · `super_shiny_hue` (**Sky**, usa `SuperShinyHue`) · `no_shinyness`.

### Otros
- `name` / `name=(str)` · `nicknamed?` · `can_change_nickname?` · `speciesName`.
- `happiness` · `affection_level` (0–5) · `changeHappiness("walking"|"levelup"|"groom"|"massage1..3"|"evberry"|"vitamin"|"wing"|"machine"|"battleitem"|"faint"|"faintbad"|"powder"|"energyroot"|"revivalherb")`.
- `pokerus` · `pokerusStrain`/`pokerusStage` (0 no / 1 infectado / 2 curado) · `givePokerus(strain=0)` · `curePokerus`.
- `ribbons` (Array) · `giveRibbon(:X)` · `hasRibbon?` · `takeRibbon` · `upgradeRibbon(*ids)` · `clearAllRibbons` · `numRibbons`.
- `markings` · `poke_ball` · `cool/beauty/cute/smart/tough/sheen` (concurso).
- `owner` (`Pokemon::Owner`) · `foreign?(trainer=$player)` · `obtain_method` (0 met, 1 huevo, 2 trade, 4 fateful) · `obtain_map` · `obtain_text` · `obtain_level` · `hatched_map` · `timeReceived` · `personalID`.
- `cannot_store` / `cannot_release` / `cannot_trade` (flags de bloqueo).
- `clone` (copia profunda de iv/ev/moves/owner/ribbons).

## Owner (`Pokemon::Owner`)
```ruby
Pokemon::Owner.new(id, name, gender, language)
Pokemon::Owner.new_from_trainer(trainer)   # copia datos de un Player/NPCTrainer
Pokemon::Owner.new_foreign(name="", gender=2, language=2)   # ID ajeno al jugador
```
Atributos: `id`, `name`, `gender`, `language`, `public_id` (`id & 0xFFFF`).

## Evoluciones desde código
- `check_evolution_on_level_up` / `_on_battle_level_up` / `_on_use_item(item)` / `_on_trade(other)` / `_after_battle(idx)` / `_by_event(value)` → devuelven la especie destino (símbolo) o nil.
- Forzar la escena: `evo = PokemonEvolutionScene.new; evo.pbStartScreen(pkmn, new_species); evo.pbEvolution; evo.pbEndScreen`.
- Disparar evoluciones por evento en todo el equipo: `pbEvolutionEvent(numero)`.
- Contadores custom Sky: `evolution_counter`, `crit_counter`, `evo_move_count(move)`/`move_count_evolution(move,qty)`, `evo_crest_count(item)`/`leaders_crest_evolution(item,qty)`, `evo_recoil_count`/`recoil_evolution(qty)`, `evo_step_count`/`walking_evolution(qty)`. Métodos y parámetros en [[pbs-data]] → enums-and-evolution.md.

## Mega / Primal / Shadow / Formas / Almacenamiento
Ver `reference/forms-mega-shadow-storage.md`.

## Editar un Pokémon ya elegido (patrón de evento)
```ruby
pbChoosePokemon(1, 3)            # var1=índice (0–5 o -1), var2=nombre
next if $game_variables[1] < 0
pkmn = pbGetPokemon(1)
pkmn.shiny = true
pkmn.calc_stats
```
Variantes: `pbChooseNonEggPokemon`, `pbChooseAblePokemon`, `pbChoosePokemonForTrade(v1,v2,species)`, `pbChooseTradablePokemon(v1,v2,proc)`.
No se pueden cambiar directamente: tipos, stats calculados, altura, peso, EV yield (dependen de la especie).
