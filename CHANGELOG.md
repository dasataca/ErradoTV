# 📜 Historial de Cambios y Avances — GarecaTV

Todas las modificaciones, análisis y versiones del proyecto quedan documentadas en este archivo según el estándar de desarrollo.

## [1.4.1] - 2026-10-24
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
