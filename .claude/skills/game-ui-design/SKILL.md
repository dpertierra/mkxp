---
name: game-ui-design
description: >
  Construcción y diseño de UI en Pokémon Essentials / La Base de Sky: patrón Scene/Screen,
  viewports, sprites, dibujo de texto, windowskins, controles MUI (047_UI Elements), utilidades
  de Luka, y principios de diseño visual adaptados a juego (legibilidad, contraste, jerarquía,
  feedback de input, timing de animación en frames). Usar al crear o mejorar pantallas, menús,
  HUDs, overlays o cualquier elemento visual. Triggers: "crea una pantalla/menú/UI", "HUD",
  "mejora el aspecto", "ventana", "interfaz".
---

# UI en Essentials / La Base de Sky

Resolución base: **512×384** (`mkxp.json`), accesible como `::Settings::SCREEN_WIDTH` / `::Settings::SCREEN_HEIGHT` (siempre con `::` inicial). Assets de UI del motor: `Graphics/UI/<Pantalla>/`. Assets de plugin: `Graphics/Plugins/NombrePlugin/`.

## Patrón Scene/Screen (estándar del motor)

```ruby
class MiPlugin_Scene
  LAYOUT = ::MiPlugin::Layout   # alias a nivel de CLASE, nunca dentro de un método

  def pbStartScene
    @viewport = Viewport.new(0, 0, ::Settings::SCREEN_WIDTH, ::Settings::SCREEN_HEIGHT)
    @viewport.z = 99999
    @sprites = {}
    @sprites["bg"] = IconSprite.new(0, 0, @viewport)
    @sprites["bg"].setBitmap(LAYOUT::GFX_PATH + "bg")
    @sprites["overlay"] = BitmapSprite.new(::Settings::SCREEN_WIDTH, ::Settings::SCREEN_HEIGHT, @viewport)
    @overlay = @sprites["overlay"].bitmap
    pbSetSystemFont(@overlay)
    pbDibujar
  end

  def pbMain
    loop do
      Graphics.update
      Input.update
      pbUpdateSpriteHash(@sprites)
      break if Input.trigger?(Input::BACK)
      # navegación: Input::UP/DOWN/LEFT/RIGHT, confirmación: Input::USE
    end
  end

  def pbEndScene
    pbDisposeSpriteHash(@sprites)
    @viewport&.dispose
  end
end

class MiPlugin_Screen
  def initialize(scene) = @scene = scene
  def pbStartScreen
    @scene.pbStartScene
    @scene.pbMain
    @scene.pbEndScene
  end
end
```

Reglas: la escena dibuja y lee input; **toda mutación de datos del juego pasa por Core** (`001_*.rb`). Dispose siempre simétrico al create. Para pantallas estilo v22 existe `048_UI/000_UI_base.rb` como framework alternativo — revisar cómo lo usan `002_UI_PauseMenu.rb` o `020_UI_Options_v22.rb` antes de elegir patrón.

## Dibujo de texto

```ruby
textpos = [
  # [texto, x, y, alineación(0 izq,1 der,2 centro), color_base, color_sombra]
  [_INTL("Título"), LAYOUT.sx(LAYOUT::TITULO_X), LAYOUT.sy(LAYOUT::TITULO_Y), 2, LAYOUT::COLOR_TEXTO, LAYOUT::COLOR_SOMBRA]
]
pbDrawTextPositions(@overlay, textpos)
```

Primitivas en `018_Objects and windows/011_DrawText.rb` (`drawTextEx`, `pbDrawShadowText`, alineaciones, word-wrap). Fuentes del proyecto en `Fonts/` (power clear, power green...); seleccionar con `pbSetSystemFont(bitmap)` / `pbSetSmallFont(bitmap)`. Colores estándar Essentials: texto claro `Color.new(248,248,248)` con sombra `Color.new(104,104,104)` sobre fondos oscuros; texto oscuro `Color.new(64,64,64)` + sombra `Color.new(176,176,176)` sobre claros — pero LBdS define paletas custom en `001_Settings/004_Settings_Extra_Base.rb` (revisar y reutilizar).

## Layout — 000_Settings.rb con escalado

```ruby
module MiPlugin
  module Layout
    BASE_W = 512
    BASE_H = 384
    SCALE_X = Graphics.width.to_f / BASE_W
    SCALE_Y = Graphics.height.to_f / BASE_H
    SCALE   = [SCALE_X, SCALE_Y].min
    def self.sx(val) = (val * SCALE_X).round
    def self.sy(val) = (val * SCALE_Y).round
    def self.s(val)  = (val * SCALE).round

    PANEL_X = 8
    TITULO_X = 256
    TITULO_Y = 16
    FONT_SIZE_NORMAL = 20
    COLOR_TEXTO  = Color.new(248, 248, 248)
    COLOR_SOMBRA = Color.new(104, 104, 104)
    GFX_PATH = "Graphics/Plugins/MiPlugin/"
  end
end
```

Cero números mágicos en el cuerpo de la escena (R9).

## Componentes ya existentes — extender, no reinventar

| Necesitas | Usa |
|-----------|-----|
| Ventana de diálogo/mensajes | `pbMessage`, `pbConfirmMessage`, `pbShowCommands` (`018_.../012_Messages.rb`) |
| Lista de comandos con cursor | `Window_CommandPokemon` y familia (`018_.../004_Window.rb`, `005_SpriteWindow.rb`) |
| Botones, checkboxes, sliders, dropdowns, textboxes | **MUI**: `047_UI Elements/` (`UIControls`, `ControlsContainer`, `ControlElements/*`) — así están hechas las Options v22 |
| Tweening/lerp, paneles, contenedores, mouse preciso | `058_Utilities/003_Luka's Scripting Utilities` (Sprites, UI, MouseInput) |
| Mouse fácil | `058_Utilities/013_Easy Mouse.rb` |
| Iconos de Pokémon/items en UI | `PokemonIconSprite`, `ItemIconSprite` (`044_Pokemon-related/004_Pokemon_Sprites.rb`, `018_.../008_BitmapSprite.rb`) |
| Entrada de texto | `pbEnterText` y pantallas en `048_UI/032_UI_TextEntry.rb` |
| Animar imágenes (GIF, interpolación) | `014_Sprites/011_PictureEx.rb` o Bitmap GIF de MKXP-Z |
| Pantallas modernas de referencia | `069_MUI/*` y `048_UI/009_Better_Summary.rb` (estilo visual LBdS) |

## Principios de diseño (adaptados a UI de juego 512×384)

Reemplazan a la skill web "ui-ux-pro-max" (irrelevante aquí: era CSS/React y además venía corrupta). Lo trasladable:

**Legibilidad (crítico)**
- Texto siempre con sombra/outline sobre fondos variables; contraste alto (claro sobre oscuro o viceversa, nunca gris sobre gris).
- Tamaño de fuente ≥ el estándar de pantallas vecinas del proyecto; nada de texto minúsculo a 512×384.
- Máx ~2 fuentes/tamaños por pantalla; alineaciones consistentes (labels izquierda, valores derecha es el patrón del motor).

**Jerarquía y consistencia**
- Una pantalla = una acción primaria clara. Título arriba, contenido centro, ayudas de control abajo (patrón del motor).
- Reutilizar el windowskin y paleta del proyecto (LBdS tiene UI propia — mirar `Graphics/UI/` y pantallas MUI antes de inventar estilo). Coherencia > originalidad.
- Espaciado en múltiplos consistentes (8px funciona bien a esta resolución); alinear todo a grid.

**Feedback de input (crítico en juego)**
- Todo cambio de selección: SE (`pbSEPlay("GUI sel cursor")`) + cambio visual del cursor/highlight en el mismo frame.
- Confirmar: `"GUI sel decision"`; cancelar: `"GUI sel cancel"`; acción inválida: `"GUI sel buzzer"`.
- Nunca dejar al jugador sin respuesta visual >2-3 frames tras pulsar.

**Animación**
- Duraciones en frames (60fps): micro-feedback 6-12 frames, transiciones 15-25, nunca >30 para acciones repetitivas.
- Animar con interpolación (lerp/tweening de Luka) y no bloquear el input para nada que el jugador repita a menudo; toda animación larga debe ser saltable con USE/BACK.
- Easing: ease-out al entrar, ease-in al salir; nada de movimiento lineal para elementos grandes.

**Estados**
- Cubrir siempre: lista vacía ("No hay X" con mensaje útil), elemento deshabilitado (gris + buzzer al pulsar), overflow de texto (recorte/scroll, sin desbordar el panel), valores extremos (nombres largos, 999 items).

## Checklist UI antes de entregar

- [ ] Resolución vía `::Settings::`; layout completo en `000_Settings.rb`
- [ ] `_INTL` en todo texto visible
- [ ] Dispose simétrico (`pbDisposeSpriteHash` + `@viewport&.dispose`)
- [ ] Sin lógica de negocio en la escena
- [ ] SE de cursor/confirmar/cancelar/buzzer colocados
- [ ] Estados vacío/deshabilitado/overflow cubiertos
- [ ] Estilo coherente con `Graphics/UI/` y pantallas MUI del proyecto
- [ ] Sin crear bitmaps por frame; redibujar solo cuando cambia el estado
