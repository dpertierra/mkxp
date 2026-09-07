# Formas (MultipleForms), Mega/Primal, Shadow y Almacenamiento

Fuentes: `043_Pokemon/003_Pokemon_MegaEvolution.rb`, `004_Pokemon_ShadowPokemon.rb`, `044_Pokemon-related/002_FormHandlers.rb`, `006_FormChanger.rb`, `005_PokemonStorage.rb`.

---

## MultipleForms — registrar comportamiento de formas

Las formas alternativas se **definen** en `pokemon_forms.txt` (ver [[pbs-data]] → species.md). Su **comportamiento dinámico** se registra con `MultipleForms`:

```ruby
MultipleForms.register(:SPECIES, {
  "getForm" => proc { |pkmn| ... },         # devuelve nº de forma o nil
  "onSetForm" => proc { |pkmn, form, oldForm| ... }
})
MultipleForms.registerIf(:SPECIES, cond, hash)   # solo si cond
MultipleForms.copy(:FROM, :TO1, :TO2)            # copia handlers a otras especies
```

API de consulta:
- `MultipleForms.call("getForm", pkmn, *args)` → ejecuta el handler (o nil).
- `MultipleForms.hasFunction?(pkmn_o_especie, "func")` · `MultipleForms.getFunction(pkmn_o_especie, "func")`.

### Handlers (funciones) reconocidos
| Función | Cuándo se llama / qué devuelve |
|---|---|
| `getForm` | Cada vez que se consulta `pkmn.form` (fuera de combate/PC). Devuelve la forma actual calculada. |
| `getFormOnCreation` | Al crear el Pokémon (`Pokemon.new` con `recheck_form`). |
| `getFormOnGenderChange` | Tras `changeGender`. |
| `getFormOnEnteringBattle` / `getFormOnLeavingBattle` (`pkmn, battle, usedInBattle, endBattle`) | Entrar/salir de combate (Burmy, Castform…). |
| `onSetForm` (`pkmn, form, oldForm`) | Tras fijar la forma (p.ej. Rotom cambia su movimiento). |
| `getPrimalForm` / `getUnprimalForm` | Reversión Primal (Groudon/Kyogre con orbe). |
| `alterBitmap` (`pkmn, bitmap`) | Modifica el sprite al vuelo (Spinda manchas). |

Ejemplos reales: Unown (`getFormOnCreation` → rand 28), Spinda (`alterBitmap`), Groudon (`getPrimalForm` con `:REDORB`), Burmy/Wormadam (forma según entorno), Rotom (`onSetForm` cambia el move asociado).

### FormChanger (custom Sky) — cambiar a forma regional desde evento
`change_pokemon_form(force_species=nil, message_var=nil, show_messages=true)` abre la selección de party y deja elegir entre formas regionales registradas. `GameData::Species#regional_forms` / `#has_alternative_form?` listan las formas disponibles. Constantes de control: `REGIONAL_SPECIES`, `CURRENT_SPECIES_BLACKLIST`, `FORMS_BLACKLIST`, `SHOW_SPRITES_IN_FORM_CHANGER`.

---

## Mega Evolución / Primal (tratadas como cambios de forma)

Datos en `pokemon_forms.txt` (`MegaStone`/`MegaMove`/`UnmegaForm`/`MegaMessage`). Métodos del Pokémon:

| Método | Qué hace |
|---|---|
| `getMegaForm` | Nº de la Mega forma accesible (lleva la piedra/move) o 0. Respeta flag `cantMegaEvolve`. |
| `hasMegaForm?` | Si tiene una Mega forma accesible distinta de la actual. |
| `mega?` | Si la forma **actual** es Mega (tiene mega_stone/mega_move). |
| `makeMega` | Cambia a la Mega forma (guarda `previous_form`). |
| `makeUnmega` | Revierte (`unmega_form`, o `previous_form` si `unmega_form == -2`). |
| `getUnmegaForm` | Forma de reversión. |
| `megaName` | Nombre Mega ("Mega Charizard" o el FormName). |
| `megaMessage` | 0 = mensaje estándar, 1 = mensaje Rayquaza. |
| `hasPrimalForm?` / `primal?` / `makePrimal` / `makeUnprimal` | Reversión Primal (vía MultipleForms `getPrimalForm`/`getUnprimalForm`). |

Tras combate el motor llama `makeUnmega`/`makeUnprimal` en todo el equipo automáticamente.

---

## Pokémon Oscuros (Shadow)

Datos opcionales en `shadow_pokemon.txt` (moveset oscuro + `GaugeSize`). API:

| Método | Qué hace |
|---|---|
| `makeShadow` | Convierte en Shadow: medidor lleno, guarda moves originales, carga moves oscuros (o `:SHADOWRUSH`). |
| `shadowPokemon?` | Si es Shadow (tiene `@shadow` y `heart_gauge >= 0`). |
| `heart_gauge` / `heart_gauge=` · `adjustHeart(±v)` · `max_gauge_size` | Medidor del corazón. |
| `heartStage` | 0–5 (5 = lleno, 0 = listo para purificar). |
| `change_heart_gauge("battle"|"call"|"walking"|"scent", mult=1)` | Reduce el medidor según naturaleza (tabla por naturaleza en el código). |
| `hyper_mode` / `hyper_mode=` | Hiper Modo (medidor a 0 o KO → false). |
| `update_shadow_moves` | Reaprende moves según `heartStage` (mezcla oscuros + originales). |
| `purifiable?` | true si medidor a 0 (Lugia nunca). |
| `check_ready_to_purify` | Mensaje cuando llega a 0. |
| `shadow_data` | El `GameData::ShadowPokemon` de su especie/forma. |

Atributos: `shadow`, `heart_gauge`, `hyper_mode`, `saved_exp`, `saved_ev`, `shadow_moves`, `heart_gauge_step_counter`. (Como Shadow, la exp/ev se acumulan en `saved_exp`/`saved_ev` hasta purificar.)

---

## Almacenamiento (`$PokemonStorage`)

El PC del jugador. Cajas indexadas; índice -1 = equipo en algunos métodos.

| Uso | Qué hace |
|---|---|
| `$PokemonStorage[box, index]` o `$PokemonStorage[box][index]` | Lee un Pokémon (o nil). |
| `$PokemonStorage.maxBoxes` · `$PokemonStorage.maxPokemon(box)` | Límites. |
| `$PokemonStorage.pbStorePokemon(pkmn)` | Guarda un Pokémon en la primera caja con hueco. |
| `$PokemonStorage.pbDelete(box, index)` | Borra. |
| `pbStorePokemon(pkmn)` (global) | Lo añade al equipo o, si está lleno, al PC. |

Helpers de `Trainer` (también en `$player`) para barrer equipo y PC: `delete_species_from_party/pc(species, form=-1)`, `delete_type_from_party/pc(type)`, `has_species?(species, form=-1, exclude_form=-1, check_pc=false)`. Ver `Trainer_Class.rb` y skill [[battles-runtime]] para la API completa de `Trainer`/`Player`.

---

## Intercambio (Trade)
`pbStartTrade(pokemonIndex, npcTradeSpecies, npcName, npcTrainerType, ...)` realiza un intercambio con NPC; el Pokémon recibido puede evolucionar (`check_evolution_on_trade`). Pokémon bloqueados: Shadow, `cannot_trade`, huevos. Ver `044_Pokemon-related/008_Trade_From_PC.rb` para el intercambio desde el PC (custom Sky).
