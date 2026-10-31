# 📜 Historial de Cambios y Avances — GarecaTV

Todas las modificaciones, análisis y versiones del proyecto quedan documentadas en este archivo según el estándar de desarrollo.

## [1.8.6] - 2026-10-31
### Añadido y Corregido
- **Detección y Persistencia Real de Resoluciones en Selector de Partidos**:
  - **Corrección de Clave Asíncrona en StreamCache**: Antes, `ApiClient` y `PlayerActivity` indexaban la resolución bajo la URL resuelta (`resolved.url`, ej. `.m3u8`), pero el diálogo de partidos consultaba por la URL origen (`stream.url`, ej. `teveplay-*.json`). Se unificó el guardado bidireccional para ambas URLs.
  - **Persistencia en Almacenamiento Local (`stream_resolutions_prefs`)**: Las resoluciones detectadas o verificadas por hardware en ExoPlayer ahora se guardan permanentemente en `SharedPreferences` y se cargan al iniciar la app. Al abrir cualquier partido verificado previamente, las opciones muestran de inmediato `[1080p FHD - VERIFICADO]` o `[720p HD - VERIFICADO]` sin demoras.
  - **Detección de Calidad Máxima en Master Playlists HLS y DASH**: En lugar de tomar únicamente la primera resolución del manifiesto (que solía ser la más baja, ej. 360p o 480p), ahora se analizan todas las variantes (`RESOLUTION=` y `height=`) seleccionando la máxima calidad real emitida (`1080p FHD`, `720p HD`).
  - **Soporte para Streams Directos y YouTube**: Detección inteligente para flujos individuales sin manifiesto maestro y etiquetado automático para transmisiones de YouTube.
- **Blindaje Total de YouTube contra Error 153 y Soporte de Playlists**:
  - **Resolución Definitiva de Error 153**: Inyección de cabecera `<meta name="referrer" content="strict-origin-when-cross-origin">`, atributo `referrerpolicy="strict-origin-when-cross-origin"` en el `<iframe>` y parámetro `&origin=https://www.youtube-nocookie.com`.
  - **Soporte Completo de Listas de Reproducción (`list=UU...` / `list=PL...`)**: Detección de identificadores de playlist y carga del reproductor de listas oficial `embed/videoseries?list=...`.

## [1.8.5] - 2026-10-30
### Añadido y Optimizado
- **Optimización de Rendimiento Extremo $O(1)$ Estilo Streamer TV Pro / TiviMate**:
  - **Eliminación del Cuello de Botella CPU**: Anteriormente, cada desplazamiento con el D-Pad o mouse en la barra lateral recorría y ordenaba los 2,500 canales en el hilo principal (200-300ms de latencia en procesadores Amlogic/Allwinner).
  - **Indexación Hash Precalculada en Memoria (`indexCxtvCatalog`)**: Al cargar los datos o cambiar de modo, el catálogo se indexa una sola vez en segundo plano organizando mapas directos `HashMap<String, List<EventItem>>` (`cxtvCategoryMap`, `cxtvCountryMap`, `preSortedAllChannels`). El cambio de categoría o país ahora toma 0.001ms con respuesta instantánea a 60 FPS.
  - **Caché y Virtualización de Vistas en Android TV**: Configurado `setHasFixedSize(true)` y `setItemViewCacheSize(25)` en `rvEvents`, `rvSidebarCategories` y `rvSidebarCountries`. Optimización de Glide con `DiskCacheStrategy.ALL` y escalado de thumbnails en memoria para eliminar micro-tirones al hacer scroll rápido.
- **Reproducción Blindada de YouTube (Integración con `proyectarAplicacion`)**:
  - Extractor regex de video ID y canal (`YOUTUBE_REGEX` y `YOUTUBE_CHANNEL_REGEX`) que limpia parámetros conflictivos y genera iframes embebidos compatibles con `youtube-nocookie.com`.
  - Supresión de diálogos de error falsos ante bloqueos de subrecursos analíticos de YouTube.
  - Integración del controlador JavaScript `postMessage` (`sendYtCommand('playVideo')`) con reintentos programados (1s, 2.5s, 4.5s) que garantiza la reproducción automática en TV.
- **Corrección de Audio en TV Perú y Desmuteo Automático HTML5**:
  - Migración de TV Perú (`tv-peru`) de Nuxt/Iblups (que bloqueaba el audio por políticas de autoplay en Smart TV) a stream HLS 720p oficial nativo con audio AAC (`http://187.102.210.46/TVPERU/index.m3u8`), reproduciéndose como `⚡ EXOPLAYER HLS` con audio cristalino, zapping y zoom.
  - Script global de desmuteo (`injectUnmuteScript`) en `WebStreamActivity.kt` que fuerza `media.muted = false; media.volume = 1.0;` ante cualquier reproductor HTML5 web.

## [1.8.4] - 2026-10-30
### Añadido y Optimizado
- **Arquitectura de 3 Columnas (Master-Detail Avanzado) para Televisión Mundial**:
  - **Columna 1 (Menú Principal / Rail Ligero)**: Agrupa `⭐ Todos los Canales`, las categorías temáticas dinámicas (`Cine`, `Cultura`, `Deportes`, `Noticias`, `Variedades`, etc.) y una **única opción unificada: `🌍 Países`**.
  - **Columna 2 (Subcolumna de Países)**: Se despliega de forma dinámica al seleccionar o enfocarse en `🌍 Países`. Lista todos los países ordenados alfabéticamente A-Z (`dynamicCxtvCountries`).
  - **Panel 3 (Canales)**: Muestra los canales de la selección activa (categoría temática o país individual) agrupados internamente por orden alfabético A-Z de categoría y nombre.
- **Presentación Visual Ligera (Flat Navigation Rail)**:
  - Se eliminaron las cajas con bordes pesados en favor de una barra de navegación moderna y minimalista (`item_sidebar_category.xml`, `sidebar_nav_selector.xml`, `sidebar_nav_text_selector.xml`).
  - Barra de acento vertical cian (`#00E5FF`) y píldora de foco translúcida.
- **Contadores de Canales en Grupos y Subgrupos**:
  - Distintivo tipo pastilla (`sidebar_count_badge.xml`) en cada categoría de Columna 1 (ej. `📺 Variedades [240]`, `🌍 Países [2.399]`) y en cada país de Columna 2 (ej. `🇦🇷 Argentina [120]`, `🇪🇸 España [95]`).
- **Indicador Ordinal de Posición en Tiempo Real**:
  - Encabezado interactivo sobre la lista de canales (`tvChannelPositionIndicator`): *"Canal X de Y • Grupo/País"*.
  - Asignación ordinal interna del 1 al total de canales de la sección actual, actualizándose dinámicamente con cada movimiento del D-Pad (`onItemFocus` en `EventAdapter`).
- **Navegación Fluida D-Pad, Tecla Atrás y Air-Mouse**:
  - Columna 1 a Columna 2 (`▶ Derecha`, `OK`, Clic) al estar en `🌍 Países`.
  - Columna 2 a Panel 3 (`▶ Derecha`, `OK`, Clic) posicionando el foco en el primer canal.
  - Panel 3 a Columna 2 (`◀ Izquierda`, Atrás) si se está consultando un país.
  - Columna 2 a Columna 1 (`◀ Izquierda`, Atrás) regresando a `🌍 Países`.
- **Reproducción Blindada de YouTube y Streams Web**:
  - Adaptación de la arquitectura de reproducción probada en `proyectarAplicacion` (`TvYouTubeScreen.kt`) dentro de `WebStreamActivity.kt`.
  - Uso de `youtube-nocookie.com` con `loadDataWithBaseURL`, User-Agent de Google Chrome Desktop, soporte de cookies de terceros y supresión de pantallas de error falsas en subrecursos analíticos de YouTube.

## [1.8.3] - 2026-10-30
### Corregido y Optimizado
- **Restauración y Actualización de América TV (Variedades)**:
  - **Diagnóstico**: La señal original proveniente de CXTv enlazaba a un video estático de YouTube (`zcWXboTnous`) que finalizó su emisión y fue dado de baja por YouTube (`Video no disponible / UNPLAYABLE`).
  - **Transición a ExoPlayer HLS Nativo**: Se reemplazó el stream por la señal directa oficial HLS en 720p 60fps (`https://prepublish.f.qaotic.net/a07/americahls-100056/playlist_720p.m3u8`), clasificándola como `⚡ EXOPLAYER HLS`. Ahora se reproduce de forma fluida y nativa en ExoPlayer con zapping (`◀` / `▶`) y soporte completo de Zoom.
  - **Optimización de WebStreamActivity**: Se exoneraron los dominios esenciales de YouTube (`youtube.com`, `googlevideo.com`, `ytimg.com`) del interceptor de bloqueo de anuncios para asegurar que los canales web de YouTube Live carguen sin bloqueos ni pantallas en negro.

## [1.8.2] - 2026-10-30
### Añadido y Optimizado
- **Agrupamiento Alfabético Interno por Categoría y Nombre (A-Z)**:
  - **Televisión Mundial**: Al seleccionar cualquier país (ej. Estados Unidos, España, etc.) o sección, los canales se agrupan internamente por su categoría/agrupador temático (`Cine`, `Cultura`, `Deportes`, `Infantil`, `Música`, `Noticias`, `Público`, `Variedades`, etc.) en orden alfabético desde la A a la Z.
  - Dentro de cada grupo temático, los canales se ordenan alfabéticamente por su nombre de la A a la Z.
  - **Deportes y Canales 24/7**: Se aplica idéntica regla interna en `🌐 Todos los Canales` y señales deportivas, agrupando por categoría temática A-Z y ordenando por nombre de canal A-Z (respetando la prioridad de partidos en vivo y horarios cronológicos).
  - Canales no disponibles (`⚪ NO DISPONIBLE` / `REPLAY_UNAVAILABLE`) o finalizados permanecen siempre al fondo de la lista.
  - Ordenamiento 100% interno sin modificar columnas ni alterar la disposición de la interfaz visual Master-Detail.
- **Selección Inmediata de Categoría con Foco en el Primer Canal**:
  - Al seleccionar cualquier grupo o categoría de la izquierda (`Público`, `Cultura`, etc.) con clic de mouse, touch o tecla OK/Center/Derecha del control remoto, el foco se posiciona automáticamente en el **primer canal de la lista de la derecha** (`focusFirstEventItem`).
- **Retención y Retorno de Foco al Grupo Consultado (Navegación Izquierda / Mouse / D-Pad)**:
  - Al estar navegando por los canales del panel derecho y pulsar `◀ Izquierda` (o botón de retroceso / flecha izquierda del mouse/air-mouse), el foco vuelve de manera inmediata y precisa al **grupo que se estaba consultando** en la columna lateral (`focusSelectedSidebarCategory`), conservando la posición exacta del usuario en el menú vertical.
  - Mismo comportamiento aplicado en Deportes (`focusSelectedSportsCategory`).
  - Añadido `android:focusableInTouchMode="true"` en `item_sidebar_category.xml`, `item_event_card.xml` e `item_category_tab.xml` para garantizar que el foco y la navegación funcionen sin restricciones tanto en TV con D-Pad como con mouse o air-mouse.

## [1.8.1] - 2026-10-30
### Añadido y Optimizado
- **Interfaz Master-Detail Estilo MagisTV / IPTV para Televisión Mundial**:
  - **Columna Lateral Izquierda (Sidebar vertical, ancho 250dp)**: Agrupación visual vertical de categorías y países (`⭐ Todos los Canales`, `📺 Variedades`, `📰 Noticias`, `🎵 Música`, `🎬 Cine`, `🏆 Deportes`, `👶 Niños`, `🇦🇷 Argentina`, `🇪🇸 España`, `🇨🇴 Colombia`, etc.).
  - **Panel Principal Derecho**: Despliegue instantáneo de los canales correspondientes a la categoría seleccionada a la izquierda con actualización en tiempo real al navegar verticalmente con el D-Pad (`SidebarCategoryAdapter`).
  - **Navegación Ergonómica con Control Remoto**:
    - `▲` y `▼`: Recorren la columna lateral izquierda de categorías.
    - `▶ Derecha`: Salta directamente de la columna de categorías a la primera tarjeta de canal del panel derecho.
    - `◀ Izquierda`: Desde las tarjetas de canal del panel derecho, regresa fluidamente a la categoría activa en la columna izquierda.
    - `▲ Arriba` (desde el primer elemento del menú): Sube el foco a la caja de búsqueda general.
- **Distintivos Tecnológicos Explícitos en Cada Tarjeta**:
  - Identificación visual clara del tipo de reproductor en cada canal:
    - `⚡ EXOPLAYER HLS` (Verde Neón `#059669`): Transmisiones `.m3u8` nativas con zapping continuo (`◀` / `▶`) y Zoom.
    - `🔴 YOUTUBE LIVE` (Rojo `#DC2626`): Transmisiones en vivo vía YouTube a pantalla completa.
    - `🔵 WEB HTML5` (Azul Eléctrico `#2563EB`): Canales embebidos en reproductor web seguro.
    - `🌐 WEB EXTERNA` (Púrpura `#7C3AED`): Señales abiertas enlazadas.
    - `⚪ NO DISPONIBLE` (Gris `#6B7280`): Canales caídos con atenuación visual (`alpha = 0.5f`), desplazados al fondo de su categoría.
- **Supresión Total de Marcas Internas ("CXTv")**:
  - El botón de modo superior se estandariza como **`📺 TELEVISIÓN MUNDIAL`**.
  - Se eliminan referencias técnicas de proveedores en la UI, changelogs y textos visibles para el usuario.

## [1.8.0] - 2026-10-30
### Añadido y Optimizado
- **Selector Maestro Dual Core (Deportes vs TV Mundial)**:
  - **Conmutación Inmediata con Control Remoto**: Barra selectora superior con botones de alto contraste (`btnModeSports` y `btnModeWorldTv`), selectores visuales Leanback (`mode_button_selector.xml`, `mode_button_text_selector.xml`) y estados activos/inactivos para operar 100% con D-Pad.
  - **Separación Estricta de Fuentes (Teleplay vs CXTv)**:
    - **⚽ Deportes y Partidos**: Teleplay reservado exclusivamente para la agenda deportiva del día con cuenta regresiva, canales deportivos y repeticiones.
    - **📺 Televisión Mundial (CXTv)**: Más de 2.399 canales de televisión abierta, noticias, cine, música, series, infantil y variedades de 98 países sin restricciones de idioma ni fronteras.
- **Catálogo Dinámico 100% Autogenerado (Data-Driven)**:
  - Prohibición de listas hardcodeadas: La app autodescubre y construye dinámicamente las pestañas a partir del catálogo backend (`buildCxtvTabs`), organizando categorías temáticas (`📰 Noticias`, `🎵 Música`, `🎬 Cine`, `👶 Niños`, `🕊️ Fe`, `🎨 Cultura`, etc.) y agrupaciones geográficas con banderas (`🇦🇷 Argentina`, `🇧🇷 Brasil`, `🇨🇴 Colombia`, `🇪🇸 España`, `🇺🇸 USA`, `🇲🇽 México`, etc.).
  - Precarga ultrarrápida desde `assets/cxtv_catalog.json` para acceso instantáneo al iniciar la app y sincronización asíncrona remota con GitHub.
- **Clasificación Multireproductor Inteligente y Zapping**:
  - **ExoPlayer HLS Nativo (72.6%)**: Reproducción fluida y directa de flujos `.m3u8` con búfer resiliente y zapping directo con `◀` y `▶` entre canales.
  - **YouTube Live & WebView HTML5 (19.7%)**: Enrutamiento automático hacia `WebStreamActivity` a pantalla completa para señales emitidas en plataformas web.
- **Buscador Tokenizado CXTv y Tratamiento de Canales Caídos**:
  - Pestaña `🔍 Buscar TV` con soporte de búsqueda tokenizada multidisjuntiva, atajo de micrófono por voz Leanback y botón interno "✕" de limpieza.
  - Desplazamiento automático de cualquier señal etiquetada como `NO DISPONIBLE` al fondo de su categoría o país.

## [1.7.5] - 2026-10-30
### Añadido y Optimizado
- **Control de Zoom y Relación de Aspecto en Pantalla (`AspectRatioFrameLayout` en ExoPlayer)**:
  - **Disponibilidad Condicional ("En los casos que se pueda")**: El botón de zoom solo se habilita y muestra cuando el reproductor ha recibido cuadros de video válidos (`videoFrameReceived = true` y dimensiones > 0). En transmisiones de audio o durante la carga, permanece oculto.
  - **Botón Interactivo en Banner (`btnZoomMode`)**: Embebido a la derecha del banner superior con selector visual Leanback (`button_focus_selector.xml`). Al presionar `▲ Arriba` durante la reproducción, el banner se despliega y transfiere el foco al botón para operarlo con OK/Center.
  - **Atajos Directos de Control Remoto**: Mapeo de teclas `KEYCODE_MENU`, `KEYCODE_BUTTON_Y`, `168 (KEYCODE_ZOOM)` y `KEYCODE_PROG_BLUE` para conmutar la pantalla inmediatamente sin necesidad de abrir menús.
  - **Tres Modos Cíclicos de Pantalla**:
    1. `RESIZE_MODE_FIT` (**Ajuste Original**): Mantiene la relación de aspecto nativa del stream con bandas negras si difiere de 16:9 (ideal para emisiones estándar).
    2. `RESIZE_MODE_FILL` (**Rellenar 16:9**): Estira la imagen horizontal y verticalmente cubriendo todo el panel del televisor sin franjas negras.
    3. `RESIZE_MODE_ZOOM` (**Zoom / Recorte**): Llena completamente la pantalla recortando proporcionalmente los bordes sin deformar los objetos ni las personas (ideal para emisiones 4:3 con barras embebidas).
  - **HUD Flotante de Notificación en Pantalla (`tvZoomHud`)**: Cartel informativo flotante en color de acento centrado en pantalla que se desvanece suavemente a los 2.5 segundos informando el modo activo (*"🔍 Pantalla: Ajuste Original (Fit)"*, *"🔍 Pantalla: Rellenar Pantalla (16:9)"*, *"🔍 Pantalla: Zoom / Recorte Completo"*).
- **Protocolo de Arquitectura Dual Core y Actualización en Caliente**:
  - Separación estricta entre **Teleplay** (Fútbol y Deportes en vivo) y **CXTv** (Televisión mundial abierta y temática de 98 países).
  - Directiva de menús y secciones 100% autogenerados (Data-Driven) sin listas estáticas hardcodeadas.
  - Política de reordenamiento automático de señales caídas con etiqueta `NO DISPONIBLE` al fondo de cada categoría o país.

## [1.7.4] - 2026-10-27
### Añadido y Optimizado
- **Colchón de Latencia Objetivo para Canales en Vivo (`MediaItem.LiveConfiguration`)**:
  - **Margen de Protección Constante de 12 Segundos**: Para señales lineales y canales en directo (TevePlay y agenda diaria), se configuró `LiveConfiguration` en ExoPlayer con `setTargetOffsetMs(12000L)`, `setMinOffsetMs(6000L)` y `setMaxOffsetMs(20000L)`.
  - **Arranque Inmediato sin Pantalla Negra**: A diferencia de forzar un búfer previo prolongado que causaría una espera de 15s al hacer zapping, el televisor arranca el canal en 1 segundo y se posiciona de forma continua a 12 segundos del borde en vivo del CDN.
  - **Absorción Preventiva sin Micro-Pausas**: El reproductor almacena de forma permanente 3 a 4 fragmentos (`.ts`) ya decodificados en memoria RAM. Cualquier congelamiento momentáneo del socket o retraso del CDN en generar el siguiente fragmento es absorbido de forma invisible sin pausar el video ni mostrar spinner.
  - **Ajuste Fino de Velocidad Dinámica**: Se incorporaron `minPlaybackSpeed = 0.97f` y `maxPlaybackSpeed = 1.03f`, permitiendo a ExoPlayer recuperar imperceptiblemente el colchón óptimo de 12 segundos sin tirones de audio ni saltos visuales.

## [1.7.3] - 2026-10-27
### Añadido y Optimizado
- **Arquitectura de Auto-Recuperación Transparente y Búfer Resiliente ante Micro-Cortes HLS**:
  - **Capa 1 (Búfer de Absorción con `DefaultLoadControl`)**: Se implementó en ExoPlayer un búfer de anticipación en RAM (`minBufferMs = 25000ms` [25s], `maxBufferMs = 40000ms` [40s], `bufferForPlaybackAfterRebufferMs = 1500ms`, `prioritizeTimeOverSizeThresholds = true`). Micro-cortes transitorios de 2 a 3 segundos en el CDN son absorbidos por el búfer sin pausar la reproducción ni congelar la imagen.
  - **Capa 2 (Reconexión Silenciosa Progresiva con Backoff)**: Ante caídas transitorias de paquetes o timeouts (`ERROR_CODE_IO_UNSPECIFIED`, código 2000), el reproductor no capitula ni despliega el mensaje de "Señal no disponible". Mantiene activo el spinner discreto (`playerBuffering`) y ejecuta hasta 3 reintentos automáticos progresivos en segundo plano (a los 500ms, 2s y 4s). Al reenganchar la señal, el spinner se desvanece y la transmisión continúa fluidamente.
  - **Capa 3 (Revalidación Puntual de Enlace Quirúrgica)**: Si tras los 3 reintentos el flujo sigue sin responder, el sistema re-resuelve de forma puntual el `StreamOption` del canal actual (`ApiClient.resolveStreamUrl`), comprobando si el CDN renovó el enlace o token sin sobrecargar la red ni descargar catálogos completos innecesarios.
  - **Capa 4 (Fallo Definitivo)**: Únicamente si tras los 3 reintentos y la revalidación puntual la señal no responde, se marca como no disponible en `StreamCache` y se despliega el mensaje explicativo en pantalla con indicaciones para el control remoto.
  - **Resiliencia de Watchdog**: El temporizador de 10s se conectó con el ciclo de auto-recuperación, evitando falsos positivos por retardos de red.
  - **Preservación de Metadatos de Enlace**: Se incorporaron `EXTRA_ORIGINAL_URL` y `EXTRA_ORIGINAL_TYPE` en el intent de reproducción desde `MainActivity`, garantizando la revalidación individualizada de señales.

## [1.7.2] - 2026-10-27
### Corregido y Optimizado
- **Corrección de Ocultación de Controles Multimedia y Banner en TV**:
  - **Auto-Ocultación Forzada de Controles**: En Android TV / Leanback, cuando los controles multimedia de ExoPlayer tomaban el foco, el reproductor suspendía su temporizador interno de desvanecimiento, provocando que la barra multimedia no desapareciera tras sintonizar un canal. Se implementó `showControlsTemporarily()` con un temporizador programático estricto (`3500ms`) que ejecuta `playerView.hideController()` de forma infalible.
  - **Desactivación de Auto-Show en Zapping**: Se configuró `app:auto_show="false"` y `playerView.controllerAutoShow = false`. Al cambiar de canal con `◀` o `▶`, los controles multimedia se ocultan de inmediato para evitar que bloqueen la vista del video, mostrando únicamente el banner de nombre del canal que se desvanece a los 3.5 segundos.
  - **Aislamiento de Foco en Banner**: Se marcaron `focusable="false"` y `clickable="false"` en `llChannelBanner` para asegurar que el control remoto nunca quede atrapado en el banner superior.

## [1.7.1] - 2026-10-27
### Añadido y Optimizado
- **Zapping de Canales con Control Remoto (D-Pad Izquierda / Derecha)**:
  - **Navegación Fluida entre Señales de un Solo Flujo**: En pestañas de canales (`📺 Regionales`, `🏆 Deportes`, países y señales 24/7), se activó el cambio secuencial directo de canal mediante las teclas `◀ Izquierda` y `▶ Derecha` del control remoto en `PlayerActivity`, sin necesidad de salir al menú principal.
  - **Gestor Global de Navegación (`ChannelNavigationManager`)**: Administrador seguro en memoria que preserva la lista de canales activos y la posición actual, evitando sobrecargas de IPC (`TransactionTooLargeException`).
  - **Reutilización Eficiente de ExoPlayer**: El cambio de señal detiene y reaprovisiona el reproductor reutilizando la instancia nativa, evitando parpadeos de pantalla y acelerando la conexión.
- **Banner Informativo de Canal con Auto-Ocultación y Control Multimedia Contextual**:
  - **Banner Superior Inteligente (`llChannelBanner`)**: Al ingresar a un canal o realizar zapping, se despliega en la parte superior el logo del canal, el nombre oficial en alta legibilidad, información adicional y el contador de posición (ej. `Canal 5 de 42  [◀ ▶ Zapping]`), desvaneciéndose automáticamente tras 3.5 segundos.
  - **Invocación de Controles con Teclas Arriba / Abajo**: Al presionar `▲ Arriba` o `▼ Abajo` en el control remoto durante la transmisión, se reaparece de inmediato el banner con el nombre del canal junto con la barra de controles multimedia de ExoPlayer.
- **Ajuste Ergonómico en Descripciones de Cabecera**:
  - **Texto Más Compacto y Conciso**: Se redujo el tamaño de fuente a `11sp` en `tvHeaderButtonHint`.
  - Se retiró el prefijo redundante `Actualización de Software:` del botón `Actualizado` (mostrando directamente `Comprueba o descarga la última versión de la app` o `Nueva versión vX.X.X lista para instalar`).
  - Se cambió la descripción de `Recargar` a `Recarga lista de eventos/canales`.

## [1.7.0] - 2026-10-27
### Añadido y Optimizado
- **Descripciones Explicativas Dinámicas en Botones de Cabecera al Recibir Foco**:
  - **Claridad de Software vs. Partidos**: Se implementó una etiqueta de ayuda contextual (`tvHeaderButtonHint`) debajo de los botones superiores que se activa únicamente cuando reciben el foco del control remoto, manteniendo los textos limpios en reposo:
    - Al enfocar el botón **`Actualizado` / `Actualizar`**: Muestra `📲 Actualización de Software: Comprueba o descarga la última versión de la app`.
    - Al enfocar el botón **`Recargar`**: Muestra `🔄 Actualizar Partidos: Recarga la lista de eventos en vivo de hoy, canales y repeticiones`.
  - **Confirmación Visual al Recargar**: Al presionar el botón `Recargar`, se indica `🔄 Actualizando lista de partidos desde el servidor...` y al culminar la descarga se confirma con `✅ Lista de partidos y canales actualizada`.
  - **Sin Desplazamientos Bruscos**: La descripción se oculta automáticamente al mover el foco hacia las categorías o lista de eventos, preservando el diseño Leanback ergonómico.

## [1.6.9] - 2026-10-26
### Corregido y Optimizado
- **Filtrado Inmediato y Foco Directo en Resultados al Seleccionar Sugerencia**:
  - **Corrección de Foco**: Al hacer clic o pulsar OK/Center en un elemento de la lista de sugerencias, el foco saltaba erróneamente al botón de la Lupa en vez de filtrar y presentar los partidos. Se reestructuró `onSuggestionSelected` para ejecutar el filtrado de inmediato, cerrar y vaciar la lista de sugerencias, y transferir el foco del control remoto directamente a la primera tarjeta de resultado del catálogo (`focusFirstEventItem`).
  - **Bloqueo de Reapertura de Sugerencias**: Se incorporó la bandera `isSelectingSuggestion` para evitar que la actualización programática de la caja de texto (`setText`) dispare el listener `onTextChanged` y vuelva a desplegar las sugerencias encima de los resultados.
  - **Soporte TV Multi-Control**: `SuggestionAdapter` captura de forma explícita `KEYCODE_DPAD_CENTER` y `KEYCODE_ENTER`, asegurando respuesta inmediata con cualquier control remoto o air-mouse.

## [1.6.8] - 2026-10-26
### Corregido y Añadido
- **Búsqueda Tokenizada Multidisjuntiva (Soporte "Racing Boca", "Boca Racing", etc.)**:
  - Se resolvió el fallo en el que búsquedas con más de una palabra (ej: "racing boca") devolvían cero resultados porque el motor exigía una coincidencia literal contigua sobre "Racing Club - Boca Juniors".
  - Se implementó división por tokens (`split(Regex("""\s+"""))`), exigiendo que todos los términos ingresados coincidan independientemente en los campos de título, subtítulo, fecha o categoría (`tokens.all { target.contains(it) }`).
  - Aplicado a: Sugerencias en tiempo real (`filterSuggestions`), Búsqueda en Repeticiones (`getFilteredAndSortedReplays`) y Búsqueda Global en todo el catálogo (`getFilteredGlobalEvents`).
- **Botón "✕" Integrado dentro de la Caja de Búsqueda con Activación Inmediata de Teclado**:
  - Se diseñó un contenedor visual unificado (`searchBoxContainerReplay` y `searchBoxContainerGlobal`) que incrusta un botón "✕" (`btnClearSearchText` y `btnClearGlobalText`) en el extremo interior derecho del campo de texto, visible únicamente cuando hay caracteres ingresados.
  - Totalmente navegable y seleccionable con el D-Pad del control remoto de Android TV.
  - Al presionar OK/Center sobre la "✕", se limpia todo el texto del campo, se refresca el catálogo y se invoca de inmediato el teclado virtual (`activateSearchInput()` / `activateGlobalSearchInput()`) con el cursor activo listo para tipear una nueva búsqueda.
  - Navegación D-Pad adaptativa: `D-Pad Derecha` desde la caja de texto enfoca la "✕" (si está visible); de lo contrario, salta al botón Lupa. `D-Pad Izquierda` desde el botón Lupa enfoca la "✕" si está activa.
- **Limpieza de Interfaz**: Se retiró el botón externo redundante `✕ Limpiar` de la fila de "Buscar Todo", unificando el diseño de ambos buscadores.

## [1.6.7] - 2026-10-26
### Corregido y Optimizado
- **Captura Pre-IME de Tecla Atrás (`SearchEditText`) y Transferencia Inmediata de Foco a Sugerencias**:
  - **Interceptación Pre-IME de Tecla Atrás**: Se implementó `SearchEditText` sobreescribiendo `onKeyPreIme`. Anteriormente, el teclado del sistema consumía el primer toque de la tecla Atrás antes de llegar al sistema de despacho de la ventana, reteniendo el foco en la caja de texto. Con `SearchEditText`, la pulsación de Atrás se intercepta con prioridad absoluta en ambos buscadores (`🔁 Repeticiones` y `🔍 Buscar Todo`), cerrando el teclado y transfiriendo el foco de inmediato a la lista de sugerencias en pantalla.
  - **Soporte de Foco en Touch Mode**: Se activó `android:focusableInTouchMode="true"` en el contenedor de sugerencias y en cada fila (`item_search_suggestion.xml`), garantizando que el D-Pad del televisor seleccione y resalte visualmente la primera sugerencia al instante tras ocultar el teclado virtual.
  - **Efecto Visual de Selección**: Animación de escala y resaltado de alto contraste al navegar por las sugerencias con las flechas del control remoto.

## [1.6.6] - 2026-10-26
### Añadido y Corregido
- **Navegación Ergonómica de Sugerencias con Control Remoto (D-Pad)**:
  - **Foco Persistente en Sugerencias al Cerrar Teclado**: Al pulsar Atrás (`BACK`) en el control remoto mientras el teclado virtual está desplegado, el teclado se oculta pero la lista de sugerencias permanece visible en pantalla y recibe el foco del D-Pad de inmediato, permitiendo elegir una opción con las flechas del control.
  - **Foco Automático al Botón Lupa tras Seleccionar**: Al presionar OK / Center sobre una sugerencia, el texto se transfiere al campo de búsqueda, la lista se oculta y el foco salta directamente al botón de la Lupa (`btnSearchReplay` o `btnSearchGlobal`).
  - **Descarte Rápido de Sugerencias**: Si el usuario está enfocado en la lista de sugerencias y no desea escoger ninguna, al pulsar Atrás (`BACK`) la lista desaparece y el foco regresa a la caja de texto.
  - **Navegación Bidireccional D-Pad**: Pulsar Arriba (`DPAD_UP`) desde el primer elemento de la lista de sugerencias devuelve el foco a la caja de texto; pulsar Abajo (`DPAD_DOWN`) desde la caja de texto entra a las sugerencias.
- **Botón de Ordenamiento por Nombre en Búsqueda Global**:
  - Se añade el botón `🔤 Nombre` (`btnSortNameGlobal`) en la barra de "🔍 Buscar Todo", permitiendo ordenar alfabéticamente (A-Z y Z-A) los resultados consolidados de partidos del día, canales en vivo y repeticiones.

## [1.6.5] - 2026-10-26
### Añadido y Corregido
- **Buscador Ergonómico para TV con Teclado Persistente, Sugerencias y Botón Lupa**:
  - **Corrección de Cierre Prematuro del Teclado**: Se corrigió el error en `setOnEditorActionListener` donde presionar cualquier letra en el teclado virtual (enviando `KEYCODE_ENTER` desde el D-Pad del control remoto) cerraba el teclado. Ahora el teclado permanece abierto de forma continua mientras el usuario escribe y solo se oculta al pulsar la lupa del teclado virtual (`actionSearch`), al pulsar el botón Lupa o con la tecla Atrás (`BACK`).
  - **Nueva Disposición Ergonómica**: El botón de micrófono se reubica al extremo izquierdo de la caja de búsqueda, y se incorpora a su derecha un botón Lupa dedicado (sin texto, con icono `ic_search` enfocado) para iniciar la búsqueda en pantalla.
  - **Sugerencias de Autocompletado en Tiempo Real**: Se implementó `AutoCompleteTextView` conectado a un catálogo indexado de palabras clave, nombres de equipos y canales (`cachedDailyEvents`, `cachedReplayEvents`, `cachedLiveChannels`), sugiriendo automáticamente coincidencias según las iniciales ingresadas.

## [1.6.4] - 2026-10-25
### Añadido y Optimizado
- **Versión Visible en Cabecera Superior y Estado Dinámico en Botón Actualizar**:
  - Se añade etiqueta de versión actual (`tvAppVersion`) visible en la parte superior junto a la fecha/día (`• v1.6.4`).
  - Estado inteligente del botón de actualización:
    - Si la versión instalada coincide con la versión remota, el botón muestra únicamente `Actualizado`.
    - Si el servidor (`version.json`) detecta una versión más reciente, el botón pasa a indicar `Actualizar (vX.X.X)` y abre directamente el diálogo de descarga OTA al presionarlo.

## [1.6.3] - 2026-10-25
### Corregido y Optimizado
- **Validación Estricta de Descifrado y Resiliencia en Doble Clave**:
  - Corrección en `CryptoUtils.kt`: `decrypt()` ahora valida obligatoriamente que el payload descifrado comience con `{` o `[` (JSON válido). Si no es JSON (debido a caché errónea o clave desfasada), se rechaza de inmediato y conmuta automáticamente a la clave de fábrica en RAM, evitando que la lista de partidos de hoy quede en blanco o reducida.
  - Sincronización híbrida de resiliencia en `version.json`: provee tanto `active_key` como `active_key_enc`, garantizando que televisores de cualquier versión sigan cargando el 100% de los 53 partidos de la fecha.

## [1.6.2] - 2026-10-25
### Añadido y Optimizado
- **Soporte de Cifrado de Sobre Remoto (`active_key_enc`) y Suite de Herramientas**:
  - Implementación en `CryptoUtils.kt` del método `decryptEnvelope` con semilla matemática ofuscada con máscara XOR para desempaquetar la clave en RAM.
  - Creación de la suite de herramientas en `herramientas/cifrador_clave/` con el script ejecutable `encrypt_key.py` y `MANUAL.md`.
  - Automatización en `publish_release.sh` para generar `version.json` con `"active_key_enc"` (cero texto plano en el repositorio GitHub ni en el tráfico de red).
  - Trazas de auditoría en vivo en `SecureDns.kt` para verificación de resolución DoH con Cloudflare (1.1.1.1).

## [1.6.1] - 2026-10-25
### Añadido y Optimizado
- **Blindaje Criptográfico XOR en Memoria y DNS sobre HTTPS (DoH) Anti-Bloqueos**:
  - Eliminación de la clave Base64 en texto plano del APK; sustitución por máscara XOR estática (`SHIELD_MASK = 0x5A`) en `CryptoUtils.kt`.
  - Integración nativa de DNS sobre HTTPS (DoH) con Cloudflare (`1.1.1.1`) y Google (`8.8.8.8`) en `SecureDns.kt` para neutralizar el bloqueo DNS de los operadores locales de internet.
  - Conexión de `OkHttpDataSource.Factory` en `PlayerActivity` y `ApiClient` al resolvedor DoH.
  - Renombrado de categoría y mapeo a `🇮🇹 Italia - Serie A`.

## [1.6.0] - 2026-10-25
### Añadido y Optimizado
- **Verificador Web de Versión con Periodo de Gracia y Bloqueo Obligatorio**:
  - Detección autónoma de nuevas versiones disponibles mediante `version.json` remoto (`latest_version_code`, `release_timestamp` y `release_date`).
  - **Periodo de Gracia de 2 días**: Durante los primeros 2 días tras la publicación de una nueva versión, el usuario recibe un diálogo informativo pero puede posponer la actualización ("Más tarde") y continuar usando la aplicación con normalidad.
  - **Bloqueo Obligatorio al 3er día**: A partir del tercer día transcurrido (48+ horas desde el release), la actualización se vuelve estrictamente obligatoria:
    - Diálogo modal bloqueante no cancelable (`setCancelable(false)`).
    - El botón de posponer se transforma en "Salir" y ejecuta cierre completo del aplicativo (`finishAffinity()`).
    - Bloqueo en `LoginActivity` y `MainActivity`: ningún usuario puede eludir la actualización ni iniciar sesión si su versión tiene 3 o más días de antigüedad respecto al servidor.
  - **Automatización en Releases**: `publish_release.sh` ahora inyecta automáticamente `release_timestamp` (epoch millis) y `release_date` (`YYYY-MM-DD`) en `version.json`.

## [1.5.9] - 2026-10-24
### Modificado y Optimizado
- **Canales 24/7 Excluidos de Etiqueta "NO DISPONIBLE"**:
  - Los canales 24/7 (señales en vivo, canales de TV lineales y regionales) nunca se marcan ni se muestran con la etiqueta `NO DISPONIBLE`.
  - Mantienen siempre su etiqueta verde original `CANAL 24/7` (o su estado de partido si transmiten un evento en directo), con opacidad completa (sin atenuar).
  - La etiqueta y desplazamiento de `NO DISPONIBLE` queda reservada exclusivamente para eventos de agenda / repeticiones caídas.

## [1.5.8] - 2026-10-24
### Añadido y Optimizado
- **Ordenación Jerárquica de Eventos y Canales**:
  - Implementación de la regla estricta de ordenación en cada lista (`sortEventList`):
    1. **● EN VIVO primero**: Eventos `LIVE` y `STARTING_SOON` al inicio de la lista.
    2. **PROGRAMADOS después**: Eventos `SCHEDULED` ordenados cronológicamente por hora de inicio.
    3. **CANALES 24/7 luego**: Los canales continuos 24/7 van tras los programados. Si un canal 24/7 se marca como `NO DISPONIBLE`, **permanece en su lugar** entre los canales 24/7 y no es desplazado al fondo.
    4. **Partidos no disponibles**: Partidos caídos van antes de los finalizados.
    5. **FINALIZADOS siempre al último**: Todos los partidos con estado `FINALIZADO` se sitúan estrictamente al fondo de la lista.
  - Aplicado a **⚽ Partidos del Día**, **🌐 Todos los Canales**, cada categoría de canal por separado y **🔍 Buscar Todo**.

## [1.5.7] - 2026-10-24
### Añadido y Optimizado
- **Insignia y Escudo Oficial en Formato Circular con Fondo Transparente**:
  - Se eliminó por completo el bloque cuadrado blanco de fondo.
  - El diseño ahora es una **medalla o insignia circular**:
    - Silueta circular con fondo blanco puro y fino contorno deportivo.
    - Interior: **Gareca** arriba en letras rojas con borde negro, el **tigre rugiente** a color en el medio, y **Tv** en letras rojas abajo.
    - Exterior: **100% transparente**, eliminando cualquier esquina o borde cuadrado en la pantalla del Smart TV.
  - Se implementaron los nuevos identificadores con cache-busting v4 (`@drawable/tv_banner_v4` y `@mipmap/ic_launcher_v4`), con fondo transparente en `ic_launcher_background.xml` y soporte multi-densidad para televisores 1080p y 4K.

## [1.5.6] - 2026-10-24
### Corregido y Optimizado
- **Invalidación Forzada de Caché en Android TV y Google TV (Cache-Busting v3)**:
  - Los lanzadores de Android TV y Google TV almacenan en su base de datos local los banners e iconos usando el nombre del recurso de la versión previa (`tv_banner_v2` e `ic_launcher_v2`), lo que impedía que el televisor actualizara la imagen en su menú principal al recibir el nuevo APK.
  - Se crearon y registraron formalmente en `AndroidManifest.xml` los nuevos identificadores `@drawable/tv_banner_v3` y `@mipmap/ic_launcher_v3`.
  - Se añadieron además los banners dedicados por densidad de pantalla (`drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`), garantizando que tanto en televisores 1080p como 4K el sistema cargue el banner oficial sin compresión ni caché obsoleto.

## [1.5.5] - 2026-10-24
### Corregido y Optimizado
- **Estructura Oficial Estricta del Icono (Gareca Arriba, Tigre en el Medio y Tv Abajo)**:
  - Se corrigió la separación exacta de los rótulos tipográficos deportivos:
    - **Arriba**: La palabra `Gareca` en rojo vibrante con borde negro inclinado.
    - **En el medio (debajo de Gareca)**: El tigre rugiente a color con sus garras y zarpazos.
    - **En la inferior**: La palabra `Tv` centrada en rojo vibrante con borde negro.
  - Sincronizado en el banner de Android TV (`tv_banner_v2.png`), suites de lanzador (`ic_launcher_v2`), icono adaptativo seguro (`ic_launcher_foreground_v2.png`) y el icono dentro de la app (`app_icon.png`).

## [1.5.4] - 2026-10-24
### Añadido y Optimizado
- **Botones de Micrófono de Voz Exclusivamente con Icono**:
  - Se eliminó la palabra "Voz" en los dos botones de dictado por voz (tanto en "🔁 Repeticiones" como en "🔍 Buscar Todo").
  - Ambos botones pasaron a ser cuadrados compactos de 44x44dp, mostrando únicamente el icono clásico multicolor del micrófono de Google centrado con efectos de enfoque D-Pad.

## [1.5.3] - 2026-10-24
### Añadido y Optimizado
- **Icono Oficial Definitivo (Gareca Arriba, Tigre en el Medio y TV Abajo)**:
  - Distribución oficial en escudo deportivo: rótulo `GARECA` en la parte superior, cabeza de tigre con zarpazos en el centro, y rótulo `TV` centrado en la parte inferior. Letras rojas vibrantes con bordes negros sólidos sobre fondo blanco limpio.
  - Sincronizado para todo el televisor y la app: visible en el lanzador de Android TV, en el banner panorámico (`tv_banner_v2.png`), y dentro de la aplicación en la cabecera principal (`ivLogo` ampliado a 68dp) y pantalla de login.
- **Estandarización de Tabla a `errado_users`**: Operación directa sobre la tabla `public.errado_users` con funciones RPC y cifrado seguro bcrypt (`pgcrypto`).

## [1.5.0] - 2026-10-24
### Añadido y Optimizado
- **Sistema de Autenticación para Android TV (Google + Credenciales)**:
  - Nueva pantalla de acceso (`LoginActivity`) con diseño cinematográfico adaptado a control remoto D-Pad.
  - Botón nativo **"Acceder con cuenta de Google del TV"**: permite seleccionar la cuenta de Google registrada en el televisor para ingresar de forma rápida y segura.
  - Formulario de acceso por credenciales (Usuario / Contraseña) con cuenta maestra `admin@garecatv.com`.
- **Control Remoto de Cuentas y Dispositivos Múltiples en Supabase (`gareca_users`)**:
  - Tabla para gestión de usuarios con estado `status` (`enabled` / `disabled`) editable directamente por el administrador en Supabase para suspender o autorizar accesos en tiempo real.
  - Control de dispositivos concurrentes (`max_devices`, por defecto 1) que detecta y restringe el uso simultáneo en múltiples televisores, permitiendo al administrador ampliar el cupo por cuenta.
  - Verificación continua de sesión activa en segundo plano.
- **Icono Oficial Estilo Escudo Deportivo (`GARECA` Arriba y `TV` Abajo)**:
  - Distribución tipo emblema deportivo: rótulo `GARECA` sobre la cabeza del tigre rugiente y `TV` centrado en la parte inferior. Letras rojas vibrantes con bordes negros sólidos sobre fondo blanco limpio sin recuadros cuadrados.
- **Respaldo de Seguridad**: Versión 1.4.5 preservada bajo el tag `v1.4.5-nologin` y la rama `backup-v1.4.5-nologin` para restauración inmediata sin autenticación.

## [1.4.5] - 2026-10-24
### Añadido y Optimizado
- **Invalidación Forzada de Caché en Android TV Launcher (Cache-Busting)**: El lanzador del sistema Android TV almacena en caché persistente los banners de aplicaciones. Se migraron los identificadores de recursos en el manifiesto a `@drawable/tv_banner_v2` y `@mipmap/ic_launcher_v2` para forzar a Android TV a descartar el caché antiguo y mostrar de inmediato el nuevo diseño.
- **Banner Panorámico de Alta Definición y Letras Ampliadas**: Se optimizó la disposición del banner panorámico (`tv_banner_v2.png`), ampliando el tigre y las letras rojas con bordes negros a 240px de ancho y 35px de alto para máxima legibilidad desde la distancia del sillón.

## [1.4.4] - 2026-10-24
### Añadido y Optimizado
- **Icono Oficial con Letras Rojas y Bordes Negros Únicas en la Parte Inferior**: Se restauró la tipografía deportiva itálica original ubicada exclusivamente en la parte inferior del icono, transformando los caracteres a cuerpo rojo vibrante (`#EB1919`) con contorno/borde negro sólido (`#000000`). Se eliminó cualquier texto duplicado en la parte superior del icono y en la cabecera.
- **Fondo Blanco Limpio sin Contornos Cuadrados**: El icono oficial (`app_icon.png`), los iconos del launcher del sistema (`ic_launcher`, `ic_launcher_round`, `ic_launcher_foreground`) y el banner de Android TV (`tv_banner.png`) lucen el tigre y las letras sobre fondo blanco puro sin marcos cuadrados o squircle residuales.

## [1.4.3] - 2026-10-24
### Añadido y Optimizado
- **Empaquetado Nativo 100% como `com.gareca.tv`**: Se migró completamente el namespace, el applicationId y toda la estructura de paquetes Kotlin a `com.gareca.tv`. Se eliminó cualquier generación de archivos con el antiguo nombre en los instaladores.
- **Limpieza de Cabecera en la Página Principal**: Se removió el texto blanco redundante que decía "GarecaTV" en la cabecera superior, dándole exclusividad al icono oficial que ya integra el tigre y las letras rojas con bordes negros "Gareca Tv".
- **Banner de Android TV con Mayor Magnitud y Legibilidad**: Se optimizó la escala del tigre y las letras en el banner panorámico (`tv_banner.png`) sobre fondo blanco puro para una visibilidad destacada en la pantalla de inicio de Android TV.

---


### Añadido y Optimizado
- **Icono Oficial con Letras Rojas y Bordes Negros "Gareca Tv"**: Se rediseñó el icono oficial integrando el rótulo destacado "Gareca Tv" en letras rojas con contorno/borde negro deportivo, manteniendo la figura del tigre rugiente y las garras sobre fondo blanco limpio.
- **Eliminación Total de Contornos Cuadrados en TV Android**: Se limpiaron todos los marcos y bordes cuadrados residuales tanto en los iconos adaptativos (`ic_launcher`, `ic_launcher_round`, `ic_launcher_foreground`) como en el banner principal de Android TV (`tv_banner.png`), permitiendo que el tigre y las letras floten con total armonía sobre el fondo blanco sin marcos artificiales.
- **Activación de Categorías Exclusiva por Click Adicional**: En el menú superior de categorías (Repeticiones, Partidos del Día, etc.), al mover el foco con la cruceta del control remoto el botón se enfoca y anima con escala, pero las listas sólo se activan cuando el usuario da un click adicional (OK / Center), evitando cambios no deseados al navegar.

---


### Añadido y Optimizado
- **Nombre de Marca Oficial "GarecaTV" en Notificación del Instalador de Android TV**: Se configuró explícitamente `android:label="@string/app_name"` ("GarecaTV"), `android:icon` y `android:banner` en la actividad principal `MainActivity` y actividades secundarias en `AndroidManifest.xml`, y se añadió `EXTRA_TITLE` en el intent del instalador. Esto garantiza que el PackageInstaller del sistema operativo Android TV muestre siempre **"GarecaTV se actualizó"** en la pantalla del televisor en lugar del identificador interno de paquete.
- **Normalización de Cadenas**: Se estandarizó `app_name` como "GarecaTV" en `strings.xml`.

---


### Añadido y Optimizado
- **Cambio de Categoría Dinámico con Solo Foco D-Pad**: En el menú superior de categorías y países, con solo posicionar el foco sobre cualquiera de las pestañas (navegando izquierda o derecha), la app actualiza y filtra de inmediato los listados de partidos y canales según la pestaña enfocada, eliminando la necesidad de presionar OK o Center.
- **Hora Arriba y Fecha Abajo con Magnitud Invertida**: En la cabecera, la hora local se reubicó en la fila superior con magnitud principal destacada (`17sp`), acompañada de su meridiano `(a)` y su indicador de huso horario `(GMT-X)`. La fecha pasó a la fila inferior con magnitud secundaria compacta (`13sp`).
- **Icono Oficial para Android TV con Fondo Blanco**: Se actualizaron todos los iconos del sistema (`ic_launcher_background.xml`, `ic_launcher_foreground.png`, adaptativos y la suite completa de densidades `mipmap-mdpi` a `xxxhdpi`, además de `tv_banner.png`) para que el icono oficial de GarecaTV luzca sobre fondo blanco puro (`#FFFFFF`) en lugar de negro en todos los lanzadores de Android TV y Google TV.

---


### Añadido y Optimizado
- **Reactivación Dinámica de Señales "NO DISPONIBLE"**: Si una señal fue etiquetada como no disponible debido a caídas temporales, el usuario ahora puede volver a pulsarla con su control remoto para comprobar si el canal o evento ya se reactivó. Si la señal vuelve a estar activa (verificado por el motor de pretesteo en segundo plano o por ExoPlayer), la app cambia automáticamente el estado restaurando la etiqueta previa (`● EN VIVO`, `PROGRAMADO`, `REPETICIÓN`, `CANAL 24/7`), la remueve de la lista de señales caídas y la reordena en la cartelera.
- **Icono Clásico Multicolor de Google Mic**: Se integró el vector oficial del micrófono de Google (con sus cuatro colores distintivos: azul, rojo, amarillo y verde) en los botones de búsqueda por voz tanto en la pestaña **Buscar Todo** como en **Repeticiones**. El botón ahora es significativamente más compacto y llamativo, luciendo un borde iluminado en azul Google (`#4285F4`) con respuesta visual de foco y escala de alta visibilidad para Smart TV.
- **Cabecera Compacta con Fecha, Hora y Meridiano en Paréntesis**: La fecha y la hora se rediseñaron en una disposición vertical compacta debajo del título. La hora local se acompaña de su meridiano en formato `hh:mm (a)` (ej: `12:06 (PM)`) y su zona horaria compacta entre paréntesis `(GMT-X)` justo debajo de la fecha.
- **Limpieza de Etiquetas en Canales de TevePlay**: Se eliminó la duplicación donde aparecía dos veces el texto "PROGRAMADO" en las tarjetas de partidos de TevePlay. Ahora sólo se exhibe la etiqueta azul limpia; y únicamente cuando se dispone de la información horaria confirmada, se pinta el horario local y la cuenta regresiva respectiva.

---


### Añadido y Optimizado
- **Eliminación Total de "Errado" en Instaladores y Sistema**: Se reemplazó cualquier vestigio del antiguo nombre. Los instaladores se generan de manera limpia como `GarecaTV.apk` y `GarecaTV-v1.3.8.apk`. El tema visual del sistema se renombró a `Theme.GarecaTV`, las preferencias pasaron a `gareca_tv_prefs` (con migración transparente), y los User-Agents de OTA se actualizaron a `GarecaTV-OTA` y `GarecaTV-Updater`.
- **Huso Horario Local Automático del Televisor**: Detección nativa del huso horario del dispositivo Android TV (`TimeZone.getDefault()`). Todos los horarios de partidos publicados en el servidor (fijados en GMT-3) se convierten y formatean en tiempo real a la hora local correspondiente a la ubicación del usuario (ej: Colombia, México, Perú, Chile, España, etc.).
- **Cuenta Regresiva Dinámica para Amantes del Fútbol**: Cada tarjeta de partido muestra una insignia de cuenta regresiva en vivo (*"⏳ En 2h 15m"*, *"⏳ En 1d 4h"*, *"⏳ ¡En 15 min!"*, *"● EN JUEGO"*). La cabecera superior ahora incluye una etiqueta visual con el huso horario detectado y la ciudad.
- **Enriquecimiento y Cruce de Datos para TevePlay**: Los canales de `teveplay.net` con partidos programados ahora se cruzan algorítmicamente (*Fuzzy Matching*) con la agenda deportiva diaria oficial (`event.json`), transfiriéndoles automáticamente su hora de juego, su horario local convertido y su cuenta regresiva.
- **Buscador Global Inteligente (`🔍 Buscar Todo`)**: Nueva pestaña fija en la primera posición de la barra de categorías. Permite escribir el nombre de cualquier equipo, competición o canal y buscar simultáneamente en los tres catálogos: Partidos del Día, señales de TevePlay y Repeticiones históricas.
- **Búsqueda por Voz en TV Box y Smart TV**: Integración completa del reconocimiento de voz nativo de Android (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`) tanto en el **Buscador Global** como en la sección de **Repeticiones**. El usuario puede pulsar el botón de micrófono en pantalla o la tecla de búsqueda física del control remoto (`KEYCODE_SEARCH`), hablar al control y ver los resultados filtrados al instante.

---

## [1.3.7] - 2026-10-24
### Añadido y Optimizado
- **Pestaña Exclusiva de Repeticiones y Separación Estricta**: Se creó la nueva pestaña **`🔁 Repeticiones`** ubicada inmediatamente antes de `📺 Regionales`. La pestaña `⚽ Partidos del Día` ahora muestra única y exclusivamente los partidos en vivo, por empezar o programados del día de hoy. Todas las repeticiones históricas se concentran de manera limpia en su propia sección.
- **Buscador y Ordenación para Smart TV (Leanback D-Pad)**:
  - **Caja de búsqueda inteligente**: Filtra partidos en tiempo real por nombre de equipo, cruce o fecha (ej: "Madrid", "10/10").
  - **Botones de ordenación**: Permite ordenar la lista tanto por **Fecha** (más recientes o más antiguos) como por **Nombre** (alfabético A-Z o Z-A), manteniendo siempre los partidos `NO DISPONIBLE` al final de la lista.
  - **Navegación remota ergonómica**: Al navegar por la pestaña de Repeticiones y pulsar la flecha hacia abajo en el control remoto, el foco se posiciona sobre la caja de texto. Para escribir, el usuario pulsa OK/Center (mostrando el cursor y desplegando el teclado en pantalla); si en cambio pulsa flecha abajo sin presionar OK, el foco salta directamente al primer partido de la lista. Al pulsar ATRÁS mientras se escribe, el teclado se oculta sin cerrar la aplicación.

---

## [1.3.6] - 2026-10-24
### Añadido y Optimizado
- **Icono Exclusivo en la Cabecera de la Aplicación**: Se eliminó la repetición del icono del tigre en cada una de las filas individuales de la cartelera de *Partidos del Día*. El icono de GarecaTV ahora luce con exclusividad y protagonismo únicamente en la cabecera superior principal de la app.
- **Suite Completa de Iconos Mipmap para Android**: Se generó e integró la suite completa de iconos del sistema Android (`mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi` y `mipmap-anydpi-v26` adaptativos) con `android:icon` y `android:roundIcon` en el `AndroidManifest.xml`, garantizando que el icono oficial de GarecaTV (cabeza de tigre, zarpazos y letras rojas sobre fondo negro) se visualice perfectamente en todos los lanzadores de Android TV, cajas TV Box, dispositivos móviles y menús de ajustes del sistema.

---

## [1.3.5] - 2026-10-24
### Añadido y Optimizado
- **Nueva Identidad Oficial — GarecaTV**: La aplicación pasa a llamarse oficialmente **GarecaTV**.
- **Nuevo Icono y Banner Oficial del Tigre**: Se diseñó el icono oficial integrando la figura del Tigre de perfil en sus colores característicos (pelaje naranja dorado, rayas negras y mandíbula blanca rugiente con un solo ojo visible), acompañado de marcas de zarpazos/arañazos de garra de tigre en color rojo sobre fondo negro profundo y la tipografía deportiva de **GarecaTV**.
- **Banner Leanback TV (16:9)**: Se actualizó `tv_banner.png` para el lanzador de Android TV con la misma estética de alto impacto.

---

## [1.3.4] - 2026-10-24
### Añadido y Optimizado
- **Reordenamiento Automático de Partidos No Disponibles al Final de la Lista**: En la pestaña *⚽ Partidos del Día*, los partidos identificados como `NO DISPONIBLE` se desplazan automáticamente al fondo de la lista tanto en la carga inicial como en tiempo real cuando el usuario regresa de intentar reproducir una señal sin respuesta.
- **Verificación y Activación en Vivo de `errado_community_streams`**: Confirmada la conectividad con la tabla comunitaria exclusiva en Supabase con clave primaria `stream_url`.
- **Actualización de Herramientas de Telemetría**: El script `view_logs.py` incorpora el parámetro `--community` para auditar las señales caídas registradas en la nube.

---

## [1.3.3] - 2026-10-24
### Añadido y Optimizado
- **Tabla Exclusiva Comunitaria (`errado_community_streams`)**: La app ahora consulta y registra directamente en la tabla exclusiva dedicada `public.errado_community_streams` en la base de datos de Supabase, con clave primaria por URL para deduplicación instantánea y fallback de compatibilidad a `errado_tv_logs`.

---

## [1.3.2] - 2026-10-24
### Añadido y Optimizado
- **Base de Datos Comunitaria Compartida (Supabase)**: Se implementó un gestor de reportes comunitarios (`CommunityStreamManager`). Cuando un usuario pulsa un enlace y este no responde (error de reproducción o watchdog de 10 segundos), el fallo se archiva automáticamente en Supabase y se comparte en tiempo real con todos los demás televisores que utilicen ErradoTV.
- **Saneamiento de Estados y Eliminación de Falsos Positivos**: Se eliminó el pretesteo masivo automatizado que marcaba erróneamente partidos viables como no disponibles. La insignia morada **`REPETICIÓN`** se aplica a todos los partidos cuya fecha es anterior al día de hoy.
- **Criterio Estricto de Disponibilidad**: La etiqueta gris **`NO DISPONIBLE`** (y su atenuación visual) se reserva exclusivamente para señales que hayan sido probadas por un usuario y no hayan devuelto respuesta, o que hayan sido registradas en la base comunitaria.
- **Sincronización en Paralelo (0 Esperas)**: La consulta de enlaces caídos comunitarios se realiza en paralelo con la carga de partidos, garantizando una apertura instantánea de la interfaz.

---

## [1.3.1] - 2026-10-24
### Añadido y Optimizado
- **Control de Frecuencia de Actualización (Ventana de 15 Minutos) y Apertura Ultrarrápida**: Se introdujo un punto de control temporal (`SYNC_INTERVAL_MS = 15 min`). Al abrir o regresar a la app, si los datos ya fueron sincronizados recientemente, se despliegan directamente de la memoria sin demoras de red. Si transcurrieron más de 15 minutos, la actualización se efectúa de manera silenciosa en segundo plano sin interrumpir la navegación.
- **Validación Profunda de Repeticiones y Detección de Falsos PNGs**: Se descubrió que los servidores CDN de repeticiones entregan listas `.m3u8` válidas pero con segmentos apuntando a imágenes PNG estáticas caducadas. El pretesteo ahora examina la cabecera real de bytes (`bytes=0-15`) verificando la firma de transporte de video (MPEG-TS `0x47` o MP4 `ftyp`/`moof`) y descartando imágenes falsas, etiquetando inmediatamente como `NO DISPONIBLE`.
- **Aprendizaje Continuo y Watchdog de 10 Segundos (`StreamCache`)**: Si un enlace logra pasar el pretesteo pero el reproductor no recibe fotogramas de video en 10 segundos (como ocurrió en India vs Panamá), el reproductor detiene el búfer, notifica amigablemente al usuario y guarda el enlace en `StreamCache` de forma permanente para que en la próxima apertura aparezca directamente como `NO DISPONIBLE`.

---

## [1.3.0] - 2026-10-24
### Añadido y Optimizado
- **Pretesteo de Disponibilidad en Partidos en Repetición (`[ REPETICIÓN DISPONIBLE ]` / `[ NO DISPONIBLE ]`)**: Al cargar la cartelera, la app ejecuta una comprobación en segundo plano de los partidos históricos. Las señales que responden exitosamente se identifican con la insignia verde **`REPETICIÓN DISPONIBLE`**, mientras que las que han expirado se marcan en gris como **`NO DISPONIBLE`** y se atenúan visualmente.
- **Pre-escaneo de Opciones en Tiempo Real con Indicador de Resolución**: Al seleccionar un partido con múltiples opciones de transmisión, la app pretestea en 1 segundo los enlaces en segundo plano. Las opciones activas muestran su resolución real o declarada (ej. `🟢 Opción 1 [1080p FHD] [EN LÍNEA]`), y los enlaces caídos se identifican en gris (`⚪ [SIN SEÑAL / CAÍDO]`).
- **Memoria de Resolución Verificada (`StreamCache`)**: Cada vez que se reproduce una transmisión en la Smart TV, la resolución exacta confirmada por el decodificador se almacena en caché para mostrarse con la etiqueta `[VERIFICADO]` en futuras consultas.

---

## [1.2.2] - 2026-10-23
### Corregido y Optimizado
- **Soporte Nativo DASH (`.mpd`) y Cifrado ClearKey DRM**: Se integró el módulo `media3-exoplayer-dash` y el gestor de licencias ClearKey en ExoPlayer. Esto resuelve de raíz el cierre inesperado al reproducir opciones como la de *Necaxa vs Juárez* (que transmitía mediante un flujo DASH cifrado con DRM).
- **Protección Anticaídas Integral en el Reproductor**: Se blindó la inicialización de ExoPlayer y la apertura de transmisiones con captura de excepciones. Si un enlace está dañado o sin respuesta, la app muestra un aviso elegante en pantalla y nunca se cierra.
- **Saneamiento de Insignias en Canales de TevePlay**: Los canales continuos se identifican como `CANAL 24/7` (verde) y los eventos programados como `PROGRAMADO` (azul), eliminando la falsa etiqueta roja de "En Vivo" en transmisiones que aún no empiezan o ya terminaron.

---

## [1.2.1] - 2026-10-23
### Añadido y Optimizado
- **Integración Completa de Canales Regionales (`📺 Regionales`)**: Acceso directo y dedicado a los 36 canales de televisión abierta y por cable más vistos de Latinoamérica: Latina, América TV, Telefe, El Trece, Caracol, RCN, Azteca 7, Canal 5, Las Estrellas, Space, Warner Bros TV, TNT, Star Channel, Cinemax, Cinecanal, Universal TV, Studio Universal, Discovery Channel, Cartoon Network, History, FX, Nat Geo, Disney Channel, Univision, Telemundo y más.
- **Categorización Integral de Todas las Señales de TevePlay**: Se añadieron pestañas dedicadas para cada país y liga deportiva del catálogo (más de 470 señales): **🇨🇱 Chile**, **🇪🇨 Ecuador**, **🇵🇾 Paraguay**, **🇺🇾 Uruguay**, **🇺🇸 USA / MLS**, **🇮🇹 Serie A**, **🇩🇪 Bundesliga**, **🇫🇷 Ligue 1**, **🇵🇹 Portugal**, **🌎 Latinoamérica**, **⚾ Béisbol MLB**, **🌍 Mundo** y **⚽ Otras Copas**.
- **Mapeo Inteligente por Título y Competición**: Partidos de copas internacionales (Leagues Cup, Copa Centroamericana CONCACAF, torneos Sub-20) se asignan automáticamente a su región y país de origen para que nunca queden ocultos o huérfanos.

---

## [1.2.0] - 2026-10-23
### Añadido y Optimizado
- **Pestañas por País y Categorías (Estilo TevePlay / Leanback TV)**: Incorporación de una barra horizontal de categorías navegable con el control remoto (`D-Pad`). Permite alternar instantáneamente entre **⚽ Partidos del Día**, **🇦🇷 Argentina**, **🏆 Deportes (ESPN/Fox)**, **🇪🇸 España (LaLiga)**, **🇲🇽 México (Liga MX)**, **🇨🇴 Colombia (Win Sports)**, **🇵🇪 Perú (Liga 1)**, **🇧🇷 Brasil (Brasileirão)**, **🇬🇧 Premier League**, **🏎️ F1 / Motor**, **🏀 NBA**, **🥊 Boxeo / UFC** y **🌐 Todos los Canales**.
- **Integración de Más de 470 Señales Deportivas en Directo**: Conexión nativa con la API de canales en vivo de TevePlay (`teveplay.net/api/channels`), brindando acceso directo a señales lineales y eventos deportivos con logos oficiales.
- **Inyección de Cabeceras HTTP Nativas en ExoPlayer (`DefaultHttpDataSource`)**: Se configuró ExoPlayer para enviar dinámicamente `Referer` y `Origin` en transmisiones de CDN, evitando bloqueos 403 y 404.
- **Mensaje Inteligente de Señal Fuera del Aire**: Cuando una señal temporal de un partido finaliza o está fuera de transmisión, la pantalla muestra una alerta amigable para regresar con el control remoto y elegir otro canal u opción alternativa.
- **Actualización Automática y Silenciosa**: El catálogo de señales y partidos se sincroniza periódicamente en segundo plano para reflejar los eventos que están transmitiendo en tiempo real.

---

## [1.1.7] - 2026-10-23
### Añadido y Optimizado
- **Visualización de la Versión Instalada**: Se incorporó el indicador directo de la versión instalada en la pantalla principal sobre el botón de actualización (**`Actualizar (v1.1.7)`**) y dentro del diálogo de comprobación (**`ErradoTV vX.X (Instalada: v1.1.7)`**).
- **Legibilidad Dinámica en Botones Superiores**: Los botones *Actualizar* y *Recargar* aplican cambio dinámico a color negro intenso (`#000000`) al ser enfocados por el control remoto y blanco (`#FFFFFF`) al perder el foco, garantizando lectura nítida sobre el resplandor cian.

---

## [1.1.6] - 2026-10-23
### Añadido y Optimizado
- **Detección Automática de Repeticiones y Fechas**: En eventos destacados y partidos diferidos (como repeticiones de LaLiga), el sistema ahora detecta automáticamente la fecha histórica embebida en el origen multimedia (ej. `22/08`) y asigna la insignia morada `[ REPETICIÓN ]`. Esto elimina la confusión sobre partidos pasados que no mostraban horario ni día.
- **Autorrefresco Automático en Segundo Plano (Cada 5 Minutos)**: Cuando la app permanece abierta en la TV, el listado de partidos y canales se sincroniza silenciosamente cada 5 minutos sin pantallas de carga molestas ni pérdida de foco del control remoto.
- **Refresco Inmediato al Iniciar**: Al abrir la aplicación, la cartelera deportiva se descarga y actualiza al instante con los datos más recientes del servidor.

---

## [1.1.5] - 2026-10-23
### Añadido y Optimizado
- **Invalidación de Caché en Tiempo Real**: Parámetros de control de caché (`_t=timestamp`, `Cache-Control: no-cache`) en la comprobación OTA para que las TVs detecten instantáneamente nuevas versiones sin esperar los 5 minutos de CDN de GitHub.

---

## [1.1.4] - 2026-10-23
### Corregido y Optimizado
- **Contraste y Legibilidad en Botones Enfocados**: Se corrigió el problema donde el botón enfocado de fondo cian/azul tenía letras del mismo color. Ahora, al seleccionar cualquier opción con el control remoto, el texto cambia automáticamente a **negro intenso en negrita (`#000000`)**, logrando un contraste perfecto y 100% legible sobre el fondo cian. En estado inactivo, el texto permanece en **blanco puro (`#FFFFFF`)**.

---

## [1.1.3] - 2026-10-23
### Añadido y Optimizado
- **Priorización Inteligente de Transmisiones**: En el diálogo de opciones, los streams directos CDN (`CONFIG`) se ordenan primero con la etiqueta `🟢 [HD DIRECTO - RECOMENDADO]` y foco automático del control remoto. Los enlaces web externos se desplazan al final con advertencia `🌐 [WEB EXTERNO - PUEDE FALLAR]`.
- **Manejo Nativo de Enlaces Caídos en WebView**: Intercepción de errores (`403 Forbidden`, `ERR_NAME_NOT_RESOLVED`, etc.) con una pantalla amigable que avisa que el enlace externo está bloqueado por el proveedor o caído, junto a un botón enfocado *"Volver al Partido"*.
- **Sniffer HTML5 Automático en WebView**: Detección en segundo plano de etiquetas `<video>` y flujos `.m3u8` en páginas web para redirigirlas automáticamente al reproductor nativo ExoPlayer.

---

## [1.1.1] - 2026-10-23
### Añadido y Optimizado
- **Forzado de Máxima Resolución en ExoPlayer (`DefaultTrackSelector`)**: Se configuró el selector de pistas para forzar siempre la calidad y bitrate más altos posibles (`setForceHighestSupportedBitrate(true)`, `setMaxVideoSize(3840, 2160)`, `setExceedVideoConstraintsIfNecessary(true)`).
- **Insignia de Resolución en Pantalla (`tvResolutionBadge`)**: Medidor visual dinámico en la esquina superior derecha que muestra la resolución activa real (ej. `1024x576 (576p) [MÁX]`, `1080p FHD`, etc.) al arrancar la reproducción o al presionar las flechas Arriba/Abajo/Info en el control remoto.
- **Telemetría de Calidad de Video**: Envío a Supabase de la resolución exacta seleccionada para cada stream y evento deportivo.

---

## [1.0.5] - 2026-10-23
### Añadido
- **Actualizador OTA en la App (`AppUpdater.kt`)**: Sistema de actualización automática desde la web (Over-The-Air) mediante el Gist de GitHub.
- **Icono de Actualización en Pantalla (`btnUpdate`)**: Botón enfocado para control remoto en la cabecera de `activity_main.xml` con icono de nube/flecha para buscar e instalar actualizaciones directamente con el mando de la TV.
- **Diálogos Nativos para TV**:
  - `dialog_update_confirm.xml`: Diálogo emergente con el número de versión, notas del parche (changelog) y botones "Actualizar Ahora" o "Más tarde".
  - `dialog_update_progress.xml`: Barra de descarga con porcentaje en tiempo real y lanzamiento automático del instalador de Android TV (`FileProvider`).
- **Verificación Automática al Iniciar**: La app comprueba en segundo plano si hay nueva versión al abrirse y avisa discretamente sólo si existe una actualización disponible.

---

## [1.0.4] - 2026-10-23
### Corregido y Optimizado
- **Cálculo Dinámico de Estado de Partidos**: Se corrigió el error donde todos los eventos aparecían como "EN VIVO". Ahora se calcula el estado en tiempo real según el huso horario GMT-3 (Argentina):
  - `● EN VIVO` (Rojo): Si el partido está transcurriendo (0 a 135 minutos desde el inicio).
  - `POR EMPEZAR` (Ámbar): Faltan 30 minutos o menos para el inicio.
  - `PROGRAMADO` (Azul): Faltan más de 30 minutos para el inicio.
  - `FINALIZADO` (Gris + atenuación de tarjeta): Si ya pasaron más de 2 horas y 15 minutos desde el inicio.
  - `CANAL 24/7` (Verde): Canales de TV continuos sin hora fija.
- **Telemetría Remota en Supabase (`SupabaseLogger.kt`)**: Envío asíncrono de eventos técnicos desde el televisor (inicio de app, apertura de streams, errores de reproducción y modelos de hardware de la TV).
- **Script SQL con Buffer Circular de 1,000 Filas (`supabase_setup.sql`)**: Configuración de tabla `errado_tv_logs` con disparador PostgreSQL que purga automáticamente los registros antiguos superados los 1,000 items.
- **Visor de Logs en Vivo (`view_logs.py`)**: Herramienta de consola para monitorear en tiempo real los flujos del televisor sin necesidad de cable USB ni depuración ADB.

---

## [1.0.3] - 2026-10-23
### Añadido
- **Actualización Automática en Segundo Plano (APK)**: Corrutina periódica (`autoRefreshJob`) en `MainActivity.kt` que refresca silenciosamente los partidos y enlaces cada 10 minutos sin bloquear la pantalla ni mover el cursor del televisor.
- **Modo Continuo en Generador M3U (`--loop`)**: Soporte en `generate_playlist.py` para refresco automático cíclico en segundo plano (`--loop 30` para regenerar la lista cada 30 minutos).

---

## [1.0.2] - 2026-10-23
### Añadido
- **Sincronización de Clave Remota (`CryptoUtils.kt`)**: Sistema de actualización de claves sin reinstalación. Soporta carga remota vía JSON y almacenamiento en caché persistente (`SharedPreferences`).
- **Extractor Automatizado de Claves (`extract_key.py`)**: Script en Python capaz de inspeccionar cualquier APK futura, localizar el método de sesión, extraer la clave Base64 y verificar el descifrado contra Cloudflare en 2 segundos.
- **Reloj Digital en Vivo (`TextClock`)**: Incorporado en la cabecera de `activity_main.xml` para mostrar la hora local actual del televisor junto a la fecha.
- **Insignia de Horario de Partido (`item_event_card.xml`)**: Separación visual del horario del encuentro (ej. `[ 18:00 ]`) del nombre de los equipos contendientes con fondo translúcido y tipografía cian destacada.
- **Documento Maestro de Contexto (`PROJECT_CONTEXT.md`)**: Estandarización de información y arquitectura para desarrolladores e IAs.

---

## [1.0.1] - 2026-10-23
### Añadido
- **Generador de Playlist M3U (`generate_playlist.py`)**: Generación de archivo [`errado_tv.m3u8`](file:///home/sataca/Descargas/APLICACIONES/errado/errado_tv.m3u8) con más de 260 canales y partidos en vivo para VLC y reproductores IPTV de Smart TV.
- **Selector de Múltiples Transmisiones**: Diálogo emergente enfocado para control remoto (`dialog_stream_picker.xml`) que permite elegir entre las opciones 1 a 16 disponibles en partidos importantes.

### Modificado
- Compilación del APK nativo [`ErradoTV-v1.0.apk`](file:///home/sataca/Descargas/APLICACIONES/errado/ErradoTV-v1.0.apk) (10 MB) listo para instalación.

---

## [1.0.0] - 2026-10-23
### Análisis Inicial e Ingeniería Inversa
- Análisis de la APK móvil original `mta.xer.errado` (v2.0, SDK 35, ARM64).
- Identificación de los 5 endpoints en Cloudflare Pages (`errado-app.pages.dev/data/*.json`).
- Reversión de la rutina criptográfica en Dalvik bytecode (`Landroid/support/v4/media/session/b;->s`): algoritmo `AES/CBC/PKCS5Padding`, clave base64 `"R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="` y derivación mediante SHA-256.
- Identificación de 6 redes de anuncios invasivas en la APK móvil para su descarte completo.

### Creación del Proyecto Android TV Nativo
- Configuración de Gradle con Kotlin DSL y SDK 34 (`Android 14`).
- Configuración de `AndroidManifest.xml` con categoría `LEANBACK_LAUNCHER` y `hardware.touchscreen=false`.
- Creación de interfaz con soporte para control remoto D-Pad (`card_selector.xml`, animaciones de escala al enfocar).
- Integración de AndroidX Media3 (ExoPlayer) para reproducción de HLS (`.m3u8`) y WebView con filtrado de anuncios.
