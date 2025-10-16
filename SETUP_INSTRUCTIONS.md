# 🚀 Instrucciones de Configuración Rápida

## ⚡ Configuración Inicial (ANTES de compilar)

### 1. Configurar Firebase

```bash
# Copia el archivo de ejemplo
cp app/google-services.json.example app/google-services.json
```

Edita `app/google-services.json` y reemplaza:
- `YOUR_PROJECT_NUMBER` → Tu número de proyecto
- `YOUR_PROJECT_ID` → Tu ID de proyecto
- `YOUR_MOBILE_SDK_APP_ID` → Tu App ID
- `YOUR_CLIENT_ID` → Tu Client ID
- `YOUR_API_KEY` → Tu API Key
- `YOUR_CERTIFICATE_HASH` → Tu hash de certificado

### 2. Configurar local.properties

```bash
# Copia el archivo de ejemplo si no existe
cp local.properties.example local.properties
```

Edita `local.properties` y agrega tus credenciales:

```properties
sdk.dir=C:\\Users\\TuUsuario\\AppData\\Local\\Android\\Sdk

# Cloudinary Configuration
cloudinary.cloud_name=TU_CLOUD_NAME
cloudinary.api_key=TU_API_KEY
cloudinary.api_secret=TU_API_SECRET
```

**⚠️ IMPORTANTE:** 
- Las credenciales se cargan automáticamente desde `local.properties`
- Este archivo está en `.gitignore` y NO se sube a GitHub
- Obtén tus credenciales en: https://cloudinary.com/console

## 📦 Compilar el Proyecto

```bash
# Limpiar y compilar
./gradlew clean build

# O desde Android Studio
Build > Clean Project
Build > Rebuild Project
```

## 🔧 Configurar Firebase Console

1. Ve a [Firebase Console](https://console.firebase.google.com/)
2. Crea/selecciona tu proyecto
3. Configura Authentication:
   - Habilita Email/Password
4. Configura Firestore:
   - Crea base de datos
   - Sube las reglas desde `firestore.rules`
5. Configura Storage:
   - Habilita Storage
   - Sube las reglas desde `storage.rules`

## 📤 Primer Commit a GitHub

### Opción A: Nuevo Repositorio

```bash
# Inicializar git (si aún no está)
git init

# Agregar todos los archivos
git add .

# Primer commit
git commit -m "chore: configuración inicial del proyecto"

# Crear repo en GitHub y luego:
git remote add origin https://github.com/TU-USUARIO/patinetatrack.git
git branch -M main
git push -u origin main
```

### Opción B: Fork/Clone Existente

```bash
# Agregar cambios
git add .

# Commit
git commit -m "chore: limpieza del código y configuración"

# Push
git push origin main
```

## ✅ Verificación Final

Antes de hacer push, verifica que estos archivos **NO** estén siendo rastreados:

```bash
git status
```

**NO deben aparecer:**
- `app/google-services.json` ✅
- `cloudinary.properties` ✅
- `local.properties` ✅
- `*.keystore` ✅
- `/build/` ✅

Si aparecen, verifica tu `.gitignore`

## 🎯 Siguientes Pasos

1. **Personalizar README.md:**
   - Cambia el nombre de usuario en los enlaces
   - Añade screenshots
   - Actualiza la sección de autor

2. **Configurar GitHub Repo:**
   - Añadir descripción
   - Añadir topics: `kotlin`, `android`, `jetpack-compose`, `firebase`
   - Habilitar Issues y Discussions

3. **Opcional - Configurar CI/CD:**
   - GitHub Actions para builds automáticos
   - Code quality checks

## 🔐 Recordatorios de Seguridad

- ✅ `google-services.json` está en `.gitignore`
- ✅ `cloudinary.properties` está en `.gitignore`
- ✅ Credenciales hardcodeadas fueron removidas
- ✅ Archivos de ejemplo creados para referencia

## 📞 ¿Problemas?

Si encuentras problemas:

1. Verifica que todas las credenciales estén configuradas
2. Revisa la consola de Firebase
3. Comprueba los logs de Android Studio
4. Lee el archivo `CLEANUP_SUMMARY.md` para ver todos los cambios

---

**¡Tu proyecto está listo para GitHub!** 🎉

