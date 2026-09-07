---
name: mkxp-z-runtime
description: >
  Runtime MKXP-Z que ejecuta La Base de Sky: API extendida sobre RGSS (Graphics, Input raw/mouse/
  controller, Bitmap GPU/GIF, Sprite wave/pattern, Audio multipista, System, CFG, HTTPLite, path
  cache), diferencias vs RPG Maker XP original, mkxp.json y rendimiento. Usar al tocar input crudo,
  ratón, bitmaps pesados, audio avanzado, HTTP, config del motor u optimización. Triggers: "mouse",
  "tecla", "FPS", "lag", "screenshot", "resolución", "mkxp.json", "HTTP", "rendimiento".
---

# MKXP-Z — runtime de La Base de Sky

Reimplementación open-source del RGSS: SDL2 + OpenGL, OpenAL para audio, **CRuby 3.1+**. Docs completas: `..\MKXP-Z\` (índice con tabla "Uso para Agentes" en `index.md`). Config del proyecto: `..\LA BASE DE SKY\mkxp.json` (512×384, título "La Base De Sky 1.2.1.1").

## Diferencias clave vs RGSS original

| Aspecto | RGSS original | MKXP-Z |
|---------|---------------|--------|
| Ruby | 1.8.x | 3.1+ (kwargs, pattern matching, `&.`, stdlib moderna, Threads) |
| Gráficos | DirectX (solo Windows) | SDL2 + OpenGL / Metal (Win/macOS/Linux) |
| Fuentes | GDI+ | SDL_ttf — `Bitmap#text_size` puede variar ligeramente |
| Audio | DirectSound | OpenAL — OGG, MP3, WAV, MIDI, **FLAC** |
| Win32API | Nativo | Emulado vía MiniFFI — no todas las funciones existen |
| Filesystem | Case-insensitive | Case-sensitive en Linux/macOS; PhysicsFS + path cache |
| Datos de Bitmap | RAM | **GPU** — modificar píxeles es costoso; `raw_data` para masivo |
| Sombra de fuente | Drop shadow | Se renderiza como **outline** |

## API extendida (la más usada en plugins)

### Graphics
```ruby
Graphics.resize_screen(w, h)     # cambia resolución interna en runtime (viewports/bitmaps NO se redimensionan solos)
Graphics.screenshot("ruta.png")  # PNG/JPEG/BMP según extensión
Graphics.delta                   # segundos desde el último frame (~0.016 a 60fps)
Graphics.average_frame_rate      # FPS real (perfilado)
Graphics.fullscreen              # get/set
Graphics.scale                   # factor de escala (read-only)
Graphics.play_movie("v.ogv", volume, skip)  # OGV YUV420p obligatorio
```

### Input (raw, mouse, texto, mando)
```ruby
Input.pressex?(:RETURN)  /  Input.triggerex?(0x01)  /  Input.repeatex?(:BACKSPACE)
Input.mouse_x ; Input.mouse_y ; Input.mouse_in_window?
Input.press?(Input::MOUSELEFT) ; Input.scroll_v
Input.text_input = true ; texto = Input.gets ; Input.clipboard
Input::Controller.connected? ; Input::Controller.pressex?(:START) ; Input::Controller.axes_left
```

Mapeo lógico de Essentials (en `003_Technical/005_Input.rb`): `USE`=C, `BACK`=X, `ACTION`=Z, `JUMPUP`=A, `JUMPDOWN`=S, `SPECIAL`=D, `AUX1`=Q, `AUX2`=W. En código de juego preferir `Input.trigger?(Input::USE)` (lógico) sobre teclas raw; raw solo para features que lo exijan. El proyecto ya trae mouse listo: `058_Utilities/013_Easy Mouse.rb` y `007_MouseInput.rb` de Luka — revisar antes de reinventar.

### Bitmap
```ruby
Bitmap.max_size ; bitmap.mega?            # límites GPU
bitmap.to_file("ruta.png")
bitmap.raw_data / bitmap.raw_data = str   # RGBA plano, len = w*h*4 — única vía sana para píxel-masivo
b = Bitmap.new("anim.gif") ; b.play if b.animated? ; b.frame_rate = 24.0 ; b.goto_and_stop(5)
```

### Sprite
```ruby
sprite.wave_amp/wave_length/wave_speed/wave_phase   # ondulación
sprite.pattern = Bitmap.new("p.png") ; sprite.pattern_blend_type ; sprite.pattern_opacity ; sprite.pattern_tile
sprite.invert = true
```

### System / CFG / Path cache
```ruby
System.platform ; System.is_windows? ; System.is_wine?
System.data_directory     # %APPDATA% etc. — ahí vive el save (SaveData::FILE_PATH lo usa)
System.user_language ; System.uptime ; System.launch("https://...")
CFG["miCustomSetting"] = "valor"   # persiste en el mkxp.json del usuario; alternativa ligera a Marshal para opciones
System.mount("mods/pack1.zip") ; System.unmount(...) ; System.reload_cache ; System.desensitize(path)
```

### HTTPLite
```ruby
r = HTTPLite.get(url)                         # r[:status], r[:body], r[:headers]
HTTPLite.post(url, {"k" => "v"})
HTTPLite.post_body(url, HTTPLite::JSON.stringify(obj), "application/json")
HTTPLite::JSON.parse(str)
```
HTTP siempre con rescue + timeout razonable; nunca bloquear el frame loop con peticiones síncronas en gameplay (el motor ya tiene wrappers en `005_Files/004_HTTP_Utilities.rb` y `058_Utilities/006_Networking.rb`).

### Audio
```ruby
Audio.bgm_play("pista.ogg", 100, 100, 0)      # firma estándar
Audio.bgm_play("amb.ogg", 80, 100, 0, 1)      # track 1 simultáneo (requiere BGMTrackCount en mkxp.json)
Audio.bgm_set_volume(60, 1) ; Audio.bgm_volume(1)
```

### Consola / logging
```ruby
Console.echoln("msg") ; Console.echoln_li("msg con timestamp")
PBDebug.log("msg")                 # → errorlog.txt (sistema Essentials)
load_data("archivo.txt", false)    # raw sin Marshal
```
Logs post-crash del usuario: `mkxp.log` (motor) y `errorlog.txt` (Essentials). El agente no ejecuta el juego: pedir estos archivos al usuario cuando reporte un crash.

## Rendimiento — reglas de oro

- Minimizar creación/destrucción de Bitmaps; **reusar**. Cachear valores que no cambian por frame (colores, strings, posiciones).
- `sprite.visible = false` en vez de moverlo fuera de pantalla.
- Menos viewports = menos overhead. Agrupar `draw_text` en menos llamadas.
- Nada de `get_pixel`/`set_pixel` por frame → `raw_data`.
- Texturas ≤ 2048×2048 (verificar `Bitmap.max_size`); dividir imágenes grandes.
- Menos tamaños de fuente distintos = mejor caché de glyphs.
- En `:on_frame_update` solo trabajo O(pequeño); diferir lo pesado.
- Perfilado: `Graphics.average_frame_rate`, `Graphics.delta`, `System.uptime` (precisión µs).

## mkxp.json — opciones relevantes

`defScreenW/defScreenH` (512×384 en este proyecto) · `smoothScaling` · `integerScaling` (pixel-perfect, más rápido) · `fixedAspectRatio` · `fullscreen` · `fixedFramerate` · `frameSkip` · `syncToRefreshrate` · `solidFonts` · `SE.sourceCount` · `BGMTrackCount` · `enableHires` · `enableBlitting` · `subImageFix` · `pathCache`.

Es JSON con comentarios `//`; backslashes escapados o forward slashes. Cambios de runtime por-usuario vía `CFG[...]`, no editando el archivo base del juego (salvo que el usuario lo pida).

## Docs de referencia

| Tema | Archivo en `..\MKXP-Z\` |
|------|------------------------|
| System, CFG, Win32API-FFI, HTTPLite, SteamLite, stdlib | `Extensions-(New).md` |
| Bitmap, Sprite, Table, Color, Tone, Rect, Font, Tilemap | `Extensions-(RGSS,-Classes).md` |
| Graphics, Input, Audio extendidos | `Extensions-(RGSS,-Modules).md` |
| Escalado xBRZ/bicúbico, mipmaps, hires | `Graphical-Enhancements.md` |
| Blitting, path cache, vsync, MJIT/YJIT | `Performance-Tips.md` |
