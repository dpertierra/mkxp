# Reference · Add-ons de batalla — Raid, SOS, Wonder Launcher

Tres add-ons que añaden modos de combate. Recompilar tras instalar (`CTRL` al cargar; `SHIFT` reaplica
PBS). Cada uno aporta sus reglas + triggers/commands de midbattle + datos PBS + contadores `$stats`.

---

## Raid Battles — Sección 7.C
Cuatro variantes: `:Basic` (sin add-on), `:Max` (requiere Dynamax), `:Tera` (requiere Tera), `:Ultra`
(requiere Z-Power). En un raid, las demás mecánicas de forma se desactivan.

**Lanzar un raid:** `RaidBattle.start(species, properties)` (ambos opcionales).
- **species** (1.er arg): species/form ID (`:CHARIZARD`, `:RAICHU_1`; especie no elegible → Ditto), o un
  **hash de filtro** para aleatorio: `:type`, `:habitat`, `:generation` (nº), `:encounter` (tipo de la
  tabla del mapa), o `nil`/`{}` para totalmente aleatorio. **No** se pasa nivel (lo decide el rango).
- **properties** (2.º arg, hash): `:rank` (1-7, def por medallas), `:style` (`:Basic`/`:Ultra`/`:Max`/
  `:Tera`), `:size` (1-3 battlers del jugador), `:partner` (`[trainer_type, "nombre", versión, true?]`;
  `true` = no escalar), `:turn_count` (Int o `nil` desactiva), `:ko_count` (Int o `nil`), `:shield_hp`
  (Int o `nil` = sin escudo), `:extra_actions` (Array de `:reset_boosts`/`:reset_drops`/`:drain_cheer`,
  o `nil`), `:support_moves` (Array o `nil`), `:spread_moves` (Array o `nil`), `:loot`/`:online` (solo
  Raid Dens). Combina con `setBattleRule("editWildPokemon", {...})`.

```ruby
RaidBattle.start(:CHARIZARD, { :rank => 5, :shield_hp => 8, :support_moves => [:SUNNYDAY] })
RaidBattle.start({ :type => :ROCK, :habitat => :Mountain }, { :partner => [:POKEMONTRAINER_May, "May"] })
RaidBattle.start(:TERAPAGOS, { :style => :Tera, :size => 2 })   # aparece en su Tera form única
```
Las especies con forma Eternamax/Ultra/Tera única aparecen en ella según el `:style`. Test rápido: Debug →
"Deluxe plugin settings..." → "Other plugin settings..." → "Raid settings..." → "Test raid battle".

**Battle Rules (cheer):** `"cheerBattle"` (habilita cheers aunque no sea raid), `"cheerMode", Integer`.
**Mid-battle Triggers:** `"BeforeCheer"`/`"AfterCheer"`/`"FailedCheer"` (ext: cheer ID/species),
`"CheerLevel"` (ext: `_#`), `"RaidShieldStart"`/`"RaidShieldDamaged"`/`"RaidShieldBroken"` (sin ext de
user; solo rival). **Commands:** `"setCheerLv" => 0..3`, `"useCheer" => :Offense` (cheer ID),
`"raidCounter" => [:turn_count|:ko_count, ±Int]`, `"raidAction" => :reset_drops|:reset_boosts|:drain_cheer`,
`"raidShield" => ±Int` (barras de escudo; funciona en cualquier rival).
**Stats:** `$stats.raid_battles_won`, `raid_dens_cleared`, `online_raid_dens_cleared`.
Raid Dens (eventos en mapa, loot, online), Adventures, Battle Mechanics (escudos/contadores/acciones) y
Cheer Mechanics: GitBook `03_Raid Raid Dens`, `04_Raid Adventures`, `05_Raid Battle Mechanics`.

---

## SOS Battles — Sección 7.B
Salvajes que piden ayuda (Sun/Moon). Settings: `SOS_CALL_SWITCH` (def 62) habilita el mecanismo global;
`LIMIT_SOS_CALLS_TO_ONE` (def true, estilo USUM + Adrenaline Orb); `SOS_CHAIN_SHINY_MULTIPLIER`.

**Battle Rules:** `"SOSBattle"` (fuerza llamadas aunque call rate 0), `"noSOSBattle"`,
`"totemBattle", true|[stat,etapas...]` (Totem; activa `SOSBattle`+`cannotRun`+`disablePokeBalls`; aura
+1 omni por defecto), `"setSOSPokemon", Species|Hash` (reemplaza a quién llama; hash = atributos como
`editWildPokemon`; en Totem = 1.er llamado), `"addSOSPokemon", Species|Hash` (añade llamada "rara" 15%;
en Totem = 2.º llamado).
**Mid-battle Triggers:** `"BeforeSOS"`/`"AfterSOS"`/`"FailedSOS"` (ext: species/type).
**Commands:** `"sosCall" => true` (fuerza llamada, nunca falla, puede meter un 3.er salvaje),
`"disableSOS" => bool`, `"addWild" => Species|true|[species, nivel]` (añade salvaje al rival sin SOS),
`"addTrainer" => [trainer_type, "nombre", versión?]` (añade entrenador rival).
**PBS (recompilar):** en species — `CallRateSOS = Int` (3/6/9/15…; 0/ausente = nunca llama),
`SpeciesSOS = ID[,ID...]` (1.º común 85%, resto raro 15%; flag `SOSForm_#` fija la forma llamada),
`ConditionalSOS = Species,EncType,Map[,...]` (llamadas condicionadas por tipo de encuentro/mapa),
`RivalSpecies = ID[,ID...]` (+flag `AllRivalForms`). En `map_metadata.txt` (manual):
`SpecialSOS = Species,%,TimeOfDay,CondType,Cond[,...]` (encuentro SOS por mapa para cualquier llamante;
`CondType` = `Weather`/`Terrain`/`Environment`/`Any`). Item: Adrenaline Orb (`BattleUse = Direct`).
**Hardcode:** battle `@sosBattle`/`@totemBattle`/`@primarySOS`/`@secondarySOS`/`@sos_chain`/
`@adrenalineOrb`, `pbCallForHelp(caller)`/`pbCallForHelpSimple`/`pbAddNewBattler(species, level)`/
`pbAddNewTrainer(type, name, version)`; battler `@totemBattler`, `sos_call_rate`/`canSOSCall?`/
`canSOSCallSimple?`/`isRivalSpecies?(other)`.

---

## Wonder Launcher — Sección 7.H
Combates de entrenador con objetos lanzables por puntos (LP), estilo BW. Settings:
`WONDER_LAUNCHER_SWITCH` (def 72) habilita global; `WONDER_LAUNCHER_POINTS_PER_TURN` (def 1);
`WONDER_LAUNCHER_MAX_POINTS` (def 14); `SHOW_LAUNCHER_SPLASH_EACH_TURN`.

**Battle Rules:** `"wonderLauncher"` (activa en este combate), `"noWonderLauncher"`.
**Mid-battle Triggers:** `"TrainerGainedLP"`, `"TrainerLostLP"`.
**Commands:** `"setLP" => Int|[Int, idxBattler]` (suma LP al entrenador del foco/índice),
`"disableLP" => bool`.
**PBS (`items.txt`, recompilar):** `LauncherPoints = Int` (>0 lo hace lanzable; coste en LP; solo
bolsillos Medicine(2)/Battle(7)); `LauncherUse = OnPokemon|OnMove|OnBattler|OnFoe|Direct|OnTarget`
(hereda de `BattleUse`; solo poner `OnTarget` para items tipo "Urge").
**Hardcode:** `launcherBattle?`, `pbCanUseLauncher?(idx)`, `pbToggleLauncher(idx, toggle=nil)`,
`pbIncreaseLauncherPoints(idx, points, showBar=false)`, `pbReduceLauncherPoints(idx, item, showBar)`,
`pbSetLauncherItems(idxSide, idxTrainer)`.
**Stats:** `$stats.wonder_launcher_item_count`, `wonder_launcher_battles_won`.

> Estos add-ons amplían el midbattle scripting del DBK: sus triggers/commands se mezclan con los del
> core (`02`/`03`) en el mismo hash de `"midbattleScript"`. Para efectos de combate custom ver
> [[battle-effects]]; para disparar combates base ver [[battles-runtime]].
