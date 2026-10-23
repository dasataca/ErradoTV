#!/usr/bin/env bash
set -e

# Script de automatización de compilación y publicación de versiones ErradoTV
# Uso: ./publish_release.sh [version_name] [version_code] [changelog]
# Ejemplo: ./publish_release.sh 1.1.0 2 "Nuevos canales y estabilidad"

VERSION_NAME=${1:-"1.0.0"}
VERSION_CODE=${2:-1}
CHANGELOG=${3:-"Correcciones y mejoras generales"}

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "🚀 Publicando ErradoTV v$VERSION_NAME ($VERSION_CODE)"
echo "=========================================="

# 1. Actualizar build.gradle.kts si los valores cambiaron
sed -i "s/versionCode = [0-9]\+/versionCode = $VERSION_CODE/g" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/g" app/build.gradle.kts

# 2. Compilar APK
echo "📦 Compilando APK..."
./gradlew assembleDebug

# 3. Organizar en carpeta release
mkdir -p release
APK_VERSIONED="release/ErradoTV-v${VERSION_NAME}.apk"
APK_LATEST="release/ErradoTV.apk"

cp app/build/outputs/apk/debug/app-debug.apk "$APK_VERSIONED"
cp app/build/outputs/apk/debug/app-debug.apk "$APK_LATEST"
cp app/build/outputs/apk/debug/app-debug.apk ErradoTV-v1.0.apk

echo "✅ APK generado:"
ls -lh "$APK_VERSIONED" "$APK_LATEST"

# 4. Git commit y push si git está configurado
git add release/ app/build.gradle.kts ErradoTV-v1.0.apk
git commit -m "release: ErradoTV v$VERSION_NAME ($VERSION_CODE) - $CHANGELOG" || true

echo "Pushing a GitHub..."
git push origin main || echo "⚠️ Recuerda configurar tus credenciales de git para subirlo automáticamente."

echo "🎉 Listo. Para que la TV actualice, edita tu Gist con:"
cat <<EOF
{
  "active_key": "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==",
  "latest_version_code": $VERSION_CODE,
  "latest_version_name": "$VERSION_NAME",
  "apk_url": "https://raw.githubusercontent.com/dasataca/ErradoTV/main/release/ErradoTV.apk",
  "changelog": "$CHANGELOG"
}
EOF
