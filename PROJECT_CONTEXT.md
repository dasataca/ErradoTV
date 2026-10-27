# 📋 GarecaTV — Contexto Integral y Documento Maestro del Proyecto

> **Nota para IAs y Desarrolladores**: Este documento es el estándar maestro de contexto del proyecto. Cualquier modelo de Inteligencia Artificial (Antigravity, Claude, Cursor, Copilot, ChatGPT) o desarrollador debe leer este archivo en primer lugar para comprender la arquitectura, el análisis criptográfico, los endpoints y el estado de avance del software.

---

## 1. Visión y Propósito del Proyecto

### ¿Qué es GarecaTV?
**GarecaTV** (anteriormente conocido como ErradoTV) es una aplicación nativa de código abierto diseñada específicamente para **Android TV, Google TV y Fire TV Stick**. Nació como una reingeniería de la aplicación móvil `mta.xer.errado` (Errado / Golazo App), con los siguientes objetivos:

1. **Eliminación Total de Anuncios**: La app móvil original contenía más de 6 SDKs publicitarios invasivos (*Google AdMob, Yandex Mobile Ads, AppLovin MAX, Vungle, Facebook Audience Network y Clever Ads Solutions*). GarecaTV prescinde del 100% de estas librerías.
2. **Identidad Visual Propia y Exclusiva**:
   * Icono y banner de alta definición con cabeza de tigre en perfil (un solo ojo visible y mandíbula rugiente en colores naturales), zarpazos de garras en rojo intenso y tipografía atlética sobre fondo negro.
   * Suite completa de iconos para el sistema Android (`mipmap-mdpi` a `mipmap-xxxhdpi` e icono adaptativo `mipmap-anydpi-v26`).
   * Visualización limpia: El icono del tigre aparece exclusivamente en la cabecera superior principal de la app y no se repite en cada fila de partidos.
3. **Experiencia Nativa de Televisión (Leanback D-Pad)**:
   * Diseñada 100% para pantallas horizontales (Landscape) con control remoto (D-Pad), tarjetas enfocables, animaciones de escala y selectores visuales de alto contraste.
3. **Buscador Global Inteligente y por Voz (`🔍 Buscar Todo`)**: Pestaña dedicada al inicio de la barra que busca simultáneamente en partidos de hoy, canales de TevePlay y repeticiones. Soporta búsqueda por voz mediante el micrófono del control remoto (`Speech-to-Text`).
4. **Huso Horario Local y Cuenta Regresiva Automática**: Convierte en tiempo real los horarios de los partidos (GMT-3) a la zona horaria del país del televisor (`TimeZone.getDefault()`), mostrando cuenta regresiva en vivo (*"⏳ En 2h 15m"*, *"⏳ En 1d 4h"*, *"● EN JUEGO"*).
5. **Cruce Inteligente de Datos para TevePlay**: Asocia los canales programados de TevePlay con los eventos de la agenda diaria oficial (`event.json`), heredando hora local y cuenta regresiva.
6. **Reproducción Limpia y de Máxima Calidad**: Reproducción directa de streams HLS (`.m3u8`) a través de la librería oficial **AndroidX Media3 (ExoPlayer)** forzando 4K/UHD y máxima tasa de bits, junto a un visor web limpio con bloqueo de scripts para enlaces externos.
7. **Resiliencia y Sincronización Remota**: Soporte para actualización de claves en la nube y actualización automática Over-The-Air (OTA) directa desde GitHub sin tocar la TV.
8. **Inteligencia Comunitaria (Supabase)**: Enlaces caídos reportados por cualquier usuario se archivan en `errado_community_streams` y se comparten en tiempo real entre todos los televisores, reordenando automáticamente los partidos `NO DISPONIBLE` al final de la lista.

---

## 2. Descubrimientos de Ingeniería Inversa (Origen de los Datos)

### Servidores de Origen
La aplicación consume archivos JSON cifrados alojados en el CDN estático **Cloudflare Pages** (`errado-app.pages.dev`):

* `https://errado-app.pages.dev/data/event.json` → Partidos de la fecha de hoy, horarios y lista de opciones de streaming (`event_url_1` a `event_url_16`).
* `https://errado-app.pages.dev/data/destacado.json` → Catálogo de repeticiones históricas completas con fecha (`refooty.com/..._YYYY-MM-DD.m3u8`) y canales de TV 24/7 con streams HLS directos.
* `https://teveplay.net/api/channels` → Catálogo de más de 470 señales y canales en vivo clasificados por países y ligas.
* `https://errado-app.pages.dev/data/golazoapp.json` → Configuración general de la app original.
* `https://errado-app.pages.dev/data/channel.json` → Canales secundarios.
* `https://errado-app.pages.dev/data/home.json` → Carrusel principal.

### Separación de Catálogos y Búsqueda en TV
1. **Partidos del Día (`⚽ Partidos del Día`)**: Muestra únicamente los partidos en vivo, por empezar o programados de la jornada actual (`cachedDailyEvents`). Se excluyen todas las repeticiones.
2. **Pestaña Exclusiva de Repeticiones (`🔁 Repeticiones`)**: Situada justo antes de `📺 Regionales`, reúne el catálogo de partidos históricos (`cachedReplayEvents`).
3. **Buscador Global (`🔍 Buscar Todo`)**: Pestaña inicial que consolida la búsqueda simultánea en partidos de hoy, canales lineales y repeticiones. Dispone de botón de ordenamiento por **`🔤 Nombre`** (`btnSortNameGlobal`), alternando entre orden por defecto, A-Z y Z-A.
4. **Arquitectura de Búsqueda, Botón "✕" Interno y Búsqueda Tokenizada**:
   * **Disposición Visual**: Botón de micrófono a la izquierda (`btnVoiceSearchReplay` / `btnVoiceGlobalSearch`), campo de texto unificado con botón "✕" interno (`btnClearSearchText` / `btnClearGlobalText`) y botón Lupa dedicado a la derecha (`btnSearchReplay` / `btnSearchGlobal`).
   * **Botón "✕" Interno con Activación Inmediata de Teclado**: Ubicado en el extremo interior derecho del campo de texto, visible sólo cuando hay texto. Al seleccionarlo con el D-Pad y pulsar OK, limpia todo el texto del campo, refresca los eventos y levanta automáticamente el teclado virtual (`activateSearchInput()` / `activateGlobalSearchInput()`) con el cursor listo para escribir una nueva consulta.
   * **Búsqueda Tokenizada Multidisjuntiva**: Divide las consultas por espacios (`split(Regex("""\s+"""))`), evaluando que cada término o equipo coincida de forma independiente en título, subtítulo, fecha o categoría (`tokens.all { target.contains(it) }`). Esto soluciona búsquedas como "racing boca" o "boca racing" en repeticiones y catálogo global.
   * **Interceptación Pre-IME de Tecla Atrás (`SearchEditText`)**: En Android TV, el teclado virtual del sistema operativo consume el primer evento `KEYCODE_BACK` para ocultarse, evitando que llegue a la `Activity` y dejando el foco atrapado en el campo de texto. `SearchEditText.kt` sobreescribe `onKeyPreIme()` para capturar la tecla Atrás con prioridad, ocultar el teclado y transferir el foco al primer elemento del listado de sugerencias.
   * **Resiliencia en Touch Mode**: Tanto el contenedor `rvSuggestions` como cada elemento (`item_search_suggestion.xml`) tienen `android:focusableInTouchMode="true"`, asegurando que el foco sea aceptado por el control remoto de TV aún cuando el sistema permanezca momentáneamente en modo táctil tras el cierre del teclado.
   * **Flujo de Navegación D-Pad**:
     1. Al escribir 2 o más letras, `filterSuggestions()` despliega sugerencias tokenizadas en `rvSuggestions`.
     2. Al pulsar **Atrás (BACK)**, el teclado desaparece y el foco salta de inmediato a la primera sugerencia.
     3. Al presionar **OK / Center** sobre una sugerencia, el texto se escribe en la caja, la lista de sugerencias se cierra, se ejecuta el filtrado inmediato y el foco salta directamente a la primera tarjeta de resultado del catálogo (`focusFirstEventItem`).
     4. Al pulsar **Atrás (BACK)** estando en la lista de sugerencias sin elegir nada, la lista se oculta y el foco regresa a la caja de búsqueda.
     5. Al pulsar **Arriba (DPAD_UP)** desde la primera sugerencia, el foco regresa a la caja de texto (`onUpFromFirst`).
     6. Al pulsar **Derecha (DPAD_RIGHT)** desde la caja de texto (cuando hay texto), el foco va al botón interno **"✕"**.
5. **Cabecera de Versión, Actualización Inteligente y Ayuda Contextual**:
   * Indicador visual de versión instalada (`tvAppVersion`) junto a la fecha oficial (`• v1.7.0`).
   * Botón de actualización dinámico: muestra `Actualizado` si la versión local coincide con `version.json`, o `Actualizar (vX.X.X)` si hay un release superior disponible en el repositorio.
    * Botón `Recargar`: fuerza la descarga inmediata de la agenda deportiva y canales ignorando la caché.
    * **Ayuda Contextual Dinámica (`tvHeaderButtonHint`)**: Al enfocar cualquiera de los dos botones superiores, se despliega debajo una etiqueta explicativa (`11sp`, texto conciso sin prefijos redundantes) que clarifica la acción ("Comprueba o descarga la última versión de la app" o "Recarga lista de eventos/canales").

---

## 3. Arquitectura del Código Fuente (Android TV)

* **`com.gareca.tv.ChannelNavigationManager`**: Administrador singleton en memoria para el playlist de canales de flujo único. Permite navegación secuencial (zapping circular con `next()` y `previous()`), conteo y formateo de posición (`Canal X de Y`) sin riesgo de desbordamiento de memoria ni excepciones IPC (`TransactionTooLargeException`).
* **`com.gareca.tv.SearchEditText`**: Componente personalizado que hereda de `AppCompatAutoCompleteTextView` y sobreescribe `onKeyPreIme()` para capturar la tecla Atrás antes de que el teclado virtual la consuma, garantizando la transferencia de foco a sugerencias en Smart TVs.
* **`com.gareca.tv.SuggestionAdapter`**: Adaptador para el `RecyclerView` de sugerencias (`rvSuggestions`). Implementa selectores visuales de alto contraste (`button_focus_selector.xml`), animación de escala (`1.02f`) al enfocar, callback de selección (`onItemClick`) y navegación vertical hacia la caja de texto (`onUpFromFirst`).
* **`com.gareca.tv.api.CryptoUtils`**: Módulo criptográfico AES-256-CBC con clave blindada con máscara XOR (`getFactoryKey()`), soporte de Cifrado de Sobre (`decryptEnvelope`) para `"active_key_enc"`, almacenamiento en `SharedPreferences` y validación estricta de JSON en memoria RAM.
* **`com.gareca.tv.network.SecureDns`**: Proveedor nativo de DNS sobre HTTPS (DoH) basado en `okhttp-dnsoverhttps` con servidores seguros de Cloudflare (`1.1.1.1`), Google (`8.8.8.8`) y conmutación automática de respaldo a DNS del sistema.
* **`com.gareca.tv.api.ApiClient`**: Cliente HTTP basado en `OkHttpClient` y corrutinas, enrutado por `SecureDns`. Descarga, descifra y mapea `event.json`, `destacado.json` y los canales de `teveplay.net`. Extrae fechas y timestamps (`YYYY-MM-DD`) para ordenación.
* **`com.gareca.tv.model.EventModel`**: Modelos de datos (`EventItem`, `StreamOption`, `EventStatus`). Soporta cálculo dinámico de estados (`● EN VIVO`, `POR EMPEZAR`, `PROGRAMADO`, `FINALIZADO`, `REPETICIÓN`, `NO DISPONIBLE`, `CANAL 24/7`), marcas de fecha (`rawDate`) y milisegundos (`timestamp`).
* **`com.gareca.tv.MainActivity`**: Actividad principal Leanback. Gestiona el ciclo de vida, reloj en tiempo real (`TextClock`), control de frecuencia de refresco, buscador dinámico, botones de ordenación (`ReplaySortMode` y `GlobalSortMode`), gestión de sugerencias, armado de playlist de canales y navegación D-Pad.
* **`com.gareca.tv.CategoryAdapter`**: Barra de pestañas horizontal (`rvCategories`) con animación de escala (`1.06f`) y captura de evento de flecha abajo para transferir el foco al buscador o lista.
* **`com.gareca.tv.EventAdapter`**: Adaptador para las tarjetas de partidos y canales con selector `card_selector.xml`, propiedad `currentList` y atenuación visual (`alpha = 0.5f`) para partidos finalizados o no disponibles.
* **`com.gareca.tv.CommunityStreamManager`**: Sincronizador con la base comunitaria de Supabase (`public.errado_community_streams`), permitiendo deduplicar enlaces caídos y compartirlos entre dispositivos.
* **`com.gareca.tv.telemetry.SupabaseLogger`**: Telemetría en segundo plano para auditoría de inicio, clics, zapping y errores de reproducción.
* **`com.gareca.tv.PlayerActivity`**: Reproductor nativo AndroidX Media3 ExoPlayer con `OkHttpDataSource.Factory` conectado a `SecureDns`. Soporta zapping directo con `◀` y `▶` del D-Pad, banner flotante con logo y posición auto-ocultable a los 3.5s (`llChannelBanner`), y reapertura de controles multimedia y banner mediante `▲` y `▼`.
* **`com.gareca.tv.WebStreamActivity`**: Visor WebView seguro con bloqueo de publicidad para señales externas web.

---

## 4. Herramientas y Scripts Auxiliares

1. **[`publish_release.sh`](file:///home/sataca/Descargas/APLICACIONES/errado/publish_release.sh)**:
   * Script de compilación y publicación OTA 100% automatizado en 1 comando: `./publish_release.sh [versionName] [versionCode] "[changelog]"`. Compila el APK, actualiza `version.json`, organiza en `release/` y hace push a GitHub.
2. **[`extract_key.py`](file:///home/sataca/Descargas/APLICACIONES/errado/extract_key.py)**:
   * Extractor automático de claves DEX para futuras versiones de la APK móvil original.
3. **[`generate_playlist.py`](file:///home/sataca/Descargas/APLICACIONES/errado/generate_playlist.py)**:
   * Generador de listas IPTV en formato `.m3u8` (`errado_tv.m3u8`) para TiviMate, VLC o reproductores externos.
4. **[`view_logs.py`](file:///home/sataca/Descargas/APLICACIONES/errado/view_logs.py)**:
   * Visor de telemetría en tiempo real (`--watch`) y visor de enlaces caídos reportados por la comunidad (`--community`).
5. **[`supabase_setup.sql`](file:///home/sataca/Descargas/APLICACIONES/errado/supabase_setup.sql)**:
   * Definición DDL de las tablas `errado_tv_logs` y `errado_community_streams` en Supabase con trigger de limpieza circular.
6. **[`herramientas/cifrador_clave/encrypt_key.py`](file:///home/sataca/Descargas/APLICACIONES/errado/herramientas/cifrador_clave/encrypt_key.py)**:
   * Herramienta de Cifrado de Sobre AES-256 para encriptar la clave secreta antes de subirla a `version.json` en GitHub, impidiendo que curiosos o sniffers capturen la clave en texto plano. Acompañada de su [`MANUAL.md`](file:///home/sataca/Descargas/APLICACIONES/errado/herramientas/cifrador_clave/MANUAL.md).

---

## 5. Sistema de Actualización Automática desde GitHub (OTA Automatizado)

1. **Archivo Maestro de Versión (`version.json`)**:
   Ubicado en la raíz del repositorio:
   `https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json`
   Contiene `latest_version_code`, `latest_version_name`, `apk_url`, `release_timestamp` y `release_date`.
2. **Detección Autónoma y Regla de Caducidad (Gracia de 2 Días / Bloqueo Obligatorio)**:
   * La app consulta `version.json` al iniciar (tanto en `LoginActivity` como en `MainActivity`) y periódicamente.
   * **Días 1 y 2 (Periodo de Gracia)**: Si hay una nueva versión, se informa al usuario mediante diálogo modal, pero este puede posponer la actualización ("Más tarde") y continuar disfrutando de la programación.
   * **Día 3 en adelante (Bloqueo Obligatorio)**: Tras 48+ horas del lanzamiento oficial de la nueva versión, la app exige obligatoriamente actualizar para iniciar. El diálogo no es cancelable, el botón se transforma en "Salir" (`finishAffinity()`), impidiendo el uso de versiones obsoletas.

---

## 6. Seguridad Criptográfica, Blindaje XOR y Evasión DNS Remota (DoH)

### 6.1. Blindaje de la Clave de Seguridad (Anti-Decompilación con Máscara XOR)
* **Problema resuelto**: Anteriormente la clave de fábrica estaba en texto plano en Kotlin (`R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==`), visible en segundos mediante descompiladores como Jadx o APKTool.
* **Mecanismo de blindaje**:
  * En `CryptoUtils.kt`, la clave se almacena descompuesta en un array de bytes ofuscados (`SHIELDED_KEY_BYTES`) procesados con una máscara XOR de 1 byte (`SHIELD_MASK = 0x5A`).
  * La clave real **no existe en ningún archivo ni DEX del APK**.
  * Solo se reconstruye dinámicamente en la memoria RAM del televisor al momento de descifrar las fuentes mediante `getFactoryKey()`.

### 6.2. DNS sobre HTTPS (DoH) y Evasión de Bloqueos de Operadores
Los operadores de internet (Movistar, Claro, Personal, etc.) aplican el 85% de los bloqueos a transmisiones deportivas manipulando las respuestas DNS de los routers domésticos.

* **Cómo lo anula GarecaTV**:
  * La app implementa `SecureDns.kt` usando `okhttp-dnsoverhttps` tanto en `ApiClient` como en el reproductor `ExoPlayer` (`OkHttpDataSource.Factory`).
  * Las consultas DNS viajan 100% encriptadas mediante HTTPS directamente a servidores seguros globales, dejando ciego al cortafuegos del router del operador.
  * **Servidor de fábrica predeterminado**: `https://cloudflare-dns.com/dns-query` (Cloudflare 1.1.1.1 con respaldo Google 8.8.8.8).

### 6.3. ¿Cómo Cambiar el Servidor DNS Remotamente sin Tocar el APK?
Si en el futuro deseas cambiar el proveedor de DNS para todos los televisores instalados, **no necesitas actualizar la app ni recompilar**. Solo debes agregar el campo `"doh_url"` en tu archivo [`version.json`](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json) en GitHub:

```json
{
  "active_key": "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==",
  "doh_url": "https://dns.quad9.net/dns-query",
  "latest_version_code": 39,
  "latest_version_name": "1.6.0",
  "apk_url": "https://raw.githubusercontent.com/dasataca/ErradoTV/main/release/GarecaTV.apk",
  "changelog": "..."
}
```

#### Proveedores DoH Globales Compatibles para Usar en `doh_url`:
| Proveedor | URL para `version.json` | Características |
| :--- | :--- | :--- |
| **Cloudflare** *(por defecto)* | `https://cloudflare-dns.com/dns-query` | Máxima velocidad y baja latencia en todo el mundo. |
| **Quad9** | `https://dns.quad9.net/dns-query` | Alta privacidad y protección contra bloqueos maliciosos. |
| **Google Public DNS** | `https://dns.google/dns-query` | Máxima disponibilidad y estabilidad global garantizada. |
| **AdGuard DNS** | `https://dns.adguard-dns.com/dns-query` | Bloqueo de dominios de rastreo y publicidad a nivel DNS. |

* **Comportamiento en la TV**: Al iniciar, la app lee `version.json`, detecta el nuevo `"doh_url"`, lo almacena en `SharedPreferences` (`cached_doh_url`) y actualiza `SecureDns` en caliente. Si el servidor DoH configurado llegase a fallar, la app conmuta automáticamente al DNS local del sistema para evitar interrupciones de señal.

### 6.4. Validación Estricta de JSON en Descifrado y Esquema Híbrido de Resiliencia
* **Problema prevenido**: Si una clave remota llegara desfasada o la memoria caché contuviera datos incompletos, el descifrado AES podría arrojar bytes corruptos en lugar de un error.
* **Mecanismo de seguridad**:
  * En `CryptoUtils.kt`, la función `decrypt()` valida obligatoriamente que la salida empiece por `{` o `[` (formato JSON válido).
  * Si no es JSON legítimo, se descarta de inmediato el resultado y se activa en caliente la clave de fábrica en RAM (`getFactoryKey()`), impidiendo caídas o que la lista de partidos quede reducida.
* **Esquema de Clave Híbrida en `version.json`**:
  `version.json` soporta simultáneamente `active_key` (para versiones anteriores) y `active_key_enc` (para versiones 1.6.2+ con Cifrado de Sobre), garantizando compatibilidad retroactiva total.

---

## 7. Próxima Tarea Técnica: Resiliencia de Streaming HLS y Auto-Recuperación Transparente (Seamless Reconnect)

### 7.1. Antecedentes y Telemetría Registrada
* **Incidente Registrado**: Durante la reproducción de canales 24/7 (ej. Latina a las 11:55 / 14:55 UTC), se produjo un corte abrupto de señal con `player_error: Source error (ERROR_CODE_IO_UNSPECIFIED, code 2000)`. Al salir al menú y volver a pulsar el canal segundos después, reprodujo de inmediato con la **misma URL** (`https://teveplay.net/api/stream/75d2ab2e0c4f85d3/index.m3u8`).
* **Diagnóstico**: No se trataba de un enlace caído ni de un cambio de ID en el CDN, sino de un microcorte transitorio de fragmentos HLS (`.ts`) o timeout de socket de 1 segundo. El reproductor actual trataba cualquier error de origen como fatal y mostraba el cartel de señal caída en vez de reconectar.

### 7.2. Directiva de Diseño: No Sobrecargar, Optimizar Quirúrgicamente
* **Prohibido**: Descargar la lista completa de canales (`/api/channels`, 312+ canales) ante un corte. Genera latencia, consumo de memoria y sobrecarga innecesaria de red.
* **Aprobado**: Arquitectura de **Auto-Recuperación en 3 Capas**:
  1. **Buffer de Absorción HLS (`DefaultLoadControl`)**: Ampliar el búfer de reproducción en RAM (25-40 segundos) para tolerar microcortes de red de hasta 3 segundos sin que el usuario note ningún parpadeo.
  2. **Bucle de Auto-Reconexión Silenciosa (Silent Auto-Retry en `PlayerActivity`)**: Ante `onPlayerError`, retener la pantalla de error y ejecutar hasta 3 reintentos automáticos progresivos (500ms, 2s, 4s) con spinner discreto.
  3. **Revalidación Fresca Puntual**: Si tras 3 intentos el flujo no engancha, re-resolver únicamente el `StreamOption` del canal actual antes de dar la señal por no disponible.
