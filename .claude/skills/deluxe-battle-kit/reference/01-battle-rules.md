# Reference · Deluxe Battle Rules (DBK)

DBK **amplía `setBattleRule`** con reglas nuevas. Se aplican igual que las del motor (antes de
`WildBattle.start`/`TrainerBattle.start`, valen solo para el siguiente combate). Las **28 reglas base**
están en [[battles-runtime]]; aquí van **solo las que añade DBK**.

> Sintaxis: `setBattleRule("nombre")` (flag) o `setBattleRule("nombre", valor)`.

## Modos de batalla (2.A)
- `"autoBattle"` — la IA controla al jugador; el combate se juega solo.
- `"towerBattle"` — combate competitivo/pvp estilo Torre de Combate: el jugador no puede usar objetos de
  la mochila y puede rendirse en combates de entrenador con Huir. Habilita el speech de victoria del rival.
- `"noBag"` — ningún entrenador (incluido el jugador) puede usar objetos; el comando "Mochila" se
  desactiva para el jugador.
- `"inverseBattle"` — combate inverso (Gen 6): la tabla de tipos se invierte.

## Visuales (2.B)
- `"battleIntroText", String` — texto de introducción del combate. `{1}`/`{2}`/`{3}` = nombres de los
  rivales. Ej: `setBattleRule("battleIntroText", "¡Te embosca un {1} salvaje!")`.
- `"opponentLoseText", Message` — texto de derrota del rival (sobrescribe el suyo). `Message` = String o
  Array de strings (uno por rival en dobles/triples).
- `"opponentWinText", Message` — texto de victoria del rival. **Solo** surte efecto con `"towerBattle"`.
- `"setSlideSprite", String` — cómo entra el sprite del rival: `"side"` (def, izquierda), `"top"`,
  `"bottom"` (sube del suelo), `"still"` (sin deslizar). Añade `"_hideBase"` para ocultar base/sombra
  (ej. `"top_hideBase"` estilo Giratina de Platino).
- `"databoxStyle", Style` — estilo de databox (pensado para jefes). `Style` = símbolo: `:Basic`
  (moderno/compacto) o `:Long` (barra larga del rival, solo si su lado tiene 1 slot). Para **títulos de
  jefe** (estilo Titanes de SV, solo salvajes): `setBattleRule("databoxStyle", [:Long, "{1} el Coloso"])`
  (`{1}` = nombre real; un string extra por cada salvaje en dobles/triples). Estilos custom: carpeta
  `Graphics/Plugins/Deluxe Battle Kit/Databoxes/<Estilo>` + `GameData::DataboxStyle` en
  `[001] Deluxe Utilities/[008] Databox Styles`.

## Audio (2.C)
- `"battleBGM", String` — música del combate. `""` = sin música.
- `"victoryBGM", String` — música de victoria. `""` = sin jingle (sigue la de combate).
- `"lowHealthBGM", String` — música de HP crítico. `""` = ninguna.
- `"captureME", String` — jingle de captura. `""` = ninguno.

## Editar al jugador (2.D)
- `"tempPlayer", Attributes` — nombre/aspecto temporal del jugador. `Attributes` = String (nombre nuevo),
  Integer (nº de outfit) o Array `[String, Integer]`.
- `"tempParty", Array` — equipo temporal. Array de objetos `Pokemon` o pares `especie, nivel`
  (ej. `[:PIKACHU, 20, :MEOWTH, 15]`). Puedes crear `pkmn = Pokemon.new(...)`, editarlo y meter `pkmn`.
- `"tempBag", Array` — mochila temporal. Pares `item, cantidad` (sin cantidad = 1).
  Ej. `[:POTION, 5, :POKEBALL, 5, :QUICKBALL, :DUSKBALL]`.

## Mega-Evolución (2.E)
- `"wildMegaEvolution"` — el salvaje con su Mega Piedra Mega-evoluciona al instante al aparecer (sin
  entrenador/Mega-aro). Activa también `"disablePokeBalls"` hasta que el salvaje llegue a su umbral de
  daño y termine la Mega. Con SOS instalado, ignora `"SOSBattle"`/`"totemBattle"`.
- `"noMegaEvolution", Symbol` — desactiva Mega para `:Player`, `:Opponent` o `:All`.

## Captura (2.F)
- `"alwaysCapture"` — cualquier Ball captura siempre.
- `"neverCapture"` — cualquier Ball falla siempre (incluida Master Ball).
- `"tutorialCapture"` — combate tutorial: lo capturado no se queda ni se registra en la Pokédex.
- `"raidStyleCapture", true|Hash` — captura estilo raid: el salvaje no se debilita a 0 PS; se te pide
  lanzar una Ball; si no, huye. Hash opcional con: `:capture_chance` (Integer %, Master Ball lo ignora),
  `:flee_msg` (String, `{1}` = nombre), `:capture_bgm` (String).

## Editar al salvaje (2.G)
- `"editWildPokemon", Hash` — edita el salvaje (slot 1) antes del combate. `"editWildPokemon2"` /
  `"editWildPokemon3"` para el 2.º/3.º en dobles/triples.

Claves del hash: `:species`, `:form`, `:form_simple` (forma sin prompt de aprender movimientos),
`:name`, `:level`, `:gender` (0=M,1=F), `:hp`, `:status`, `:statusCount`, `:shiny`, `:super_shiny`,
`:nature`, `:item`, `:mail` (objeto Mail), `:ability`, `:ability_index`, `:moves` (ID o Array),
`:ribbons` (ID o Array), `:pokerus`, `:happiness` (0-255), `:iv` (Int/Array/Hash), `:ev` (Int/Array/Hash),
`:obtain_text` (String, texto de lugar de captura).
- Con **Enhanced Pokemon UI**: `:shiny_leaf` (0-6). Con **Improved Mementos**: `:memento` (Ribbon/Mark),
  `:scale` (0-255). Con **Dynamax**: `:dynamax_able`, `:dynamax_lvl` (0-10), `:gmax_factor`, `:dynamax`.
  Con **Terastallization**: `:terastal_able`, `:tera_type`, `:terastallized`.

```ruby
setBattleRule("editWildPokemon", {
  :name => "Sparky", :shiny => true, :ability => :VOLTABSORB, :item => :LIGHTBALL,
  :nature => :HASTY, :moves => [:VOLTTACKLE, :SURF, :WISH, :ENCORE], :iv => 31
})
WildBattle.start(:PIKACHU, 35)
```

Reglas de los add-ons (SOS, Raid, Z-Power, Dynamax, Tera, Wonder Launcher) → ver sus reference. Para crear
efectos de combate custom ver [[battle-effects]]; para construir el Pokémon/entrenador ver
[[pokemon-api]]/[[battles-runtime]].
