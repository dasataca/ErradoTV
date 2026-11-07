# 🩺 04. Auditoría Forense y Deuda Técnica — GarecaTV

## 1. Cadena de Latencia Detectada (Diagnóstico Forense)

| Componente | Latencia Medida | Causa Raíz en Código | Solución Aplicada |
| :--- | :--- | :--- | :--- |
| **Resolución DNS** | 180 ms - 350 ms | `SecureDns` ejecuta peticiones HTTPS DoH repetidas. | Caché en RAM volátil `ConcurrentHashMap` con TTL de 30 min. |
| **Resolución Stream 'config'** | 250 ms - 600 ms | Petición HTTP síncrona a `cdnapp3.pages.dev` en cada zapping. | Mapa de caché en memoria RAM para URLs ya resueltas. |
| **Descarga Inicial de Chunks** | 800 ms - 1.400 ms | `setForceHighestSupportedBitrate(true)` forzando chunks 1080p pesados. | Fast-Start ABR adaptativo con arranque a 720p/300KB y ascenso fluido. |
| **Live Target Offset** | 300 ms - 600 ms | `targetOffsetMs(12_000L)` salta 12s atrás saliendo de la ventana del CDN. | `targetOffsetMs(3500L)` alineado a la ventana real. |
| **Pipeline en Zapping** | 400 ms - 600 ms | `player.stop()` destruyendo extractores y superficies. | Reutilización persistente de `DefaultMediaSourceFactory`. |
| **Render de Listas UI** | Picos de 20-30 FPS | `notifyDataSetChanged()` y dobles submits vacíos. | Migración a `DiffUtil.ItemCallback` con actualización quirúrgica. |

## 2. Puntos de Monitoreo Continuo
- Monitoreo de memoria Heap JVM en Smart TVs de 1GB RAM (mantener por debajo de 120MB).
- Verificación periódica de certificados SSL y endpoints Cloudflare Pages.
- Validación de que ningún commit introduzca SDKs publicitarios.
