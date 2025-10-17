# Cambio de Licencia - ZipStats

**Fecha**: 17 de Octubre de 2025

## Resumen

Se ha realizado el cambio de licencia del proyecto de **MIT** a **GPL v3**, con exclusiones específicas para assets y materiales de marketing, siguiendo el modelo de proyectos como SD Maid SE.

## Cambios Realizados

### 1. ✅ Licencia Principal (GPL v3)
- **Archivo**: `LICENSE`
- Reemplazada licencia MIT por GPL v3 completa
- Añadidas exclusiones al final del archivo:
  - Iconos, logos, mascotas y materiales de marketing
  - Animaciones y videos
  - Documentación
  - Capturas de pantalla de Google Play
  - Textos y descripciones de Google Play

### 2. ✅ Actualización del README
- **Archivo**: `README.md`
- Añadido espacio para banner (`.github/assets/banner.png`)
- Añadida sección de capturas de pantalla con 4 imágenes
- Actualizada sección de Licencia con:
  - Referencia a GPL v3
  - Lista de exclusiones
  - Enlaces relevantes

### 3. ✅ Estructura de Assets
Creada estructura de carpetas:
```
.github/
├── assets/
│   ├── README.md (documentación de assets)
│   ├── banner.png (pendiente de añadir)
│   └── screenshots/
│       ├── README.md (guía de capturas)
│       ├── screenshot1.png (pendiente)
│       ├── screenshot2.png (pendiente)
│       ├── screenshot3.png (pendiente)
│       └── screenshot4.png (pendiente)
```

### 4. ✅ Documentación de Licenciamiento
- **Archivo**: `.github/LICENSING.md`
- Explicación detallada de qué cubre GPL v3
- Qué assets están excluidos
- Razones de la distinción
- Guía para contribuidores

## Próximos Pasos

### 📸 Para completar la configuración visual:

1. **Banner** (`.github/assets/banner.png`):
   - Dimensiones recomendadas: 1280x640px (ratio 2:1)
   - Debe incluir: Logo/nombre del proyecto y tagline
   - Formato: PNG o JPG optimizado

2. **Capturas de Pantalla** (`.github/assets/screenshots/`):
   - Añadir 4 capturas principales:
     * `screenshot1.png` - Pantalla principal
     * `screenshot2.png` - Funcionalidad destacada 1
     * `screenshot3.png` - Funcionalidad destacada 2
     * `screenshot4.png` - Funcionalidad destacada 3
   - Dimensiones: 1080x1920px para teléfonos
   - Optimizar para web (< 500KB cada una)

### 📋 Checklist Final

- [x] Cambiar licencia a GPL v3
- [x] Añadir exclusiones a la licencia
- [x] Actualizar README con nueva licencia
- [x] Crear estructura de carpetas para assets
- [x] Añadir documentación de licenciamiento
- [ ] **Añadir banner personalizado**
- [ ] **Añadir capturas de pantalla**
- [ ] Hacer commit de cambios
- [ ] Push a GitHub

## Notas Importantes

⚠️ **Impacto Legal**: Este cambio de MIT a GPL v3 hace que cualquier trabajo derivado también deba ser GPL v3. Esto protege más el software libre pero es más restrictivo que MIT.

✅ **Compatibilidad**: GPL v3 es compatible con muchas otras licencias de código abierto.

🎨 **Assets Protegidos**: Los assets visuales y de marketing permanecen bajo copyright completo, permitiendo mantener la identidad única de ZipStats.

## Comandos Git Sugeridos

```bash
# Revisar cambios
git status

# Añadir archivos
git add LICENSE README.md .github/

# Commit
git commit -m "feat: cambio de licencia de MIT a GPL v3 con exclusiones para assets"

# Push
git push origin main
```

---

**Creado**: 17/10/2025  
**Autor**: @Shurdani

