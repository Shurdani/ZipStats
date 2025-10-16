# Guía de Contribución

¡Gracias por tu interés en contribuir a ZipStats! 🎉

## Cómo Contribuir

### Reportar Bugs

Si encuentras un bug, por favor abre un issue incluyendo:

- **Descripción clara** del problema
- **Pasos para reproducir** el error
- **Comportamiento esperado** vs comportamiento actual
- **Screenshots** si es aplicable
- **Versión** de Android y dispositivo

### Sugerir Nuevas Características

Para sugerir nuevas características:

1. Abre un issue con la etiqueta "enhancement"
2. Describe la característica y por qué sería útil
3. Si es posible, incluye mockups o ejemplos

### Pull Requests

1. **Fork** el repositorio
2. **Crea una rama** desde `main`:
   ```bash
   git checkout -b feature/nombre-caracteristica
   ```
3. **Realiza tus cambios** siguiendo las convenciones del proyecto
4. **Escribe tests** si es necesario
5. **Commit** tus cambios:
   ```bash
   git commit -m "feat: descripción breve del cambio"
   ```
6. **Push** a tu fork:
   ```bash
   git push origin feature/nombre-caracteristica
   ```
7. **Abre un Pull Request** hacia `main`

## Estándares de Código

### Kotlin

- Sigue las [convenciones de Kotlin](https://kotlinlang.org/docs/coding-conventions.html)
- Usa nombres descriptivos para variables y funciones
- Documenta funciones públicas complejas
- Mantén funciones pequeñas y enfocadas

### Compose

- Usa `@Composable` solo para funciones UI
- Extrae componentes reutilizables
- Usa `remember` y `LaunchedEffect` apropiadamente
- Sigue Material Design 3 guidelines

### Arquitectura

- **MVVM**: Usa ViewModels para lógica de UI
- **Repository Pattern**: Separa lógica de datos
- **Dependency Injection**: Usa Hilt para DI
- **Clean Code**: Mantén separación de responsabilidades

## Commits Convencionales

Usa el formato [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` Nueva característica
- `fix:` Corrección de bug
- `docs:` Cambios en documentación
- `style:` Cambios de formato (no afectan código)
- `refactor:` Refactorización de código
- `test:` Añadir o modificar tests
- `chore:` Tareas de mantenimiento

Ejemplos:
```bash
git commit -m "feat: añadir exportación a PDF"
git commit -m "fix: corregir crash al guardar patinete"
git commit -m "docs: actualizar README con instrucciones"
```

## Tests

- Escribe tests para nueva funcionalidad
- Asegúrate de que todos los tests pasen antes de PR
- Mantén cobertura de tests alta

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Configuración del Proyecto

Consulta el [README.md](README.md) para:
- Requisitos del sistema
- Configuración de Firebase
- Configuración de Cloudinary
- Compilación del proyecto

## Code Review

Tu PR será revisado por los mantenedores. Ten en cuenta:

- Responde a los comentarios de manera constructiva
- Realiza los cambios solicitados
- Mantén la conversación profesional y respetuosa

## Licencia

Al contribuir, aceptas que tu código se publique bajo la [Licencia MIT](LICENSE).

## ¿Necesitas Ayuda?

- Revisa los [issues abiertos](../../issues)
- Pregunta en el issue relacionado
- Contacta a los mantenedores

¡Gracias por contribuir! 🚀

