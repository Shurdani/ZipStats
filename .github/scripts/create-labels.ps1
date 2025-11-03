# Script PowerShell para crear labels automáticamente en GitHub
# Requiere: GitHub CLI (gh) instalado y autenticado
# Uso: .\create-labels.ps1

$ErrorActionPreference = "Stop"

# Colores para output
function Write-Success { Write-Host $args -ForegroundColor Green }
function Write-Warning { Write-Host $args -ForegroundColor Yellow }
function Write-Error { Write-Host $args -ForegroundColor Red }
function Write-Info { Write-Host $args -ForegroundColor Cyan }

Write-Success "🏷️  Creando labels en GitHub..."

# Verificar que gh está instalado
try {
    $null = Get-Command gh -ErrorAction Stop
} catch {
    Write-Error "❌ Error: GitHub CLI (gh) no está instalado."
    Write-Info "Instálalo desde: https://cli.github.com/"
    exit 1
}

# Verificar autenticación
try {
    gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw
    }
} catch {
    Write-Warning "⚠️  No estás autenticado con GitHub CLI."
    Write-Info "Ejecuta: gh auth login"
    exit 1
}

# Array de labels: nombre, color, descripción
$labels = @(
    @{ Name = "🐛 bug"; Color = "FF6B6B"; Description = "Algo no funciona correctamente" }
    @{ Name = "✨ feature"; Color = "51CF66"; Description = "Nueva funcionalidad o característica" }
    @{ Name = "🎨 ui"; Color = "9775FA"; Description = "Cambios de interfaz o diseño" }
    @{ Name = "🔧 refactor"; Color = "339AF0"; Description = "Refactorización de código" }
    @{ Name = "📝 documentation"; Color = "F59F00"; Description = "Cambios en documentación" }
    @{ Name = "🧪 tests"; Color = "37B24D"; Description = "Tests o mejoras de testing" }
    @{ Name = "🔒 security"; Color = "E03131"; Description = "Cambios relacionados con seguridad" }
    @{ Name = "⚙️ config"; Color = "868E96"; Description = "Cambios de configuración" }
    @{ Name = "🚀 release"; Color = "FA5252"; Description = "Preparación de release o versión" }
    @{ Name = "🔨 maintenance"; Color = "495057"; Description = "Tareas de mantenimiento" }
    @{ Name = "⚡ performance"; Color = "845EF7"; Description = "Mejoras de rendimiento" }
    @{ Name = "🐛 bugfix"; Color = "FF6B6B"; Description = "Corrección de errores" }
    @{ Name = "📦 dependencies"; Color = "845EF7"; Description = "Actualización de dependencias" }
)

# Obtener labels existentes
$existingLabels = gh label list --json name --jq '.[].name' | ForEach-Object { $_.Trim() }

$created = 0
$skipped = 0
$failed = 0

# Crear cada label
foreach ($label in $labels) {
    $labelName = $label.Name
    
    # Verificar si la label ya existe
    if ($existingLabels -contains $labelName) {
        Write-Warning "⏭️  Label '$labelName' ya existe, saltando..."
        $skipped++
    } else {
        # Crear label
        try {
            gh label create $labelName --color $label.Color --description $label.Description 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Success "✅ Creada: $labelName"
                $created++
            } else {
                throw "Error en gh label create"
            }
        } catch {
            Write-Error "❌ Error creando: $labelName"
            $failed++
        }
    }
}

# Resumen
Write-Host ""
Write-Success "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
Write-Success "✅ Creadas: $created"
Write-Warning "⏭️  Saltadas: $skipped"
if ($failed -gt 0) {
    Write-Error "❌ Fallidas: $failed"
}
Write-Success "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
Write-Host ""

if ($failed -eq 0) {
    Write-Success "🎉 ¡Labels creadas exitosamente!"
    exit 0
} else {
    Write-Error "⚠️  Algunas labels fallaron. Revisa los errores arriba."
    exit 1
}

