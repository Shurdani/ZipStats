# 🔒 Configuración de Seguridad para Repositorio Público

Este documento explica cómo se protegen las credenciales sensibles en este proyecto para permitir un repositorio público seguro.

## 🛡️ Estrategia de Seguridad Implementada

### 1. Firebase (google-services.json)
- ✅ **Archivo local:** `app/google-services.json` 
- ✅ **En .gitignore:** SÍ - No se sube a GitHub
- ✅ **Archivo de ejemplo:** `app/google-services.json.example` con placeholders
- ✅ **Riesgo si se expone:** ALTO - acceso completo a servicios Firebase

### 2. Cloudinary (Credenciales de Imágenes)
- ✅ **Ubicación:** `local.properties` (líneas cloudinary.*)
- ✅ **En .gitignore:** SÍ - No se sube a GitHub  
- ✅ **Método de carga:** BuildConfig desde Gradle
- ✅ **Archivo de ejemplo:** `local.properties.example` con placeholders
- ✅ **Implementación:** 
  ```kotlin
  // CloudinaryService.kt
  private val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
  private val API_KEY = BuildConfig.CLOUDINARY_API_KEY
  private val API_SECRET = BuildConfig.CLOUDINARY_API_SECRET
  ```

### 3. Android SDK Path
- ✅ **Ubicación:** `local.properties` (sdk.dir)
- ✅ **En .gitignore:** SÍ
- ✅ **Razón:** Ruta específica de cada desarrollador

## 📋 Archivos Protegidos

| Archivo | Contiene | Estado | Alternativa Pública |
|---------|----------|--------|---------------------|
| `local.properties` | SDK path + Cloudinary | ❌ No se sube | ✅ `local.properties.example` |
| `app/google-services.json` | Firebase config | ❌ No se sube | ✅ `app/google-services.json.example` |
| `*.keystore` | Certificados de firma | ❌ No se sube | - |
| `*.jks` | Certificados de firma | ❌ No se sube | - |

## 🚀 Configuración para Nuevos Desarrolladores

### Paso 1: Clonar el Repositorio
```bash
git clone https://github.com/Shurdani/ZipStats.git
cd ZipStats
```

### Paso 2: Configurar Credenciales Locales

#### Firebase
```bash
cp app/google-services.json.example app/google-services.json
# Editar app/google-services.json con tus credenciales de Firebase
```

#### Cloudinary
```bash
cp local.properties.example local.properties
# Editar local.properties y agregar:
# cloudinary.cloud_name=TU_CLOUD_NAME
# cloudinary.api_key=TU_API_KEY
# cloudinary.api_secret=TU_API_SECRET
```

### Paso 3: Compilar
```bash
./gradlew assembleDebug
```

## 🔍 Verificación de Seguridad

### Antes de Hacer Commit/Push

Verifica que estos archivos NO aparezcan:
```bash
git status
```

**NO deben estar staged:**
- ❌ `local.properties`
- ❌ `app/google-services.json`
- ❌ `*.keystore` o `*.jks`

Si aparecen, significa que `.gitignore` no está funcionando correctamente.

### Comando de Verificación
```bash
# Ver qué archivos serían subidos
git add .
git status

# Si ves archivos sensibles, NO hagas push
# Revisa tu .gitignore
```

## ⚠️ ¿Qué Hacer Si Expones Credenciales?

### Si subiste credenciales por error:

1. **Firebase:**
   - Regenera `google-services.json` en Firebase Console
   - Rota las API Keys
   - Revoca el archivo anterior

2. **Cloudinary:**
   - Ve a [Cloudinary Console](https://cloudinary.com/console)
   - Settings → Security → Reset API Secret
   - Actualiza `local.properties` local

3. **Git History:**
   - Usa `git filter-branch` o BFG Repo-Cleaner para eliminar del historial
   - O crea un nuevo repositorio limpio

## 📊 Resumen de Seguridad

### ✅ Protecciones Implementadas
- BuildConfig para credenciales Cloudinary
- .gitignore comprensivo
- Archivos de ejemplo sin datos reales
- Documentación clara de configuración
- Separación de configuración local vs código

### ❌ Riesgos Eliminados
- No hay credenciales hardcodeadas en el código
- No hay archivos de configuración sensibles en el repo
- Cada desarrollador usa sus propias credenciales

### 🎯 Resultado
**✅ Repositorio 100% seguro para ser público**

---

**Última actualización:** 16 de Octubre, 2025
**Mantenedor:** @tu-usuario

