# 🗺️ ROADMAP.md — Hoja de Ruta Maestra de Rescate y Rendimiento (v4.6)

> **Reglas de Ejecución con Micro-Comandos**:
> - `[auto]`: Tareas mecánicas o de infraestructura. El Planificador analiza en lote, espera un único `APROBADO` y las corre en subagentes en cadena sin pausas.
> - `[ver]`: Tareas delicadas de arquitectura o visuales. Frena antes de codificar mostrando el diseño esperando `APROBADO`; ejecuta en subagente limpio; y frena al terminar mostrando el log de tests.

---

## Fase 0: Base Heredada Estable y Operativa (`[x]`)
- [x] **0.1 [auto]**: Arquitectura Android Leanback TV 60 FPS sin animadores CPU en listas (`app/src/main/res/drawable/*_selector.xml`).
- [x] **0.2 [auto]**: Motor criptográfico AES-256-CBC con clave ofuscada XOR en (`app/src/main/java/com/gareca/tv/api/CryptoUtils.kt`).
- [x] **0.3 [auto]**: Telemetría y reporte comunitario de enlaces caídos en (`app/src/main/java/com/gareca/tv/telemetry/SupabaseLogger.kt`).
- [x] **0.4 [auto]**: Botón interactivo y HUD de Zoom / Aspect Ratio en (`app/src/main/java/com/gareca/tv/PlayerActivity.kt`).
- [x] **0.5 [auto]**: Pipeline de publicación y actualización OTA en (`publish_release.sh`).

---

## Fase 1: Saneamiento de Red, Sockets y DNS (0 ms Lookup) (`[x]`)
- [x] **1.1 [auto]**: Implementación de caché DNS en RAM volátil (`ConcurrentHashMap`, TTL 30 min) en (`app/src/main/java/com/gareca/tv/network/SecureDns.kt`).
- [x] **1.2 [auto]**: Configuración de `ConnectionPool` para streaming (32 sockets keep-alive) y mapa en RAM de URLs resueltas `config` en (`app/src/main/java/com/gareca/tv/api/ApiClient.kt`).

---

## Fase 2: Motor ExoPlayer Fast-Start estilo TiviMate (< 800ms) (`[x]`)
- [x] **2.1 [auto]**: Ajuste de `DefaultLoadControl` (buffer inicio 300ms, min 15s, max 25s) y `LiveConfiguration` (targetOffsetMs a 3.5s) en (`app/src/main/java/com/gareca/tv/PlayerActivity.kt`).
- [x] **2.2 [auto]**: Eliminación de `setForceHighestSupportedBitrate(true)` en `DefaultTrackSelector` para Fast-Start ABR adaptativo en (`app/src/main/java/com/gareca/tv/PlayerActivity.kt`).
- [x] **2.3 [auto]**: Zapping continuo sin destrucción de pipeline (`DefaultMediaSourceFactory` persistente sin `player.stop()`) en (`app/src/main/java/com/gareca/tv/PlayerActivity.kt`).

---

## Fase 3: Fluidez de Listas D-Pad a 60 FPS (`[x]`)
- [x] **3.1 [auto]**: Migración de `EventAdapter` a `DiffUtil.ItemCallback` (eliminando `notifyDataSetChanged()`) en (`app/src/main/java/com/gareca/tv/EventAdapter.kt`).
- [x] **3.2 [auto]**: Supresión de llamadas `adapter.submitList(emptyList())` redundantes en (`app/src/main/java/com/gareca/tv/MainActivity.kt`).

---

## Fase 4: Verificación, Compilación y Release OTA (`[x]`)
- [x] **4.1 [auto]**: Verificación estricta de compilación nativa (`./gradlew assembleRelease`).
- [x] **4.2 [auto]**: Publicación y despliegue OTA v1.9.21 (86) mediante (`publish_release.sh`).
