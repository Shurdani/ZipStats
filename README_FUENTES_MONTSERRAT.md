# 📝 Instrucciones para Descargar Fuentes Montserrat

## 🔗 Enlaces de Descarga

Para completar la implementación de la fuente Montserrat, necesitas descargar los siguientes archivos TTF desde Google Fonts:

### Fuentes Requeridas:
1. **Montserrat Light (300)**: https://fonts.google.com/specimen/Montserrat?query=montserrat#standard-styles
2. **Montserrat Regular (400)**: https://fonts.google.com/specimen/Montserrat?query=montserrat#standard-styles  
3. **Montserrat Bold (700)**: https://fonts.google.com/specimen/Montserrat?query=montserrat#standard-styles

### 📁 Archivos a Descargar:
- `montserrat_light_ttf.ttf` (peso 300)
- `montserrat_regular_ttf.ttf` (peso 400) 
- `montserrat_bold_ttf.ttf` (peso 700)

### 📂 Ubicación:
Coloca los archivos TTF descargados en: `app/src/main/res/font/`

### ✅ Verificación:
Una vez descargados los archivos TTF, la aplicación usará automáticamente la fuente Montserrat en todos los textos.

## 🎨 Características de Montserrat:
- **Estilo**: Moderna, geométrica y limpia
- **Legibilidad**: Excelente en pantallas móviles
- **Personalidad**: Profesional y contemporánea
- **Compatibilidad**: Total con Android y Material Design

## 🔧 Implementación Técnica:
- **Archivo de configuración**: `app/src/main/java/com/zipstats/app/ui/theme/Type.kt`
- **Familia de fuentes**: `MontserratFontFamily`
- **Aplicación**: Automática en toda la aplicación a través de `AppTypography`
