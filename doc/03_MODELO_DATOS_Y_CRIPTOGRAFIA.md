# 🔐 03. Modelo de Datos, Persistencia y Criptografía — GarecaTV

## 1. Esquema de Cifrado y Protección en Memoria
* **Algoritmo**: `AES/CBC/PKCS5Padding` (256 bits).
* **Blindaje Anti-Decompilación XOR**:
  - La clave de fábrica en Base64 no existe en texto plano en el DEX.
  - Se recompone en memoria volátil aplicando máscara XOR de 1 byte (`SHIELD_MASK = 0x5A`) sobre `SHIELDED_KEY_BYTES`.
  - Derivación: `SHA-256("R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==")`.
* **Estructura del Payload Cifrado**:
  - Bytes 0 a 15: Vector de Inicialización (IV).
  - Bytes 16 al final: Ciphertext AES-256.
* **Resiliencia Remota**:
  - `CryptoUtils.kt` consulta primero `version.json` en GitHub. Si falla o entrega datos corruptos, conmuta de forma autónoma a la clave de fábrica en RAM.

## 2. Persistencia Local (`AppDatabase.kt` Room / SQLite)
* **Tablas**:
  - `channels`: Almacena catálogos serializados (`daily`, `replay`, `live`, `cxtv`, `freetv`).
  - `favorites`: Identificadores de canales y streams marcados como favoritos por el usuario.
* **Caché en RAM O(1)**:
  - `cachedSportsFavorites` y `cachedCxtvFavorites` en memoria RAM volátil para verificar estados durante el scroll sin lecturas síncronas a disco.

## 3. Telemetría y Comunidad (Supabase)
* **Proyecto**: `https://pfhjrplnfuupftgzdnop.supabase.co`.
* **Tabla Telemetría**: `public.errado_tv_logs` (buffer circular de 1.000 filas).
* **Tabla Comunitaria**: `public.errado_community_streams` (almacena reportes de enlaces caídos y alternativas comunitarias).
