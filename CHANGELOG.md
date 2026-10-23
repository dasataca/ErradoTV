# 📜 Historial de Cambios y Avances — ErradoTV

Todas las modificaciones, análisis y versiones del proyecto quedan documentadas en este archivo según el estándar de desarrollo.

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
