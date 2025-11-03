# 🤖 Automatización de Releases

Este documento explica cómo usar las herramientas automatizadas para crear releases de ZipStats.

## 🚀 Proceso Automatizado de Release

### Método 1: Script Local (Más Rápido)

1. **Preparar la versión**:
   ```bash
   chmod +x .github/scripts/prepare-release.sh
   ./.github/scripts/prepare-release.sh [major|minor|patch]
   ```
   
   Ejemplo:
   ```bash
   ./.github/scripts/prepare-release.sh minor  # 3.0 → 3.1
   ```

2. **Revisar y commitear**:
   ```bash
   git diff
   git commit -am "chore: bump version to X.Y.Z"
   git push
   ```

3. **Crear tag y publicar**:
   ```bash
   git tag -a v3.1 -m "Release 3.1"
   git push origin v3.1
   ```

4. **El workflow se ejecutará automáticamente**:
   - ✅ Construirá el APK
   - ✅ Generará el changelog desde CHANGELOG.md
   - ✅ Creará el release en GitHub
   - ✅ Subirá el APK como archivo adjunto

### Método 2: GitHub Actions (Sin tocar código local)

1. Ve a **Actions** → **🤖 Auto Version Bump Helper**
2. Haz clic en **Run workflow**
3. Selecciona el tipo de versión (patch/minor/major)
4. El workflow creará un PR automáticamente
5. Revisa y mergea el PR
6. Después del merge, crea el tag:
   ```bash
   git tag -a v3.1 -m "Release 3.1"
   git push origin v3.1
   ```

## 📝 Mantener el CHANGELOG.md

Para que el proceso funcione perfectamente, mantén actualizado el `CHANGELOG.md`:

1. **Antes de crear un release**, actualiza `CHANGELOG.md` con los cambios:
   ```markdown
   ## [3.1.0] - 2024-12-XX
   
   ### ✨ Nuevas Características
   - Nueva funcionalidad X
   - Mejora Y
   
   ### 🐛 Correcciones
   - Fix para bug Z
   ```

2. **El workflow leerá automáticamente** esa sección para el release.

3. Si no existe la sección, el workflow generará el changelog desde los PRs mergeados.

## 🏷️ Formato de Tags

Los tags deben seguir el formato semántico:
- `v3.0.0` - Versión mayor
- `v3.1.0` - Versión menor  
- `v3.0.1` - Versión patch

El workflow extraerá automáticamente la versión del tag.

## 📋 Checklist antes de un Release

- [ ] Actualizar `CHANGELOG.md` con los cambios
- [ ] Actualizar `versionName` y `versionCode` en `app/build.gradle`
- [ ] Actualizar badge de versión en `README.md`
- [ ] Probar que la app compila: `./gradlew assembleRelease`
- [ ] Crear el tag: `git tag -a vX.Y.Z -m "Release X.Y.Z"`
- [ ] Push del tag: `git push origin vX.Y.Z`

## 🔍 Verificar el Release

Después de crear el tag, puedes verificar el progreso:

1. Ve a **Actions** → **📦 Release ZipStats**
2. Revisa los logs del workflow
3. Verifica que el release se haya creado en **Releases**

## 💡 Tips

- **Usa labels en PRs**: El workflow generará mejor el changelog si tus PRs tienen labels como `feature`, `bug`, `enhancement`, etc.
- **Commits descriptivos**: Los mensajes de commit ayudan a generar mejores changelogs
- **CHANGELOG.md es la fuente de verdad**: Si existe, el workflow lo usará en lugar de generar desde PRs

