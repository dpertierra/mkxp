---
name: events-and-messages
description: >
  API de scripting que se usa DENTRO de los eventos del mapa y desde plugins en Pokémon Essentials 21.1
  / La Base de Sky: el sistema de mensajes (pbMessage, pbConfirmMessage, pbChooseNumber, pbChooseList,
  entrada de texto), el catálogo COMPLETO de códigos de control de texto (\c[], \v[], \pn, \b/\r, \wt[],
  \., \|, \!, estilos), el Interpreter y los script-calls (get_character, pbMoveRoute, pbWait, switches/
  variables/self-switches, common events) y las extensiones custom de eventos por nombre/comentario de
  Zik (s:Hitbox, s:Float, s:pokemon_event…). Para diálogos de NPC, elecciones, cutscenes y lógica de
  evento. Extraído 1:1 de 018_Objects and windows, 009_Game processing y 037_Overworld. Para construir
  UNA pantalla/HUD propia ver [[game-ui-design]]; para menús/opciones ver [[menu-and-options]]; para dar
  Pokémon/items ver [[pokemon-api]]. Triggers: "pbMessage", "diálogo", "NPC", "cutscene", "elección",
  "choice", "código de color del texto", "\c[", "ruta de movimiento", "switch", "variable", "common
  event", "evento que".
---

# Eventos y mensajes

Fuentes (verificar firmas reales — ver [[essentials-codemap]]):
`018_Objects and windows/012_Messages.rb` (familia pbMessage), `011_DrawText.rb` + `006_SpriteWindow_text.rb`
(formato y códigos de control), `009_Game processing/004_Interpreter.rb` + `005_Interpreter_Commands.rb`
(Interpreter / comandos), `037_Overworld/002_Overworld.rb` (helpers de movimiento) y
`037_Overworld/010_Events Utilities.rb` (custom Zik: extensiones por nombre/comentario).

Esta skill cubre lo que va **dentro** de un evento (cajas "Script", comandos) y la API de mensajes desde
código. Maker Studio edita los eventos visualmente ([[maker-studio]]); aquí está el API que se llama.

## Regla de oro: texto y datos del juego

- **Todo** texto visible al jugador con `_INTL("...")` e interpolación `{1}`, `{2}` — nunca `#{}` dentro
  de `_INTL` (R1). `_INTL("Hola, {1}.", $player.name)`.
- Switches y variables **siempre por constante descriptiva** del plugin, nunca el literal numérico (R12).
  En eventos visuales se usan por número; desde plugin, define la constante en `Settings`.

## Mensajes (lo más usado)

```ruby
pbMessage(_INTL("¡Hola, {1}!", $player.name))                     # diálogo simple
if pbConfirmMessage(_INTL("¿Quieres continuar?"))                 # Sí/No → true si "Sí"
  pbMessage(_INTL("¡Adelante!"))
end
eleccion = pbMessage(_INTL("Elige:"),
                     [_INTL("Rojo"), _INTL("Azul"), _INTL("Verde")], 0)  # → índice (0,1,2)
```

Familia completa, parámetros, caras, ventanas de dinero/monedas y entrada de texto/número:
`reference/01-messages.md`.

## Códigos de control de texto

Dentro de cualquier string de mensaje puedes insertar códigos: color (`\c[n]`), nombre del jugador
(`\pn`), variable (`\v[n]`), pausas (`\.`, `\|`, `\!`), esperas (`\wt[n]`), sonidos (`\se[name]`),
estilos (`\b`/`\i`/`\fs[n]`…). Catálogo completo (sustitución, estilo y flujo) en `reference/02-text-codes.md`.

## Interpreter, script-calls y switches/variables

En una caja "Script" de un evento (o desde un plugin que controla un evento) tienes acceso a helpers como
`get_character`, `pbMoveRoute`, `pbWait`, manejo de switches/variables/self-switches y common events. Más
las **extensiones custom** de Zik que amplían qué hace un evento mediante su nombre o comentarios
(`s:Hitbox`, `s:Float`, `s:pokemon_event/...`). Todo en `reference/03-interpreter-and-script-calls.md`.

## Recetas rápidas

```ruby
# NPC que pregunta y da un objeto:
if pbConfirmMessage(_INTL("¿Quieres una Poké Ball?"))
  if pbReceiveItem(:POKEBALL)   # gestiona mochila llena, mensaje incluido
    # entregado
  end
end

# Dar un Pokémon (ver [[pokemon-api]] para construirlo a medida):
pbAddPokemon(:PIKACHU, 5)

# Lanzar un combate de entrenador desde un evento (ver [[battles-runtime]]):
TrainerBattle.start(:CAMPER, "Dani")
```
Construir Pokémon a medida → [[pokemon-api]]; combates y reglas → [[battles-runtime]]; definir
items/trainers en PBS → [[pbs-data]].

## Reference

| Archivo | Cubre |
|---|---|
| `reference/01-messages.md` | Familia `pbMessage` completa: diálogo, elecciones, números, texto libre, ventanas auxiliares |
| `reference/02-text-codes.md` | Códigos de control: sustitución (`\v`, `\pn`…), estilo (`\c`, `\b`, `\fs`…), flujo (`\.`, `\wt`…) |
| `reference/03-interpreter-and-script-calls.md` | Interpreter, script-calls, switches/variables/self-switches, common events, custom de eventos (Zik) |

Skills relacionadas: [[game-ui-design]] (pantallas/HUD propios), [[menu-and-options]] (menús y opciones),
[[plugin-architecture]] (empaquetar lógica de evento en un plugin), [[pokemon-api]] (dar/editar Pokémon),
[[maker-studio]] (editor visual de eventos).
