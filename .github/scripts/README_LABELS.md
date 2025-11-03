# 🏷️ Scripts para Crear Labels

Este directorio contiene scripts para crear automáticamente todas las labels necesarias en GitHub.

## 📋 Labels que se crearán

- `🐛 bug` - Algo no funciona correctamente
- `✨ feature` - Nueva funcionalidad o característica
- `🎨 ui` - Cambios de interfaz o diseño
- `🔧 refactor` - Refactorización de código
- `📝 documentation` - Cambios en documentación
- `🧪 tests` - Tests o mejoras de testing
- `🔒 security` - Cambios relacionados con seguridad
- `⚙️ config` - Cambios de configuración
- `🚀 release` - Preparación de release o versión
- `🔨 maintenance` - Tareas de mantenimiento
- `⚡ performance` - Mejoras de rendimiento
- `🐛 bugfix` - Corrección de errores
- `📦 dependencies` - Actualización de dependencias

## 🚀 Uso

### Opción 1: Script Bash (Linux/Mac/WSL)

```bash
# Dar permisos de ejecución
chmod +x .github/scripts/create-labels.sh

# Ejecutar
./.github/scripts/create-labels.sh
```

**Requisitos:**
- GitHub CLI (`gh`) instalado
- Autenticado con `gh auth login`

### Opción 2: Script PowerShell (Windows)

```powershell
# Ejecutar
.\.github\scripts\create-labels.ps1
```

**Requisitos:**
- GitHub CLI (`gh`) instalado
- Autenticado con `gh auth login`

### Opción 3: Script Node.js (Multiplataforma)

```bash
# Configurar token
export GITHUB_TOKEN=tu_token_github

# Ejecutar
node .github/scripts/create-labels.js
```

**Requisitos:**
- Node.js instalado
- Token de GitHub (Personal Access Token con permisos `repo`)

## 🔑 Obtener Token de GitHub

1. Ve a: **GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. Click en **"Generate new token"**
3. Selecciona el scope: `repo` (Full control of private repositories)
4. Copia el token y úsalo en el script

## 📝 Notas

- Los scripts verifican si las labels ya existen antes de crearlas
- Si una label ya existe, se omite automáticamente
- Los scripts son idempotentes (puedes ejecutarlos múltiples veces)

## ⚠️ Troubleshooting

### GitHub CLI no está instalado

**Windows:**
```powershell
winget install GitHub.cli
```

**Linux:**
```bash
# Ubuntu/Debian
sudo apt install gh

# macOS
brew install gh
```

### No estás autenticado

```bash
gh auth login
```

Sigue las instrucciones en pantalla para autenticarte.

### Error de permisos

- Verifica que tu token tiene permisos `repo`
- Verifica que tienes permisos de administrador en el repositorio

