# 🤖 Instrucciones y Contexto Estándar para Inteligencias Artificiales (AI Guidelines)

Este documento ha sido generado para que cualquier Inteligencia Artificial (Antigravity, Cursor, Claude, GitHub Copilot, OpenAI GPT) que cargue este repositorio disponga de forma inmediata del contexto técnico, decisiones de arquitectura y directrices de desarrollo sin necesidad de reanalizar el código desde cero.

---

## 1. Identidad del Repositorio
* **Proyecto**: `ErradoTV` (Paquete Android: `com.errado.tv`)
* **Plataforma**: Android TV / Google TV (SDK Mínimo: 24, SDK Compilación: 34).
* **Propósito**: Cliente nativo libre de publicidad y optimizado para control remoto, derivado de la app móvil `mta.xer.errado`.

---

## 2. Puntos Clave de la Arquitectura Criptográfica
* **Endpoints Remotos**: Servidos públicamente en Cloudflare Pages (`https://errado-app.pages.dev/data/event.json` y `destacado.json`).
* **Algoritmo de Descifrado**: `AES/CBC/PKCS5Padding`.
* **Clave de Fábrica (Base64)**: `R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==` (Texto: `GolazooAppSecretKey2024Secure!!`).
* **Derivación**: Se aplica hash `SHA-256` al string en Base64 para obtener los 32 bytes necesarios para AES-256.
* **Vector de Inicialización (IV)**: Primeros 16 bytes de la carga Base64 decodificada. El resto es el texto cifrado.
* **Sincronización Dinámica**: `CryptoUtils.kt` cuenta con un sistema de clave activa persistida en `SharedPreferences` con capacidad de sincronizarse con un JSON remoto en la nube y fallback automático a la clave local.

---

## 3. Convenciones de Desarrollo
1. **Sin Redes de Publicidad**: No incluir bajo ninguna circunstancia SDKs de monetización (AdMob, AppLovin, CAS, etc.).
2. **Compatibilidad TV Estricta**:
   * Mantener siempre `android.hardware.touchscreen=false` y la categoría `LEANBACK_LAUNCHER`.
   * Todo componente interactivo debe ser navegable mediante D-Pad con selector visual (`card_selector.xml`).
3. **Reproducción de Video**:
   * Priorizar `androidx.media3:media3-exoplayer` para enlaces directos `.m3u8` y tipo `exo`.
   * En enlaces tipo `web`, utilizar `WebStreamActivity` con bloqueo de anuncios en `shouldInterceptRequest`.
4. **Mantenimiento**:
   * Para extraer nuevas claves si se publica una APK nueva, usar `extract_key.py`.
   * Para generar listas IPTV para reproductores externos, usar `generate_playlist.py`.
