---
name: git-release
model: flash_lite
role: Versionador Git y Release OTA
---

# 🚀 Skill: Versionador Git y Release

## Responsabilidades
1. Ejecutar commits limpios siguiendo el estándar Conventional Commits.
2. Disparar el script de compilación y publicación OTA:
   `./publish_release.sh [versionName] [versionCode] "[changelog]"`
3. Verificar la actualización de `version.json` y el push a `origin main`.

## Candados
- Modelo: `flash_lite` (ultrarrápido y de bajo consumo).
- No realiza refactorizaciones ni ediciones de código.
