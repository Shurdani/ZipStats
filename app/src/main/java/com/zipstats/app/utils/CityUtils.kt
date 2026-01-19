package com.zipstats.app.utils

import com.zipstats.app.model.Route
import com.zipstats.app.model.VehicleType

/**
 * Utilidades para trabajar con ciudades y nombres de rutas
 */
object CityUtils {
    
    data class CityArea(
        val name: String,
        val latMin: Double,
        val latMax: Double,
        val lonMin: Double,
        val lonMax: Double
    )

    private val cityAreas = listOf(
    // CAPITALES DE PROVINCIA Y CIUDADES IMPORTANTES DE ESPAÑA
    CityArea("A Coruña", 43.35, 43.40, -8.45, -8.35),
    CityArea("Albacete", 38.95, 39.05, -1.90, -1.80),
    CityArea("Alcalá de Henares", 40.47, 40.49, -3.38, -3.32),
    CityArea("Alicante", 38.30, 38.40, -0.55, -0.45),
    CityArea("Almería", 36.80, 36.90, -2.50, -2.40),
    CityArea("Ávila", 40.63, 40.68, -4.75, -4.65),
    CityArea("Badajoz", 38.85, 38.90, -7.02, -6.92),
    CityArea("Barcelona", 41.35, 41.45, 2.10, 2.25),
    CityArea("Bilbao", 43.20, 43.30, -3.00, -2.90),
    CityArea("Burgos", 42.32, 42.37, -3.75, -3.65),
    CityArea("Cáceres", 39.45, 39.50, -6.42, -6.32),
    CityArea("Cádiz", 36.48, 36.54, -6.35, -6.20),
    CityArea("Castellón de la Plana", 39.95, 40.05, -0.10, 0.00),
    CityArea("Ceuta", 35.87, 35.91, -5.35, -5.28),
    CityArea("Ciudad Real", 38.96, 39.01, -3.97, -3.87),
    CityArea("Córdoba", 37.85, 37.90, -4.85, -4.75),
    CityArea("Cuenca", 40.05, 40.10, -2.18, -2.08),
    CityArea("Gijón", 43.50, 43.55, -5.70, -5.60),
    CityArea("Girona", 41.95, 42.00, 2.78, 2.88),
    CityArea("Granada", 37.15, 37.20, -3.65, -3.55),
    CityArea("Guadalajara", 40.61, 40.66, -3.21, -3.11),
    CityArea("Huelva", 37.23, 37.28, -7.00, -6.90),
    CityArea("Huesca", 42.11, 42.16, -0.45, -0.35),
    CityArea("Jaén", 37.75, 37.80, -3.83, -3.73),
    CityArea("León", 42.57, 42.62, -5.62, -5.52),
    CityArea("Lleida", 41.59, 41.64, 0.58, 0.68),
    CityArea("Logroño", 42.44, 42.49, -2.49, -2.39),
    CityArea("Lugo", 42.99, 43.04, -7.60, -7.50),
    CityArea("Madrid", 40.35, 40.50, -3.80, -3.60),
    CityArea("Málaga", 36.60, 36.80, -4.50, -4.30),
    CityArea("Melilla", 35.27, 35.31, -2.97, -2.91),
    CityArea("Murcia", 37.90, 38.00, -1.20, -1.10),
    CityArea("Ourense", 42.31, 42.36, -7.91, -7.81),
    CityArea("Oviedo", 43.35, 43.40, -5.90, -5.80),
    CityArea("Palencia", 41.98, 42.03, -4.57, -4.47),
    CityArea("Palma", 39.50, 39.60, 2.60, 2.70),
    CityArea("Pamplona", 42.80, 42.85, -1.70, -1.60),
    CityArea("Pontevedra", 42.41, 42.46, -8.69, -8.59),
    CityArea("Salamanca", 40.94, 40.99, -5.70, -5.60),
    CityArea("San Sebastián", 43.30, 43.35, -2.00, -1.90),
    CityArea("Santander", 43.45, 43.50, -3.85, -3.75),
    CityArea("Segovia", 40.92, 40.97, -4.16, -4.06),
    CityArea("Sevilla", 37.30, 37.45, -6.05, -5.85),
    CityArea("Soria", 41.74, 41.79, -2.51, -2.41),
    CityArea("Tarragona", 41.09, 41.14, 1.20, 1.30),
    CityArea("Teruel", 40.32, 40.37, -1.15, -1.05),
    CityArea("Toledo", 39.83, 39.88, -4.07, -3.97),
    CityArea("Valencia", 39.40, 39.50, -0.40, -0.30),
    CityArea("Valladolid", 41.62, 41.67, -4.77, -4.67),
    CityArea("Vigo", 42.20, 42.25, -8.75, -8.65),
    CityArea("Vitoria-Gasteiz", 42.82, 42.87, -2.72, -2.62),
    CityArea("Zamora", 41.48, 41.53, -5.80, -5.70),
    CityArea("Zaragoza", 41.60, 41.70, -0.95, -0.85),

    // MUNICIPIOS DE CATALUÑA (BARCELONA)
    CityArea("Badalona", 41.43, 41.46, 2.23, 2.26),
    CityArea("Berga", 42.09, 42.11, 1.83, 1.86),
    CityArea("Castelldefels", 41.27, 41.29, 1.96, 1.98),
    CityArea("Cerdanyola del Vallès", 41.48, 41.50, 2.13, 2.15),
    CityArea("Cornellà de Llobregat", 41.34, 41.36, 2.07, 2.09),
    CityArea("El Prat de Llobregat", 41.31, 41.33, 2.07, 2.09),
    CityArea("Esplugues de Llobregat", 41.36, 41.38, 2.07, 2.09),
    CityArea("Gavà", 41.28, 41.30, 2.00, 2.02),
    CityArea("Granollers", 41.59, 41.62, 2.27, 2.30),
    CityArea("L'Hospitalet de Llobregat", 41.35, 41.37, 2.09, 2.11),
    CityArea("Igualada", 41.57, 41.59, 1.60, 1.63),
    CityArea("Manresa", 41.71, 41.74, 1.81, 1.84),
    CityArea("Mataró", 41.53, 41.55, 2.43, 2.46),
    CityArea("Mollet del Vallès", 41.53, 41.55, 2.20, 2.23),
    CityArea("Montgat", 41.46, 41.48, 2.27, 2.29),
    CityArea("Premià de Mar", 41.49, 41.51, 2.35, 2.38),
    CityArea("Rubí", 41.48, 41.50, 2.03, 2.05),
    CityArea("Sabadell", 41.53, 41.56, 2.10, 2.13),
    CityArea("Sant Adrià de Besòs", 41.42, 41.44, 2.21, 2.23),
    CityArea("Sant Boi de Llobregat", 41.33, 41.35, 2.02, 2.04),
    CityArea("Sant Cugat del Vallès", 41.46, 41.48, 2.07, 2.09),
    CityArea("Santa Coloma de Gramenet", 41.44, 41.46, 2.20, 2.22),
    CityArea("Sitges", 41.22, 41.25, 1.78, 1.82),
    CityArea("Terrassa", 41.56, 41.59, 2.00, 2.04),
    CityArea("Vic", 41.92, 41.94, 2.24, 2.27),
    CityArea("Vilafranca del Penedès", 41.33, 41.36, 1.68, 1.71),
    CityArea("Vilanova i la Geltrú", 41.21, 41.24, 1.71, 1.74),
    CityArea("Viladecans", 41.30, 41.32, 2.00, 2.02),

    // MUNICIPIOS DE CATALUÑA (GIRONA)
    CityArea("Banyoles", 42.10, 42.13, 2.75, 2.78),
    CityArea("Blanes", 41.66, 41.69, 2.78, 2.81),
    CityArea("Figueres", 42.25, 42.28, 2.95, 2.98),
    CityArea("Lloret de Mar", 41.69, 41.71, 2.83, 2.86),
    CityArea("Olot", 42.17, 42.19, 2.47, 2.50),
    CityArea("Palafrugell", 41.90, 41.93, 3.15, 3.18),
    CityArea("Salt", 41.96, 41.98, 2.78, 2.81),
    CityArea("Sant Feliu de Guíxols", 41.77, 41.79, 3.01, 3.04),

    // MUNICIPIOS DE CATALUÑA (LLEIDA)
    CityArea("Balaguer", 41.78, 41.80, 0.80, 0.83),
    CityArea("La Seu d'Urgell", 42.35, 42.37, 1.44, 1.47),
    CityArea("Mollerussa", 41.62, 41.64, 0.88, 0.91),
    CityArea("Tàrrega", 41.63, 41.66, 1.13, 1.16),

    // MUNICIPIOS DE CATALUÑA (TARRAGONA)
    CityArea("Amposta", 40.70, 40.72, 0.57, 0.60),
    CityArea("Cambrils", 41.06, 41.08, 1.04, 1.07),
    CityArea("El Vendrell", 41.21, 41.23, 1.52, 1.55),
    CityArea("Reus", 41.14, 41.17, 1.09, 1.12),
    CityArea("Salou", 41.06, 41.09, 1.12, 1.15),
    CityArea("Tortosa", 40.80, 40.83, 0.51, 0.54),
    CityArea("Valls", 41.27, 41.30, 1.24, 1.27),

    // ÁREA METROPOLITANA DE MADRID
    CityArea("Alcorcón", 40.34, 40.36, -3.84, -3.82),
    CityArea("Alcobendas", 40.52, 40.54, -3.64, -3.62),
    CityArea("Aranjuez", 40.02, 40.04, -3.60, -3.58),
    CityArea("Coslada", 40.42, 40.44, -3.57, -3.55),
    CityArea("Fuenlabrada", 40.27, 40.29, -3.81, -3.79),
    CityArea("Getafe", 40.30, 40.32, -3.73, -3.67),
    CityArea("Las Rozas de Madrid", 40.48, 40.50, -3.89, -3.87),
    CityArea("Leganés", 40.31, 40.33, -3.76, -3.74),
    CityArea("Majadahonda", 40.46, 40.48, -3.87, -3.85),
    CityArea("Móstoles", 40.31, 40.33, -3.88, -3.86),
    CityArea("Parla", 40.23, 40.25, -3.78, -3.76),
    CityArea("Pinto", 40.24, 40.26, -3.71, -3.69),
    CityArea("Pozuelo de Alarcón", 40.42, 40.44, -3.82, -3.80),
    CityArea("San Sebastián de los Reyes", 40.54, 40.56, -3.63, -3.61),
    CityArea("Torrejón de Ardoz", 40.44, 40.46, -3.49, -3.47),
    CityArea("Valdemoro", 40.18, 40.20, -3.67, -3.65),

    // ÁREA METROPOLITANA DE VALENCIA
    CityArea("Alboraya", 39.50, 39.52, -0.35, -0.33),
    CityArea("Burjassot", 39.50, 39.52, -0.42, -0.40),
    CityArea("Manises", 39.49, 39.51, -0.46, -0.44),
    CityArea("Mislata", 39.47, 39.49, -0.42, -0.40),
    CityArea("Paiporta", 39.42, 39.44, -0.42, -0.40),
    CityArea("Paterna", 39.50, 39.52, -0.44, -0.42),
    CityArea("Silla", 39.36, 39.38, -0.42, -0.40),
    CityArea("Tavernes Blanques", 39.51, 39.53, -0.36, -0.34),
    CityArea("Torrent", 39.43, 39.45, -0.47, -0.45),
    CityArea("Xirivella", 39.46, 39.48, -0.42, -0.40),

    // ÁREA METROPOLITANA DE SEVILLA
    CityArea("Alcalá de Guadaíra", 37.32, 37.34, -5.86, -5.84),
    CityArea("Alcalá del Río", 37.52, 37.54, -5.97, -5.95),
    CityArea("Bormujos", 37.36, 37.38, -6.07, -6.05),
    CityArea("Coria del Río", 37.27, 37.29, -6.05, -6.03),
    CityArea("Dos Hermanas", 37.26, 37.28, -5.95, -5.93),
    CityArea("La Rinconada", 37.48, 37.50, -5.99, -5.97),
    CityArea("Los Palacios y Villafranca", 37.15, 37.17, -5.93, -5.91),
    CityArea("Mairena del Aljarafe", 37.33, 37.35, -6.08, -6.06),
    CityArea("San Juan de Aznalfarache", 37.35, 37.37, -6.03, -6.01),
    CityArea("Tomares", 37.37, 37.39, -6.04, -6.02),

    // ÁREA METROPOLITANA DE BILBAO
    CityArea("Barakaldo", 43.29, 43.31, -3.01, -2.99),
    CityArea("Basauri", 43.23, 43.25, -2.90, -2.88),
    CityArea("Getxo", 43.34, 43.36, -3.03, -3.01),
    CityArea("Portugalete", 43.31, 43.33, -3.03, -3.01),
    CityArea("Santurtzi", 43.32, 43.34, -3.04, -3.02),

    // ÁREA METROPOLITANA DE MÁLAGA (COSTA DEL SOL)
    CityArea("Benalmádena", 36.59, 36.61, -4.53, -4.51),
    CityArea("Estepona", 36.41, 36.44, -5.17, -5.13),
    CityArea("Fuengirola", 36.53, 36.55, -4.63, -4.61),
    CityArea("Marbella", 36.48, 36.52, -4.95, -4.85),
    CityArea("Mijas", 36.58, 36.60, -4.65, -4.63),
    CityArea("Rincón de la Victoria", 36.71, 36.73, -4.29, -4.27),
    CityArea("Torremolinos", 36.61, 36.63, -4.51, -4.49),
    CityArea("Vélez-Málaga", 36.77, 36.80, -4.12, -4.08),

    // ÁREA METROPOLITANA DE ZARAGOZA
    CityArea("Cuarte de Huerva", 41.59, 41.61, -0.94, -0.92),
    CityArea("La Puebla de Alfindén", 41.66, 41.68, -0.77, -0.75),
    CityArea("Utebo", 41.69, 41.71, -1.00, -0.98),

    // ÁREA METROPOLITANA DE ALICANTE-ELCHE
    CityArea("Elche", 38.25, 38.29, -0.73, -0.67),
    CityArea("Elda", 38.47, 38.49, -0.80, -0.78),
    CityArea("Petrer", 38.48, 38.50, -0.78, -0.76),
    CityArea("San Vicente del Raspeig", 38.39, 38.41, -0.53, -0.51),

    // ÁREA METROPOLITANA DE MURCIA
    CityArea("Alcantarilla", 37.93, 37.95, -1.22, -1.20),
    CityArea("Las Torres de Cotillas", 38.02, 38.04, -1.25, -1.23),
    CityArea("Molina de Segura", 38.04, 38.06, -1.22, -1.20),

    // ÁREA METROPOLITANA DE VIGO
    CityArea("Cangas de Morrazo", 42.25, 42.27, -8.80, -8.78),
    CityArea("Moaña", 42.27, 42.29, -8.76, -8.74),
    CityArea("Redondela", 42.27, 42.29, -8.62, -8.60),

    // ÁREA METROPOLITANA DE A CORUÑA
    CityArea("Arteixo", 43.29, 43.31, -8.52, -8.50),
    CityArea("Culleredo", 43.28, 43.30, -8.40, -8.38),
    CityArea("Oleiros", 43.33, 43.35, -8.32, -8.30),

    // BAHÍA DE CÁDIZ
    CityArea("Chiclana de la Frontera", 36.40, 36.44, -6.17, -6.13),
    CityArea("El Puerto de Santa María", 36.58, 36.62, -6.25, -6.21),
    CityArea("Jerez de la Frontera", 36.67, 36.71, -6.16, -6.10),
    CityArea("Puerto Real", 36.51, 36.54, -6.15, -6.11),
    CityArea("San Fernando", 36.45, 36.48, -6.22, -6.18),

    // ÁREA METROPOLITANA DE CASTELLÓN Y LEVANTE
    CityArea("Almassora", 39.93, 39.96, -0.08, -0.04),
    CityArea("Benicàssim", 40.04, 40.07, 0.05, 0.08),
    CityArea("Benidorm", 38.52, 38.55, -0.15, -0.10),
    CityArea("Gandia", 38.95, 38.98, -0.20, -0.15),
    CityArea("Torrevieja", 37.96, 38.00, -0.70, -0.65),
    CityArea("Vila-real", 39.92, 39.95, -0.12, -0.08),

    // ÁREA METROPOLITANA DE ASTURIAS (ZONA CENTRAL)
    CityArea("Avilés", 43.54, 43.57, -5.94, -5.90),
    CityArea("Langreo", 43.29, 43.32, -5.71, -5.67),
    CityArea("Siero", 43.38, 43.41, -5.68, -5.63),

    // ÁREA METROPOLITANA DE GRANADA
    CityArea("Albolote", 37.22, 37.24, -3.67, -3.65),
    CityArea("Armilla", 37.14, 37.16, -3.63, -3.61),
    CityArea("Las Gabias", 37.13, 37.15, -3.70, -3.68),
    CityArea("La Zubia", 37.12, 37.14, -3.60, -3.58),
    CityArea("Maracena", 37.20, 37.22, -3.64, -3.62),

    // ZONAS TURÍSTICAS (ISLAS)
    CityArea("Adeje", 28.09, 28.13, -16.75, -16.71),
    CityArea("Arona", 28.05, 28.11, -16.70, -16.66),
    CityArea("Calvià", 39.52, 39.58, 2.48, 2.54),
    CityArea("Ibiza", 38.90, 38.92, 1.41, 1.44),
    CityArea("Las Palmas de Gran Canaria", 28.10, 28.20, -15.50, -15.40),
    CityArea("San Bartolomé de Tirajana", 27.85, 27.95, -15.60, -15.54),
    CityArea("Santa Cruz de Tenerife", 28.45, 28.50, -16.30, -16.20),

    // CIUDADES IMPORTANTES DE EUROPA
    CityArea("Ámsterdam", 52.32, 52.42, 4.80, 5.00),
    CityArea("Atenas", 37.93, 38.03, 23.67, 23.87),
    CityArea("Berlín", 52.45, 52.55, 13.30, 13.50),
    CityArea("Bruselas", 50.80, 50.90, 4.30, 4.50),
    CityArea("Dublín", 53.30, 53.40, -6.35, -6.15),
    CityArea("Estocolmo", 59.28, 59.38, 17.95, 18.15),
    CityArea("Lisboa", 38.69, 38.79, -9.22, -9.02),
    CityArea("Londres", 51.45, 51.55, -0.20, 0.00),
    CityArea("París", 48.80, 48.90, 2.25, 2.45),
    CityArea("Praga", 50.03, 50.13, 14.35, 14.55),
    CityArea("Roma", 41.85, 41.95, 12.40, 12.60),
    CityArea("Viena", 48.15, 48.25, 16.30, 16.50),

    // CIUDADES IMPORTANTES DE AMÉRICA
    CityArea("Bogotá", 4.55, 4.75, -74.20, -74.00),
    CityArea("Buenos Aires", -34.65, -34.55, -58.50, -58.30),
    CityArea("Chicago", 41.80, 42.00, -87.80, -87.60),
    CityArea("Ciudad de México", 19.35, 19.55, -99.25, -99.05),
    CityArea("Lima", -12.15, -11.95, -77.15, -76.95),
    CityArea("Los Ángeles", 33.95, 34.15, -118.40, -118.20),
    CityArea("Nueva York", 40.65, 40.85, -74.10, -73.90),
    CityArea("Santiago de Chile", -33.50, -33.30, -70.75, -70.55),
    CityArea("São Paulo", -23.60, -23.50, -46.75, -46.55),
    CityArea("Toronto", 43.60, 43.80, -79.50, -79.30),

    // CIUDADES IMPORTANTES DE ASIA
    CityArea("Bangkok", 13.65, 13.85, 100.40, 100.60),
    CityArea("Dubái", 25.05, 25.25, 55.10, 55.40),
    CityArea("Estambul", 40.95, 41.15, 28.80, 29.20),
    CityArea("Hong Kong", 22.20, 22.40, 114.00, 114.30),
    CityArea("Mumbai", 18.90, 19.20, 72.75, 73.00),
    CityArea("Pekín", 39.80, 40.00, 116.20, 116.60),
    CityArea("Seúl", 37.45, 37.65, 126.80, 127.20),
    CityArea("Shanghái", 31.00, 31.40, 121.20, 121.80),
    CityArea("Singapur", 1.25, 1.45, 103.75, 104.00),
    CityArea("Tokio", 35.60, 35.80, 139.60, 139.90)
    )

    /**
     * Obtiene el nombre de la ciudad basándose en las coordenadas
     */
    fun getCityName(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        val matchingCities = cityAreas.filter { city ->
            latitude >= city.latMin && latitude <= city.latMax &&
                    longitude >= city.lonMin && longitude <= city.lonMax
        }
        if (matchingCities.isEmpty()) return null
        return matchingCities.minByOrNull { city ->
            (city.latMax - city.latMin) * (city.lonMax - city.lonMin)
        }?.name
    }

    /**
     * Genera un título para la ruta basándose en las ciudades de inicio y fin
     * @param vehicleType Tipo de vehículo (opcional). Se usa cuando no hay ciudad detectada.
     */
    fun getRouteTitleText(route: Route, vehicleType: VehicleType? = null): String {
        if (route.notes.isNotBlank()) return route.notes
        if (route.points.isEmpty()) return "Mi ruta"

        val startCity = getCityName(route.points.first().latitude, route.points.first().longitude)
        val endCity = getCityName(route.points.last().latitude, route.points.last().longitude)

        return if (startCity != null && endCity != null && startCity != endCity) {
            "$startCity → $endCity"
        } else if (startCity != null) {
            // Misma ciudad de origen y final: "Mi ruta por [ciudad]"
            "Mi ruta por $startCity"
        } else {
            // Sin ciudad detectada: usar vehicleType si está disponible
            if (vehicleType != null) {
                "Mi ruta en ${vehicleType.displayName.lowercase()}"
            } else {
                "Mi ruta"
            }
        }
    }
}

