# 📜 Historial de Cambios y Avances — ErradoTV

Todas las modificaciones, análisis y versiones del proyecto quedan documentadas en este archivo según el estándar de desarrollo.

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
