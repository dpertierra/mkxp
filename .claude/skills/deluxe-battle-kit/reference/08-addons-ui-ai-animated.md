# Reference · Add-ons UI/IA/animados — Enhanced UI, Improved Item AI, Animated Pokemon, Animated Trainer Intros

## Enhanced Battle UI — Sección 7.A
Tres UIs nuevas en combate (QoL estilo gen moderna):
- **Battler Info** (tecla `JUMPUP`/A): stats de todos los battlers, efectos en juego, Tera Type (con Tera).
- **Poke Ball shortcut** (tecla `JUMPDOWN`/S en menú de comandos): lanzar Ball sin abrir la mochila.
- **Move Info** (tecla `JUMPDOWN`/S en menú Fight): detalles del movimiento.
Setting `UI_PROMPT_DISPLAY` (en `[000] Main`): `0` oculta los prompts, `1` siempre, `2` (def) se ocultan
tras unos segundos inactivos. No añade keys de midbattle; habilita que `editWildPokemon :shiny_leaf` y la
visualización de Tera Type en combate funcionen.

## Improved Item AI — Sección 7.G
Mejora cómo la IA usa objetos (party entera, más tipos de item, menos desperdicio). Sistema de **scoring**
en `[001] Battle_AI`: `ITEM_FAIL_SCORE = 20`, `ITEM_USELESS_SCORE = 60`, `ITEM_BASE_SCORE = 100`.

**Handlers de IA por objeto** (modular, como los de movimientos). El tipo depende del `BattleUse` del item
(`items.txt`):
- `PokemonItemEffectScore` — items `BattleUse = OnPokemon` (pociones, curas de estado, revivir, PP…).
  Ejemplos en `[002] OnPokemon Handlers`.
- `BattlerItemEffectScore` — items `BattleUse = OnBattler` (X Items, Dire Hit, Persim, Red Flute…).
  Ejemplos en `[003] OnBattler Handlers`.
- `ItemEffectScore` — items `BattleUse = Direct` (Guard Spec., Poke Flute, y Z-Booster/Wishing Star/
  Radiant Tera Jewel de los add-ons). Ejemplos en `[004] Direct Handlers`.
- `GeneralItemScore` — ajuste **general** que se aplica a todos los items tras los handlers anteriores
  (no es por item). Trae `:inventory_count` (más copias → más uso). Útil para perfilar un trainer type
  (p.ej. usar `BaseMoney` para un "niño rico" derrochador, o un flag `ReserveHealingItems`).

Para que un NPC use un objeto en combate que normalmente no sabría usar, **añade un handler** del tipo
correcto. Utilidades en la clase Battle: `pbItemHealsHP?(item, onlyHP=false)`,
`pbItemCuresStatus?(item, oneStatus=false)`, `pbItemRaisesStats?(item, oneStat=false)`,
`pbItemRevivesFainted?(item)`, `pbItemRestoresPP?(item, mode=0)` (1=Ether, 2=Elixir),
`pbGetItemValue(item, itemType)` (`:potion`/`:ether`/`:elixir`/`:stats`/`:revive`),
`pbHasHealingItem?(idxBattler, healAmt=1000, itemType=:potion)`. Concepto análogo al scoring de
movimientos de [[battle-effects]].

## Animated Pokemon System — Sección 7.I
Sprites animados de Pokémon (descarga separada: plugin + sprites). Activado por defecto; toggle en menú
Opciones. Features: escalado/velocidad de animación, sombras animadas, tonos Super Shiny, cambios de forma
estilo mosaico, muñeco de Sustituto, overlay de estado, ocultar sprite en semi-invulnerable.

**Mid-battle Commands:** `"spriteSpeed" => 0..4` (0 estático, 1 rápido, 2 normal, 3 lento, 4 muy lento),
`"spriteReverse" => bool` (animar al revés), `"spriteHue" => -255..255` (tono).
**Hardcode:** `GameData::Species#has_gendered_sprites?`/`shows_shadow?(back)`;
`GameData::SpeciesMetrics` `back_sprite_scale`/`front_sprite_scale`/`back_sprite_speed`/`front_sprite_speed`/
`sprite_super_hue`/`get_random_hue`; `Battle::Scene` `pbGetBattlerSprites(idx)`/
`pbAnimateSubstitute(idx, mode, delay=false)`/`pbUpdateSubstituteSprite(battler, mode)`;
`Battle::Battler` `airborneOffScreen?`/`vanishedOffScreen?(ignoreAirborne=false)`;
`Battle::Scene::BattlerSprite` `speed` (0-4)/`reversed`/`hue` (-255..255, no sobre Super Shiny).

## Animated Trainer Intros — Sección 7.J
Intros animadas de entrenador (estilo HGSS/Platino/BW). Reemplaza los sprites frontales de todas las
clases de Essentials por versiones gen 4/5 (animadas o redimensionadas), añade muchas clases/personajes
nuevos, sprites de espalda extra y **todos los mugshots** gen 4/5. (Lista completa en el GitBook; es un
pack de assets + animación.)

**Mid-battle Commands:** `"setAnimSpeaker" => Integer|Symbol|Array` (idéntico a `setSpeaker` de
`03-mid-battle-commands.md`, pero el speaker reproduce su **animación de intro** antes de hablar; añade
`:Reversed` como último elemento del array para reproducirla al revés), `"hideAnimSpeaker" => true|:Reversed`
(reproduce la intro antes de salir; `:Reversed` la invierte).
**Hardcode:** `GameData::TrainerType` `trainer_sprite_scale`/`trainer_sprite_hue`/`shows_shadow?`;
`Battle::Scene` `pbShowAnimatedSpeaker(1, nil, reversed=false, id=nil)` (id = índice de battler o trainer
type), `pbHideAnimatedSpeaker(reversed=false)`.

> Para diseño de UI propia fuera de DBK ver [[game-ui-design]]; para los sprites/assets de batalla ver
> [[assets-pipeline]].
