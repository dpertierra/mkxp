---
name: assets-pipeline
description: >
  Rutas, nombres y formatos de los assets de La Base de Sky: estructura completa de Graphics/
  (Pokémon, items, trainers, characters, UI, animaciones de batalla, tilesets, autotiles) y
  Audio/ (BGM, BGS, ME, SE, cries), convenciones de nombres por especie/forma/shiny, tamaños,
  y dónde colocar assets de plugins. Usar al añadir o referenciar cualquier gráfico o sonido.
  Triggers: "sprite", "icono", "imagen", "música", "sonido", "grito", "cry", "tileset", "asset".
---

# Assets — Graphics/ y Audio/ de La Base de Sky

Raíz: `..\LA BASE DE SKY\`. El motor resuelve rutas SIN extensión (`pbResolveBitmap`, `005_Files/002_FileTests.rb`); en código se referencia `"Graphics/Plugins/MiPlugin/fondo"` y el motor encuentra el .png. Filesystem case-sensitive en Linux/macOS → respetar mayúsculas exactas.

## Graphics/ — estructura verificada

| Carpeta | Contenido / convención |
|---------|------------------------|
| `Pokemon/Front/`, `Back/` | Battlers: `ESPECIE.png` (ej. `BULBASAUR.png`); forma alternativa: `ESPECIE_<forma>.png` (ej. `ROTOM_1.png`); género: sufijo `_female` cuando aplica |
| `Pokemon/Front shiny/`, `Back shiny/` | Mismos nombres, versión shiny (carpeta separada, no sufijo) |
| `Pokemon/Icons/`, `Icons shiny/` | Iconos de party/PC: `ESPECIE.png`, strip horizontal de 2 frames |
| `Pokemon/Eggs/` | Sprites de huevo (`ESPECIE` con override, default genérico) |
| `Pokemon/Footprints/` | Huellas para la Pokédex |
| `Pokemon/Shadow/` | Sombras bajo el battler |
| `Items/` | `SIMBOLO.png` **48×48** (ej. `POTION.png`); `000.png` = fallback |
| `Trainers/` | `TRAINERTYPE.png` battlers de entrenador (front); versiones por símbolo de trainer_types.txt |
| `Characters/` | Charsets de overworld (4 direcciones × 4 frames): NPCs (`NPC 01.png`...), `Followers/` y `Followers shiny/` (Pokémon siguiendo: `ESPECIE.png`), charsets del jugador (definidos en metadata.txt: WalkCharset, RunCharset, SurfCharset...) |
| `UI/` | Una subcarpeta por pantalla: `Bag/`, `Party/`, `Pokedex/`, `Save/`, `Load/`, `Options/`, `Phone/`, `Mart/`, `Battle/`, `Naming/`, etc. + custom LBdS: `Enhanced Pokemon UI/`, `Pokedex Data Page/`, `EncounterUI/`, `BetterMoveSummary/`... Hojas compartidas tipo `types.png`, `ribbons.png` también viven aquí |
| `Plugins/` | **Assets de plugins**: `Graphics/Plugins/NombrePlugin/...` — toda imagen de un plugin `[ZBOX]` va aquí, nunca suelta en las carpetas del motor |
| `Battle animations/` | Partículas y hojas del editor de animaciones; pokéballs: `ball_SIMBOLO.png` + `ball_SIMBOLO_open.png`; bursts: `ballBurst_*.png` |
| `Battlebacks/` | Fondos de batalla: `<entorno>_bg.png`, `_base0/1.png`, `_message.png` (enlazados por BattleBack de map_metadata y entorno) |
| `Battlers/` | Battlers genéricos no-Pokémon |
| `Tilesets/`, `Autotiles/` | Gráficos de mapa (registrados vía RMXP/Data, no renombrar sin actualizar Data) |
| `Animations/` | Animaciones RPG Maker clásicas |
| `Pictures/` | Imágenes para eventos (`Show Picture`) y escenas |
| `Transitions/` | Imágenes de transición de batalla/mapa |
| `Titles/`, `Gameovers/` | Pantalla de título y game over |
| `Windowskins/` | Skins de ventanas (seleccionables en Opciones) |
| `Fogs/`, `Panoramas/` | Capas de mapa |
| `Weather/` | Partículas de clima |
| `Icons/` | Iconos misc del juego/ventana |

### Reglas para gráficos nuevos

1. PNG salvo necesidad concreta (MKXP-Z también lee GIF animado — útil para efectos).
2. Tamaños coherentes con los vecinos: items 48×48; iconos Pokémon = strip 2 frames (mirar uno existente de la misma carpeta y copiar dimensiones).
3. Battlers de especies nuevas: Front + Back + shiny de ambos + icono (+ female si dimorfismo). El locator es `026_PBS data/010_Species_files.rb` — ahí están las rutas/sufijos exactos que el motor intenta.
4. Texturas grandes ≤2048px por lado (límite GPU, skill mkxp-z-runtime).
5. Plugin = su propia carpeta bajo `Graphics/Plugins/`; ruta en constante `GFX_PATH` del `000_Settings.rb`.

## Audio/ — estructura verificada

| Carpeta | Contenido | Uso en código |
|---------|-----------|---------------|
| `BGM/` | Música de fondo | `pbBGMPlay("nombre")`, mapas vía Data/metadata, `Audio.bgm_play` multipista (MKXP-Z) |
| `BGS/` | Sonido ambiental en loop | `pbBGSPlay` |
| `ME/` | Efectos musicales cortos (victoria, captura, curación) | `pbMEPlay` |
| `SE/` | Efectos de sonido: UI (`GUI sel decision`, `GUI sel cursor`, `GUI sel cancel`, `GUI sel buzzer`...), batalla (`Battle ball shake`, `Battle damage super`...), mundo (`Door enter`...), subcarpeta `Anim/` (animaciones de batalla) | `pbSEPlay("GUI sel decision")` |
| `SE/Cries/` | **Gritos**: `ESPECIE.ogg`, formas: `ESPECIE_<forma>.ogg` (ej. `ABSOL_1.ogg`) | `pbCryPlay`/`GameData::Species.cry_filename...` (ver `020_Audio/003_Audio_Play.rb`) |

Formatos soportados (OpenAL): **OGG (preferido)**, MP3, WAV, MIDI, FLAC. Nombres con espacios permitidos (el motor los usa); en código, sin extensión. Volumen/pitch como args: `pbSEPlay("nombre", volumen, pitch)`.

### Reglas para audio nuevo

1. OGG para todo lo nuevo (loop limpio en BGM/BGS con tags LOOPSTART/LOOPLENGTH si hace falta loop interno).
2. SEs de UI nuevos: nombrar siguiendo el patrón existente (`GUI mi accion.ogg`) y referenciarlos desde constantes del plugin, no strings repetidas.
3. Cries de especies nuevas: `SE/Cries/ESPECIE.ogg` exacto al símbolo PBS.

## Fonts/

`power clear.ttf` / `bold`, `power green*.ttf`, `power red*.ttf`, `liberation.ttf`. Se seleccionan vía `MessageConfig` / `pbSetSystemFont`. Añadir fuente nueva = colocar .ttf + registrar nombre en MessageConfig (pedir confirmación: afecta global).

## Comprobación de existencia en código

```ruby
bitmap_path = pbResolveBitmap("Graphics/Plugins/MiPlugin/icono")
return if bitmap_path.nil?    # asset ausente → degradar con elegancia, no crashear
```

Para audio: `pbResolveAudioSE` y helpers de `005_Files/002_FileTests.rb`. Un plugin nunca debe crashear por asset faltante: log + fallback.
