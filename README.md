# 📺 GarecaTV — Android TV App (Libre de Publicidad)

Aplicación nativa para **Android TV, Google TV y Fire TV Stick** basada en las transmisiones de Errado / Golazo App, completamente limpia de anuncios y 100% optimizada para control remoto (Leanback D-Pad).

---

## 🚀 Archivos y Versión Actual

* 📱 **[`release/ErradoTV.apk`](file:///home/sataca/Descargas/APLICACIONES/errado/release/ErradoTV.apk)**: APK instalable directa (versión actual **v1.3.7**).
* 📺 **[`errado_tv.m3u8`](file:///home/sataca/Descargas/APLICACIONES/errado/errado_tv.m3u8)**: Lista de reproducción IPTV directa para TiviMate, OTT Navigator o VLC.
* 🐅 **Identidad GarecaTV**:
  * Icono y banner de alto contraste con cabeza de tigre en perfil (un ojo visible, mandíbula rugiente y zarpazos rojos sobre fondo negro).
  * Suite completa de iconos adaptativos Android (`res/mipmap-*`).
  * Icono exclusivo en la cabecera superior principal de la app (filas de partidos limpias sin repeticiones).
* 🔁 **Pestaña Exclusiva de Repeticiones con Buscador TV**:
  * Pestaña `🔁 Repeticiones` situada justo antes de `📺 Regionales`.
  * Buscador en tiempo real y ordenación por **Fecha** y **Nombre**.
  * Navegación fluida con control remoto: flecha abajo enfoca la búsqueda; presionar OK abre el teclado; presionar abajo sin pulsar OK salta directo al primer partido.
  * La pestaña `⚽ Partidos del Día` muestra únicamente los partidos en vivo y programados de hoy.
* 🌐 **Inteligencia Comunitaria (Supabase)**:
  * Registro y sincronización automática de señales caídas con la base de datos comunitaria (`errado_community_streams`), reordenando partidos `NO DISPONIBLE` al final de la lista.

---

## 🛠️ Cómo Instalar el APK en tu Televisor

1. **Vía WiFi con la app "Send Files to TV" (Recomendado)**:
   * Instala "Send Files to TV" en tu teléfono móvil/PC y en tu televisor desde Google Play Store.
   * Envía el archivo `release/ErradoTV.apk` a la TV.
   * En la TV, ábrelo con cualquier explorador de archivos (ej. *X-plore* o *File Commander*) e instálalo.
2. **Vía Memoria USB**:
   * Copia `release/ErradoTV.apk` a un pendrive USB.
   * Conéctalo a tu televisor e instálalo con el explorador de archivos.
3. **Vía ADB**:
   ```bash
   adb connect <IP_DE_TU_TV>:5555
   adb install /home/sataca/Descargas/APLICACIONES/errado/release/ErradoTV.apk
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
