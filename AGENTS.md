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
5. **Reproducción de Video Nativa**:
   * Prioridad absoluta a **AndroidX Media3 ExoPlayer** para reproducir flujos HLS (`.m3u8`).
   * Configuración de `DefaultTrackSelector` forzando siempre la máxima resolución (`setMaxVideoSize(3840, 2160)`) y bitrate disponible (`setForceHighestSupportedBitrate(true)`).
6. **Resiliencia, Actualización OTA y Botones de Cabecera**:
   * Etiqueta de versión visible en cabecera junto a la fecha (`tvAppVersion`).
   * Botón de actualización inteligente: indica `Actualizado` si la versión local coincide con `version.json`, o `Actualizar (vX.X.X)` abriendo el diálogo OTA si hay una versión superior.
   * Botón `Recargar`: fuerza la descarga inmediata de la agenda deportiva y canales ignorando la caché.
   * **Ayuda Contextual Dinámica (`tvHeaderButtonHint`)**: Al enfocar cualquiera de los dos botones superiores, se despliega debajo una etiqueta explicativa que clarifica si la acción es para actualizar el software de la app o para recargar la lista de partidos en vivo.
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
