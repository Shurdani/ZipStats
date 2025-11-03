# 🔒 Recomendaciones de Seguridad para ZipStats

Este documento contiene recomendaciones de reglas de seguridad para el repositorio ZipStats en GitHub.

## 🛡️ 1. Reglas de Protección de Ramas

### Para la rama `main` (Producción)

**Configuración recomendada:**

✅ **Require a pull request before merging**
- Require approvals: **1** (o más según tu equipo)
- Dismiss stale pull request approvals when new commits are pushed: **Activado**
- Require review from Code Owners: **Opcional** (si tienes CODEOWNERS)

✅ **Require status checks to pass before merging**
- Require branches to be up to date before merging: **Activado**
- Status checks requeridos:
  - `CodeQL / Analyze (java-kotlin)`
  - Build checks (si los tienes)

✅ **Require conversation resolution before merging**: **Activado**

✅ **Require signed commits**: **Opcional** (recomendado para proyectos públicos)

✅ **Require linear history**: **Opcional** (si prefieres evitar merge commits)

✅ **Do not allow bypassing the above settings**: **Activado**
  - Solo admins pueden bypass (o desactivar si necesitas flexibilidad)

✅ **Allow force pushes**: **Desactivado**

✅ **Allow deletions**: **Desactivado**

### Para ramas de release (ej: `release/*`)

Configuración similar a `main` pero más permisiva:
- Require approvals: **1**
- Allow force pushes: **Desactivado** (mantener histórico)
- Allow deletions: **Desactivado**

### Para ramas de desarrollo (ej: `develop`, `feature/*`)

Configuración más flexible:
- Require pull request: **Opcional**
- Allow force pushes: **Solo para el dueño** (si aplica)
- Allow deletions: **Solo para el dueño**

---

## 🔐 2. Secret Scanning y Protección

### Secret Scanning (Automático)

GitHub ya tiene esto activado por defecto, pero asegúrate de que esté habilitado:

✅ **Settings → Security → Secret scanning**
- ✅ Alertas automáticas para secretos expuestos
- ✅ Alertas para secretos en PRs

### Secrets que NO deben estar en el código

Asegúrate de que estos estén solo en GitHub Secrets:

- ❌ `local.properties` (debe estar en `.gitignore`)
- ❌ API Keys de Google Maps
- ❌ Firebase credentials
- ❌ Cloudinary keys
- ❌ OpenWeather API key
- ❌ Signing keys (keystore passwords)
- ❌ Cualquier token de acceso

### Configurar Secrets en GitHub

**Settings → Secrets and variables → Actions → New repository secret**

Secrets recomendados si necesitas firmar releases:
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `KEYSTORE_BASE64` (keystore codificado en base64)

---

## 🔍 3. Dependency Scanning (Dependabot)

Ya tienes Dependabot configurado, pero puedes mejorarlo:

### Configuración recomendada (`.github/dependabot.yml`)

```yaml
version: 2
updates:
  # Gradle dependencies
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    open-pull-requests-limit: 10
    reviewers:
      - "Shurdani"
    labels:
      - "dependencies"
      - "automated"
    
  # GitHub Actions
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "monthly"
    reviewers:
      - "Shurdani"
    labels:
      - "github-actions"
      - "automated"
```

### Configuración adicional en GitHub

✅ **Settings → Security → Dependabot alerts**: **Activado**
✅ **Settings → Security → Dependabot security updates**: **Activado**
✅ **Settings → Security → Dependabot version updates**: **Activado**

---

## 📊 4. Code Scanning (CodeQL)

Ya tienes CodeQL configurado, verifica:

✅ **Settings → Security → Code scanning alerts**: **Activado**
✅ Verificar que el workflow se ejecute en:
  - Push a `main`
  - Pull requests a `main`
  - Schedule semanal

### Mejorar la configuración

Puedes agregar más queries de seguridad:
```yaml
queries: security-extended,security-and-quality
```

---

## 🔑 5. Permisos de Workflows (OAuth App)

### Configuración recomendada

✅ **Settings → Actions → General → Workflow permissions**
- **Read and write permissions**: Para workflows que necesitan crear releases/PRs
- **Read repository contents and packages permissions**: Para la mayoría
- **Read repository contents and packages permissions** (recomendado por seguridad)

Para workflows específicos, usa `permissions:` en cada workflow:
```yaml
permissions:
  contents: write      # Para crear releases
  pull-requests: write # Para crear PRs
  actions: read        # Para leer otros workflows
```

---

## 🚫 6. Restricciones de Acceso

### Branch Protection - Restricciones adicionales

Para `main`:
✅ **Restrict who can push to matching branches**
  - Solo admins o un equipo específico

✅ **Restrict who can force push to matching branches**
  - Nadie (o solo admins)

### Tags Protection (Nuevo Sistema: Rulesets)

✅ **Settings → Rulesets → New tag ruleset**

**Configuración recomendada:**

1. **Ruleset Name**: `Tag Protection v*`

2. **Enforcement status**: `Active` (o `Active, but allow bypass` si quieres bypass list)

3. **Bypass list** (opcional):
   - Repository admin Role: Always allow
   - (Opcional: Dependabot si es necesario)

4. **Target Tags** → Click "Tag targeting criteria":
   - Selecciona "Name pattern"
   - Pattern: `v*` (para proteger todos los tags de versión como v3.0, v3.1, etc.)

5. **Tag Rules**:
   - ✅ **Restrict creations**: Solo admins pueden crear tags
   - ✅ **Restrict deletions**: Solo admins pueden eliminar tags
   - ✅ **Block force pushes**: Activar (si aplica)
   - ⚪ Restrict updates: Opcional
   - ⚪ Require signed commits: Opcional

6. Click **Create**

---

## 📦 7. Protección de Releases

### Configuración recomendada

✅ **Settings → Code and automation → Tags**
- Create tag protection rule para `v*`
  - Restrict who can create tags: **Solo admins**

✅ **Settings → Actions → General → Artifact and log retention**
- Retention period: **30 días** (ya configurado en workflow)
- Remove logs older than: **90 días**

---

## 🛠️ 8. Seguridad de GitHub Actions

### Best Practices para workflows

✅ **Usar versiones específicas de acciones**:
```yaml
uses: actions/checkout@v4  # ✅ Versión específica
# NO: uses: actions/checkout@main  # ❌
```

✅ **Minimizar permisos**:
```yaml
permissions:
  contents: read  # Mínimo necesario
```

✅ **No exponer secrets en logs**:
- GitHub ya lo hace automáticamente
- Pero verifica que no uses `echo $SECRET` en scripts

✅ **Validar inputs de workflows**:
```yaml
inputs:
  version_type:
    type: choice  # ✅ Restringe opciones
    options: [patch, minor, major]
```

---

## 🔒 9. Security Advisories

✅ **Settings → Security → Security advisories**
- **Activado** para crear advisories privadas cuando encuentres vulnerabilidades
- Permite coordinar fixes antes de hacerlas públicas

---

## 📝 10. Archivo CODEOWNERS (Opcional pero recomendado)

Crea `.github/CODEOWNERS`:

```
# Propietarios globales
* @Shurdani

# Workflows y automatización
/.github/ @Shurdani

# Configuración de seguridad
/.github/CODEOWNERS @Shurdani
/CHANGELOG.md @Shurdani

# Configuración de build
/app/build.gradle @Shurdani
/build.gradle @Shurdani
```

Esto requiere revisión del propietario antes de mergear cambios críticos.

---

## ✅ Checklist de Implementación

### Prioridad Alta 🔴

- [ ] Activar protección de rama `main` (requiere PR + approvals)
- [ ] Verificar que `local.properties` esté en `.gitignore`
- [ ] Configurar Dependabot alerts y security updates
- [ ] Verificar CodeQL está activo y funcionando
- [ ] Configurar permisos mínimos en workflows

### Prioridad Media 🟡

- [ ] Configurar protección de tags `v*`
- [ ] Agregar más queries de seguridad a CodeQL
- [ ] Configurar CODEOWNERS file
- [ ] Mejorar configuración de Dependabot

### Prioridad Baja 🟢

- [ ] Require signed commits (opcional)
- [ ] Require linear history
- [ ] Configurar Security Advisories

---

## 🔗 Enlaces Útiles

- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [Branch Protection Rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [Dependabot Configuration](https://docs.github.com/en/code-security/dependabot)
- [CodeQL Documentation](https://codeql.github.com/docs/)

