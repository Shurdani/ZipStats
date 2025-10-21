package com.zipstats.app.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType
import com.zipstats.app.repository.RecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio centralizado para la gestión de logros y notificaciones.
 * Este servicio es responsable de:
 * - Mantener la lista completa de logros
 * - Verificar si se desbloquearon nuevos logros
 * - Mostrar notificaciones de logros
 * - Mantener el estado de logros vistos/notificados
 */
@Singleton
class AchievementsService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordRepository: RecordRepository
) {
    
    private val _newAchievementMessage = MutableStateFlow<String?>(null)
    val newAchievementMessage: StateFlow<String?> = _newAchievementMessage.asStateFlow()
    
    private val _lastUnlockedAchievementId = MutableStateFlow<String?>(null)
    val lastUnlockedAchievementId: StateFlow<String?> = _lastUnlockedAchievementId.asStateFlow()
    
    private val _shouldRefreshAchievements = MutableStateFlow(0)
    val shouldRefreshAchievements: StateFlow<Int> = _shouldRefreshAchievements.asStateFlow()
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)
    }
    
    /**
     * Lista completa de todos los logros disponibles en la aplicación.
     * Esta es la fuente única de verdad para los logros.
     */
    val allAchievements = listOf(
        // ========== NOVATO (Primeros Pasos y Hábito) ========== 
        Achievement(
            id = "novato_1",
            title = "Primeros Pasos",
            description = "Recorre 50 km totales.",
            level = AchievementLevel.NOVATO,
            emoji = "👶🛴",
            hashtag = "#RodandoAndo",
            shareMessage = "¡Lo hice! 50 km recorridos y mi VMP y yo ya somos mejores amigos. ¡Que empiece el juego! #RodandoAndo",
            requiredDistance = 50.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "novato_2",
            title = "El Recolector",
            description = "Registra 10 viajes en la aplicación.",
            level = AchievementLevel.NOVATO,
            emoji = "📋✅",
            hashtag = "#DataGeek",
            shareMessage = "¡No solo ruedo, también registro! Mis primeros 10 viajes ya están en la base de datos. ¡La organización es la clave! #DataGeek",
            requiredTrips = 10,
            requirementType = AchievementRequirementType.TRIPS
        ),
        Achievement(
            id = "novato_3",
            title = "El Viaje Semanal",
            description = "Viajes en 3 semanas diferentes.",
            level = AchievementLevel.NOVATO,
            emoji = "📅🗓️",
            hashtag = "#HábitoVerde",
            shareMessage = "Soy más consistente que la alarma de las 6 AM. ¡Tres semanas seguidas de movilidad sostenible! ¿Quién dijo rutina? #HábitoVerde",
            requiredUniqueWeeks = 3,
            requirementType = AchievementRequirementType.UNIQUE_WEEKS
        ),
        Achievement(
            id = "novato_4",
            title = "El Rodador Diario",
            description = "Registra 7 días de viaje consecutivos.",
            level = AchievementLevel.NOVATO,
            emoji = "⏱️🔥",
            hashtag = "#NoFaltes",
            shareMessage = "El asfalto me llama, y yo respondo. ¡Una semana completa sin fallar! Mi VMP y yo somos imparables. #NoFaltes",
            requiredConsecutiveDays = 7,
            requirementType = AchievementRequirementType.CONSECUTIVE_DAYS
        ),
        Achievement(
            id = "novato_5",
            title = "Conquistador del Asfalto",
            description = "Recorre 250 km totales.",
            level = AchievementLevel.NOVATO,
            emoji = "🗺️📍",
            hashtag = "#AsfaltoConquistado",
            shareMessage = "¡250 km! Ya conozco mi barrio mejor que el repartidor de pizza. Ahora a conquistar la ciudad entera. #AsfaltoConquistado",
            requiredDistance = 250.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "novato_6",
            title = "Medio Millar",
            description = "Recorre 500 km totales.",
            level = AchievementLevel.NOVATO,
            emoji = "🏅🥉",
            hashtag = "#LeyendaLocal",
            shareMessage = "¡Medio millar de kilómetros! ¡Casi un centenario y no me he cansado! A este paso, me hacen un monumento. #LeyendaLocal",
            requiredDistance = 500.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),

        // ========== EXPLORADOR (Consistencia y Mantenimiento) ==========
        Achievement(
            id = "explorador_1",
            title = "El Héroe Local",
            description = "1.000 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🦸‍♂️🌟",
            hashtag = "#MilYContando",
            shareMessage = "¡Héroe de los 1.000! Si esto fuera un juego de rol, acabo de subir de nivel. ¡A por los 2.000! #MilYContando",
            requiredDistance = 1000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "explorador_2",
            title = "Día a Día",
            description = "2.500 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🛣️💨",
            hashtag = "#KilómetrosSinFin",
            shareMessage = "2.500 km, y contando. Parece que mi VMP tiene más vida social que yo. ¡El movimiento constante es la clave! #KilómetrosSinFin",
            requiredDistance = 2500.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "explorador_3",
            title = "El Archivero Experto",
            description = "50 viajes registrados.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🗃️🔎",
            hashtag = "#TrackingPro",
            shareMessage = "50 registros y mi historial es más largo que un libro de fantasía. ¡Me encanta tener mis datos bajo control! #TrackingPro",
            requiredTrips = 50,
            requirementType = AchievementRequirementType.TRIPS
        ),
        Achievement(
            id = "explorador_4",
            title = "El Mecánico Preventivo",
            description = "5 mantenimientos registrados.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🔧⚙️",
            hashtag = "#Mantenimiento",
            shareMessage = "Cinco veces en el 'taller' (mi garaje). Un VMP bien cuidado es un VMP feliz. ¡Siempre revisando los detalles! #Mantenimiento",
            requiredMaintenanceCount = 5,
            requirementType = AchievementRequirementType.MAINTENANCE_COUNT
        ),
        Achievement(
            id = "explorador_5",
            title = "El Ulises Urbano",
            description = "5.000 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🧭🏛️",
            hashtag = "#ViajeroÉpico",
            shareMessage = "¡5.000 km! Mi épica odisea urbana no ha hecho más que empezar. Ulises lo hizo en barco, yo en ruedas. #ViajeroÉpico",
            requiredDistance = 5000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "explorador_6",
            title = "Eco-Amigo",
            description = "50 kg de CO2 ahorrados.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🌳🌍",
            hashtag = "#PlanetaVerde",
            shareMessage = "¡50 kg de CO2 que no fueron al ambiente! Mi contribución al planeta hoy es ir rodando. ¡Soy un Eco-Amigo de verdad! #PlanetaVerde",
            requiredCO2Saved = 50.0,
            requirementType = AchievementRequirementType.CO2_SAVED
        ),
        Achievement(
            id = "explorador_7",
            title = "El Explorador Local",
            description = "Viajes en 10 meses diferentes.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🗓️🔟",
            hashtag = "#Explorador",
            shareMessage = "Llevo 10 meses explorando la ciudad sin importar el clima. ¡Ni la lluvia ni el sol me detienen! #Explorador",
            requiredUniqueMonths = 10,
            requirementType = AchievementRequirementType.UNIQUE_MONTHS
        ),
        Achievement(
            id = "explorador_8",
            title = "El Correcaminos",
            description = "7.500 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🏃💨",
            hashtag = "#Correcaminos",
            shareMessage = "7.500 km, casi un viaje transcontinental. Mis ruedas están echando humo (figurativamente, claro). #Correcaminos",
            requiredDistance = 7500.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "explorador_9",
            title = "Flota en Marcha",
            description = "10.000 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🚀🔟",
            hashtag = "#DobleCifra",
            shareMessage = "¡10.000 km! El primer gran hito de cinco cifras. Gracias a mi VMP por ser mi fiel compañero de fatigas. #DobleCifra",
            requiredDistance = 10000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "explorador_10",
            title = "El Kilometraje Medio",
            description = "12.500 km totales.",
            level = AchievementLevel.EXPLORADOR,
            emoji = "🎚️📈",
            hashtag = "#MidPoint",
            shareMessage = "12.500 km. Justo en el punto medio de la leyenda. ¡La inercia me impulsa hacia la meta final! #MidPoint",
            requiredDistance = 12500.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),

        // ========== MAESTRO (Dominio y Hitos Épicos) ==========
        Achievement(
            id = "maestro_1",
            title = "El Viajero Constante",
            description = "15.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "🗺️🌐",
            hashtag = "#RodandoSiempre",
            shareMessage = "15.000 km. Más vueltas que una noria en hora punta. ¡Mi VMP y yo somos un equipo imparable! #RodandoSiempre",
            requiredDistance = 15000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_2",
            title = "El Guardián Verde",
            description = "100 kg de CO2 ahorrados.",
            level = AchievementLevel.MAESTRO,
            emoji = "🛡️♻️",
            hashtag = "#GuardiánVerde",
            shareMessage = "¡100 kg de CO2 evitados! No soy un superhéroe, solo un ciudadano rodante. ¡Salvando el planeta de a poco! #GuardiánVerde",
            requiredCO2Saved = 100.0,
            requirementType = AchievementRequirementType.CO2_SAVED
        ),
        Achievement(
            id = "maestro_3",
            title = "El Veterano",
            description = "20.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "👑👴",
            hashtag = "#Veterano",
            shareMessage = "¡20.000 km! Ya soy un veterano del asfalto. Tengo más historias de ruedas que un libro de mecánica. ¡A seguir sumando! #Veterano",
            requiredDistance = 20000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_4",
            title = "El Titán del Tracking",
            description = "100 viajes registrados.",
            level = AchievementLevel.MAESTRO,
            emoji = "💯📊",
            hashtag = "#Titán",
            shareMessage = "¡100 registros en la app! Mi historial es tan limpio como mi conciencia ecológica. ¡El orden de los datos es un arte! #Titán",
            requiredTrips = 100,
            requirementType = AchievementRequirementType.TRIPS
        ),
        Achievement(
            id = "maestro_5",
            title = "El Incombustible",
            description = "25.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "🔥🔋",
            hashtag = "#Incombustible",
            shareMessage = "¡25.000 km! Sigo rodando como si fuera el primer día. Mi energía es inagotable, ¡o al menos mi batería lo es! #Incombustible",
            requiredDistance = 25000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_6",
            title = "El Dueño del Camino",
            description = "30.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "🏆🥇",
            hashtag = "#MiTerritorio",
            shareMessage = "¡30.000 km! He conquistado tres veces la distancia del ecuador. Soy el dueño absoluto de mi camino. #MiTerritorio",
            requiredDistance = 30000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_7",
            title = "El Trotamundos",
            description = "40.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "🌎💫",
            hashtag = "#Trotamundos",
            shareMessage = "¡40.000 km! Técnicamente acabo de darle una vuelta entera a la Tierra. ¿Próximo destino? La luna. #Trotamundos",
            requiredDistance = 40000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_8",
            title = "El Viaje Anual",
            description = "Viajes en 12 meses consecutivos.",
            level = AchievementLevel.MAESTRO,
            emoji = "🔄📅",
            hashtag = "#CicloCompleto",
            shareMessage = "¡Un año entero sin parar! El movimiento es vida, y yo no pienso detenerme. Gracias por acompañarme. #CicloCompleto",
            requiredConsecutiveMonths = 12,
            requirementType = AchievementRequirementType.CONSECUTIVE_MONTHS
        ),
        Achievement(
            id = "maestro_9",
            title = "La Leyenda",
            description = "50.000 km totales.",
            level = AchievementLevel.MAESTRO,
            emoji = "🌟✨",
            hashtag = "#Leyenda",
            shareMessage = "¡La Leyenda! ¡50.000 km alcanzados! Si me vieran en el museo, sería la estrella. ¡El hito más grande de mi vida sobre ruedas! #Leyenda",
            requiredDistance = 50000.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "maestro_10",
            title = "Maestro Absoluto",
            description = "Desbloquea todos los demás logros.",
            level = AchievementLevel.MAESTRO,
            emoji = "🤖👑",
            hashtag = "#MaestroTotal",
            shareMessage = "¡Lo hice! No queda ni un logro por desbloquear. Soy el Maestro Absoluto de la Movilidad Personal. ¡A esperar el siguiente parche! #MaestroTotal",
            requirementType = AchievementRequirementType.ALL_OTHERS
        ),

        // ========== SECRETOS (Cifras Especiales Ocultas) ==========
        Achievement(
            id = "secreto_1",
            title = "El Triplete Perfecto",
            description = "El kilometraje total acumulado alcanza 555 km.",
            level = AchievementLevel.SECRETO,
            emoji = "🥉🎯",
            hashtag = "#Triplete",
            shareMessage = "¡Lo logré! Mi odómetro marcó la cifra mágica: 555 km exactos. No fue suerte, fue precisión milimétrica. ¡A rodar con estilo! #Triplete",
            requiredDistance = 555.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "secreto_2",
            title = "El Reflejo",
            description = "El kilometraje total acumulado alcanza 2552 km.",
            level = AchievementLevel.SECRETO,
            emoji = "🔄🪞",
            hashtag = "#Capicúa",
            shareMessage = "¡Mi kilometraje es un espejo! Alcanzar el hito capicúa fue un reflejo de mi dedicación. ¡Esto es arte numérico! #Capicúa",
            requiredDistance = 2552.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "secreto_3",
            title = "La Secuencia Maestra",
            description = "El kilometraje total acumulado alcanza 12345 km.",
            level = AchievementLevel.SECRETO,
            emoji = "🔢🚀",
            hashtag = "#Perfecto",
            shareMessage = "¡Secuencia Maestra desbloqueada! Mis kilómetros van en orden perfecto. Esta cifra es un regalo para cualquier ingeniero. #Perfecto",
            requiredDistance = 12345.0,
            requirementType = AchievementRequirementType.DISTANCE
        ),
        Achievement(
            id = "secreto_4",
            title = "El Muro Final",
            description = "El kilometraje total acumulado alcanza 22222 km.",
            level = AchievementLevel.SECRETO,
            emoji = "🧱✖️",
            hashtag = "#Muro",
            shareMessage = "¡Derribé el Muro de los Dos! 22.222 km de pura constancia. No hay obstáculo que se interponga entre yo y el asfalto. #Muro",
            requiredDistance = 22222.0,
            requirementType = AchievementRequirementType.DISTANCE
        )
    )

    /**
     * Verifica si hay nuevos logros desbloqueados y muestra notificación si es necesario.
     * Este método debe llamarse cuando:
     * - Se añade un nuevo registro
     * - Se actualiza un registro
     * - Se elimina un registro
     * - Se añade un mantenimiento/reparación
     */
    suspend fun checkAndNotifyNewAchievements() {
        try {
            val stats = recordRepository.getAchievementStats()
            val notifiedSet = prefs.getStringSet("notified_achievements", emptySet())?.toMutableSet() ?: mutableSetOf()
            
            Log.d("AchievementsService", "Verificando logros - Stats: $stats")
            Log.d("AchievementsService", "Logros notificados: ${notifiedSet.size}")
            
            // Verificar logros desbloqueados
            val currentlyUnlockedIds = mutableSetOf<String>()
            allAchievements.forEach { achievement ->
                val isUnlocked = isAchievementUnlocked(achievement, stats)
                
                if (isUnlocked) {
                    currentlyUnlockedIds.add(achievement.id)
                }
            }
            
            // Limpiar logros que ya no están desbloqueados
            val toRemoveFromNotified = notifiedSet.filter { it !in currentlyUnlockedIds }
            
            if (toRemoveFromNotified.isNotEmpty()) {
                Log.d("AchievementsService", "Limpiando ${toRemoveFromNotified.size} logros notificados que ya no están desbloqueados")
                notifiedSet.removeAll(toRemoveFromNotified.toSet())
                prefs.edit()
                    .putStringSet("notified_achievements", notifiedSet)
                    .apply()
            }
            
            // Verificar logros nuevos (desbloqueados pero no notificados)
            val newlyUnlocked = mutableListOf<Achievement>()
            allAchievements.forEach { achievement ->
                if (achievement.id in currentlyUnlockedIds && achievement.id !in notifiedSet) {
                    newlyUnlocked.add(achievement)
                    Log.d("AchievementsService", "¡Logro nuevo!: ${achievement.title}")
                }
            }
            
            // Mostrar notificación del primer logro nuevo
            if (newlyUnlocked.isNotEmpty()) {
                val achievement = newlyUnlocked.first()
                _newAchievementMessage.value = "🏆 ¡Logro desbloqueado! ${achievement.emoji} ${achievement.title}"
                _lastUnlockedAchievementId.value = achievement.id
                
                // Marcar como notificado
                notifiedSet.add(achievement.id)
                prefs.edit().putStringSet("notified_achievements", notifiedSet).apply()
                
                Log.d("AchievementsService", "Mostrando notificación para: ${achievement.title}")
            }
            
            // Notificar que deben actualizarse los logros en la UI
            _shouldRefreshAchievements.value++
            
            Log.d("AchievementsService", "Total logros nuevos: ${newlyUnlocked.size}")
        } catch (e: Exception) {
            Log.e("AchievementsService", "Error al verificar logros: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Verifica si un logro específico está desbloqueado según las estadísticas actuales
     */
    private suspend fun isAchievementUnlocked(
        achievement: Achievement,
        stats: RecordRepository.AchievementStats
    ): Boolean {
        return when (achievement.requirementType) {
            AchievementRequirementType.DISTANCE -> 
                stats.totalDistance >= (achievement.requiredDistance ?: 0.0)
            AchievementRequirementType.TRIPS -> 
                stats.totalTrips >= (achievement.requiredTrips ?: 0)
            AchievementRequirementType.CONSECUTIVE_DAYS -> 
                stats.consecutiveDays >= (achievement.requiredConsecutiveDays ?: 0)
            AchievementRequirementType.UNIQUE_SCOOTERS -> 
                stats.uniqueScooters >= (achievement.requiredUniqueScooters ?: 0)
            AchievementRequirementType.LONGEST_TRIP -> 
                stats.longestTrip >= (achievement.requiredLongestTrip ?: 0.0)
            AchievementRequirementType.UNIQUE_WEEKS -> 
                stats.uniqueWeeks >= (achievement.requiredUniqueWeeks ?: 0)
            AchievementRequirementType.MAINTENANCE_COUNT -> 
                stats.maintenanceCount >= (achievement.requiredMaintenanceCount ?: 0)
            AchievementRequirementType.CO2_SAVED -> 
                stats.co2Saved >= (achievement.requiredCO2Saved ?: 0.0)
            AchievementRequirementType.UNIQUE_MONTHS -> 
                stats.uniqueMonths >= (achievement.requiredUniqueMonths ?: 0)
            AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                stats.consecutiveMonths >= (achievement.requiredConsecutiveMonths ?: 0)
            AchievementRequirementType.ALL_OTHERS -> {
                // Verificar si todos los demás logros están desbloqueados
                val otherAchievements = allAchievements.filter { it.id != achievement.id && it.requirementType != AchievementRequirementType.ALL_OTHERS }
                otherAchievements.all { other ->
                    isAchievementUnlocked(other, stats)
                }
            }
            AchievementRequirementType.MULTIPLE -> false // Por ahora no implementado
        }
    }
    
    /**
     * Limpia el mensaje de notificación después de mostrarlo
     */
    fun clearNotificationMessage() {
        _newAchievementMessage.value = null
    }
    
    /**
     * Limpia el ID del último logro desbloqueado
     */
    fun clearLastUnlockedAchievementId() {
        _lastUnlockedAchievementId.value = null
    }
}

