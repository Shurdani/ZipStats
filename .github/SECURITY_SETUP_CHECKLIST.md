# ✅ Checklist de Configuración de Seguridad

Este checklist te guía paso a paso para configurar todas las reglas de seguridad recomendadas.

## 🎯 Configuración en GitHub (Settings)

### 1. Branch Protection Rules

**Ruta:** `Settings → Branches → Rulesets` (o `Settings → Branches → Add rule` en versiones antiguas)

**Rama:** `main`

✅ Configuraciones activadas:

1. **Enforcement status**: ✅ **Active** (con bypass list configurada)
   - [x] Repository admin Role: Always allow
   - [x] Dependabot App: Always allow  
   - [x] Cursor App: Always allow

2. **Require a pull request before merging**
   - [x] Number of approvals required: **1** ✅
   - [x] Dismiss stale pull request approvals when new commits are pushed: ✅
   - [ ] Require review from Code Owners: ⚪ (opcional)

3. **Require status checks to pass before merging**
   - [x] Require branches to be up to date before merging: ✅
   - [x] Status checks requeridos:
     - [x] `CodeQL / Analyze (java-kotlin)` ✅
     - [ ] Cualquier otro check de build que tengas

4. **Require conversation resolution before merging**: ✅

5. **Block force pushes**: ✅

6. **Restrict deletions**: ✅

---

### 2. Tag Protection (Rulesets)

**Ruta:** `Settings → Rulesets → New tag ruleset`

✅ Configuración:

1. **Ruleset Name**: 
   - [ ] `Tag Protection v*` (o el nombre que prefieras)

2. **Enforcement status**:
   - [ ] Seleccionar: `Active` (o `Active, but allow bypass`)

3. **Bypass list** (opcional):
   - [ ] Repository admin Role: Always allow
   - [ ] (Opcional: Dependabot si es necesario)

4. **Target Tags**:
   - [ ] Click "Tag targeting criteria"
   - [ ] Seleccionar "Name pattern"
   - [ ] Pattern: `v*` (proteger tags como v3.0, v3.1, etc.)
   - [ ] Click "Add target"

5. **Tag Rules** - Activar:
   - [x] ✅ **Restrict creations**: Solo admins pueden crear
   - [ ] ⚪ Restrict updates: Opcional
   - [x] ✅ **Restrict deletions**: Solo admins pueden eliminar
   - [x] ✅ **Block force pushes**: Activar
   - [ ] ⚪ Require signed commits: Opcional

6. [ ] Click **Create**

---

### 3. Security Settings

**Ruta:** `Settings → Security`

#### Secret Scanning
- [ ] Secret scanning alerts: ✅ **Activado**
- [ ] Push protection: ✅ **Activado** (recomendado)

#### Dependabot
- [ ] Dependabot alerts: ✅ **Activado**
- [ ] Dependabot security updates: ✅ **Activado**
- [ ] Dependabot version updates: ✅ **Activado**

#### Code Scanning
- [ ] Code scanning alerts: ✅ **Activado**
- [ ] CodeQL analysis: ✅ **Verificado** (debe ejecutarse automáticamente)

---

### 4. Actions Settings

**Ruta:** `Settings → Actions → General`

#### Workflow permissions
- [ ] Read and write permissions: ✅ (necesario para releases)
   - O mejor: **Read repository contents and packages permissions** (más seguro)
   - Los workflows ya tienen `permissions:` explícitos

#### Artifact and log retention
- [ ] Retention period: **30 días** (o el que prefieras)
- [ ] Remove logs older than: **90 días**

---

### 5. Code Security (Verificaciones)

**Ruta:** `Security` tab en el repositorio

Verifica que estén activos:
- [ ] Dependabot alerts: ✅
- [ ] Code scanning alerts: ✅
- [ ] Secret scanning: ✅

---

## 📝 Archivos a Commitear

Asegúrate de que estos archivos estén en el repositorio:

- [x] `.github/CODEOWNERS` ✅ (ya creado)
- [x] `.github/dependabot.yml` ✅ (mejorado)
- [x] `.github/workflows/release.yml` ✅ (con permisos)
- [x] `.github/workflows/version-bump.yml` ✅ (con permisos)
- [x] `.gitignore` ✅ (verificado que incluye `local.properties`)

---

## 🔍 Verificaciones Finales

### Verificar que no hay secretos expuestos

**Ruta:** `Security → Secret scanning → View all secret scanning alerts`

- [ ] No hay alertas de secretos expuestos
- [ ] Si hay alguna, revísala y rota el secreto si es necesario

### Verificar dependencias vulnerables

**Ruta:** `Security → Dependabot alerts`

- [ ] Revisar alertas activas
- [ ] Actualizar dependencias vulnerables

### Verificar CodeQL

**Ruta:** `Security → Code scanning alerts`

- [ ] Verificar que el workflow se ejecute correctamente
- [ ] Revisar alertas de código si las hay

---

## 🚨 Importante: Antes de Activar Protección de Rama `main`

Si activas la protección de rama `main` ahora, necesitarás:

1. **Mergear el PR actual** de automatización ANTES de activar la protección
2. O asegurarte de que tienes permisos de admin para bypass temporalmente

**Recomendación:** Activa la protección DESPUÉS de mergear el PR de automatización.

---

## 📊 Estado Actual

Marca las que ya están configuradas:

### Configuración de GitHub (Settings)
- [x] Branch protection para `main` ✅ **ACTIVADA**
- [x] Tag protection para `v*` ✅ **ACTIVADA**
- [ ] Secret scanning activado
- [ ] Dependabot alerts activado
- [ ] Dependabot security updates activado
- [ ] Code scanning activado
- [ ] Workflow permissions configurado
- [ ] Artifact retention configurado

### Archivos en Repositorio
- [x] `.github/CODEOWNERS`
- [x] `.github/dependabot.yml` (mejorado)
- [x] Workflows con permisos mínimos
- [x] `.gitignore` correcto

---

## 🎯 Prioridades

### 🔴 Prioridad Alta (Hacer AHORA)
1. Verificar `.gitignore` incluye `local.properties` ✅
2. Verificar Dependabot está activado
3. Verificar CodeQL está funcionando
4. **Después de mergear PR de automatización:** Activar branch protection

### 🟡 Prioridad Media (Esta semana)
1. Activar tag protection
2. Configurar CODEOWNERS (ya creado)
3. Mejorar queries de CodeQL si es necesario

### 🟢 Prioridad Baja (Cuando tengas tiempo)
1. Require signed commits (opcional)
2. Require linear history (opcional)

---

## 💡 Tips

- **Empieza simple**: Activa primero las protecciones básicas y luego ve agregando más
- **Prueba con una rama de prueba**: Antes de activar protección en `main`, prueba con otra rama
- **Mantén flexibilidad**: Si eres el único mantenedor, algunas reglas pueden ser más flexibles

---

## 📚 Recursos

- [Documentación de Branch Protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [Guía de Seguridad de GitHub](https://docs.github.com/en/code-security)
- Ver: `.github/SECURITY_RECOMMENDATIONS.md` para detalles completos

