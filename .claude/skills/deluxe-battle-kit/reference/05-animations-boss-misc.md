# Reference · Animaciones, Wild Boss Attributes, Misc y ejemplos

## Animaciones (Sección 1)
DBK añade animaciones y utilidades visuales. Configurables en el Settings del plugin:
- `HIDE_DATABOXES_DURING_MOVES` (def true) — oculta databoxes durante animaciones de movimiento (estilo
  Gen 5+).
- Animación de **uso de objeto** sobre battler + texto más específico + SE.
- Animación de **huida** del salvaje (roamers, Safari, `raidStyleCapture`, command `wildFlee`).
- **Mega-Evolución** y **Primal**: animaciones propias. Se saltan con la tecla `ACTION`; si las
  animaciones de combate están off (Opciones) o `SHOW_MEGA_ANIM`/`SHOW_PRIMAL_ANIM` = false, se usa la
  "quick-change". Si ya tienes una common animation llamada `"MegaEvolution"` o `"Primal<Especie>"`
  (`"PrimalGroudon"`), **esa tiene prioridad**. Gráficos custom Primal en
  `Graphics/Plugins/Deluxe Battle Kit/Primal` (icono + silueta blanca). Iconos Mega/Primal en los lados
  de la databox.
- **Shadow Pokémon overlay**: aplica un patrón (`shadow_pattern` en `Graphics/Plugins/Deluxe Battle Kit`)
  sobre cualquier sprite Shadow sin necesidad de sprite propio. `SHADOW_PATTERN_MOVEMENT = [x, y]` con
  símbolos X (`:none`/`:left`/`:right`/`:erratic`) e Y (`:none`/`:up`/`:down`/`:erratic`);
  `[:none, :none]` = estático. `DONT_OVERLAY_EXISTING_SHADOW_SPRITES` (true por defecto) respeta sprites
  Shadow ya existentes. Icono de Hyper Mode en la databox.

> El estilo de animación Mega es la base de las de Z-Move, Dynamax y Tera (add-ons).

## Wild Boss Attributes (Sección 3)
Dos claves **extra** del hash de `"editWildPokemon"` (ver `01-battle-rules.md`), para jefes salvajes.
Vuelven a la normalidad si lo capturas.

- **`:hp_level`** (Integer) — multiplica los PS totales (`:hp_level => 6` = 6× PS). Sin límite (miles de
  PS). **Importante:** los efectos basados en % de PS (daño/recoil/cura) se calculan sobre los PS
  **originales**, no los escalados — incluye Super Fang, Endeavor, Pain Split, Curse, daño/cura pasivos
  (veneno/quemadura/clima/Leftovers/Life Orb/Leech Seed…), recover-likes, etc. (los de cantidad fija como
  Oran/Shell Bell no se escalan).
- **`:immunities`** (Array de símbolos) — inmunidades de jefe solo para este combate:
  `:SLEEP`, `:POISON`, `:BURN`, `:PARALYSIS`, `:FROZEN`, `:FROSTBITE` (Gen9), `:DROWSY` (Gen9),
  `:CONFUSION`, `:ATTRACT`, `:ALLSTATUS` (todos los estados + confusión + atracción), `:FLINCH`,
  `:CRITICALHIT`, `:STATDROPS` (no propios), `:PPLOSS` (PP infinito), `:TYPECHANGE`, `:ITEMREMOVAL`,
  `:ABILITYREMOVAL`, `:INDIRECT` (sin daño indirecto, estilo Magic Guard), `:DISABLE` (Disable/Taunt/
  Encore/Torment/Heal Block/Throat Chop), `:OHKO` (incl. Destiny Bond/Perish Song), `:SELFKO`
  (Explosion/Final Gambit/Memento…), `:ESCAPE` (Roar/Whirlwind/Teleport…), `:TRANSFORM` (no copiable ni
  puede transformarse).

```ruby
setBattleRule("editWildPokemon", {
  :name => "Big Kahuna", :form => 1, :item => :THICKCLUB, :hp_level => 10,
  :immunities => [:SLEEP, :FROZEN, :OHKO, :ITEMREMOVAL]
})
setBattleRule("cannotRun"); setBattleRule("disablePokeBalls")
WildBattle.start(:MAROWAK, 50)
```

## Miscellaneous Utilities (Sección 6)
- `SHORTEN_MOVES` (Settings) — trunca con "…" nombres de movimiento largos en el menú.
- `PLAY_LOW_HP_MUSIC` (Settings) — música de HP bajo (global script `:low_hp_music`); por combate con la
  regla `"lowHealthBGM"`.
- **`TrainerBattle.select_start(size, *args)`** — combate donde el jugador elige solo `size` Pokémon del
  equipo (`args` = lo normal de `TrainerBattle.start`).
- **Menú Debug "Deluxe plugin settings..."** — togglear Mega/Z/Dynamax/mapas/Tera Orb, etc. (cada add-on
  añade opciones aquí).
- Iconos de **Mega Stone** en el menú de party.
- Contadores: `$stats.mega_evolution_count`, `$stats.primal_reversion_count`,
  `$stats.wild_mega_battles_won`.
- Flag de movimiento **`HighCriticalHitRate_#`** en `moves.txt` (etapas de crítico; sin número = +1).
- Flag de objeto **`UsesAllBattleActions`** (consume todo el turno como las Poké Balls; requiere además
  `firstAction` en el item handler).
- **Refactor de daño**: `pbCalcDamageMultipliers` reescrito en trozos pequeños en
  `[000] Essentials Patches/[003] Damage Calc Refactor` (útil al añadir efectos que tocan daño;
  ver [[battle-effects]]).

## Patrones de los Example Battles (Sección 5)
Plantillas reales que combinan reglas + midbattle script. Úsalas como modelo:

- **Jefe salvaje por fases** (Deoxys): `editWildPokemon` con `:hp_level`/`:immunities`, `battlerHPCap`
  (= `-1` para sobrevivir con 1 PS hasta cierto punto), regeneración por `RoundEnd_foe_repeat`
  (`battlerForm => [:Cycle, ...]`, `battlerHP => [4, ...]`), corte de regeneración con
  `ignoreAfter => "TargetHPLow_foe"`, y captura forzada en `BattlerReachedHPCap_foe`
  (`disableBalls => false` + `useItem => :POKEBALL`) con `alwaysCapture`.
- **Salvajes que se apoyan** (Latias/Latios): `TargetHPLow_foe_repeat` cura al aliado;
  `BattlerFainted_foe` → `setBattler => :Ally` + `wildFlee => true`. Partner con
  `pbRegisterPartner(:POKEMONTRAINER_May, "May")` antes del `WildBattle.start`.
- **Quiz battle** (Opal): `RoundEnd_#_foe` con `setChoices`/`speech [..., :Choices]` y consecuencias en
  `ChoiceRight_<id>`/`ChoiceWrong_<id>` (`setBattler => :Opposing` + `battlerStats`).
- **Combate con cuenta atrás** (cueva que colapsa): `canLose`, `RoundEnd_player_repeat` (SE+texto),
  `RoundEnd_#_player` para eventos por turno (`playAnim`, `battlerHP`, `battlerStatus`),
  `RoundEnd_4_player` fuerza huida con `endBattle => 3`, y `BattleEndForfeit` con la pulla final.

Combinar reglas DBK + un midbattle script bien estructurado es la fórmula general. Para escribirlos, usa
`02-mid-battle-triggers.md` (cuándo) + `03-mid-battle-commands.md` (qué) + `04-advanced-scripting.md`.
