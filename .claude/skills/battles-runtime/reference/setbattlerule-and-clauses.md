# setBattleRule (todas las reglas) · Clauses · Poké Ball effects · API Trainer/Player

Fuentes: `039_Battle triggering/002_Overworld_BattleStarting.rb` (`BATTLE_RULES`, `prepare_battle`), `034_Other battle code/007_Battle_Clauses.rb`, `011_Battle_PokeBallEffects.rb`, `046_Trainers and player/002_Trainer_Class.rb` + `005_Player.rb`.

---

## Todas las reglas de `setBattleRule` (hash `BATTLE_RULES`)

Cada regla escribe una clave en `$game_temp.battle_rules`, que `prepare_battle` traslada a `battle.rules`. **Mayúsculas/minúsculas indistintas.** Reglas marcadas con (valor) requieren un argumento a continuación.

### Tamaño del combate → `:side_sizes`
`single` / `1v1` · `1v2` · `2v1` · `1v3` · `3v1` · `double` / `2v2` · `2v3` · `3v2` · `triple` / `3v3`. (El string se guarda tal cual y `setBattleMode` lo interpreta como "aliados vs rivales".)

### Visual
| Regla | Clave | Efecto |
|---|---|---|
| `backdrop` / `battleback` (valor) | `:backdrop_name` | Fondo de batalla (nombre base de `Graphics/Battlebacks/`). |
| `base` (valor) | `:base_name` | Gráfico de la base. |
| `anims` | `:no_battle_animations=false` | Fuerza animaciones de batalla ON. |
| `noanims` | `:no_battle_animations=true` | Fuerza OFF. |

### Entorno / clima / terreno
| Regla | Clave | Efecto |
|---|---|---|
| `environment` / `environ` (valor) | `:environment` | Fuerza el `GameData::Environment` (afecta fondo, ciertos moves, Buceo Ball). |
| `weather` (valor) | `:default_weather` | `GameData::BattleWeather` inicial (Sun/Rain/Sandstorm/Hail/Snowstorm/HarshSun/HeavyRain/StrongWinds/ShadowSky). |
| `terrain` (valor) | `:default_terrain` | `GameData::BattleTerrain` inicial (Electric/Grassy/Misty/Psychic). |

### Acciones disponibles / partner
| Regla | Clave | Efecto |
|---|---|---|
| `canrun` / `cannotrun` | `:cannot_run` | Permite/impide huir. |
| `canswitch` / `cannotswitch` | `:cannot_switch` | Permite/impide cambiar Pokémon. |
| `switchstyle` / `setstyle` | `:no_switch_style` | Estilo cambio/fijo (avisar del relevo o no). |
| `disablebag` | `:disable_bag=true` | Sin Mochila. |
| `disablepokeballs` | `:disable_poke_balls=true` | Sin Poké Balls. |
| `autobattle` | `:auto_battle=true` | La IA controla al jugador (`battle.controlPlayer = true`). |
| `nopartner` | `:no_partner_trainer=true` | Ignora al partner aunque exista. |

### Roamers / capturas / resultado / recompensas
| Regla | Clave | Efecto |
|---|---|---|
| `roamerflees` | `:roamer_flees=true` | El roamer huirá. |
| `certaincapture` | `:certain_capture=true` | Toda captura tiene éxito (también flag de mapa `CertainCaptures`). |
| `forcecatchintoparty` | `:force_catch_into_party=true` | El capturado va al equipo (`sendToBoxes = 2`). |
| `canlose` / `cannotlose` | `:continue_if_lose` | Si perder NO causa "black out" (cura el equipo y continúa). |
| `outcome` / `outcomevar` (valor) | `:outcome_variable` | Game Variable donde se guarda el resultado (default 1). |
| `noexp` | `:no_exp_gain=true` | Sin experiencia. |
| `nomoney` | `:no_money_gain=true` | Sin dinero. |

### Reglas comentadas en `add_battle_rule` (existen como concepto, aplicadas en `prepare_battle`)
`adjustlevels` (`:adjust_levels`) y `adjustlevelsresetmoves` (`:adjust_levels_reset_moves`): igualan el nivel del equipo rival al del jugador (y opcionalmente resetean moves). Solo surten efecto si la clase Battle define `adjust_levels` (Frontier).

---

## Clauses (reglas de torneo) — `battle.rules["..."] = true`

No se ponen con `setBattleRule` (no están en la whitelist); se fijan en la clase Battle (Frontier / setup manual). Cada una modifica el combate vía alias en `Battle_Clauses.rb`:

| Clave | Efecto |
|---|---|
| `sleepclause` | No se puede dormir a un rival si ya tiene otro Pokémon dormido. |
| `modifiedsleepclause` | Como sleepclause pero incluye autosueño. |
| `freezeclause` | No congelar si ya hay otro congelado. |
| `evasionclause` | Fallan Doble Equipo / Minimicé (movimientos de evasión). |
| `ohkoclause` | Fallan los movimientos OHKO (Guillotina, Fisura…). |
| `skillswapclause` | Falla Intercambio. |
| `sonicboomclause` | Fallan Bomba Sónica y Furia Dragón (daño fijo). |
| `perishsongclause` | Fallan Canto Mortal / Mismo Destino si no quedan Pokémon en banca. |
| `selfdestructclause` | Autodestrucción/Explosión que dejaría a 0 Pokémon → descalifica al usuario. |
| `selfkoclause` | Decide ganador si el último move provoca empate por autoKO. |
| `modifiedselfdestructclause` | Variante: Self-Destruct en empate decide ganador. |
| `drawclause` | En empate por KO mutuo decide ganador (excepto Liquid Ooze). |
| `suddendeath` | Al final de cada ronda, gana quien tenga más Pokémon vivos. |

---

## Poké Ball effects (`Battle::PokeBallEffects`)

Handlers registrados como `IsUnconditional` (captura segura), `ModifyCatchRate` (multiplica/modifica la tasa), `OnCatch`, `OnFailCatch`. Multiplicadores reales (los condicionales se aplican solo si se cumple la condición; resultado siempre clamp 1–255):

| Ball | Efecto |
|---|---|
| `MASTERBALL` | captura incondicional. |
| `GREATBALL` | ×1.5 · `ULTRABALL` ×2 · `SAFARIBALL` ×1.5 · `SPORTBALL` ×1.5. |
| `NETBALL` | ×3.5 (×3 si no `NEW_POKE_BALL_CATCH_RATES`) si Bicho/Agua. |
| `DIVEBALL` | ×3.5 si entorno Underwater. |
| `NESTBALL` | mayor cuanto más bajo el nivel del rival (≤30). |
| `REPEATBALL` | ×3.5/×3 si ya capturada la especie. |
| `TIMERBALL` | +0.3 por turno (máx ×4). |
| `DUSKBALL` | ×3/×3.5 de noche o en cueva (`battle.time == 2`). |
| `QUICKBALL` | ×5 en el turno 0. |
| `FASTBALL` | ×4 si base de Velocidad ≥100. |
| `LEVELBALL` | ×2/×4/×8 según cuánto supera tu nivel al del rival. |
| `LUREBALL` | ×5/×3 si el encuentro es de pesca. |
| `HEAVYBALL` | suma/resta puntos fijos según el peso del rival. |
| `LOVEBALL` | ×8 si hay un aliado de misma especie y género opuesto. |
| `MOONBALL` | ×4 si la familia evolutiva usa Piedra Lunar. |
| `DREAMBALL` | ×4 si el rival está dormido. |
| `BEASTBALL` | ×5 si Ultraente (flag `UltraBeast`), ×0.1 si no. |
| `HEALBALL` (OnCatch) | cura al capturado. |
| `FRIENDBALL` (OnCatch) | felicidad a 150/200. |

Registrar una ball nueva: `Battle::PokeBallEffects::ModifyCatchRate.add(:MIBALL, proc { |ball, catchRate, battle, battler| ... })`.

---

## API de `Trainer` / `NPCTrainer` / `Player`

### `Trainer` (base) y `NPCTrainer`
Constructor: `Trainer.new(name, trainer_type)` (genera `id` y `language`). `NPCTrainer.new(name, trainer_type, version=0)` añade `items`, `lose_text`, `win_text`.

Consultas a su tipo: `trainer_type_name`, `base_money`, `gender`, `male?/female?`, `skill_level`, `default_poke_ball`, `flags`, `has_flag?(f)`, `full_name`, `public_ID`/`secret_ID`, `make_foreign_ID`.

Party: `party` (Array), `pokemon_party` (sin huevos), `able_party` (sin KO/huevos), `party_count`, `pokemon_count`, `able_pokemon_count`, `party_full?`, `all_fainted?`, `first_party/first_pokemon/first_able_pokemon`, `last_*`.
Manipular: `remove_pokemon_at_index(i)`, `delete_type_from_party/pc(type)`, `delete_species_from_party/pc(species, form=-1)`, `heal_party`, `give_status_party_pokemon(status, count=1, prob=25, in_order=true)`.
Buscar: `has_species?(species, form=-1, exclude_form=-1, check_pc=false)`, `has_fateful_species?`, `has_pokemon_of_type?(type, excluded=[])`, `find_pokemon_of_type/species`, `has_pokemon_with_ability?`, `find_pokemon_with_ability`, `get_pokemon_with_move(move)`, `get_pokemon_can_learn_move(move)`.
Helper Sky: `battle_self(name, type)` — combate contra clon de tu equipo.

### `Player < Trainer` (`$player`)
Atributos: `money` (`money=` clamp `MAX_MONEY`), `coins` (`coins=` clamp `MAX_COINS`), `battle_points` (clamp `MAX_BATTLE_POINTS`), `soot`, `badges` (Array bool [8]), `badge_count`, `receive_badge(i)` (= `pbReceiveBadge(i)`, dispara `:on_badge_obtained`), `pokedex`, `outfit` / `outfit=`, `character_ID`, `has_pokedex`, `has_pokegear`, `has_running_shoes`, `has_box_link`, `has_exp_all`, `mystery_gift_unlocked`, `mystery_gifts`, `running?`. Pokédex: `seen?(species)`, `owned?(species)`.

---

## Helpers de evento relacionados
- `pbTrainerIntro(:TIPO)` / `pbTrainerEnd` — congela/descongela eventos + BGM de intro (envuelven el combate de entrenador).
- `pbNoticePlayer(get_self)` — el entrenador "ve" al jugador y se acerca.
- `pbPhoneRegisterBattle`, `pbPhoneBattleCount`, `pbPhoneIncrement` — rematches vía teléfono (ver [[pbs-data]] → phone + Rematches).
- `pbSetSelfSwitch(eventId, "A", true)` — sincronizar el self-switch del otro NPC en dobles.
- `pbEvolutionCheck` — comprueba evoluciones del equipo tras combate (lo dispara `:on_end_battle`).
