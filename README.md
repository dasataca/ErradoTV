# 📺 ErradoTV — Android TV App (Libre de Publicidad)

Aplicación nativa para **Android TV, Google TV y Fire TV Stick** basada en las transmisiones de Errado / Golazo App, completamente limpia de anuncios y optimizada para control remoto.

---

## 🚀 Archivos Listos para Usar

* 📱 **[`ErradoTV-v1.0.apk`](file:///home/sataca/Descargas/APLICACIONES/errado/ErradoTV-v1.0.apk)**: APK compilada (10 MB) para instalar en tu Smart TV o TV Box.
* 📺 **[`errado_tv.m3u8`](file:///home/sataca/Descargas/APLICACIONES/errado/errado_tv.m3u8)**: Lista de reproducción IPTV directa para TiviMate, OTT Navigator o VLC.
* 🔍 **[`extract_key.py`](file:///home/sataca/Descargas/APLICACIONES/errado/extract_key.py)**: Extractor automático de claves criptográficas para futuras versiones de la APK.
* ⚡ **[`generate_playlist.py`](file:///home/sataca/Descargas/APLICACIONES/errado/generate_playlist.py)**: Script para regenerar la lista de partidos del día en un clic.

---

## 🛠️ Cómo Instalar el APK en tu Televisor

1. **Vía WiFi con la app "Send Files to TV" (Recomendado)**:
   * Instala "Send Files to TV" en tu teléfono móvil/PC y en tu televisor desde Google Play Store.
   * Envía el archivo `ErradoTV-v1.0.apk` a la TV.
   * En la TV, ábrelo con cualquier explorador de archivos (ej. *X-plore* o *File Commander*) e instálalo.
2. **Vía Memoria USB**:
   * Copia `ErradoTV-v1.0.apk` a un pendrive.
   * Conéctalo al puerto USB de tu televisor e instálalo.
3. **Vía ADB**:
   ```bash
   adb connect <IP_DE_TU_TV>:5555
   adb install /home/sataca/Descargas/APLICACIONES/errado/ErradoTV-v1.0.apk
   ```

---

## 🌐 Dónde Alojar el Archivo Web de la Clave Remota (Paso a Paso)

Para que tu app de TV se actualice automáticamente si cambian la clave secreta en el futuro, puedes alojar un archivo JSON gratuito en cualquiera de estos servicios:

### Tu Gist Personal (Ya Vinculado en la App)
Tu aplicación de Android TV ya quedó enlazada a tu Gist permanente:
* **URL en vivo**: `https://gist.githubusercontent.com/dasataca/b5f27059b9db7e878e6a0fab05a7a951/raw/config.json`
* **Cómo editarlo en el futuro**: Entra a tu cuenta de GitHub, abre tu Gist, haz clic en **Edit**, actualizas el campo `"active_key"` si algún día cambia y guardas. La app en tu TV leerá el nuevo valor al instante.

> **Nota**: La app además incluye la clave de respaldo integrada, por lo que si te quedas sin conexión a internet en la TV o el Gist estuviera inaccesible, la app sigue funcionando con la clave local.

---

## 🔑 Cómo Extraer la Nueva Clave si Lanzan Otra APK

Si los desarrolladores publican una versión 3.0 o actualizan su aplicación:
```bash
python3 /home/sataca/Descargas/APLICACIONES/errado/extract_key.py ruta_a_la_nueva_apk.apk
```
El script analizará los DEX, extraerá la clave Base64 y te la mostrará en pantalla en solo 2 segundos.

---

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
./publish_release.sh 1.1.3 5 "Descripción de mejoras"
```
El script compila, versiona los APKs en `release/`, actualiza `version.json` y sube todo a GitHub automáticamente. Todas las Smart TVs detectarán la nueva versión sola por WiFi.
