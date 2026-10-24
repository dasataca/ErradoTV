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
   * **Buscador Integrado para TV**: En la pestaña de Repeticiones, cuenta con una barra de búsqueda y opciones de ordenación (Fecha y Nombre) con manejo ergonómico del control remoto.
4. **Reproducción Limpia y de Máxima Calidad**: Reproducción directa de streams HLS (`.m3u8`) a través de la librería oficial **AndroidX Media3 (ExoPlayer)** forzando 4K/UHD y máxima tasa de bits, junto a un visor web limpio con bloqueo de scripts para enlaces externos.
5. **Resiliencia y Sincronización Remota**: Soporte para actualización de claves en la nube y actualización automática Over-The-Air (OTA) directa desde GitHub sin tocar la TV.
6. **Inteligencia Comunitaria (Supabase)**: Enlaces caídos reportados por cualquier usuario se archivan en `errado_community_streams` y se comparten en tiempo real entre todos los televisores, reordenando automáticamente los partidos `NO DISPONIBLE` al final de la lista.

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
3. **Buscador y Ordenación Leanback**:
   * Permite filtrar repeticiones al instante por equipo, título o fecha.
   * Botones de ordenación por **Fecha** (más recientes o más antiguos) y por **Nombre** (alfabético A-Z o Z-A).
   * **Navegación con control remoto**: Al pulsar flecha abajo desde la pestaña, el foco pasa a la caja de texto. Si el usuario pulsa OK/Center, se activa el cursor y se abre el teclado; si pulsa abajo directamente sin presionar OK, el foco salta al primer partido de la lista.

---

## 3. Arquitectura del Código Fuente (Android TV)

* **`com.errado.tv.api.CryptoUtils`**: Módulo criptográfico AES-256-CBC con clave local de fábrica, almacenamiento en `SharedPreferences` y sincronización con GitHub/Gist.
* **`com.errado.tv.api.ApiClient`**: Cliente HTTP basado en `OkHttpClient` y corrutinas. Descarga, descifra y mapea `event.json`, `destacado.json` y los canales de `teveplay.net`. Extrae fechas y timestamps (`YYYY-MM-DD`) para ordenación.
* **`com.errado.tv.model.EventModel`**: Modelos de datos (`EventItem`, `StreamOption`, `EventStatus`). Soporta cálculo dinámico de estados (`● EN VIVO`, `POR EMPEZAR`, `PROGRAMADO`, `FINALIZADO`, `REPETICIÓN`, `NO DISPONIBLE`, `CANAL 24/7`), marcas de fecha (`rawDate`) y milisegundos (`timestamp`).
* **`com.errado.tv.MainActivity`**: Actividad principal Leanback. Gestiona el ciclo de vida, reloj en tiempo real (`TextClock`), control de frecuencia de refresco (ventana de 15 min), buscador dinámico, botones de ordenación y navegación D-Pad.
* **`com.errado.tv.CategoryAdapter`**: Barra de pestañas horizontal (`rvCategories`) con animación de escala (`1.06f`) y captura de evento de flecha abajo para transferir el foco al buscador o lista.
* **`com.errado.tv.EventAdapter`**: Adaptador para las tarjetas de partidos y canales con selector `card_selector.xml` y atenuación visual (`alpha = 0.5f`) para partidos finalizados o no disponibles.
* **`com.errado.tv.CommunityStreamManager`**: Sincronizador con la base comunitaria de Supabase (`public.errado_community_streams`), permitiendo deduplicar enlaces caídos y compartirlos entre dispositivos.
* **`com.errado.tv.telemetry.SupabaseLogger`**: Telemetría en segundo plano para auditoría de inicio, clics y errores de reproducción.
* **`com.errado.tv.PlayerActivity`**: Reproductor nativo AndroidX Media3 ExoPlayer en pantalla completa con soporte de mandos a distancia.
* **`com.errado.tv.WebStreamActivity`**: Visor WebView seguro con bloqueo de publicidad para señales externas web.

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

---

## 5. Sistema de Actualización Automática desde GitHub (OTA Automatizado)

1. **Archivo Maestro de Versión (`version.json`)**:
   Ubicado en la raíz del repositorio:
   `https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json`
2. **Detección Autónoma en la TV**:
   La app consulta `version.json` al iniciar y en segundo plano. Al detectar una versión superior a la instalada, presenta el diálogo de confirmación en la TV y descarga/actualiza el APK por WiFi sin requerir intervención manual.
