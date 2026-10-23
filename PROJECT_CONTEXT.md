# 📋 ErradoTV — Contexto Integral y Documento Maestro del Proyecto

> **Nota para IAs y Desarrolladores**: Este documento es el estándar maestro de contexto del proyecto. Cualquier modelo de Inteligencia Artificial (Antigravity, Claude, Cursor, Copilot, ChatGPT) o desarrollador debe leer este archivo en primer lugar para comprender la arquitectura, el análisis criptográfico, los endpoints y el estado de avance del software.

---

## 1. Visión y Propósito del Proyecto

### ¿Qué es ErradoTV?
**ErradoTV** es una aplicación nativa de código abierto diseñada específicamente para **Android TV, Google TV y Fire TV Stick**. Nació como una reingeniería de la aplicación móvil `mta.xer.errado` (Errado / Golazo App), con los siguientes objetivos:

1. **Eliminación Total de Anuncios**: La app móvil original contenía más de 6 SDKs publicitarios invasivos (*Google AdMob, Yandex Mobile Ads, AppLovin MAX, Vungle, Facebook Audience Network y Clever Ads Solutions*). ErradoTV prescinde del 100% de estas librerías.
2. **Experiencia Nativa de Televisión**: La app original está pensada para pantallas táctiles verticales de teléfonos móviles. ErradoTV está diseñada desde cero para pantallas horizontales (Landscape) con control remoto (D-Pad), tarjetas enfocables, animaciones de foco y menú de selección de transmisiones con el botón OK.
3. **Reproducción Limpia**: Reproducción directa de streams HLS (`.m3u8`) a través de la librería oficial **AndroidX Media3 (ExoPlayer)** y un visor web con bloqueo de scripts publicitarios para transmisiones de tipo WebView.
4. **Resiliencia y Sincronización Remota**: Soporte para actualización de claves en la nube sin requerir reinstalación de la app en la TV.

---

## 2. Descubrimientos de Ingeniería Inversa (Origen de los Datos)

### Servidores de Origen
La aplicación no utiliza un backend con base de datos dinámico, sino que consume archivos JSON cifrados alojados en el CDN estático **Cloudflare Pages**:

* `https://errado-app.pages.dev/data/event.json` → Partidos de la fecha y lista de enlaces de streaming (`event_url_1` a `event_url_16`).
* `https://errado-app.pages.dev/data/destacado.json` → Canales de TV y eventos destacados con streams HLS directos (`.m3u8`).
* `https://errado-app.pages.dev/data/golazoapp.json` → Configuración general de la app e identificadores de anuncios.
* `https://errado-app.pages.dev/data/channel.json` → Canales secundarios.
* `https://errado-app.pages.dev/data/home.json` → Carrusel principal.

### Arquitectura Criptográfica
1. **Sin autenticación HTTP**: Cloudflare Pages sirve los archivos con cabecera pública `Access-Control-Allow-Origin: *`. No se requieren tokens de sesión, cookies ni headers especiales.
2. **Cifrado del Contenido**: El servidor responde con una cadena en Base64 que contiene la carga cifrada.
3. **Mecanismo de Descifrado**:
   * **Algoritmo**: `AES/CBC/PKCS5Padding`.
   * **Clave Base64 Embebida**: `R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==` (Texto decodificado: `GolazooAppSecretKey2024Secure!!`).
   * **Derivación de Clave**: Debido a que la cadena decodificada mide 31 bytes (< 32 bytes necesarios para AES-256), la aplicación obtiene el hash **SHA-256** del string en base64 para derivar la clave simétrica de 256 bits (`3e2998e90f7fdb6becafd0df6889e1578a8032a41823a0f2f2e036d7be15b783`).
   * **Vector de Inicialización (IV)**: Los primeros 16 bytes de los datos decodificados en Base64.
   * **Texto Cifrado**: Los bytes a partir del byte 16 en adelante.

---

## 3. Arquitectura del Código Fuente (Android TV)

El proyecto está construido bajo Gradle y Kotlin:

* **`com.errado.tv.api.CryptoUtils`**: Módulo criptográfico con soporte para clave local de fábrica, persistencia en `SharedPreferences` y sincronización con URL remota en la nube.
* **`com.errado.tv.api.ApiClient`**: Cliente HTTP basado en `OkHttpClient` y corrutinas Kotlin (`Dispatchers.IO`). Descarga, descifra y mapea los modelos de datos.
* **`com.errado.tv.model.EventModel`**: Clases de datos (`EventItem`, `StreamOption`). Separa automáticamente la hora del partido (`18:00`) del título de los equipos.
* **`com.errado.tv.MainActivity`**: Pantalla principal de Android TV. Integra reloj en tiempo real (`TextClock`), fecha del servidor, botón de recarga con control remoto y lista RecyclerView con selector de foco `card_selector`.
* **`com.errado.tv.EventAdapter`**: Adaptador de lista con animación de escala (`scaleX/scaleY: 1.02f`) y cálculo de insignia de estado dinámico (`● EN VIVO`, `POR EMPEZAR`, `PROGRAMADO`, `FINALIZADO`, `CANAL 24/7`).
* **`com.errado.tv.telemetry.SupabaseLogger`**: Cliente de telemetría asíncrona hacia Supabase (`pfhjrplnfuupftgzdnop`) que registra flujos de usuario, reproducciones y errores con metadata de hardware de la TV.
* **`com.errado.tv.PlayerActivity`**: Reproductor de video en pantalla completa con AndroidX Media3 (ExoPlayer). Soporte de controles de reproducción en el mando (`KEY_DPAD_CENTER`, `KEY_MEDIA_PLAY_PAUSE`, `KEY_BACK`).
* **`com.errado.tv.WebStreamActivity`**: Visor web con interceptor de recursos (`shouldInterceptRequest`) que neutraliza dominios publicitarios comunes (`popads`, `adsterra`, etc.).

---

## 4. Herramientas y Scripts Auxiliares

1. **[`extract_key.py`](file:///home/sataca/Descargas/APLICACIONES/errado/extract_key.py)**:
   * Script automatizado que analiza cualquier archivo `.apk` o carpeta de APKs.
   * Extrae los archivos DEX, escanea candidatos a claves Base64, calcula su hash SHA-256 y verifica el descifrado contra el servidor en vivo en menos de 3 segundos.
2. **[`generate_playlist.py`](file:///home/sataca/Descargas/APLICACIONES/errado/generate_playlist.py)**:
   * Generador de listas IPTV en formato `.m3u8` (`errado_tv.m3u8`).
   * Descarga y descifra todos los partidos del día y canales destacados para reproducirlos en aplicaciones como TiviMate, VLC u OTT Navigator.
3. **[`view_logs.py`](file:///home/sataca/Descargas/APLICACIONES/errado/view_logs.py)**:
   * Visor de logs en vivo desde Supabase en la consola (`python3 view_logs.py --watch`).
4. **[`supabase_setup.sql`](file:///home/sataca/Descargas/APLICACIONES/errado/supabase_setup.sql)**:
   * Script DDL con trigger de limpieza circular para mantener un máximo estricto de 1,000 registros en Supabase.

---

## 5. Estrategia de Clave Remota (Resiliencia ante Cambios)

Para evitar tener que reinstalar el APK en caso de que los creadores actualicen la clave:
1. `CryptoUtils.kt` cuenta con una clave local predeterminada.
2. Al iniciar o refrescar, intenta consultar `remoteConfigUrl` en segundo plano.
3. Si la URL remota entrega un JSON con formato:
   ```json
   {
     "active_key": "NUEVA_CLAVE_BASE64_AQUI"
   }
   ```
   La aplicación almacena la nueva clave en `SharedPreferences` y la utiliza de inmediato.
4. **URL de Gist Vinculada en la App**:
   `https://gist.githubusercontent.com/dasataca/b5f27059b9db7e878e6a0fab05a7a951/raw/config.json`
5. Si la consulta remota falla o no hay conexión a internet, utiliza la clave local de respaldo.
 
---
 
## 6. Sistema de Actualización Automática desde la Web (OTA)

Para actualizar el APK sin cables ni reinstalación manual por USB:
1. **Repositorio Oficial de Distribución**:
   `https://github.com/dasataca/ErradoTV`
2. **Icono en la Cabecera de la TV**: Al lado del reloj y del botón de recarga se ubica el botón `btnUpdate` navegable con el control remoto.
3. **Verificación**: Consulta el Gist de GitHub (`config.json`), el cual además de `active_key` contiene:
   ```json
   {
     "active_key": "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==",
     "latest_version_code": 2,
     "latest_version_name": "1.1.0",
     "apk_url": "https://raw.githubusercontent.com/dasataca/ErradoTV/main/ErradoTV.apk",
     "changelog": "- Nuevos canales agregados\n- Mejoras de rendimiento"
   }
   ```
   *(También compatible con GitHub Releases: `https://github.com/dasataca/ErradoTV/releases/latest/download/ErradoTV.apk`)*
4. **Flujo de Usuario en TV**:
   - Si `latest_version_code > current_code`, muestra una ventana emergente nativa con los cambios.
   - Al presionar **Actualizar Ahora**, descarga el APK mostrando una barra de progreso porcentual con cliente optimizado (timeout 120s, soporte de redirecciones y cabecera User-Agent).
   - Al completar la descarga, invoca automáticamente el instalador nativo de paquetes de Android TV (`ACTION_VIEW` con `FileProvider`).

