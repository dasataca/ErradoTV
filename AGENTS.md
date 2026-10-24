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
   * **Navegación Ergonómica en Búsqueda**: Al pulsar abajo desde la pestaña de Repeticiones, el foco pasa a la caja de texto. Para escribir se pulsa OK/Center (activa el cursor y despliega teclado); si se pulsa abajo de nuevo sin presionar OK, el foco salta directamente al primer partido de la lista.
   * **Resiliencia de Disponibilidad**: Los partidos etiquetados como `NO DISPONIBLE` se desplazan siempre de forma automática al fondo de la lista.
5. **Reproducción de Video Nativa**:
   * Prioridad absoluta a **AndroidX Media3 ExoPlayer** para reproducir flujos HLS (`.m3u8`).
   * Configuración de `DefaultTrackSelector` forzando siempre la máxima resolución (`setMaxVideoSize(3840, 2160)`) y bitrate disponible (`setForceHighestSupportedBitrate(true)`).
6. **Resiliencia y Actualización OTA Autónoma**:
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

## 4. Ingeniería Inversa Criptográfica

* **Algoritmo**: `AES/CBC/PKCS5Padding`.
* **Clave Base64 de Fábrica**: `R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==` (Texto plano: `GolazooAppSecretKey2024Secure!!`).
* **Derivación de Clave de 256 bits**:
  $$\text{Key}_{256} = \text{SHA-256}(\text{"R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="})$$
  Hash resultante: `3e2998e90f7fdb6becafd0df6889e1578a8032a41823a0f2f2e036d7be15b783`
* **Estructura del Payload Cifrado**:
  * Bytes `0` a `15`: Vector de Inicialización (**IV**).
  * Bytes `16` al final: Carga útil cifrada (**Ciphertext**).
* **Mecanismo de Resiliencia**:
  `CryptoUtils.kt` consulta primero [`version.json`](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json) en GitHub. Si la clave cambia en el servidor, se almacena en `SharedPreferences` y la app la utiliza de inmediato sin requerir reinstalación.

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
./publish_release.sh 1.3.7 20 "Pestaña Repeticiones antes de Regionales con buscador y ordenacion"
```
El script compila el APK, actualiza `version.json`, organiza los archivos en `release/` y realiza el `git push` a `origin main` de manera completamente autónoma.
