# 🤖 AGENTS.md — Protocolo y Contexto Integral para Inteligencias Artificiales

> **Audiencia**: Agentes de IA autónomos (Antigravity, Cursor, Claude 3.7 Sonnet, Copilot, ChatGPT) y desarrolladores.  
> **Objetivo**: Proveer un contexto completo, exacto e inmutable sobre la arquitectura, ingeniería inversa, endpoints, cifrado y estándares de codificación de **GarecaTV** (anteriormente ErradoTV).

---

## 1. Misión del Proyecto y Restricciones Absolutas

1. **Sin Publicidad**: Prohibido bajo cualquier circunstancia importar SDKs de monetización publicitaria (AdMob, AppLovin, Vungle, Clever Ads Solutions, UnityAds, etc.).
2. **Identidad Visual Oficial — GarecaTV**:
   * Icono y banner oficial con cabeza de tigre en perfil (con un solo ojo visible y mandíbula rugiente en colores vibrantes), zarpazos de garras en rojo y letras rojas sobre fondo negro profundo.
   * Icono de launcher para todo el sistema Android: suite completa de densidades en `res/mipmap-*` e icono adaptativo en `mipmap-anydpi-v26` (`ic_launcher.xml` e `ic_launcher_round.xml`).
   * El icono del tigre solo se muestra en la cabecera superior principal de la pantalla, manteniendo las filas de partidos limpias y sin repeticiones innecesarias.
3. **Experiencia Nativa para TV (Leanback D-Pad)**:
   * Toda la navegación debe ser 100% operable con control remoto (D-Pad: Arriba, Abajo, Izquierda, Derecha, OK/Center, Back).
   * La app debe mantener `android.hardware.touchscreen=false` y la categoría `LEANBACK_LAUNCHER`.
   * Los elementos enfocables deben utilizar animaciones de escala y selectores visuales (`card_selector.xml`, `button_focus_selector.xml`, `search_box_selector.xml`).
4. **Organización de Catálogos y Búsqueda en TV**:
   * **⚽ Partidos del Día**: Muestra única y exclusivamente los partidos en vivo, por empezar o programados de la fecha de hoy.
   * **🔁 Repeticiones**: Pestaña dedicada situada inmediatamente antes de `📺 Regionales`, con buscador en tiempo real y botones de ordenación por Fecha (recientes/antiguos) y Nombre (A-Z/Z-A).
   * **🔍 Buscar Todo**: Pestaña consolidada inicial para buscar en eventos del día, señales en vivo y repeticiones simultáneamente, con botón de ordenamiento por Nombre (`btnSortNameGlobal`).
   * **Navegación Ergonómica, Botón "✕" Interno y Búsqueda Tokenizada**:
     * **Disposición**: Botón de micrófono a la izquierda, caja de texto unificada con botón interno "✕" (`btnClearSearchText`/`btnClearGlobalText`) al centro, y botón de la Lupa dedicado a la derecha.
     * **Botón "✕" Interno**: Embebido dentro del borde de la caja de texto. Visible únicamente si hay texto. Al enfocarse con D-Pad y pulsar OK, vacía el texto, actualiza los resultados e invoca inmediatamente el teclado virtual con el cursor activo.
     * **Búsqueda Tokenizada Multidisjuntiva**: Divide las consultas por espacios en blanco (`tokens.all { target.contains(it) }`), garantizando que búsquedas compuestas como "racing boca" o "boca racing" coincidan correctamente en repeticiones y catálogo global.
     * **Sugerencias en Tiempo Real**: Al ingresar 2 o más caracteres, se despliegan sugerencias tokenizadas (`rvSuggestions`).
     * **Captura Pre-IME de Tecla Atrás**: `SearchEditText` sobrescribe `onKeyPreIme()` para capturar la tecla Atrás antes de que el teclado virtual la consuma silenciosamente. Oculta el teclado y transfiere el foco al primer elemento de la lista de sugerencias.
     * **Filtrado Directo y Foco en Resultados**: Al seleccionar una sugerencia con OK/Center, se oculta y vacía la lista, se ejecuta el filtrado instantáneo del catálogo y el foco se transfiere directamente a la primera tarjeta de resultado (`focusFirstEventItem`).
     * **Descarte de Sugerencias**: Si el usuario está enfocado en las sugerencias y pulsa Atrás, la lista se oculta y el foco regresa a la caja de texto.
     * **Resiliencia Touch Mode**: `rvSuggestions` y cada fila (`item_search_suggestion.xml`) implementan `android:focusableInTouchMode="true"` para garantizar que el foco sea aceptado tras cerrar el teclado en Android TV.
   * **Resiliencia de Disponibilidad**: Los partidos etiquetados como `NO DISPONIBLE` se desplazan siempre de forma automática al fondo de la lista (los canales 24/7 nunca se etiquetan como `NO DISPONIBLE`).
5. **Reproducción de Video Nativa y Zapping en TV**:
   * Prioridad absoluta a **AndroidX Media3 ExoPlayer** para reproducir flujos HLS (`.m3u8`).
   * Configuración de `DefaultTrackSelector` forzando siempre la máxima resolución (`setMaxVideoSize(3840, 2160)`) y bitrate disponible (`setForceHighestSupportedBitrate(true)`).
   * **Zapping de Canales con D-Pad**: En categorías de canales de flujo único (`📺 Regionales`, `🏆 Deportes`, países, etc.), las teclas `◀ Izquierda` y `▶ Derecha` del control remoto ejecutan zapping directo entre canales (`ChannelNavigationManager`) reutilizando la instancia de ExoPlayer.
   * **Banner Informativo de Canal (`llChannelBanner`)**: Al ingresar a un canal o cambiar de señal, se despliega en la parte superior el logo, nombre del canal, subtítulo y contador de posición (ej. `Canal 5 de 42  [◀ ▶ Zapping]`), auto-ocultándose a los 3.5 segundos.
   * **Invocación Contextual con Teclas Arriba / Abajo**: Al presionar `▲ Arriba` o `▼ Abajo` durante la reproducción, se reactiva el banner informativo y se despliegan los controles multimedia nativos del reproductor.
   * **Control de Zoom y Relación de Aspecto en Pantalla (`AspectRatioFrameLayout`)**:
     * **Disponibilidad Condicional**: La opción de zoom solo está disponible y visible cuando el reproductor ha recibido cuadros de video válidos (`videoFrameReceived = true` con `videoSize > 0`). Si el canal está cargando o es solo audio, el botón se oculta automáticamente.
     * **Botón Interactivo en Banner (`btnZoomMode`)**: Embebido a la derecha del banner superior con selector visual Leanback (`button_focus_selector.xml`). Al presionar `▲ Arriba`, el banner se despliega y transfiere el foco al botón de Zoom para accionarlo con OK/Center.
     * **Atajos de Control Remoto TV**: Las teclas `MENU`, `BUTTON_Y`, `KEYCODE_ZOOM (168)` y `PROG_BLUE` conmutan el modo de pantalla de forma inmediata sin necesidad de abrir menús.
     * **Tres Modos Cíclicos de Pantalla**:
       1. `RESIZE_MODE_FIT` (**Ajuste Original**): Mantiene la relación de aspecto nativa de la señal (ideal para emisiones estándar con formato original).
       2. `RESIZE_MODE_FILL` (**Rellenar 16:9**): Estira la imagen horizontal y verticalmente cubriendo todo el panel del televisor sin franjas negras.
       3. `RESIZE_MODE_ZOOM` (**Zoom / Recorte**): Llena completamente la pantalla recortando proporcionalmente los bordes superior/inferior o laterales sin deformar la imagen (ideal para señales 4:3 o transmisiones con barras embebidas).
     * **Feedback Visual Inmediato (HUD Flotante `tvZoomHud`)**: Cada conmutación de zoom despliega un cartel informativo flotante en color de acento durante 2.5 segundos (*"🔍 Pantalla: Ajuste Original (Fit)"*, *"🔍 Pantalla: Rellenar Pantalla (16:9)"*, *"🔍 Pantalla: Zoom / Recorte Completo"*).
   * **Resiliencia ante Micro-Cortes HLS (Seamless Auto-Reconnect)**: Ante caídas transitorias de paquetes o timeouts HLS (`ERROR_CODE_IO_UNSPECIFIED`), está prohibido marcar la señal como caída al primer fallo. El reproductor debe ejecutar auto-reintentos silenciosos y absorción por búfer (`DefaultLoadControl`) antes de declarar un canal caído.
6. **Resiliencia, Actualización OTA y Botones de Cabecera**:
   * Etiqueta de versión visible en cabecera junto a la fecha (`tvAppVersion`).
   * Botón de actualización inteligente: indica `Actualizado` si la versión local coincide con `version.json`, o `Actualizar (vX.X.X)` abriendo el diálogo OTA si hay una versión superior.
   * Botón `Recargar`: fuerza la descarga inmediata de la agenda deportiva y canales ignorando la caché.
   * **Ayuda Contextual Dinámica (`tvHeaderButtonHint`)**: Al enfocar cualquiera de los dos botones superiores, se despliega debajo una etiqueta explicativa (`11sp`, texto conciso sin prefijos redundantes) que clarifica la acción ("Comprueba o descarga la última versión de la app" o "Recarga lista de eventos/canales").
   * El sistema de actualización remota Over-The-Air se gestiona a través del archivo [`version.json`](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json) en el repositorio GitHub (`dasataca/ErradoTV`).

---

## 2. Los 4 Documentos Maestros del Proyecto

Siguiendo el estándar de desarrollo de este entorno, la documentación se divide en 4 archivos esenciales:
1. **[`AGENTS.md`](file:///home/sataca/Descargas/APLICACIONES/errado/AGENTS.md)**: Reglas de arquitectura, criptografía y directivas técnicas para Inteligencias Artificiales.
2. **[`PROJECT_CONTEXT.md`](file:///home/sataca/Descargas/APLICACIONES/errado/PROJECT_CONTEXT.md)**: Documento maestro integral del proyecto (visión, origen de datos, endpoints, modelos de negocio y hardware).
3. **[`CHANGELOG.md`](file:///home/sataca/Descargas/APLICACIONES/errado/CHANGELOG.md)**: Historial cronológico estricto de versiones, correcciones y nuevas funcionalidades añadidas.
4. **[`README.md`](file:///home/sataca/Descargas/APLICACIONES/errado/README.md)**: Guía rápida para el usuario (instalación en TV, uso de scripts auxiliares y repositorio).

---

## 3. Servidores de Origen y Endpoints de Datos

La aplicación consume archivos JSON cifrados alojados en el CDN global **Cloudflare Pages** (`errado-app.pages.dev`). No requiere autenticación por cabeceras (`Access-Control-Allow-Origin: *`):

| Endpoint | Propósito | Frecuencia de Actualización |
| :--- | :--- | :--- |
| `https://errado-app.pages.dev/data/event.json` | Partidos del día, horarios y opciones de streaming | Se actualiza varias veces al día según agenda deportiva |
| `https://errado-app.pages.dev/data/destacado.json` | Catálogo de repeticiones históricas y canales 24/7 con HLS directo | Actualización continua |
| `https://teveplay.net/api/channels` | Catálogo de más de 470 señales y canales deportivos en vivo | Actualización continua |
| `https://errado-app.pages.dev/data/golazoapp.json` | Configuración general de la app móvil original | Estático / ocasional |
| `https://errado-app.pages.dev/data/channel.json` | Canales de televisión secundarios | Estático / semanal |
| `https://errado-app.pages.dev/data/home.json` | Banners del carrusel de portada | Diario |

### Servidores de Streaming y Resolución de Enlaces:
1. **Resolutores CDN Propios (`event_type_url = "config"`)**:
   * Puntos de enlace: `https://cdnapp3.pages.dev/d/teveplay-*.json?gox=x` o `fawanews-*.json`.
   * Estos endpoints devuelven un JSON con la URL directa de video HLS:
     `https://teveplay.net/api/stream/{id}/index.m3u8`
   * **Comportamiento**: 100% estables, limpios de publicidad y compatibles con ExoPlayer nativo.
2. **Enlaces Web de Terceros (`event_type_url = "web"`)**:
   * Puntos de enlace: `https://la18hd.su/vivo/canales.php?stream=...` o `https://www.tarjetarojita.xyz/...`.
   * Páginas web externas con bloqueos antibot o bloqueos DNS locales por operadores.

---

## 4. Ingeniería Inversa Criptográfica y Evasión DNS (DoH)

* **Algoritmo**: `AES/CBC/PKCS5Padding`.
* **Blindaje de Clave en Memoria (Anti-Decompilación XOR)**:
  * La clave Base64 de fábrica (`R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==`) **no existe en texto plano en el APK ni en el DEX**.
  * Se almacena ofuscada mediante máscara XOR de 1 byte (`SHIELD_MASK = 0x5A`) en `SHIELDED_KEY_BYTES` y se recompone en memoria volátil en `getFactoryKey()`.
* **Derivación de Clave de 256 bits**:
  $$\text{Key}_{256} = \text{SHA-256}(\text{"R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="})$$
  Hash resultante: `3e2998e90f7fdb6becafd0df6889e1578a8032a41823a0f2f2e036d7be15b783`
* **Estructura del Payload Cifrado**:
  * Bytes `0` a `15`: Vector de Inicialización (**IV**).
  * Bytes `16` al final: Carga útil cifrada (**Ciphertext**).
* **Mecanismo de Resiliencia Remota y Cifrado de Sobre**:
  * `CryptoUtils.kt` consulta primero [`version.json`](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json) en GitHub.
  * Soporta `"active_key_enc"` (Sobre Cifrado con AES-256-CBC derivado de semilla matemática interna) y `"active_key"` (texto plano para versiones legadas).
  * Para generar nuevos sobres cifrados sin exponer la clave en GitHub ni en sniffers, se utiliza el script ejecutable [`herramientas/cifrador_clave/encrypt_key.py`](file:///home/sataca/Descargas/APLICACIONES/errado/herramientas/cifrador_clave/encrypt_key.py).
* **Validación Estricta de JSON en Descifrado**:
  * `CryptoUtils.decrypt()` comprueba obligatoriamente que el payload comience con `{` o `[`. Si arroja bytes corruptos por desfase de clave o caché, conmuta de inmediato y de forma autónoma a la clave de fábrica en memoria RAM (`getFactoryKey()`).
* **DNS sobre HTTPS (DoH) Remoto**:
  `SecureDns.kt` resuelve dominios con Cloudflare (`https://cloudflare-dns.com/dns-query`) y Google (`8.8.8.8`). Si se desea cambiar el proveedor DoH globalmente, se añade el campo `"doh_url"` en `version.json`.

---

## 5. Telemetría y Base Comunitaria (Supabase)

* **Proyecto**: `pfhjrplnfuupftgzdnop` (`https://pfhjrplnfuupftgzdnop.supabase.co`).
* **Tabla de Telemetría**: `public.errado_tv_logs` (Buffer circular de 1,000 filas para eventos: `app_start`, `event_click`, `play_stream`, `player_error`, `update_check`).
* **Tabla Comunitaria Exclusiva**: `public.errado_community_streams` (Almacena permanentemente enlaces caídos con deduplicación por clave primaria `stream_url` y conteo de reportes).
* **Visor de Consola**: 
  * `python3 view_logs.py --watch` (Logs en tiempo real).
  * `python3 view_logs.py --community` (Señales caídas reportadas por la comunidad).

---

## 6. Publicación Automatizada

Para generar y subir una nueva versión del software:
```bash
./publish_release.sh [versionName] [versionCode] "[changelog]"
# Ejemplo:
./publish_release.sh 1.6.7 46 "GarecaTV: Captura Pre-IME de tecla Atrás y transferencia instantánea de foco a sugerencias en ambos buscadores"
```
El script compila el APK, actualiza `version.json`, organiza los archivos en `release/` y realiza el `git push` a `origin main` de manera completamente autónoma.

---

## 7. Protocolo Obligatorio de Notificación (Mix Caliente vs. APK) y División de Fuentes

Toda IA o desarrollador que trabaje en este repositorio debe cumplir estrictamente las siguientes directivas:

1. **Notificación Explícita de Actualización de APK vs. Caliente**:
   * **Cuando se modifica código nativo Kotlin / XML / Android**: La IA **DEBE** avisar explícitamente al usuario que se requiere compilar y actualizar el APK, indicando el comando exacto:
     > *"He modificado el código nativo de la aplicación. Para aplicar estos cambios en la TV, ejecutamos `./publish_release.sh [versionName] [versionCode] "[changelog]"` y se compila e instala la nueva versión."*
   * **Cuando se modifican catálogos, servidores, JSON, watchdogs o scripts de scraping**: La IA **DEBE** aclarar que se trata de una actualización en caliente:
     > *"Este cambio fue en caliente en los datos. No necesitas compilar ni actualizar el APK; la TV lo refleja automáticamente."*

2. **División Estricta de Fuentes y Supresión de Marcas Internas**:
   * **Teleplay (`teveplay.net`, `event.json`, `destacado.json`)**: Reservado exclusivamente para **Fútbol y Deportes en Vivo** (partidos del día con cuenta regresiva, repeticiones y canales deportivos).
   * **Televisión Mundial Abierta**: Reservada para **Televisión Abierta y Temática Mundial** (organizado por países sin discriminación de idioma, noticias, cine/series, infantil, música, etc.).
   * **Prohibición de Nombres Internos**: Queda terminantemente prohibido mostrar nombres de proveedores técnicos o internos (como "CXTv") en la interfaz de usuario, botones, hints de búsqueda, diálogos o changelogs. Para el usuario es simplemente "📺 TELEVISIÓN MUNDIAL".

3. **Arquitectura Master-Detail (Estilo MagisTV / OTT Player) para TV Mundial**:
   * En **⚽ Deportes y Partidos**: Se mantiene la barra horizontal superior de categorías con eventos a pantalla completa.
   * En **📺 Televisión Mundial**:
     * **Buscador Superior**: Con botón de micrófono por voz, caja de búsqueda unificada y botón interno "✕".
     * **Columna Lateral Izquierda (Sidebar vertical, ancho 250dp)**: Listado vertical de categorías temáticas y países (`SidebarCategoryAdapter`) con estados visuales activos/inactivos (`mode_button_selector.xml`).
     * **Panel Principal Derecho**: Listado de canales correspondientes a la categoría seleccionada a la izquierda (`rvEvents`).
     * **Navegación D-Pad Fluida**:
       - `▲` / `▼` en la columna izquierda: Cambia de categoría actualizando en tiempo real los canales de la derecha.
       - `▶ Derecha` desde la columna izquierda: Salta directamente al primer canal del panel derecho.
       - `◀ Izquierda` desde el panel derecho: Regresa al menú vertical de categorías.
       - `▲ Arriba` desde el primer elemento de la columna izquierda: Salta a la caja de búsqueda.

4. **Distintivos Tecnológicos Explícitos en Cada Canal**:
   * Toda tarjeta de canal en Televisión Mundial debe portar obligatoriamente su insignia técnica:
     - `⚡ EXOPLAYER HLS` (Verde `#059669`): Transmisiones nativas directas `.m3u8` con zapping continuo (`◀` / `▶`) y Zoom.
     - `🔴 YOUTUBE LIVE` (Rojo `#DC2626`): Emisiones en directo vía YouTube sin bloqueos.
     - `🔵 WEB HTML5` (Azul `#2563EB`): Streams web embebidos (Dailymotion/Twitch).
     - `🌐 WEB EXTERNA` (Púrpura `#7C3AED`): Enlaces abiertos web.
     - `⚪ NO DISPONIBLE` (Gris `#6B7280`): Señales caídas con transparencia al 50%, desplazadas al fondo.

5. **Menús y Secciones 100% Autogenerados (Data-Driven)**:
   * Queda terminantemente prohibido hardcodear listas estáticas de categorías o países en el código Kotlin.
   * La interfaz de la app debe autodescubrir y construir dinámicamente las pestañas y secciones a partir de los datos recibidos del backend (`buildWorldTvTabs()`).

6. **Tratamiento de Canales Caídos (`NO DISPONIBLE`)**:
   * Todo canal que no responda o cuya señal esté caída debe recibir la etiqueta visual `⚪ NO DISPONIBLE` y ser desplazado **automáticamente al fondo de la lista** de su categoría o país.
   * El monitor / watchdog en segundo plano debe reintentar la captura periódicamente; al recuperar la transmisión, el canal vuelve a su posición superior de forma autónoma.


