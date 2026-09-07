# Reference · Sistema de mensajes (familia `pbMessage`)

Fuente: `018_Objects and windows/012_Messages.rb`. Todas estas funciones son globales (Kernel): se pueden
llamar desde una caja "Script" de un evento o desde un plugin. Todo texto va con `_INTL` (R1).

## Diálogo y elecciones

```ruby
def pbMessage(message, commands = nil, cmdIfCancel = 0, skin = nil, defaultCmd = 0, &block)
```
- Sin `commands` → muestra el mensaje y espera. Devuelve `0`.
- Con `commands` (Array de strings) → muestra un menú de elección y **devuelve el índice** (0-based) de
  la opción elegida.
- `cmdIfCancel` → índice que se devuelve si el jugador cancela (0 = ninguno especial). Si es `commands.length`
  o un valor fuera de rango "real", se interpreta como cancelar.
- `defaultCmd` → opción preseleccionada.
- `skin` → windowskin alternativo (ruta).

```ruby
eleccion = pbMessage(_INTL("¿Qué eliges?"),
                     [_INTL("Atacar"), _INTL("Huir")], 1)   # cancelar = índice 1 (Huir)
case eleccion
when 0 then pbMessage(_INTL("¡Al ataque!"))
when 1 then pbMessage(_INTL("Huyes."))
end
```

### Confirmaciones (Sí/No)
```ruby
pbConfirmMessage(_INTL("¿Seguro?"))          # → true si elige "Sí" (por defecto). Cancelar = No.
pbConfirmMessageSerious(_INTL("¿Borrar?"))   # → true si elige "Sí", pero el cursor empieza en "No"
```
`pbConfirmMessage` = `pbMessage(msg, ["Sí","No"], 2) == 0`. `pbConfirmMessageSerious` invierte el orden
(`["No","Sí"]`, default "No") para decisiones peligrosas.

### Con ayuda
```ruby
def pbMessageWithHelp(message, help, commands = nil, cmdIfCancel = 0, skin = nil, defaultCmd = 0, lines = 2, &block)
```
Como `pbMessage` pero muestra una ventana de ayuda contextual por opción (`help` = Array paralelo a
`commands`).

### Bloque para acciones por opción
Todas aceptan un `&block` que se llama con el índice resaltado mientras navegas (para previsualizar):
```ruby
pbMessage(_INTL("Elige un color:"), colores, 0) { |i| actualiza_preview(i) }
```

## Elegir un número

```ruby
def pbMessageChooseNumber(message, params, &block)
```
`params` es un objeto `ChooseNumberParams`. Configúralo con:
- `setRange(min, max)` — rango permitido.
- `setDefaultValue(n)` / `setInitialValue(n)` — valor por defecto / inicial mostrado.
- `setCancelValue(n)` — valor devuelto al cancelar.
- `setMaxDigits(n)` — nº de dígitos.
- `setNegativesAllowed(bool)` — permitir negativos.
- `setMessageSkin(skin)` / `setSkin(skin)` — windowskin.

```ruby
params = ChooseNumberParams.new
params.setRange(1, 99)
params.setDefaultValue(1)
cantidad = pbMessageChooseNumber(_INTL("¿Cuántos?"), params)
```

## Entrada de texto libre

```ruby
def pbMessageFreeText(message, currenttext, passwordbox, maxlength, width = 240, &block)
```
Pide una cadena al jugador (`passwordbox = true` la oculta). Devuelve el texto introducido. Para nombrar
Pokémon/jugador hay helpers de más alto nivel (`pbEnterPokemonName`, `pbEnterPlayerName` — ver
`048_UI/032_UI_TextEntry.rb`).

## Listas seleccionables largas

Para una lista scrollable (más larga que un menú de comandos junto al mensaje) usa `pbChooseList` /
pantallas de lista. Si necesitas algo más rico (iconos, columnas, filtros) ya es **UI propia** →
[[game-ui-design]]. Grep `def pbChooseList` para la firma exacta de tu versión.

## Ventanas auxiliares

Durante un mensaje puedes mostrar contadores:
- `pbDisplayGoldWindow(msgwindow)` — dinero del jugador (también con el código `\g`, ver
  `02-text-codes.md`).
- `pbDisplayCoinsWindow(msgwindow, goldwindow)` — fichas del casino.
- `pbDisplayBattlePointsWindow(msgwindow)` — Puntos de Batalla.

## Bajo nivel (control manual de la ventana)

Normalmente no hace falta, pero existe:
- `pbCreateMessageWindow(viewport = nil, skin = nil, lines = 2)` → crea la ventana.
- `pbMessageDisplay(msgwindow, message, letterbyletter = true, commandProc = nil)` → pinta el texto
  (aquí se procesan los códigos de flujo, ver `02-text-codes.md`).
- `pbDisposeMessageWindow(msgwindow)` → la destruye.

Úsalo solo si necesitas mantener una ventana abierta mientras ocurre otra cosa; para diálogo normal,
`pbMessage` ya crea/destruye su ventana.
