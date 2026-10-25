#!/usr/bin/env bash
set -e

# Script de automatización de compilación y publicación de versiones GarecaTV
# Uso: ./publish_release.sh [version_name] [version_code] [changelog]
# Ejemplo: ./publish_release.sh 1.3.8 21 "GarecaTV: Buscador global por voz, horarios locales y cuenta regresiva"

VERSION_NAME=${1:-"1.3.8"}
VERSION_CODE=${2:-21}
CHANGELOG=${3:-"Correcciones y mejoras generales"}

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "🚀 Publicando GarecaTV v$VERSION_NAME ($VERSION_CODE)"
echo "=========================================="

# 1. Actualizar build.gradle.kts si los valores cambiaron
sed -i "s/versionCode = [0-9]\+/versionCode = $VERSION_CODE/g" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/g" app/build.gradle.kts

# 2. Compilar APK
echo "📦 Compilando APK..."
./gradlew assembleDebug

# 3. Organizar en carpeta release
mkdir -p release
APK_VERSIONED="release/GarecaTV-v${VERSION_NAME}.apk"
APK_LATEST="release/GarecaTV.apk"

cp app/build/outputs/apk/debug/app-debug.apk "$APK_VERSIONED"
cp app/build/outputs/apk/debug/app-debug.apk "$APK_LATEST"
cp app/build/outputs/apk/debug/app-debug.apk GarecaTV.apk

echo "✅ APK generado:"
ls -lh "$APK_VERSIONED" "$APK_LATEST"

# 4. Generar version.json automático con Sobre Cifrado para la TV
RELEASE_TIMESTAMP=$(date +%s%3N 2>/dev/null || echo "$(date +%s)000")
RELEASE_DATE=$(date +%Y-%m-%d)
ACTIVE_KEY_ENC=$(python3 herramientas/cifrador_clave/encrypt_key.py | grep '"active_key_enc":' | awk -F'"' '{print $4}')

cat <<EOF > version.json
{
  "active_key": "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==",
  "active_key_enc": "$ACTIVE_KEY_ENC",
  "latest_version_code": $VERSION_CODE,
  "latest_version_name": "$VERSION_NAME",
  "release_timestamp": $RELEASE_TIMESTAMP,
  "release_date": "$RELEASE_DATE",
  "apk_url": "https://raw.githubusercontent.com/dasataca/ErradoTV/main/release/GarecaTV.apk",
  "changelog": "$CHANGELOG"
}
EOF

# 5. Git commit y push automático a GitHub
git add -A
git commit -m "release: GarecaTV v$VERSION_NAME ($VERSION_CODE) - $CHANGELOG" || true

echo "Pushing a GitHub..."
git push origin main

echo "=========================================="
echo "🎉 ¡PUBLICACIÓN 100% AUTOMATIZADA COMPLETADA!"
echo "📡 La versión $VERSION_NAME ($VERSION_CODE) ya está viva en GitHub:"
echo "👉 https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json"
echo "📺 Tus Smart TVs la detectarán solas sin tocar ningún archivo ni Gist."
echo "=========================================="
