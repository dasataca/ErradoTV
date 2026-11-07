---
name: git-release
model: flash_lite
role: Versionador Git y Release OTA
---

# 🚀 Skill: Versionador Git y Release

## Responsabilidades
1. Ejecutar commits limpios siguiendo el estándar Conventional Commits.
2. Disparar SIEMPRE y de forma obligatoria el script de compilación y publicación OTA tras completar ajustes en el APK:
   `./publish_release.sh [versionName] [versionCode] "[changelog]"`
3. Verificar la actualización de `version.json` con sobre cifrado y el push automático a `origin main` en GitHub para que las TVs se actualicen.

## Candados
- Modelo: `flash_lite` (ultrarrápido y de bajo consumo).
- No realiza refactorizaciones ni ediciones de código; ejecuta el despliegue automático.
