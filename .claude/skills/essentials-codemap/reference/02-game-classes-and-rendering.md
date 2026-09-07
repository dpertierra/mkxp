# Reference · Clases de juego y renderizado

Carpetas: `011_Game classes`, `014_Sprites`, `016_Map renderer`, `018_Objects and windows`.

## 011_Game classes
Clases RGSS del juego (estado lógico del mapa y sus actores). Heredan/extienden las de RPG Maker XP.

### 002_Game_Screen.rb
- **Core:** estado visual de pantalla (tinte, clima de overworld, flash, shake, pictures).
- **Encontrarás:** el control del tono de pantalla, el tipo de clima activo y los efectos de cámara.
- **Consulta si:** clima de mapa, tintes día-noche, flash, temblor de pantalla.

### 003_Game_System.rb
- **Core:** estado del sistema (BGM/BGS actuales, timers, flags de menú).
- **Encontrarás:** la pila de música, el temporizador y banderas de habilitación de menú/encuentros/guardado.
- **Consulta si:** pausar/reanudar música, deshabilitar el menú/encuentros, el cronómetro.

### 004_Game_Picture.rb
- **Core:** imágenes mostradas con comandos de evento ("Mostrar imagen").
- **Encontrarás:** la lógica de posición/zoom/tono/opacidad de pictures.
- **Consulta si:** manipular pictures de evento desde código.

### 005_Game_Map.rb
- **Core:** estado lógico del mapa (tiles, pasabilidad, terrain tags, eventos).
- **Encontrarás:** colisiones, consulta de terrain tag, metadata del mapa, lista de eventos.
- **Consulta si:** pasabilidad, terrain tags, datos del mapa actual, scroll.

### 006_Game_MapFactory.rb
- **Core:** mapas conectados (multi-mapa contiguo).
- **Encontrarás:** la gestión de varios mapas unidos por conexiones.
- **Consulta si:** transiciones entre mapas conectados o coordenadas entre mapas.

### 007_Game_Character.rb
- **Core:** clase base de movimiento de actores del mapa.
- **Encontrarás:** la lógica común de movimiento/orientación de jugador, eventos y seguidores.
- **Consulta si:** comportamiento de movimiento genérico de cualquier personaje del mapa.

### 008_Game_Event.rb
- **Core:** eventos del mapa.
- **Encontrarás:** la lógica de páginas, triggers y comandos de un evento.
- **Consulta si:** cómo se comporta un evento, sus triggers o su charset.

### 009_Game_Player.rb
- **Core:** el jugador en el mapa (`$game_player`).
- **Encontrarás:** input de movimiento, correr/bici/surf, detección de triggers (entrenadores, encuentros), terrain del jugador.
- **Consulta si:** movimiento del jugador, surf/bici, detectar lo que tiene delante, disparar encuentros.

### 010_Game_CommonEvent.rb
- **Core:** eventos comunes (Common Events).
- **Encontrarás:** la ejecución de eventos comunes en paralelo/automáticos.
- **Consulta si:** un Common Event corre en bucle/paralelo.

### 011_Game_Follower.rb
- **Core:** Pokémon/NPC seguidores.
- **Encontrarás:** la lógica de los seguidores que caminan tras el jugador.
- **Consulta si:** sistema de seguidores (comportamiento, aparición).

### 012_Game_FollowerFactory.rb
- **Core:** creación/gestión de seguidores.
- **Encontrarás:** la fábrica que instancia y mantiene la cola de seguidores.
- **Consulta si:** añadir/quitar seguidores o cambiar cómo se generan.

### 013_Game_Stats.rb
- **Core:** estadísticas de la partida (`$stats`).
- **Encontrarás:** los contadores de juego (pasos, combates ganados, capturas, tiempos…).
- **Consulta si:** leer/incrementar una estadística de la partida.

## 014_Sprites
Sprites visuales del mapa (representación gráfica de lo anterior).

### 002_Sprite_Picture.rb
- **Core:** sprite que dibuja un `Game_Picture`.
- **Encontrarás:** la sincronización imagen-de-evento ↔ sprite.
- **Consulta si:** la representación visual de pictures.

### 003_Sprite_Timer.rb
- **Core:** sprite del temporizador en pantalla.
- **Encontrarás:** el dibujado del cronómetro.
- **Consulta si:** mostrar/estilizar el timer.

### 004_Sprite_Character.rb
- **Core:** sprite de un personaje del mapa (jugador/evento/seguidor).
- **Encontrarás:** recorte del charset, animación de paso, manejo de hierba alta/bush.
- **Consulta si:** cómo se dibuja un personaje, animaciones de caminar, efectos de hierba.

### 005_Sprite_Reflection.rb
- **Core:** reflejos en agua.
- **Encontrarás:** la generación del reflejo de personajes sobre tiles de agua.
- **Consulta si:** reflejos de personajes.

### 006_Sprite_SurfBase.rb
- **Core:** base visual al surfear/bucear.
- **Encontrarás:** el sprite sobre el que va el jugador surfeando.
- **Consulta si:** apariencia de surf/buceo.

### 007_Spriteset_Global.rb
- **Core:** sprites globales persistentes entre mapas.
- **Encontrarás:** los sprites que no dependen de un mapa concreto.
- **Consulta si:** elementos visuales globales del overworld.

### 008_Spriteset_Map.rb
- **Core:** conjunto de sprites del mapa actual.
- **Encontrarás:** el ensamblado de tilemap + personajes + clima + pictures + panorama de un mapa.
- **Consulta si:** entender qué se dibuja en el mapa y en qué orden.

### 009_Sprite_AnimationSprite.rb
- **Core:** reproducción de animaciones RPG Maker en el overworld.
- **Encontrarás:** el reproductor de animaciones de batalla/efecto en el mapa.
- **Consulta si:** lanzar una animación sobre un personaje/tile.

### 010_Sprite_DynamicShadows.rb
- **Core:** sombras dinámicas de personajes.
- **Encontrarás:** la generación de sombras bajo los actores del mapa.
- **Consulta si:** sombras del overworld (ver también `038_Overworld visuals`).

### 011_PictureEx.rb
- **Core:** pictures extendidas (animación tipo GIF, tweening).
- **Encontrarás:** soporte de movimiento/zoom/rotación interpolados para pictures.
- **Consulta si:** animar una picture con keyframes.

### 012_ScreenPosHelper.rb
- **Core:** conversión coordenadas mapa→pantalla.
- **Encontrarás:** helpers que calculan la posición en pantalla de un tile/personaje teniendo en cuenta zoom y scroll.
- **Consulta si:** posicionar algo sobre un personaje/tile en pantalla.

## 016_Map renderer
Motor de dibujado de tiles (custom, soporta extras de Maker Studio).

### 002_TilemapRenderer.rb
- **Core:** renderer del tilemap (dibuja capas, autotiles, prioridades Z).
- **Encontrarás:** el algoritmo que pinta el mapa por capas y resuelve prioridades.
- **Consulta si:** cómo se renderiza el mapa, capas extendidas, prioridad de tiles (ver skill `maker-studio-plugin`).

### 003_TilesetWrapper.rb
- **Core:** acceso a los datos de un tileset.
- **Encontrarás:** envoltura para consultar pasabilidad/prioridad/terrain de tiles.
- **Consulta si:** leer propiedades de un tileset.

### 004_AutotileExpander.rb
- **Core:** expansión de autotiles.
- **Encontrarás:** la generación de las variantes de un autotile según vecinos.
- **Consulta si:** problemas con autotiles o autotiles con nombre.

### 005_TileDrawingHelper.rb
- **Core:** primitivas de dibujado de tiles.
- **Encontrarás:** helpers para pintar tiles individuales (usado por renderer y dungeons).
- **Consulta si:** dibujar tiles manualmente.

## 018_Objects and windows
Ventanas, mensajes, texto y caché de bitmaps.

### 002_RPG_Cache.rb
- **Core:** **caché de bitmaps** (`RPG::Cache`).
- **Encontrarás:** la carga cacheada de gráficos por ruta.
- **Consulta si:** cargar un gráfico de forma eficiente o entender por qué un asset queda cacheado.

### 003_MessageConfig.rb
- **Core:** configuración de mensajes (fuentes, colores `\c[N]`, windowskins).
- **Encontrarás:** definición de tipografías, paletas de color de texto y skins de ventana.
- **Consulta si:** cambiar fuente/color de texto o el windowskin global.

### 004_Window.rb
- **Core:** clase base de ventana.
- **Encontrarás:** la ventana base sobre la que se construyen las demás.
- **Consulta si:** crear una ventana desde cero a bajo nivel.

### 005_SpriteWindow.rb
- **Core:** ventana basada en sprites (la usada por casi todo).
- **Encontrarás:** la implementación de ventanas con viewport/cursor.
- **Consulta si:** ventanas de selección/comando.

### 006_SpriteWindow_text.rb
- **Core:** ventana de texto/diálogo principal.
- **Encontrarás:** el parseo de códigos de control (`\v`, `\c`, `\w`…) y el renderizado de diálogo.
- **Consulta si:** cómo se muestra y formatea el texto de los mensajes.

### 007_SpriteWindow_pictures.rb
- **Core:** ventanas que muestran imágenes.
- **Encontrarás:** ventanas especializadas en bitmaps/iconos.
- **Consulta si:** una ventana que pinta una imagen.

### 008_BitmapSprite.rb
- **Core:** sprite con un bitmap propio para dibujar encima.
- **Encontrarás:** la base para "lienzos" sobre los que pintar texto/figuras.
- **Consulta si:** necesitas un sprite-lienzo para dibujar (muy usado en UI).

### 009_AnimatedBitmap.rb
- **Core:** bitmap animado (multi-frame).
- **Encontrarás:** el contenedor de bitmaps con animación por frames.
- **Consulta si:** gráficos animados (iconos de Pokémon, sprites con frames).

### 010_Planes.rb
- **Core:** planos (fondos con scroll/tiling).
- **Encontrarás:** la clase de plano para panoramas/fogs con desplazamiento.
- **Consulta si:** fondos repetidos con scroll.

### 011_DrawText.rb
- **Core:** **primitivas de dibujado de texto** (`pbDrawTextPositions` y similares).
- **Encontrarás:** las funciones de bajo nivel para pintar texto con sombra/alineación sobre un bitmap.
- **Consulta si:** dibujar texto en cualquier UI (ver skill `game-ui-design`).

### 012_Messages.rb
- **Core:** flujo de diálogos (`pbMessage` y familia).
- **Encontrarás:** la maquinaria que muestra mensajes, elecciones y entradas numéricas.
- **Consulta si:** mostrar un mensaje/elección desde código.

### 013_TextEntry.rb
- **Core:** entrada de texto (teclado en pantalla).
- **Encontrarás:** la lógica de introducir texto (nombres, búsquedas).
- **Consulta si:** pedir texto al jugador.

### 014_NameBox.rb
- **Core:** caja de nombre del hablante en diálogos.
- **Encontrarás:** el recuadro con el nombre del personaje que habla.
- **Consulta si:** mostrar/estilizar el nombre del hablante.
