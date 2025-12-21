[4.7.0] — 2024-12-25
🌦️ Mejoras en la detección de lluvia y calzada mojada
Corrección de falsos positivos

Verificación de cielo despejado
Las condiciones probabilísticas (humedad + probabilidad de lluvia) solo se evalúan cuando el cielo no está despejado.

Eliminación de detecciones erróneas
Ya no se detecta lluvia en días soleados con alta humedad (caso habitual en zonas costeras como Barcelona).

Lógica de detección refinada

Precipitación medida > 0.1 mm → detección directa.

Humedad ≥ 85% + probabilidad ≥ 30% → solo con cielo nublado.

Humedad ≥ 88% + viento ≤ 10 km/h → solo con cielo nublado.

Mejora de experiencia de usuario

Precipitación con cielo despejado
Si hay precipitación medida pero el cielo está despejado, se muestra “Calzada mojada” (amarillo) en lugar de “Lluvia detectada” (azul/rosa).

Consistencia entre pantallas
La lógica de detección es idéntica tanto en la pantalla de precarga como en los badges del diálogo de detalles.

🗺️ Corrección del problema de carga del mapa
Solución definitiva al “cold start”

Gestión correcta del ciclo de vida del MapView (onStart() / onStop()).

Activación de aceleración de hardware para mejorar rendimiento y evitar parpadeos.

Eliminación de recargas innecesarias causadas por un bucle infinito.

Carga inicial fiable desde la primera apertura de la app, sin necesidad de abrir pantalla completa.

🎬 Modo inmersivo para grabación de vídeo
Experiencia de pantalla completa

Ocultación completa de la barra de estado y navegación durante la grabación.

Detección automática de ventana, incluso dentro de Dialog.

Ocultación de iconos del sistema (hora, batería, notificaciones).

Restauración automática del sistema UI al cerrar el diálogo.

Eliminación de paddings de barras del sistema para un layout preciso.

Mejoras visuales

Unificación del estilo del botón de velocidad (1x / 2x) con el botón de descarga.

Mejora de contraste y legibilidad del control de velocidad.

🚀 Migración a KSP (Kotlin Symbol Processing)
Rendimiento y mantenimiento

Migración completa de KAPT → KSP.

Reducción significativa de tiempos de compilación.

Eliminación del warning de deprecación de KAPT.

Configuración modernizada con Version Catalogs.

🌦️ Mejoras en el diálogo de información meteorológica
Interfaz más clara y profesional

Precipitación integrada en la lista principal de parámetros.

Lógica de visualización inteligente:

Si ha llovido → Precipitación: X mm.

Si no ha llovido → Prob. de lluvia: X%.

Nunca se muestran ambos valores simultáneamente.

Eliminación de textos redundantes.

Badge visual limpio: “Ruta realizada con lluvia”.

🌦️ Sistema de preavisos meteorológicos mejorado
Centro de alertas unificado

Tarjeta inteligente única (PreRideSmartWarning) para todos los preavisos.

Priorización clara:

Lluvia / calzada mojada → prioridad alta.

Condiciones extremas → complementarias.

Colores diferenciados por gravedad (azul/rosa, naranja, rojo).

Mensajes dinámicos según la condición detectada:

Viento fuerte, ráfagas intensas, calor extremo, frío bajo cero, UV muy alto o tormenta.

Preavisos visibles solo antes de iniciar la ruta (pantalla de precarga GPS).

🏆 Badges de resumen de ruta
Mejoras de visualización

Soporte para múltiples badges simultáneos.

Orden coherente con los preavisos.

Paleta de colores unificada en toda la app.

Destacado de condiciones extremas

Parámetros críticos resaltados en los detalles:

Texto en negrita.

Color rojo.

Indicador visual ⚠️ cuando corresponde.

Sin duplicación de información entre badges y detalles.

🔧 Correcciones y mejoras técnicas
Limpieza y consistencia

Eliminación de imports no utilizados.

Corrección de verificaciones redundantes en repositorios.

Simplificación de condiciones siempre verdaderas.

Supresión controlada de deprecaciones necesarias.

Manejo seguro de MediaPlayer (eliminado uso de !!).

Formato de temperatura

Función unificada formatTemperature() para evitar mostrar -0°C.

Aplicación global del formato correcto en toda la app.

Umbrales compartidos

Funciones comunes checkActiveRain() y checkWetRoadConditions().

Criterios idénticos entre preavisos y badges.

Versiones anteriores

Consulta el historial completo en los releases de GitHub:
https://github.com/shurdani/Patinetatrack/releases