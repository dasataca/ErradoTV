# 📺 GarecaTV — Android TV App (Libre de Publicidad)

Aplicación nativa para **Android TV, Google TV y Fire TV Stick** basada en las transmisiones de Errado / Golazo App, completamente limpia de anuncios y 100% optimizada para control remoto (Leanback D-Pad).

---

## 🚀 Archivos y Versión Actual

* 📱 **[`release/GarecaTV.apk`](file:///home/sataca/Descargas/APLICACIONES/errado/release/GarecaTV.apk)**: APK instalable directa (versión actual **v1.3.8**).
* 📺 **[`gareca_tv.m3u8`](file:///home/sataca/Descargas/APLICACIONES/errado/errado_tv.m3u8)**: Lista de reproducción IPTV directa para TiviMate, OTT Navigator o VLC.
* 🎙️ **Buscador Global Inteligente y Búsqueda por Voz**:
  * Pestaña **`🔍 Buscar Todo`** en la cabecera del catálogo.
  * Búsqueda por voz nativa (`Speech-to-Text`) pulsando el botón de micrófono en pantalla o la tecla de voz del control remoto (`KEYCODE_SEARCH`).
  * Búsqueda simultánea en Partidos del Día, señales de TevePlay y Repeticiones históricas.
  * Búsqueda por voz integrada también en la pestaña **`🔁 Repeticiones`**.
* ⏰ **Huso Horario Local y Cuenta Regresiva Automática**:
  * Detección del huso horario de la caja Android TV (`TimeZone.getDefault()`) y conversión de horarios a la hora local exacta del usuario.
  * Cuenta regresiva en cada tarjeta de partido: *"⏳ En 2h 15m"*, *"⏳ En 1d 4h"*, *"● EN JUEGO"*.
  * Cruce inteligente de partidos programados de `teveplay.net` con la agenda diaria.
* 🐅 **Identidad GarecaTV**:
  * Icono y banner de alto contraste con cabeza de tigre en perfil (un ojo visible, mandíbula rugiente y zarpazos rojos sobre fondo negro).
  * Suite completa de iconos adaptativos Android (`res/mipmap-*`).
  * Icono exclusivo en la cabecera superior principal de la app (filas de partidos limpias sin repeticiones).
* 🌐 **Inteligencia Comunitaria (Supabase)**:
  * Registro y sincronización automática de señales caídas con la base de datos comunitaria (`errado_community_streams`), reordenando partidos `NO DISPONIBLE` al final de la lista.

---

## 🛠️ Cómo Instalar el APK en tu Televisor

1. **Vía WiFi con la app "Send Files to TV" (Recomendado)**:
   * Instala "Send Files to TV" en tu teléfono móvil/PC y en tu televisor desde Google Play Store.
   * Envía el archivo `release/GarecaTV.apk` a la TV.
   * En la TV, ábrelo con cualquier explorador de archivos (ej. *X-plore* o *File Commander*) e instálalo.
2. **Vía Memoria USB**:
   * Copia `release/GarecaTV.apk` a un pendrive USB.
   * Conéctalo a tu televisor e instálalo con el explorador de archivos.
3. **Vía ADB**:
   ```bash
   adb connect <IP_DE_TU_TV>:5555
   adb install /home/sataca/Descargas/APLICACIONES/errado/release/GarecaTV.apk
   ```

---

## 📚 Los 4 Documentos Maestros del Proyecto (Estándar de Desarrollo)

Este proyecto mantiene 4 documentos estándar para que cualquier desarrollador o modelo de IA (Antigravity, Cursor, Claude, GPT) cuente con el 100% del contexto:

1. 🤖 **[`AGENTS.md`](file:///home/sataca/Descargas/APLICACIONES/errado/AGENTS.md)**: Directivas maestras, arquitectura técnica y reglas inmutables para Agentes de Inteligencia Artificial.
2. 📋 **[`PROJECT_CONTEXT.md`](file:///home/sataca/Descargas/APLICACIONES/errado/PROJECT_CONTEXT.md)**: Documento integral del proyecto: descubrimientos de ingeniería inversa, endpoints, descifrado AES-256 y comportamiento en Smart TV.
3. 📜 **[`CHANGELOG.md`](file:///home/sataca/Descargas/APLICACIONES/errado/CHANGELOG.md)**: Historial cronológico de cambios, correcciones de errores y nuevas funciones por versión.
4. 📖 **[`README.md`](file:///home/sataca/Descargas/APLICACIONES/errado/README.md)**: Guía rápida para usuarios, instalación de APK y scripts de automatización.

---

## 🔄 Sistema de Actualización Automática (OTA Autónomo)

El proyecto incluye el archivo maestro [`version.json`](https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json) en GitHub.  
Para compilar y publicar una nueva versión sin tocar ningún archivo manualmente:
```bash
./publish_release.sh 1.3.7 20 "Pestaña Repeticiones antes de Regionales con buscador y ordenacion"
```
El script compila, versiona los APKs en `release/`, actualiza `version.json` y sube todo a GitHub automáticamente. Todas las Smart TVs detectarán la nueva versión sola por WiFi.
