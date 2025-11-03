# 🏷️ Guía: Protección de Tags (Tag Protection)

## 📋 Configuración paso a paso

### 1. Acceder a Rulesets

**Ruta:** `Settings → Rulesets → New tag ruleset`

O desde: `Settings → Code and automation → Rules → Rulesets → New ruleset → Tag ruleset`

---

### 2. Configurar el Ruleset

#### Nombre del Ruleset
```
Ruleset Name: Tag Protection v*
```
(Cualquier nombre descriptivo funciona)

#### Estado de Enforcement
- Selecciona: **Active** (o `Active, but allow bypass` si necesitas bypass)

#### Bypass List (Opcional)
Si quieres que los admins puedan hacer bypass:
- Click **"+ Add bypass"**
- Selecciona: **Repository admin Role**
- Permiso: **Always allow**

---

### 3. Configurar Target Tags (OBLIGATORIO)

Esta es la parte más importante:

1. En la sección **"Target tags"**, click en **"Tag targeting criteria"** (o **"Add target"**)

2. Selecciona **"Name pattern"**

3. En el campo de pattern, escribe:
   ```
   v*
   ```
   Esto protegerá todos los tags que empiecen con "v" (ej: v3.0, v3.1, v3.2.5)

4. Click en **"Add target"** o el botón de confirmar

5. Verás que ahora aparece: **"Applies to 1 target: v*"**

---

### 4. Configurar Reglas (Tag Rules)

Activa las siguientes reglas:

#### ✅ Obligatorio:
- [x] **Restrict creations**: Solo usuarios con bypass pueden crear tags
- [x] **Restrict deletions**: Solo usuarios con bypass pueden eliminar tags
- [x] **Block force pushes**: Prevenir force pushes a tags

#### ⚪ Opcional:
- [ ] **Restrict updates**: Opcional (los tags normalmente no se actualizan)
- [ ] **Require signed commits**: Opcional (más estricto)

---

### 5. Crear el Ruleset

Click en el botón verde **"Create"** al final de la página.

---

## ✅ Resultado Esperado

Después de crear el ruleset:

- ✅ Solo admins podrán crear tags `v*`
- ✅ Solo admins podrán eliminar tags `v*`
- ✅ Force pushes a tags bloqueados
- ✅ Otros usuarios no podrán crear/modificar tags de versión

---

## 🔍 Verificar que Funciona

1. Intenta crear un tag desde otro usuario (o sin permisos de admin):
   ```bash
   git tag v3.1.1
   git push origin v3.1.1
   ```
   Debería fallar si no eres admin.

2. Como admin, deberías poder crear tags normalmente.

---

## 💡 Notas Importantes

- El pattern `v*` protege todos los tags que empiecen con "v"
- Si usas otro formato de tags (ej: `release-*`), ajusta el pattern
- La bypass list permite que los admins siempre puedan crear tags
- Dependabot y otros bots también pueden necesitar bypass si crean tags automáticamente

---

## 🎯 Configuración Recomendada Final

```
Ruleset Name: Tag Protection v*
Enforcement: Active (con bypass para admins)
Target: v* (name pattern)
Rules:
  ✅ Restrict creations
  ✅ Restrict deletions  
  ✅ Block force pushes
```

