# 📈 PROGRESS.md — Bitácora de Avance y Rescate del Sistema (v4.6)

## Estado General
- **Proyecto**: GarecaTV (Android TV / ExoPlayer / Kotlin)
- **Versión Actual**: 1.9.21 (86)
- **Modo**: Motor Bimodal Inteligente v4.6 (`[auto]` / `[ver]`)

---

## Registro de Actividades y Transiciones

### [2026-11-06] Ejecución de Fases 1, 2, 3 y 4 (Release v1.9.21 - 86)
- **Fase 1 (Saneamiento de Red y DNS)**:
  - Implementación de caché DNS en RAM volátil (`ConcurrentHashMap`, TTL 30 min) en `SecureDns.kt` para lookups en 0 ms.
  - Configuración de `ConnectionPool` de 32 sockets keep-alive y caché en RAM de URLs resueltas tipo `config` en `ApiClient.kt`.
- **Fase 2 (Motor ExoPlayer Fast-Start estilo TiviMate TTFF < 800ms)**:
  - Ajuste de `DefaultLoadControl` (buffer inicio 300ms, min 15s, max 25s) y `LiveConfiguration` (targetOffsetMs a 3.5s).
  - Eliminación de `setForceHighestSupportedBitrate(true)` en `DefaultTrackSelector` para Fast-Start ABR adaptativo.
  - Zapping continuo sin destrucción de pipeline (`DefaultMediaSourceFactory` persistente sin llamar a `player.stop()`).
- **Fase 3 (Fluidez de Listas D-Pad a 60 FPS)**:
  - Migración de `EventAdapter` a `ListAdapter` con `DiffUtil.ItemCallback` (eliminando `notifyDataSetChanged()`).
  - Supresión de llamadas `adapter.submitList(emptyList())` redundantes en `MainActivity.kt`.
- **Fase 4 (Verificación y Despliegue)**:
  - Verificación exitosa de compilación nativa `./gradlew assembleRelease`.
  - Publicación y despliegue OTA v1.9.21 (86) mediante `publish_release.sh`.

### [2026-11-06] Inicialización y Auditoría Forense de Rescate
- **Actividad**: Diagnóstico Forense y Benchmark frente a TiviMate / OTT Navigator.
- **Hallazgos**: Identificada la cadena de latencia de 2.8s (DNS DoH sin caché, bitrate forzado inicial, targetOffset 12s, destrucción de pipeline en zapping).
- **Acciones Realizadas**:
  - Actualización de `AGENTS.md` con el Escuadrón Fijo y Diccionario de Micro-Comandos `[auto]` y `[ver]`.
  - Creación de `ROADMAP.md` con fases 0, 1, 2, 3 y 4 con rutas explícitas de archivos.
  - Creación de suite documental `doc/` e inicialización de `.agents/skills/`.
