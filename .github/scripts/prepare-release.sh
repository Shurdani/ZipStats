#!/bin/bash
# Script para facilitar la creación de releases
# Uso: ./prepare-release.sh [major|minor|patch]

set -e

VERSION_TYPE=${1:-patch}
CURRENT_VERSION=$(grep "versionName" app/build.gradle | sed -n 's/.*versionName "\(.*\)".*/\1/p')
CURRENT_CODE=$(grep "versionCode" app/build.gradle | sed -n 's/.*versionCode \(.*\).*/\1/p')

IFS='.' read -ra ADDR <<< "$CURRENT_VERSION"
MAJOR=${ADDR[0]}
MINOR=${ADDR[1]}
PATCH=${ADDR[2]}

case $VERSION_TYPE in
  major)
    NEW_MAJOR=$((MAJOR + 1))
    NEW_MINOR=0
    NEW_PATCH=0
    ;;
  minor)
    NEW_MAJOR=$MAJOR
    NEW_MINOR=$((MINOR + 1))
    NEW_PATCH=0
    ;;
  patch)
    NEW_MAJOR=$MAJOR
    NEW_MINOR=$MINOR
    NEW_PATCH=$((PATCH + 1))
    ;;
  *)
    echo "❌ Tipo de versión inválido. Usa: major, minor o patch"
    exit 1
    ;;
esac

NEW_VERSION="$NEW_MAJOR.$NEW_MINOR.$NEW_PATCH"
NEW_CODE=$((CURRENT_CODE + 1))

echo "📦 Preparando release..."
echo "Versión actual: $CURRENT_VERSION ($CURRENT_CODE)"
echo "Nueva versión: $NEW_VERSION ($NEW_CODE)"
echo ""

# Actualizar build.gradle
sed -i "s/versionName \"$CURRENT_VERSION\"/versionName \"$NEW_VERSION\"/" app/build.gradle
sed -i "s/versionCode $CURRENT_CODE/versionCode $NEW_CODE/" app/build.gradle

# Actualizar README.md
sed -i "s/Version-$CURRENT_VERSION/Version-$NEW_VERSION/" README.md

echo "✅ Archivos actualizados"
echo ""
echo "📝 Próximos pasos:"
echo "1. Revisa los cambios: git diff"
echo "2. Commit: git commit -am \"chore: bump version to $NEW_VERSION\""
echo "3. Crea el tag: git tag -a v$NEW_VERSION -m \"Release $NEW_VERSION\""
echo "4. Push: git push && git push origin v$NEW_VERSION"
echo ""
echo "🚀 El workflow de release se ejecutará automáticamente al hacer push del tag!"

