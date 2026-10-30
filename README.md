# 📺 GarecaTV — Android TV App (Libre de Publicidad)

Aplicación nativa para **Android TV, Google TV y Fire TV Stick** basada en las transmisiones de Errado / Golazo App, completamente limpia de anuncios y 100% optimizada para control remoto (Leanback D-Pad).

---

## 🚀 Archivos y Versión Actual

* 📱 **[`release/GarecaTV.apk`](file:///home/sataca/Descargas/APLICACIONES/errado/release/GarecaTV.apk)**: APK instalable directa (versión actual **v1.8.3 - código 58**).
* 📺 **[`gareca_tv.m3u8`](file:///home/sataca/Descargas/APLICACIONES/errado/errado_tv.m3u8)**: Lista de reproducción IPTV directa para TiviMate, OTT Navigator o VLC.
* 🏷️ **Versión en Pantalla y Botón de Actualización Inteligente**:
  * Etiqueta de versión visible en cabecera junto a la fecha (`tvAppVersion`).
  * Botón dinámico: muestra `Actualizado` si la versión local es igual a la remota, o `Actualizar (vX.X.X)` abriendo el diálogo OTA si hay una nueva versión.
* 🛡️ **Evasión de Bloqueos de Operadores (DoH) y Blindaje XOR**:
  * Integración nativa de **DNS sobre HTTPS (DoH)** con Cloudflare (`1.1.1.1`) y Google (`8.8.8.8`) en ExoPlayer y llamadas de red. Neutraliza el 85% de los bloqueos locales de proveedores de Internet.
  * Clave de fábrica blindada con máscara XOR en RAM; cero texto plano legible en el APK descompilado.
  * Sistema de **Cifrado de Sobre** (`active_key_enc`) para rotar claves remotamente desde GitHub sin exponerlas a sniffers ni curiosos.
* 🔍 **Buscadores Ergonómicos TV con Sugerencias y Control Remoto D-Pad**:
  * Pestaña **`🔍 Buscar Todo`** (partidos de hoy, canales 24/7 y repeticiones) y buscador en **`🔁 Repeticiones`**.
  * **Disposición Ergonómica**: Botón de micrófono a la izquierda, caja de texto al centro y botón Lupa dedicado a la derecha.
  * **Sugerencias en Vivo con Foco D-Pad**: Al escribir, aparecen sugerencias de equipos y canales. Al pulsar Atrás (`BACK`) en el control, el teclado se oculta y el foco salta instantáneamente al listado de sugerencias.
  * **Foco Automático al Botón Lupa**: Al presionar OK en una sugerencia, el texto se carga y el foco pasa a la Lupa.
  * **Ordenamiento por Nombre en Búsqueda Global**: Botón `🔤 Nombre` para ordenar resultados alfabéticamente (A-Z y Z-A).
  * Búsqueda por voz nativa (`Speech-to-Text`) pulsando el micrófono o la tecla de voz (`KEYCODE_SEARCH`).
* ⏰ **Huso Horario Local y Cuenta Regresiva Automática**:
  * Detección del huso horario de la caja Android TV (`TimeZone.getDefault()`) y conversión de horarios a la hora local exacta del usuario.
  * Cuenta regresiva en cada tarjeta de partido: *"⏳ En 2h 15m"*, *"⏳ En 1d 4h"*, *"● EN JUEGO"*.
  * Cruce inteligente de partidos programados de `teveplay.net` con la agenda diaria.
* 🐅 **Identidad GarecaTV**:
  * Icono circular e insignia de alto contraste con cabeza de tigre en perfil, garras rojas y rótulos atléticos sobre fondo negro.
  * Suite completa de iconos adaptativos Android (`res/mipmap-*`).
  * Visualización limpia: Icono exclusivo en la cabecera superior principal de la app.
* 🎬 **Control de Zoom y Relación de Aspecto en Pantalla (ExoPlayer)**:
  * Ajuste dinámico de pantalla para canales antiguos (4:3) o transmisiones con barras negras:
    1. **Ajuste Original (Fit)**: Relación nativa del canal.
    2. **Rellenar Pantalla (Fill 16:9)**: Estira la imagen para ocupar todo el televisor.
    3. **Zoom / Recorte (Zoom)**: Llena la pantalla recortando proporcionalmente sin deformar la imagen.
  * Botón interactivo en el banner (`btnZoomMode`) accesible con `▲ Arriba` y atajos directos en el control remoto (`MENU`, `BUTTON_Y`, `ZOOM`, `PROG_BLUE`) con indicador visual flotante (`tvZoomHud`).
* 📺 **Arquitectura Dual Core (Deportes vs Televisión Mundial)**:
  * **Motor Deportes (Teleplay)**: Partidos del día con cuenta regresiva, huso horario local y repeticiones.
  * **Motor Televisión Mundial (Estilo MagisTV / IPTV)**: Dos columnas (sidebar lateral de categorías a la izquierda y panel de canales a la derecha) con 2.400 señales de 98 países sin restricciones de idioma.
  * **Distintivos Tecnológicos en Canales**: `⚡ EXOPLAYER HLS`, `🔴 YOUTUBE LIVE`, `🔵 WEB HTML5`, `🌐 WEB EXTERNA`, `⚪ NO DISPONIBLE`.
  * **Menús 100% Autogenerados (Data-Driven)**: Menús dinámicos y canales caídos desplazados al fondo con etiqueta `NO DISPONIBLE`.
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
./publish_release.sh 1.6.7 46 "GarecaTV: Captura Pre-IME de tecla Atrás y transferencia instantánea de foco a sugerencias en ambos buscadores"
```
El script compila, versiona los APKs en `release/`, actualiza `version.json` y sube todo a GitHub automáticamente. Todas las Smart TVs detectarán la nueva versión sola por WiFi.
