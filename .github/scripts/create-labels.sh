#!/bin/bash

# Script para crear labels automáticamente en GitHub
# Requiere: GitHub CLI (gh) instalado y autenticado
# Uso: ./create-labels.sh

set -e

# Colores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🏷️  Creando labels en GitHub...${NC}"

# Verificar que gh está instalado
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ Error: GitHub CLI (gh) no está instalado.${NC}"
    echo "Instálalo desde: https://cli.github.com/"
    exit 1
fi

# Verificar autenticación
if ! gh auth status &> /dev/null; then
    echo -e "${YELLOW}⚠️  No estás autenticado con GitHub CLI.${NC}"
    echo "Ejecuta: gh auth login"
    exit 1
fi

# Array de labels: "nombre" "color" "descripción"
declare -a labels=(
    "🐛 bug|FF6B6B|Algo no funciona correctamente"
    "✨ feature|51CF66|Nueva funcionalidad o característica"
    "🎨 ui|9775FA|Cambios de interfaz o diseño"
    "🔧 refactor|339AF0|Refactorización de código"
    "📝 documentation|F59F00|Cambios en documentación"
    "🧪 tests|37B24D|Tests o mejoras de testing"
    "🔒 security|E03131|Cambios relacionados con seguridad"
    "⚙️ config|868E96|Cambios de configuración"
    "🚀 release|FA5252|Preparación de release o versión"
    "🔨 maintenance|495057|Tareas de mantenimiento"
    "⚡ performance|845EF7|Mejoras de rendimiento"
    "🐛 bugfix|FF6B6B|Corrección de errores"
    "📦 dependencies|845EF7|Actualización de dependencias"
)

# Contador
created=0
skipped=0
failed=0

# Crear cada label
for label_info in "${labels[@]}"; do
    IFS='|' read -r name color description <<< "$label_info"
    
    # Verificar si la label ya existe
    if gh label list --json name --jq '.[].name' | grep -q "^${name}$"; then
        echo -e "${YELLOW}⏭️  Label '${name}' ya existe, saltando...${NC}"
        ((skipped++))
    else
        # Crear label
        if gh label create "${name}" --color "${color}" --description "${description}" 2>/dev/null; then
            echo -e "${GREEN}✅ Creada: ${name}${NC}"
            ((created++))
        else
            echo -e "${RED}❌ Error creando: ${name}${NC}"
            ((failed++))
        fi
    fi
done

# Resumen
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Creadas: ${created}${NC}"
echo -e "${YELLOW}⏭️  Saltadas: ${skipped}${NC}"
if [ $failed -gt 0 ]; then
    echo -e "${RED}❌ Fallidas: ${failed}${NC}"
fi
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ $failed -eq 0 ]; then
    echo -e "${GREEN}🎉 ¡Labels creadas exitosamente!${NC}"
    exit 0
else
    echo -e "${RED}⚠️  Algunas labels fallaron. Revisa los errores arriba.${NC}"
    exit 1
fi

