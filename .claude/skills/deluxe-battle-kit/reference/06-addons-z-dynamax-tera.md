# Reference · Add-ons de formas — Z-Power, Dynamax, Terastallization

Tres add-ons del DBK que añaden mecánicas de transformación. **Patrón común:** cada uno necesita que el
entrenador tenga un objeto-llave (Z-Ring/Dynamax Band/Tera Orb por flag PBS) y el Pokémon un objeto/forma
compatible; trae una regla `wild<X>` (salvaje que se transforma solo al aparecer, umbral de daño = 1/6 PS,
activa `disablePokeBalls`, **ignora** `midbattleScript` y las reglas SOS), una regla `no<X>`
(`:Player`/`:Opponent`/`:All`), un switch `NO_<X>` para desactivar global, opciones en el menú Debug
"Deluxe plugin settings...", contadores `$stats`, y triggers/commands de midbattle. **Recompilar** tras
instalar (mantén `CTRL` al cargar; `SHIFT` para reaplicar datos PBS).

---

## Z-Power (Z-Moves + Ultra Burst) — Sección 7.D
Settings: `NO_ZMOVE` (switch, def 64), `NO_ULTRA_BURST` (def 65). Objeto-llave: flag `ZRing` en un item.
Item extra: Z-Booster (recarga Z una vez en combate, gasta todo el turno).

**PBS:** archivos nuevos `moves_zpower.txt` (Z-Moves) e `items_zpower.txt` (Z-Rings/Z-Crystals).
Z-Crystal: `FieldUse = OnPokemon`, flags `KeyItem,ZCrystal`, `Move = <ZMOVE>` (la Z-Move que desbloquea),
`Pocket` ignorado (van al último bolsillo, configurable con `ZCRYSTAL_BAG_POCKET`/`_NAME`).
- **Z-Move genérica**: `Power = 1` (escala según el movimiento base), `Accuracy = 0`, `TotalPP = 1`,
  flags `ZMove_<TIPO>,CannotMetronome` (la category se hereda del base).
- **Z-Move exclusiva**: puede tener Power fijo y ser status; flag `ZMove` (sin tipo). El Z-Crystal lleva
  `ZCombo = <MOVE_BASE>,<ESPECIE>` (y `UsableByAllForms` si aplica a todas las formas).
- **Status move con Z-Power**: flag `ZPower_<FLAG>` en el move: `HealUser`, `HealSwitch`, `FollowMe`,
  `CriticalHit`, `ResetStats`, `<STAT>_<n>` (p.ej. `ZPower_ATTACK_3`), o `AllStats_<n>`.
- **Z-Move custom con efecto**: clase `Battle::ZMove::<Code> < Battle::Move` (puede heredar de un
  function code base existente). Ver [[battle-effects]].

**Battle Rules:** `"wildZMoves"`, `"noZMoves", :Player/:Opponent/:All`, `"wildUltraBurst"`,
`"noUltraBurst", :Sym`.
**Ultra Burst** (forma tipo Mega para cualquier especie): item con `getUltraItem` + `MultipleForms.register`
con `getUltraForm`/`getUnUltraForm`/`getUltraItem` (y `getDataPageInfo` para Pokedex Data Page).
**Mid-battle Triggers:** `"BeforeZMove"`, `"BeforeZStatus"`, `"BeforeUltraBurst"`, `"AfterUltraBurst"`
(ext: species/type/move ID). **Commands:** `"useZMove" => true|"msg"` (antes de mover; combina con
`useMove`), `"disableZMoves" => bool`, `"ultraBurst" => true|"msg"`, `"disableUltra" => bool`.
**Hardcode:** `pbHasZRing?`/`pbGetZRingName`/`pbCanZMove?`/`pbCanUltraBurst?`/`pbUltraBurst(idx)`;
battler `hasZMove?`/`hasCompatibleZMove?(base)`/`display_zmoves`/`ultra?`/`hasUltra?`/`unUltra`.
**Stats:** `$stats.named_zmove_count`, `status_zmove_count`, `total_zmove_count`,
`wild_zpower_battles_won`, `ultra_burst_count`, `wild_ultra_battles_won`.

---

## Dynamax (+ Gigantamax) — Sección 7.E
Settings: `DYNAMAX_TURNS` (def 3; `-1` = infinito), `SHOW_DYNAMAX_SIZE`/`SHOW_DYNAMAX_OVERLAY`
(sprites 50% más grandes + overlay rojo), `DYNAMAX_PATTERN_MOVEMENT [x,y]`, `STORAGE_GMAX_FACTOR`.
Objeto-llave: flag `Dynamax Band`. Dynamax acaba al cambiar/debilitarse/fin de combate. Solo en mapas que
soporten Dynamax (configurable por debug).

**Datos:**
- **Metrics**: `pokemon_metrics.txt` añade `DmaxBackSprite`/`DmaxFrontSprite`/`DmaxShadowX` (editor debug
  "Dynamax metrics..."). Overlays custom por especie en `Graphics/Plugins/Dynamax/Patterns/<ESPECIE>`;
  sombras en `Graphics/Pokemon/Shadow/<ESPECIE>_dmax`.
- **Dynamax level** (0-10; +5% PS/nivel, 0=+50%, 10=×2): `pkmn.dynamax_lvl` (0-10). Jugador: Dynamax
  Candy / Candy XL. NPC: `DynamaxLv = #` en `trainers.txt`. Salvaje: `:dynamax_lvl => #` en
  `editWildPokemon`.
- **G-Max Factor** (para forma Gigantamax): `pkmn.gmax_factor?`/`= true`. Jugador: Max Soup. NPC:
  `Gigantamax = true` en `trainers.txt`. Salvaje: `:gmax_factor => true` en `editWildPokemon`.
- Max Moves y Form Data: ver GitBook `02_Dynamax Move Data` / `03_Dynamax Form Data`.

**Battle Rules:** `"wildDynamax"`, `"noDynamax", :Player/:Opponent/:All`.
**Mid-battle Triggers:** `"BeforeDynamax"`, `"BeforeGigantamax"`, `"AfterDynamax"`, `"AfterGigantamax"`
(ext: species/type). **Commands:** `"dynamax" => true|"msg"` (convierte el movimiento a Max si es antes de
mover), `"disableDynamax" => bool`.
**Hardcode:** `pbHasDynamaxBand?`/`pbGetDynamaxBandName`/`pbCanDynamax?`/`pbDynamax(idx)`; battler
`dynamax?`/`gmax?`/`emax?`/`dynamax_able?`/`gmax_factor?`/`hasDynamax?`/`hasGmax?`/`hasEmax?`/`unDynamax`/
`display_dynamax_moves`.

---

## Terastallization — Sección 7.F
Settings: `NO_TERASTALLIZE` (switch def 69), `TERA_ORB_ALWAYS_CHARGED` (def 70), `RANDOMIZED_TERA_TYPES`
(def 71), `TERA_SHARDS_REQUIRED` (def 50), `SUMMARY_TERA_TYPES`/`STORAGE_TERA_TYPES`, `TERABLAST_BANLIST`.
Objeto-llave: flag `TeraOrb`. La Tera Orb del jugador se gasta y recarga en Centro Pokémon (carga infinita
en mapas con metadata `AreaZero`); las de NPC siempre cargadas. Item extra: Radiant Tera Jewel (recarga en
combate, gasta el turno).

**No pueden Terastalizar:** Shadow; ya Tera; en/elegible para Mega/Primal/Z/Ultra/Dynamax; transformado a
especie con Tera form única; sin Tera Type; flag `CannotTerastallize` en la especie; `pkmn.terastal_able = false`.
NPC: `NoTera = true` por Pokémon en `trainers.txt`; `ReserveLastPokemon` guarda la Tera para el último.

**Tera Type** (`pkmn.tera_type` / `= :FIRE`): por defecto uno de sus tipos base. Jugador: Tera Shards (×50),
Mystery/Master Tera Jewel. NPC: `TeraType = <TIPO>` en `trainers.txt`. Salvaje: `:tera_type => <TIPO>` en
`editWildPokemon`. Bloqueada por flag de especie `TeraType_<TIPO>` (o `TeraType_Random`). `:Random` como
valor randomiza (nunca Stellar). `$player.has_pokemon_tera_type?(type)`. Tipo **Stellar** (19.º) lo añade
el plugin: sin debilidades/resistencias, supereficaz contra cualquier Terastalizado; solo Tera Blast/Tera
Starstorm. Tera forms únicas: ver GitBook `02_Terastal Tera Forms`.

**Battle Rules:** `"wildTerastallize"`, `"noTerastallize", :Player/:Opponent/:All`.
**Mid-battle Triggers:** `"BeforeTerastallize"`, `"AfterTerastallize"`, `"BeforeTeraMove"` (ext: species,
Tera type; move ID solo en `BeforeTeraMove`). **Commands:** `"terastallize" => true|"msg"`,
`"disableTera" => bool`, `"battlerTeraType" => :Sym` (cambio permanente; no en Ogerpon/Terapagos).
**Hardcode:** `pbHasTeraOrb?`/`pbGetTeraOrbName`/`pbCanTerastallize?`/`pbTerastallize(idx)`; battler
`tera?`/`tera_form?`/`hasTera?`/`unTera(break)`.
**Stats:** `$stats.terastallize_count`, `wild_tera_battles_won`, `total_tera_types_changed`.

> `editWildPokemon` admite estas claves de add-on (ver `01-battle-rules.md`): `:dynamax_able`,
> `:dynamax_lvl`, `:gmax_factor`, `:dynamax`; `:terastal_able`, `:tera_type`, `:terastallized`.
> Para escribir efectos de movimientos/habilidades custom de estas mecánicas, ver [[battle-effects]].
