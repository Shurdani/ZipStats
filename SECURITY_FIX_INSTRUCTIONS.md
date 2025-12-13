# 🔒 Instrucciones para Corregir la Fuga de Seguridad de Mapbox

## ⚠️ ACCIÓN INMEDIATA REQUERIDA

Se detectó que los tokens de Mapbox estaban expuestos en el repositorio. Se han realizado cambios para corregir esto, pero **DEBES** completar los siguientes pasos:

## 📋 Pasos a Seguir

### 1. **REVOCAR LOS TOKENS EXPUESTOS** (CRÍTICO)

1. Ve a https://account.mapbox.com/access-tokens/
2. **REVOCA** los siguientes tokens que fueron expuestos:
   - Token público (pk.eyJ...): Usado en la app
   - Token de descargas (sk.eyJ...): Usado para descargar el SDK

### 2. **Generar Nuevos Tokens**

1. Crea un **nuevo token público** con los permisos necesarios para la app
2. Crea un **nuevo token de descargas** con el scope "Downloads:Read"

### 3. **Configurar los Tokens Localmente**

Edita tu archivo `local.properties` (que NO está en Git) y añade:

```properties
# Mapbox Access Token (para uso en la app)
mapbox.access.token=TU_NUEVO_TOKEN_PUBLICO_AQUI

# Mapbox Downloads Token (para descargar el SDK)
MAPBOX_DOWNLOADS_TOKEN=TU_NUEVO_TOKEN_DESCARGAS_AQUI
```

### 4. **Verificar que los Archivos Están en .gitignore**

Asegúrate de que estos archivos NO se suban a Git:
- ✅ `local.properties` (ya está en .gitignore)
- ✅ `gradle.properties` (ahora está en .gitignore)

### 5. **Limpiar el Historial de Git (Opcional pero Recomendado)**

Si quieres eliminar completamente los tokens del historial de Git:

```bash
# ADVERTENCIA: Esto reescribe el historial. Solo hazlo si es necesario.
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch app/src/main/res/values/strings.xml gradle.properties" \
  --prune-empty --tag-name-filter cat -- --all
```

O usa BFG Repo-Cleaner (más seguro):
```bash
bfg --replace-text passwords.txt
```

## ✅ Cambios Realizados

1. ✅ Token eliminado de `strings.xml` (reemplazado con placeholder)
2. ✅ Token eliminado de `gradle.properties` (reemplazado con placeholder)
3. ✅ `gradle.properties` añadido a `.gitignore`
4. ✅ Código actualizado para leer tokens desde `local.properties` vía `BuildConfig`
5. ✅ `AndroidManifest.xml` actualizado para usar `manifestPlaceholders`
6. ✅ `local.properties.example` actualizado con instrucciones

## 🔍 Verificación

Después de configurar los nuevos tokens en `local.properties`:

1. Limpia y reconstruye el proyecto
2. Verifica que la app funciona correctamente con Mapbox
3. Verifica que NO hay tokens en ningún archivo que esté en Git

## 📝 Notas Importantes

- **NUNCA** vuelvas a commitear tokens en archivos que estén en Git
- **SIEMPRE** usa `local.properties` para tokens y credenciales
- El archivo `local.properties` está en `.gitignore` y NO se sube a Git
- Si necesitas compartir la configuración, usa `local.properties.example` como plantilla

