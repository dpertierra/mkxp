# Plan: portar MKXP-Z para Nintendo 3DS

## Objetivo

Crear una versión homebrew de MKXP-Z que ejecute La Base de Sky con el mínimo de cambios al juego.

**Alcance inicial:** New Nintendo 3DS, pantalla superior y juego sin funciones online. Old 3DS, uso de la pantalla inferior, HTTPS, Steam, video y compatibilidad general con todos los juegos MKXP-Z quedan fuera de la primera etapa.

La meta es conservar las APIs que Essentials necesita para arrancar, jugar y guardar; no reescribir el juego.

## Hechos revisados

- El repo es MKXP-Z 2.4.2; el build contempla Windows y Linux, no 3DS.
- El motor depende de SDL2, OpenGL, OpenAL, PhysFS, FreeType, SDL_ttf, SDL_image, Vorbis, Theora y otras bibliotecas.
- La configuración del build usa MRI 3.1 como versión predeterminada.
- La base incluye `Data/Ruby Library 3.3.0`, usa Essentials 21.1 y declara resolución lógica 512×384.
- Scripts de la base usan APIs MKXP-Z como `System.uptime`, `System.data_directory` y `HTTPLite`.

**Pendiente de confirmar:** la versión de Ruby enlazada por el ejecutable usado con la base y si Essentials/plugins requieren extensiones nativas concretas. La presencia de la biblioteca Ruby 3.3 no prueba por sí sola que MKXP-Z necesite MRI 3.3.

## Fase 0 — Definir alcance y preparar el entorno

1. Elegir New 3DS como hardware inicial.
2. Fijar tag/revisión de MKXP-Z y toolchain de 3DS.
3. Definir distribución del homebrew y ubicación de datos del juego.
4. Mantener el port en rama o fork separado del juego.
5. Registrar licencias y condiciones de redistribución de MKXP-Z, Ruby y dependencias.

**Salida:** entorno reproducible y documento de compatibilidad. El toolchain debe compilar un homebrew de prueba para la consola.

## Fase 1 — Inventario de dependencias y APIs

### MKXP-Z

| Área | Componentes | Trabajo por determinar |
|---|---|---|
| Eventos, ventana, temporización | SDL2 | SDL compatible con 3DS o reemplazos de plataforma |
| Renderizado | OpenGL y renderer MKXP-Z | Compatibilidad del backend o backend específico |
| Audio | OpenAL, SDL_sound, Vorbis, MIDI/FluidSynth | Port de salida y selección de formatos requeridos |
| Imagen y texto | SDL_image, SDL_ttf, FreeType, libpng | Port de codecs necesarios para assets |
| Archivos | PhysFS y filesystem | Rutas de recursos y guardados en SD |
| Ruby | MRI y extensiones nativas | Versión, ABI y extensiones disponibles |
| Red | HTTPS/OpenSSL, `HTTPLite` | Omitir inicialmente salvo dependencia del juego |
| Video | Theora/reproducción de películas | Omitir inicialmente y revisar uso real |

### La Base de Sky

1. Identificar el ejecutable MKXP-Z de referencia y su versión real de Ruby.
2. Revisar scripts y plugins por uso de Win32API/MiniFFI, extensiones nativas, `require`, rutas absolutas, APIs de sistema, red, video y MIDI.
3. Clasificar APIs por necesarias para arrancar, jugar, guardar o extras.

**Salida:** matriz `API/dependencia → uso en el juego → requisito → estrategia de soporte`. No modificar `Data/` durante esta auditoría.

## Fase 2 — Prueba temprana de viabilidad

Prototipo en New 3DS que:

1. Compile y arranque como homebrew.
2. Inicialice Ruby y evalúe un script mínimo.
3. Cree/presente un bitmap y cargue una imagen.
4. Lea botones y stick.
5. Reproduzca un sonido de prueba.
6. Lea recursos y escriba un archivo en SD.
7. Mida memoria y tiempo por frame.

**Criterio para avanzar:** Ruby y presentación gráfica funcionan en hardware real sin exigir reemplazar una porción inviable del motor. Si falla alguno de estos pilares, reevaluar alcance antes de portar el resto.

## Fase 3 — Añadir plataforma 3DS a MKXP-Z

- Incorporar configuración de build/toolchain para 3DS.
- Añadir selección explícita de backend 3DS y conservar los targets actuales.
- Implementar inicio/cierre, eventos, temporizadores, suspensión y salida.
- Añadir logging consultable desde la consola/SD.
- Adaptar rutas de configuración, datos y guardados.
- Crear empaquetado homebrew.

Mantener el código específico de 3DS en backend/archivos separados cuando sea viable. No cambiar defaults de Windows/Linux.

## Fase 4 — Renderizado y pantalla

1. Probar si el renderer OpenGL actual funciona con el stack gráfico disponible.
2. Si no, implementar el backend mínimo para las primitivas requeridas: bitmaps, blits, sprites, ventanas, planos, fuentes, transparencias y efectos usados.
3. Mostrar el juego en pantalla superior; dejar la inferior sin uso inicialmente.
4. Adaptar la resolución lógica 512×384 a 400×240 y evaluar escalado, bandas/recorte, legibilidad y rendimiento.
5. Probar mapas, menús y batallas.

**Criterio:** contenido completo y utilizable, sin defectos visuales bloqueantes.

## Fase 5 — Ruby y bindings

1. Confirmar versión/configuración MRI basadas en el ejecutable real de referencia.
2. Compilar Ruby para 3DS.
3. Portar o excluir extensiones nativas no disponibles.
4. Validar el binding MRI y APIs consumidas por Essentials.
5. Probar carga de scripts, excepciones y serialización.
6. Confirmar compatibilidad de MiniFFI/Win32API si los scripts cargados dependen de ella.

**Criterio:** Essentials inicia sin errores de carga y puede guardar y recuperar datos.

## Fase 6 — Entrada, sonido, archivos y APIs del sistema

### Entrada
- Mapear botones/stick a las acciones MKXP-Z.
- Resolver repetición, diagonales y pausa.
- Considerar táctil en una etapa posterior.

### Sonido
- Portar salida y mezclas necesarias.
- Probar formatos presentes en `Audio/`.
- Dejar MIDI, video y formatos no usados fuera hasta que sean requisito.

### Archivos
- Resolver carga de scripts/assets desde la distribución elegida.
- Guardar persistentemente en SD con ruta estable.
- Revisar separadores, permisos y rutas dependientes de Windows.

### APIs de sistema
Implementar las APIs consumidas por la base, incluyendo las observadas `System.uptime`, `System.data_directory` y `HTTPLite` si la ruta de carga las requiere. Las APIs opcionales solo pueden quedar sin soporte si el juego tolera ese estado.

**Criterio:** una partida se puede jugar, guardar, cerrar y cargar otra vez.

## Fase 7 — Essentials limpio

Validar por orden:

1. Arranque hasta título.
2. Nueva partida y selección de idioma, si corresponde.
3. Movimiento, encuentros y diálogos.
4. Menús, equipo e inventario.
5. Combate básico.
6. Guardar, cerrar y cargar.
7. Audio durante juego y cambios de escena.

Clasificar fallos como backend, Ruby, API, archivos, rendimiento o contenido. No agregar plugins propios todavía.

## Fase 8 — La Base de Sky

Agregar componentes por grupos:

1. Utilidades y scripts de inicio.
2. Plugins de carga, guardado y UI básica.
3. Plugins de mapas, gráficos y audio.
4. Plugins de combate.
5. Plugins opcionales y herramientas.

Por cada fallo, decidir si es bug del port, dependencia de escritorio o adaptación necesaria del juego. Mantener cualquier adaptación Ruby separada y opt-in; no modificar la base original durante el port.

**Criterio:** matriz explícita de características compatibles/no compatibles.

## Fase 9 — Rendimiento, memoria y estabilidad

Medir en hardware real:

- tiempos por frame en mapa, menú y batalla;
- memoria pico en cambios de mapa y combate;
- tiempo de carga;
- sesiones largas y crecimiento de memoria;
- estabilidad de audio;
- espacio de SD necesario.

Optimizar cuellos medidos. Si la base completa excede límites, evaluar primero recursos/funciones opcionales antes de reescribir sistemas.

## Fase 10 — Empaquetado y mantenimiento

1. Definir formato, instalación y actualización.
2. Separar ejecutable, dependencias y datos del juego.
3. Documentar compatibilidad, limitaciones y requisitos de consola.
4. Automatizar builds reproducibles cuando el prototipo sea estable.
5. Probar cada release en hardware real y conservar logs.
6. Revisar licencias antes de distribuir binarios y bibliotecas.

## Riesgos y mitigación

| Riesgo | Impacto | Mitigación inicial |
|---|---|---|
| MRI requerido no compila/no cabe | Bloqueante | Prototipo Ruby primero |
| Renderer OpenGL no compatible | Bloqueante o reescritura grande | Prueba gráfica temprana |
| SDL/dependencias no disponibles | Alto | Inventario y prueba por módulo |
| Lógica 512×384 en pantalla 400×240 | Alto para legibilidad | Prototipo de escalado temprano |
| Guardado/rutas incompatibles | Alto | Escritura/lectura SD en el primer prototipo |
| Extensiones nativas en plugins | Variable | Auditoría y carga gradual |
| Rendimiento/memoria insuficientes | Alto | Medir primero en New 3DS |

## Orden de ejecución recomendado

No portar todas las dependencias de entrada. Primero validar en hardware real:

1. toolchain y homebrew básico;
2. Ruby compatible;
3. presentación gráfica;
4. controles y acceso a SD.

Si pasan, seguir con audio y luego Essentials limpio. Si falla una base, detenerse y ajustar alcance antes de buscar compatibilidad completa con los plugins.

## Compatibilidad con builds existentes

El riesgo para usuarios de Windows/Linux/Mac depende de cuánto código común cambie. Mantener el port en rama/fork, agregar un target/backend 3DS independiente y evitar cambios globales a Ruby, dependencias y opciones reduce el riesgo. Compilar Windows y Linux después de cambios compartidos; revisar el proyecto Xcode si se modifica. El `meson.build` inspeccionado no soporta macOS y remite a Xcode, por lo que el estado de Mac debe validarse en ese proyecto por separado.

Este plan es una evaluación arquitectónica basada en el árbol revisado; no se han ejecutado builds ni probado hardware.
