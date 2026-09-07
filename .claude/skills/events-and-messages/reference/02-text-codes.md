# Reference · Códigos de control de texto

Un string de mensaje pasa por **tres** etapas, cada una con su juego de códigos:
1. **Sustitución** — `pbReplaceMessageText` (`018/012_Messages.rb`): reemplaza placeholders por su valor.
2. **Estilo / dibujo** — `getFormattedText` (`018/011_DrawText.rb`): aplica color, negrita, fuente,
   alineación. Usa etiquetas tipo `<...>` (la etapa 1 convierte algunos `\` en estas).
3. **Flujo** — `pbMessageDisplay` (`018/012`): pausas, esperas, sonidos mientras se imprime letra a letra.

Primero `_INTL` interpola sus `{1}`/`{2}`; luego se procesan estos códigos.

## 1. Sustitución (placeholders)

| Código | Reemplazo |
|---|---|
| `\v[n]` | Valor de la variable de juego `n` (`$game_variables[n]`). Anidable. |
| `\pn` | Nombre del jugador (`$player.name`). |
| `\pm` | Dinero del jugador, formateado con símbolo de moneda. |
| `\n[1-8]` | Nombre del "actor" RPG Maker `n` (`$game_actors`). |
| `\b` | Color de **texto masculino** (definido en `MessageConfig`). |
| `\r` | Color de **texto femenino**. |
| `\pg` | Color según género del jugador (masc.→`\b`, fem.→`\r`). |
| `\pog` | Igual que `\pg` pero invertido (el género opuesto). |
| `\@` / `\@a` | Vocal de género en español (masc.→`o`, fem.→`a`). Para concordancia: `list\@` → "listo"/"lista". |
| `\c[n]` | Color del índice `n` del windowskin actual. |
| `\[rrggbbaa]` | Color RGBA por hex de 8 dígitos (se convierte a `<c2=...>`). |
| `\sign[Texto]` | Caja de "letrero" (se expande a `\op\cl\ts[]\w[Texto]`). |
| `\\` | Barra invertida literal. |
| `\n` | Salto de línea. |

```ruby
pbMessage(_INTL("¡Hola, \\pn! Tienes \\v[5] objetos."))   # en Ruby la \ se escapa: \\
```
> Cuidado al escribir desde código Ruby: dentro de `"..."` la barra se escapa (`"\\v[5]"`). En el editor
> de eventos de RPG Maker / Maker Studio se escribe con una sola barra (`\v[5]`).

## 2. Estilo / dibujo (etiquetas `<...>`)

Procesadas por `getFormattedText`. Útiles tanto en mensajes como al dibujar texto en bitmaps de UI
(ver [[game-ui-design]]). Son anidables y se cierran con `</...>`.

| Etiqueta | Efecto |
|---|---|
| `<c=rrggbb>` … `</c>` | Color de texto (RGB). |
| `<c2=rrggbbaa>` | Color con sombra empaquetada (8 hex base + 8 sombra). |
| `<c3=base,sombra>` | Color base y sombra explícitas (lo que generan `\b`/`\r`/`\c[n]`). |
| `<o=opacidad>` | Opacidad del texto. |
| `<b>` `</b>` | Negrita. |
| `<i>` `</i>` | Cursiva. |
| `<u>` `</u>` | Subrayado. |
| `<s>` `</s>` | Tachado. |
| `<outln>` / `<outln2>` | Contorno (dos estilos). |
| `<fs=tamaño>` | Tamaño de fuente. |
| `<fn=nombre>` | Cambia la fuente (si existe; si no, Arial). |
| `<al>` / `<ar>` / `<ac>` | Alinear a izquierda / derecha / centro. |

## 3. Flujo (mientras se imprime)

Procesados por `pbMessageDisplay` letra a letra:

| Código | Efecto |
|---|---|
| `\.` | Pausa breve (≈¼ s). |
| `\|` | Pausa de ≈1 s. |
| `\!` | Espera a que el jugador pulse para continuar. |
| `\^` | Cierra el mensaje sin esperar pulsación al final. |
| `\wt[n]` | Espera `n` "vigésimos" de segundo (n/20 s) con prompt. |
| `\wtnp[n]` | Igual que `\wt` pero **sin** prompt de pausa. |
| `\ts[n]` | Cambia la velocidad de texto para el resto del mensaje. |
| `\se[nombre]` | Reproduce un efecto de sonido (`Audio/SE/...`). |
| `\me[nombre]` | Reproduce una música corta (ME). |
| `\g` | Muestra la ventana de dinero mientras dura el mensaje. |
| `\op` / `\cl` | Abre / cierra la caja de "letrero" (sign). |
| `\w[nombre]` | Cambia el windowskin de la ventana de mensaje. |
| `\ch[...]` | Texto aleatorio entre opciones. |

Otros tokens menores (`\f`, `\ff`, `\cn`, `\pt`, `\wu`/`\wm`/`\wd`, `\sh`) existen en `pbMessageDisplay`;
si necesitas uno concreto, grep su `when "..."` en `018/012_Messages.rb`.

## Ejemplo combinando etapas

```
\pn mira el cartel...\wt[10]\nDice: "<c3=8038f8,302060>¡Cuidado!</c3>"\!
```
Sustituye el nombre, espera medio segundo, salta de línea, escribe "¡Cuidado!" en morado con sombra y
espera pulsación.
