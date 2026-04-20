package com.zipstats.app.utils

import com.zipstats.app.model.Route
import com.zipstats.app.model.VehicleType
import kotlin.math.pow

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

        // ─────────────────────────────────────────────────────────────────────
        // CAPITALES DE PROVINCIA Y CIUDADES IMPORTANTES DE ESPAÑA
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // CATALUÑA - PROVÍNCIA DE BARCELONA
        // ─────────────────────────────────────────────────────────────────────
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
        // Baix Llobregat nord
        CityArea("Martorell", 41.47, 41.49, 1.92, 1.95),
        CityArea("Abrera", 41.51, 41.53, 1.89, 1.92),
        CityArea("Esparreguera", 41.53, 41.55, 1.86, 1.89),
        CityArea("Olesa de Montserrat", 41.54, 41.56, 1.88, 1.91),
        CityArea("Castellbisbal", 41.47, 41.49, 1.97, 2.00),
        CityArea("Sant Andreu de la Barca", 41.44, 41.46, 1.97, 2.00),
        CityArea("Pallejà", 41.41, 41.43, 1.99, 2.01),
        CityArea("Sant Vicenç dels Horts", 41.38, 41.40, 2.00, 2.03),
        CityArea("Corbera de Llobregat", 41.37, 41.39, 1.96, 1.99),
        CityArea("Cervelló", 41.38, 41.40, 1.96, 1.99),
        // Vallès Occidental interior
        CityArea("Montcada i Reixac", 41.48, 41.50, 2.18, 2.21),
        CityArea("Ripollet", 41.49, 41.51, 2.15, 2.17),
        CityArea("Barberà del Vallès", 41.51, 41.53, 2.12, 2.15),
        CityArea("Badia del Vallès", 41.51, 41.53, 2.11, 2.13),
        CityArea("Santa Perpètua de Mogoda", 41.53, 41.55, 2.17, 2.20),
        CityArea("Polinyà", 41.55, 41.57, 2.15, 2.18),
        CityArea("Sentmenat", 41.57, 41.59, 2.15, 2.18),
        CityArea("Castellar del Vallès", 41.60, 41.62, 2.07, 2.10),
        CityArea("Sant Quirze del Vallès", 41.52, 41.54, 2.08, 2.11),
        CityArea("Matadepera", 41.60, 41.62, 2.02, 2.05),
        // Vallès Oriental
        CityArea("La Llagosta", 41.52, 41.54, 2.20, 2.23),
        CityArea("Parets del Vallès", 41.56, 41.58, 2.22, 2.25),
        CityArea("Montmeló", 41.55, 41.57, 2.24, 2.27),
        CityArea("Montornès del Vallès", 41.55, 41.57, 2.27, 2.30),
        CityArea("Les Franqueses del Vallès", 41.60, 41.62, 2.30, 2.33),
        CityArea("Cardedeu", 41.63, 41.65, 2.35, 2.38),
        CityArea("La Garriga", 41.68, 41.70, 2.28, 2.31),
        CityArea("Caldes de Montbui", 41.62, 41.64, 2.16, 2.19),
        CityArea("Lliçà d'Amunt", 41.60, 41.62, 2.22, 2.25),
        CityArea("Bigues i Riells", 41.67, 41.69, 2.22, 2.25),
        // Maresme
        CityArea("Tiana", 41.47, 41.49, 2.27, 2.30),
        CityArea("Alella", 41.49, 41.51, 2.29, 2.32),
        CityArea("El Masnou", 41.48, 41.50, 2.31, 2.33),
        CityArea("Teià", 41.50, 41.52, 2.33, 2.36),
        CityArea("Vilassar de Mar", 41.50, 41.52, 2.38, 2.41),
        CityArea("Vilassar de Dalt", 41.52, 41.54, 2.37, 2.40),
        CityArea("Cabrera de Mar", 41.53, 41.55, 2.40, 2.43),
        CityArea("Argentona", 41.55, 41.57, 2.40, 2.43),
        CityArea("Sant Andreu de Llavaneres", 41.56, 41.58, 2.47, 2.50),
        CityArea("Sant Vicenç de Montalt", 41.57, 41.59, 2.49, 2.52),
        CityArea("Caldes d'Estrac", 41.57, 41.59, 2.52, 2.54),
        CityArea("Arenys de Mar", 41.57, 41.59, 2.54, 2.57),
        CityArea("Arenys de Munt", 41.59, 41.61, 2.54, 2.57),
        CityArea("Canet de Mar", 41.58, 41.60, 2.58, 2.61),
        CityArea("Sant Pol de Mar", 41.60, 41.62, 2.62, 2.65),
        CityArea("Calella", 41.61, 41.63, 2.65, 2.68),
        CityArea("Pineda de Mar", 41.62, 41.64, 2.67, 2.70),
        CityArea("Santa Susanna", 41.64, 41.66, 2.70, 2.73),
        CityArea("Malgrat de Mar", 41.64, 41.66, 2.73, 2.76),
        // Garraf
        CityArea("Begues", 41.28, 41.30, 1.89, 1.92),
        CityArea("Olivella", 41.28, 41.30, 1.83, 1.86),
        CityArea("Sant Pere de Ribes", 41.22, 41.25, 1.76, 1.80),
        CityArea("Cubelles", 41.19, 41.21, 1.67, 1.70),
        CityArea("Cunit", 41.19, 41.21, 1.62, 1.65),

        // ─────────────────────────────────────────────────────────────────────
        // CATALUÑA - PROVÍNCIA DE GIRONA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Banyoles", 42.10, 42.13, 2.75, 2.78),
        CityArea("Blanes", 41.66, 41.69, 2.78, 2.81),
        CityArea("Figueres", 42.25, 42.28, 2.95, 2.98),
        CityArea("Lloret de Mar", 41.69, 41.71, 2.83, 2.86),
        CityArea("Olot", 42.17, 42.19, 2.47, 2.50),
        CityArea("Palafrugell", 41.90, 41.93, 3.15, 3.18),
        CityArea("Salt", 41.96, 41.98, 2.78, 2.81),
        CityArea("Sant Feliu de Guíxols", 41.77, 41.79, 3.01, 3.04),

        // ─────────────────────────────────────────────────────────────────────
        // CATALUÑA - PROVÍNCIA DE LLEIDA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Balaguer", 41.78, 41.80, 0.80, 0.83),
        CityArea("La Seu d'Urgell", 42.35, 42.37, 1.44, 1.47),
        CityArea("Mollerussa", 41.62, 41.64, 0.88, 0.91),
        CityArea("Tàrrega", 41.63, 41.66, 1.13, 1.16),

        // ─────────────────────────────────────────────────────────────────────
        // CATALUÑA - PROVÍNCIA DE TARRAGONA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Amposta", 40.70, 40.72, 0.57, 0.60),
        CityArea("Cambrils", 41.06, 41.08, 1.04, 1.07),
        CityArea("El Vendrell", 41.21, 41.23, 1.52, 1.55),
        CityArea("Reus", 41.14, 41.17, 1.09, 1.12),
        CityArea("Salou", 41.06, 41.09, 1.12, 1.15),
        CityArea("Tortosa", 40.80, 40.83, 0.51, 0.54),
        CityArea("Valls", 41.27, 41.30, 1.24, 1.27),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE MADRID
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE VALENCIA
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE SEVILLA
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE BILBAO
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Barakaldo", 43.29, 43.31, -3.01, -2.99),
        CityArea("Basauri", 43.23, 43.25, -2.90, -2.88),
        CityArea("Getxo", 43.34, 43.36, -3.03, -3.01),
        CityArea("Portugalete", 43.31, 43.33, -3.03, -3.01),
        CityArea("Santurtzi", 43.32, 43.34, -3.04, -3.02),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE MÁLAGA (COSTA DEL SOL)
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Benalmádena", 36.59, 36.61, -4.53, -4.51),
        CityArea("Estepona", 36.41, 36.44, -5.17, -5.13),
        CityArea("Fuengirola", 36.53, 36.55, -4.63, -4.61),
        CityArea("Marbella", 36.48, 36.52, -4.95, -4.85),
        CityArea("Mijas", 36.58, 36.60, -4.65, -4.63),
        CityArea("Rincón de la Victoria", 36.71, 36.73, -4.29, -4.27),
        CityArea("Torremolinos", 36.61, 36.63, -4.51, -4.49),
        CityArea("Vélez-Málaga", 36.77, 36.80, -4.12, -4.08),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE ZARAGOZA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Cuarte de Huerva", 41.59, 41.61, -0.94, -0.92),
        CityArea("La Puebla de Alfindén", 41.66, 41.68, -0.77, -0.75),
        CityArea("Utebo", 41.69, 41.71, -1.00, -0.98),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE ALICANTE-ELCHE
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Elche", 38.25, 38.29, -0.73, -0.67),
        CityArea("Elda", 38.47, 38.49, -0.80, -0.78),
        CityArea("Petrer", 38.48, 38.50, -0.78, -0.76),
        CityArea("San Vicente del Raspeig", 38.39, 38.41, -0.53, -0.51),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE MURCIA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Alcantarilla", 37.93, 37.95, -1.22, -1.20),
        CityArea("Las Torres de Cotillas", 38.02, 38.04, -1.25, -1.23),
        CityArea("Molina de Segura", 38.04, 38.06, -1.22, -1.20),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE VIGO
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Cangas de Morrazo", 42.25, 42.27, -8.80, -8.78),
        CityArea("Moaña", 42.27, 42.29, -8.76, -8.74),
        CityArea("Redondela", 42.27, 42.29, -8.62, -8.60),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE A CORUÑA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Arteixo", 43.29, 43.31, -8.52, -8.50),
        CityArea("Culleredo", 43.28, 43.30, -8.40, -8.38),
        CityArea("Oleiros", 43.33, 43.35, -8.32, -8.30),

        // ─────────────────────────────────────────────────────────────────────
        // BAHÍA DE CÁDIZ
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Chiclana de la Frontera", 36.40, 36.44, -6.17, -6.13),
        CityArea("El Puerto de Santa María", 36.58, 36.62, -6.25, -6.21),
        CityArea("Jerez de la Frontera", 36.67, 36.71, -6.16, -6.10),
        CityArea("Puerto Real", 36.51, 36.54, -6.15, -6.11),
        CityArea("San Fernando", 36.45, 36.48, -6.22, -6.18),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE CASTELLÓN Y LEVANTE
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Almassora", 39.93, 39.96, -0.08, -0.04),
        CityArea("Benicàssim", 40.04, 40.07, 0.05, 0.08),
        CityArea("Benidorm", 38.52, 38.55, -0.15, -0.10),
        CityArea("Gandia", 38.95, 38.98, -0.20, -0.15),
        CityArea("Torrevieja", 37.96, 38.00, -0.70, -0.65),
        CityArea("Vila-real", 39.92, 39.95, -0.12, -0.08),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE ASTURIAS (ZONA CENTRAL)
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Avilés", 43.54, 43.57, -5.94, -5.90),
        CityArea("Langreo", 43.29, 43.32, -5.71, -5.67),
        CityArea("Siero", 43.38, 43.41, -5.68, -5.63),

        // ─────────────────────────────────────────────────────────────────────
        // ÁREA METROPOLITANA DE GRANADA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Albolote", 37.22, 37.24, -3.67, -3.65),
        CityArea("Armilla", 37.14, 37.16, -3.63, -3.61),
        CityArea("Las Gabias", 37.13, 37.15, -3.70, -3.68),
        CityArea("La Zubia", 37.12, 37.14, -3.60, -3.58),
        CityArea("Maracena", 37.20, 37.22, -3.64, -3.62),

        // ─────────────────────────────────────────────────────────────────────
        // ILLES BALEARS - MALLORCA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Palma", 39.55, 39.60, 2.62, 2.70),
        CityArea("Calvià", 39.52, 39.58, 2.48, 2.54),
        CityArea("Llucmajor", 39.47, 39.50, 2.87, 2.91),
        CityArea("Manacor", 39.56, 39.59, 3.20, 3.24),
        CityArea("Inca", 39.71, 39.73, 2.90, 2.93),
        CityArea("Marratxí", 39.62, 39.64, 2.73, 2.76),
        CityArea("Alcúdia", 39.85, 39.87, 3.11, 3.14),
        CityArea("Pollença", 39.87, 39.89, 3.01, 3.04),
        CityArea("Sa Pobla", 39.76, 39.78, 3.01, 3.04),
        CityArea("Sóller", 39.76, 39.78, 2.70, 2.73),
        CityArea("Felanitx", 39.46, 39.48, 3.14, 3.17),
        CityArea("Campos", 39.42, 39.44, 2.98, 3.01),
        CityArea("Santanyí", 39.35, 39.37, 3.12, 3.15),
        CityArea("Andratx", 39.56, 39.58, 2.41, 2.44),
        CityArea("Muro", 39.73, 39.75, 3.03, 3.06),
        CityArea("Petra", 39.60, 39.62, 3.10, 3.13),
        CityArea("Artà", 39.69, 39.71, 3.34, 3.37),
        CityArea("Capdepera", 39.70, 39.72, 3.42, 3.45),
        CityArea("Son Servera", 39.62, 39.64, 3.36, 3.39),
        CityArea("Sant Llorenç des Cardassar", 39.61, 39.63, 3.28, 3.31),
        CityArea("Binissalem", 39.68, 39.70, 2.84, 2.87),
        CityArea("Consell", 39.66, 39.68, 2.83, 2.86),
        CityArea("Bunyola", 39.68, 39.70, 2.70, 2.73),
        CityArea("Esporles", 39.65, 39.67, 2.61, 2.64),
        CityArea("Establiments", 39.63, 39.65, 2.67, 2.70),
        CityArea("Lloseta", 39.71, 39.73, 2.87, 2.90),
        CityArea("Selva", 39.74, 39.76, 2.90, 2.93),
        CityArea("Campanet", 39.77, 39.79, 2.95, 2.98),
        CityArea("Búger", 39.76, 39.78, 2.97, 3.00),

        // ─────────────────────────────────────────────────────────────────────
        // ILLES BALEARS - MENORCA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Maó", 39.88, 39.90, 4.26, 4.29),
        CityArea("Ciutadella de Menorca", 39.99, 40.01, 3.82, 3.85),
        CityArea("Alaior", 39.92, 39.94, 4.13, 4.16),
        CityArea("Es Mercadal", 39.98, 40.00, 4.06, 4.09),
        CityArea("Ferreries", 39.98, 40.00, 3.97, 4.00),
        CityArea("Es Castell", 39.86, 39.88, 4.27, 4.30),
        CityArea("Sant Lluís", 39.84, 39.86, 4.24, 4.27),

        // ─────────────────────────────────────────────────────────────────────
        // ILLES BALEARS - EIVISSA (IBIZA)
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Eivissa", 38.90, 38.92, 1.41, 1.44),
        CityArea("Sant Antoni de Portmany", 38.97, 38.99, 1.29, 1.32),
        CityArea("Santa Eulària des Riu", 38.98, 39.00, 1.52, 1.55),
        CityArea("Sant Josep de sa Talaia", 38.91, 38.93, 1.34, 1.37),
        CityArea("Sant Joan de Labritja", 39.07, 39.09, 1.49, 1.52),

        // ─────────────────────────────────────────────────────────────────────
        // ILLES BALEARS - FORMENTERA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Formentera", 38.69, 38.73, 1.40, 1.49),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - TENERIFE
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Santa Cruz de Tenerife", 28.45, 28.50, -16.30, -16.20),
        CityArea("San Cristóbal de La Laguna", 28.47, 28.50, -16.34, -16.28),
        CityArea("Adeje", 28.09, 28.13, -16.75, -16.71),
        CityArea("Arona", 28.05, 28.11, -16.70, -16.66),
        CityArea("Granadilla de Abona", 28.10, 28.13, -16.60, -16.56),
        CityArea("San Miguel de Abona", 28.04, 28.07, -16.64, -16.60),
        CityArea("Güímar", 28.30, 28.33, -16.43, -16.39),
        CityArea("Candelaria", 28.35, 28.38, -16.38, -16.34),
        CityArea("El Rosario", 28.44, 28.47, -16.31, -16.27),
        CityArea("Tegueste", 28.51, 28.53, -16.33, -16.29),
        CityArea("Tacoronte", 28.47, 28.50, -16.41, -16.37),
        CityArea("El Sauzal", 28.47, 28.50, -16.44, -16.40),
        CityArea("La Matanza de Acentejo", 28.47, 28.49, -16.46, -16.42),
        CityArea("La Victoria de Acentejo", 28.45, 28.47, -16.48, -16.44),
        CityArea("Santa Úrsula", 28.43, 28.46, -16.50, -16.46),
        CityArea("La Orotava", 28.38, 28.41, -16.55, -16.51),
        CityArea("Puerto de la Cruz", 28.40, 28.43, -16.57, -16.53),
        CityArea("Los Realejos", 28.37, 28.40, -16.60, -16.56),
        CityArea("Icod de los Vinos", 28.36, 28.39, -16.73, -16.69),
        CityArea("Garachico", 28.36, 28.38, -16.77, -16.73),
        CityArea("Buenavista del Norte", 28.37, 28.39, -16.88, -16.84),
        CityArea("Los Silos", 28.36, 28.38, -16.83, -16.79),
        CityArea("Santiago del Teide", 28.28, 28.31, -16.84, -16.80),
        CityArea("Guía de Isora", 28.18, 28.22, -16.80, -16.76),
        CityArea("Vilaflor", 28.15, 28.18, -16.65, -16.61),
        CityArea("Arico", 28.16, 28.20, -16.51, -16.47),
        CityArea("Fasnia", 28.22, 28.25, -16.46, -16.42),
        CityArea("Arafo", 28.30, 28.33, -16.45, -16.41),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - GRAN CANARIA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Las Palmas de Gran Canaria", 28.10, 28.20, -15.50, -15.40),
        CityArea("Telde", 27.99, 28.03, -15.44, -15.40),
        CityArea("Santa Lucía de Tirajana", 27.90, 27.94, -15.55, -15.51),
        CityArea("San Bartolomé de Tirajana", 27.85, 27.95, -15.60, -15.54),
        CityArea("Mogán", 27.88, 27.92, -15.74, -15.70),
        CityArea("La Aldea de San Nicolás", 27.99, 28.03, -15.80, -15.76),
        CityArea("Agaete", 28.09, 28.12, -15.72, -15.68),
        CityArea("Gáldar", 28.13, 28.16, -15.67, -15.63),
        CityArea("Guía de Gran Canaria", 28.12, 28.15, -15.64, -15.60),
        CityArea("Moya", 28.10, 28.13, -15.60, -15.56),
        CityArea("Teror", 28.05, 28.08, -15.56, -15.52),
        CityArea("Valleseco", 28.04, 28.07, -15.59, -15.55),
        CityArea("Vega de San Mateo", 28.00, 28.03, -15.57, -15.53),
        CityArea("Tejeda", 27.98, 28.01, -15.65, -15.61),
        CityArea("Artenara", 28.00, 28.03, -15.68, -15.64),
        CityArea("San Bartolomé de Fontanales", 28.06, 28.09, -15.63, -15.59),
        CityArea("Arucas", 28.11, 28.14, -15.53, -15.49),
        CityArea("Firgas", 28.07, 28.10, -15.57, -15.53),
        CityArea("Las Palmas Norte (Arucas)", 28.11, 28.13, -15.52, -15.49),
        CityArea("Ingenio", 27.92, 27.95, -15.44, -15.40),
        CityArea("Agüimes", 27.90, 27.93, -15.46, -15.42),
        CityArea("Vecindario", 27.85, 27.88, -15.44, -15.40),
        CityArea("San Fernando de Maspalomas", 27.87, 27.91, -15.58, -15.54),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - LANZAROTE
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Arrecife", 28.96, 28.99, -13.56, -13.52),
        CityArea("San Bartolomé", 28.99, 29.02, -13.63, -13.59),
        CityArea("Tías", 28.94, 28.97, -13.67, -13.63),
        CityArea("Yaiza", 28.94, 28.97, -13.78, -13.74),
        CityArea("Tinajo", 29.06, 29.09, -13.72, -13.68),
        CityArea("Haría", 29.14, 29.17, -13.53, -13.49),
        CityArea("Teguise", 29.05, 29.08, -13.57, -13.53),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - FUERTEVENTURA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Puerto del Rosario", 28.49, 28.52, -13.87, -13.83),
        CityArea("Pájara", 28.32, 28.36, -14.12, -14.08),
        CityArea("La Oliva", 28.60, 28.63, -13.93, -13.89),
        CityArea("Corralejo", 28.72, 28.75, -13.88, -13.84),
        CityArea("Antigua", 28.42, 28.45, -14.00, -13.96),
        CityArea("Tuineje", 28.31, 28.34, -14.04, -14.00),
        CityArea("Betancuria", 28.42, 28.45, -14.07, -14.03),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - LA PALMA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Santa Cruz de La Palma", 28.67, 28.70, -17.77, -17.74),
        CityArea("Los Llanos de Aridane", 28.65, 28.68, -17.92, -17.88),
        CityArea("El Paso", 28.64, 28.67, -17.88, -17.84),
        CityArea("Breña Alta", 28.65, 28.68, -17.80, -17.76),
        CityArea("Tazacorte", 28.63, 28.66, -17.94, -17.90),
        CityArea("Tijarafe", 28.69, 28.72, -17.97, -17.93),
        CityArea("Puntagorda", 28.76, 28.79, -17.99, -17.95),
        CityArea("Garafía", 28.84, 28.87, -17.96, -17.92),
        CityArea("Barlovento", 28.82, 28.85, -17.82, -17.78),
        CityArea("San Andrés y Sauces", 28.78, 28.81, -17.78, -17.74),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - LA GOMERA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("San Sebastián de La Gomera", 28.09, 28.12, -17.12, -17.08),
        CityArea("Hermigua", 28.16, 28.19, -17.20, -17.16),
        CityArea("Agulo", 28.18, 28.21, -17.22, -17.18),
        CityArea("Valle Gran Rey", 28.07, 28.10, -17.33, -17.29),
        CityArea("Vallehermoso", 28.16, 28.19, -17.27, -17.23),

        // ─────────────────────────────────────────────────────────────────────
        // CANARIAS - EL HIERRO
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Valverde", 27.80, 27.83, -17.91, -17.88),
        CityArea("La Frontera", 27.76, 27.79, -18.02, -17.98),

        // ─────────────────────────────────────────────────────────────────────
        // PAÍS VASCO - GIPUZKOA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Irun", 43.33, 43.37, -1.80, -1.74),
        CityArea("Hondarribia", 43.35, 43.38, -1.81, -1.77),
        CityArea("Rentería", 43.30, 43.32, -1.90, -1.87),
        CityArea("Pasaia", 43.32, 43.34, -1.93, -1.90),
        CityArea("Hernani", 43.26, 43.28, -1.99, -1.96),
        CityArea("Lasarte-Oria", 43.26, 43.28, -2.02, -1.99),
        CityArea("Usurbil", 43.26, 43.28, -2.05, -2.02),
        CityArea("Zarautz", 43.27, 43.30, -2.18, -2.14),
        CityArea("Zumaia", 43.28, 43.30, -2.27, -2.23),
        CityArea("Getaria", 43.29, 43.31, -2.22, -2.19),
        CityArea("Azpeitia", 43.17, 43.19, -2.28, -2.25),
        CityArea("Azkoitia", 43.17, 43.19, -2.32, -2.29),
        CityArea("Bergara", 43.11, 43.13, -2.44, -2.41),
        CityArea("Arrasate-Mondragón", 43.06, 43.08, -2.50, -2.47),
        CityArea("Beasain", 43.04, 43.06, -2.21, -2.18),
        CityArea("Ordizia", 43.05, 43.07, -2.18, -2.15),
        CityArea("Tolosa", 43.13, 43.15, -2.08, -2.05),
        CityArea("Andoain", 43.21, 43.23, -2.02, -1.99),
        CityArea("Eibar", 43.18, 43.20, -2.49, -2.46),
        CityArea("Elgoibar", 43.20, 43.22, -2.44, -2.41),
        CityArea("Deba", 43.28, 43.30, -2.36, -2.33),
        CityArea("Mutriku", 43.30, 43.32, -2.40, -2.37),
        CityArea("Ondarroa", 43.32, 43.34, -2.43, -2.40),

        // ─────────────────────────────────────────────────────────────────────
        // PAÍS VASCO - BIZKAIA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Leioa", 43.32, 43.34, -2.99, -2.97),
        CityArea("Erandio", 43.30, 43.32, -2.98, -2.96),
        CityArea("Sondika", 43.29, 43.31, -2.95, -2.93),
        CityArea("Derio", 43.28, 43.30, -2.89, -2.87),
        CityArea("Zamudio", 43.27, 43.29, -2.87, -2.85),
        CityArea("Galdakao", 43.22, 43.24, -2.85, -2.83),
        CityArea("Durango", 43.16, 43.18, -2.64, -2.61),
        CityArea("Amorebieta-Etxano", 43.21, 43.23, -2.74, -2.71),
        CityArea("Mungia", 43.35, 43.37, -2.85, -2.82),
        CityArea("Bermeo", 43.41, 43.43, -2.74, -2.71),
        CityArea("Gernika-Lumo", 43.31, 43.33, -2.69, -2.66),
        CityArea("Sestao", 43.30, 43.32, -3.01, -2.99),
        CityArea("Muskiz", 43.34, 43.36, -3.12, -3.09),
        CityArea("Ortuella", 43.31, 43.33, -3.06, -3.03),
        CityArea("Valle de Trápaga", 43.30, 43.32, -3.04, -3.01),
        CityArea("Balmaseda", 43.18, 43.20, -3.22, -3.19),
        CityArea("Zalla", 43.22, 43.24, -3.15, -3.12),
        CityArea("Arrigorriaga", 43.20, 43.22, -2.90, -2.88),
        CityArea("Bakio", 43.40, 43.42, -2.82, -2.79),
        CityArea("Plentzia", 43.39, 43.41, -2.95, -2.92),
        CityArea("Gorliz", 43.40, 43.42, -2.94, -2.91),
        CityArea("Sopelana", 43.37, 43.39, -2.99, -2.97),

        // ─────────────────────────────────────────────────────────────────────
        // PAÍS VASCO - ÁLAVA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Llodio", 43.13, 43.15, -2.97, -2.94),
        CityArea("Amurrio", 43.05, 43.07, -3.00, -2.97),
        CityArea("Laguardia", 42.55, 42.57, -2.59, -2.56),

        // ─────────────────────────────────────────────────────────────────────
        // NAVARRA - ÁREA METROPOLITANA DE PAMPLONA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Burlada", 42.81, 42.83, -1.62, -1.59),
        CityArea("Villava", 42.82, 42.84, -1.64, -1.61),
        CityArea("Huarte", 42.82, 42.84, -1.60, -1.57),
        CityArea("Ansoáin", 42.83, 42.85, -1.67, -1.64),
        CityArea("Barañáin", 42.79, 42.81, -1.68, -1.65),
        CityArea("Zizur Mayor", 42.77, 42.79, -1.70, -1.67),
        CityArea("Berriozar", 42.84, 42.86, -1.68, -1.65),
        CityArea("Orkoien", 42.83, 42.85, -1.70, -1.67),

        // ─────────────────────────────────────────────────────────────────────
        // NAVARRA - OTRAS CIUDADES
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Tudela", 42.05, 42.07, -1.62, -1.59),
        CityArea("Estella-Lizarra", 42.66, 42.68, -2.04, -2.01),
        CityArea("Tafalla", 42.52, 42.54, -1.68, -1.65),
        CityArea("Alsasua", 42.89, 42.91, -2.17, -2.14),
        CityArea("Bera de Bidasoa", 43.27, 43.29, -1.69, -1.66),
        CityArea("Lesaka", 43.25, 43.27, -1.73, -1.70),
        CityArea("Lodosa", 42.42, 42.44, -2.08, -2.05),

        // ─────────────────────────────────────────────────────────────────────
        // COMUNITAT VALENCIANA - PROVÍNCIA DE VALÈNCIA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Quart de Poblet", 39.47, 39.49, -0.46, -0.44),
        CityArea("Aldaia", 39.46, 39.48, -0.46, -0.44),
        CityArea("Alaquàs", 39.45, 39.47, -0.44, -0.42),
        CityArea("Albal", 39.39, 39.41, -0.41, -0.39),
        CityArea("Alcàsser", 39.38, 39.40, -0.40, -0.38),
        CityArea("Alfafar", 39.41, 39.43, -0.38, -0.36),
        CityArea("Benetússer", 39.42, 39.44, -0.39, -0.37),
        CityArea("Sedaví", 39.42, 39.44, -0.37, -0.35),
        CityArea("Massanassa", 39.40, 39.42, -0.40, -0.38),
        CityArea("Catarroja", 39.39, 39.41, -0.42, -0.40),
        CityArea("Picanya", 39.44, 39.46, -0.44, -0.42),
        CityArea("Picassent", 39.35, 39.37, -0.46, -0.44),
        CityArea("L'Eliana", 39.54, 39.56, -0.53, -0.51),
        CityArea("Bétera", 39.58, 39.60, -0.47, -0.45),
        CityArea("Llíria", 39.62, 39.64, -0.60, -0.58),
        CityArea("Riba-roja de Túria", 39.54, 39.56, -0.57, -0.55),
        CityArea("Benaguasil", 39.59, 39.61, -0.56, -0.54),
        CityArea("Puçol", 39.60, 39.62, -0.31, -0.29),
        CityArea("El Puig de Santa Maria", 39.57, 39.59, -0.32, -0.30),
        CityArea("Rafelbunyol", 39.57, 39.59, -0.35, -0.33),
        CityArea("Meliana", 39.52, 39.54, -0.35, -0.33),
        CityArea("Almàssera", 39.52, 39.54, -0.37, -0.35),
        CityArea("Massamagrell", 39.55, 39.57, -0.37, -0.35),
        CityArea("Sueca", 39.19, 39.21, -0.32, -0.30),
        CityArea("Cullera", 39.15, 39.18, -0.24, -0.21),
        CityArea("Alzira", 39.14, 39.16, -0.44, -0.42),
        CityArea("Algemesí", 39.18, 39.20, -0.44, -0.42),
        CityArea("Carlet", 39.22, 39.24, -0.52, -0.50),
        CityArea("Carcaixent", 39.11, 39.13, -0.44, -0.42),
        CityArea("Benifaió", 39.28, 39.30, -0.43, -0.41),
        CityArea("Almussafes", 39.29, 39.31, -0.41, -0.39),
        CityArea("Xàtiva", 38.98, 39.00, -0.53, -0.51),
        CityArea("Ontinyent", 38.81, 38.83, -1.09, -1.07),
        CityArea("Sagunt", 39.67, 39.69, -0.29, -0.27),

        // ─────────────────────────────────────────────────────────────────────
        // COMUNITAT VALENCIANA - MARINA ALTA I BAIXA / COSTA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Oliva", 38.91, 38.93, -0.13, -0.11),
        CityArea("Dénia", 38.83, 38.86, 0.09, 0.12),
        CityArea("Xàbia", 38.78, 38.81, 0.17, 0.20),
        CityArea("Calp", 38.63, 38.65, 0.04, 0.07),
        CityArea("Altea", 38.59, 38.61, -0.06, -0.03),
        CityArea("La Nucía", 38.61, 38.63, -0.11, -0.08),
        CityArea("Alfàs del Pi", 38.58, 38.60, -0.08, -0.05),
        CityArea("Finestrat", 38.56, 38.58, -0.19, -0.16),
        CityArea("Villajoyosa", 38.49, 38.52, -0.24, -0.21),
        CityArea("El Campello", 38.42, 38.44, -0.39, -0.37),

        // ─────────────────────────────────────────────────────────────────────
        // COMUNITAT VALENCIANA - INTERIOR (ALCOIÀ, VINALOPÓ, BAIX SEGURA)
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Alcoi", 38.69, 38.71, -0.48, -0.46),
        CityArea("Cocentaina", 38.74, 38.76, -0.44, -0.42),
        CityArea("Villena", 38.62, 38.64, -1.03, -1.01),
        CityArea("Novelda", 38.37, 38.39, -0.77, -0.75),
        CityArea("Crevillent", 38.24, 38.26, -0.82, -0.80),
        CityArea("Santa Pola", 38.18, 38.20, -0.56, -0.54),
        CityArea("Guardamar del Segura", 38.08, 38.10, -0.66, -0.64),
        CityArea("Orihuela", 38.07, 38.09, -0.94, -0.92),

        // ─────────────────────────────────────────────────────────────────────
        // COMUNITAT VALENCIANA - PROVÍNCIA DE CASTELLÓ
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Nules", 39.85, 39.87, -0.15, -0.13),
        CityArea("La Vall d'Uixó", 39.82, 39.84, -0.23, -0.21),
        CityArea("Burriana", 39.88, 39.90, -0.09, -0.07),
        CityArea("Onda", 39.96, 39.98, -0.27, -0.25),
        CityArea("Segorbe", 39.85, 39.87, -0.49, -0.47),
        CityArea("Peñíscola", 40.35, 40.37, 0.39, 0.42),
        CityArea("Benicarló", 40.41, 40.43, 0.41, 0.44),
        CityArea("Vinaròs", 40.46, 40.48, 0.46, 0.49),
        CityArea("Oropesa del Mar", 40.09, 40.11, 0.13, 0.16),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - PROVÍNCIA DE ALMERÍA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Roquetas de Mar", 36.76, 36.78, -2.62, -2.60),
        CityArea("El Ejido", 36.77, 36.79, -2.82, -2.80),
        CityArea("Adra", 36.74, 36.76, -3.02, -3.00),
        CityArea("La Mojonera", 36.77, 36.79, -2.72, -2.70),
        CityArea("Níjar", 36.96, 36.98, -2.21, -2.19),
        CityArea("Mojácar", 37.13, 37.15, -1.87, -1.85),
        CityArea("Garrucha", 37.18, 37.20, -1.83, -1.81),
        CityArea("Vera", 37.23, 37.25, -1.87, -1.85),
        CityArea("Huércal-Overa", 37.38, 37.40, -1.95, -1.93),
        CityArea("Huércal de Almería", 36.88, 36.90, -2.43, -2.41),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - PROVÍNCIA DE GRANADA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Motril", 36.73, 36.75, -3.54, -3.52),
        CityArea("Salobreña", 36.73, 36.75, -3.59, -3.57),
        CityArea("Almuñécar", 36.73, 36.75, -3.70, -3.68),
        CityArea("Nerja", 36.74, 36.76, -3.88, -3.86),
        CityArea("Guadix", 37.29, 37.31, -3.15, -3.13),
        CityArea("Baza", 37.48, 37.50, -2.78, -2.76),
        CityArea("Loja", 37.16, 37.18, -4.16, -4.14),
        CityArea("Santa Fe", 37.18, 37.20, -3.73, -3.71),
        CityArea("Atarfe", 37.21, 37.23, -3.68, -3.66),
        CityArea("Peligros", 37.22, 37.24, -3.62, -3.60),
        CityArea("Cúllar Vega", 37.12, 37.14, -3.68, -3.66),
        CityArea("Churriana de la Vega", 37.14, 37.16, -3.70, -3.68),
        CityArea("Ogíjares", 37.13, 37.15, -3.62, -3.60),
        CityArea("Huétor Vega", 37.14, 37.16, -3.60, -3.58),
        CityArea("Alhendín", 37.11, 37.13, -3.65, -3.63),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - PROVÍNCIA DE JAÉN
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Andújar", 38.03, 38.05, -4.07, -4.05),
        CityArea("Bailén", 38.08, 38.10, -3.78, -3.76),
        CityArea("Mengíbar", 37.97, 37.99, -3.81, -3.79),
        CityArea("Torredonjimeno", 37.76, 37.78, -3.97, -3.95),
        CityArea("Martos", 37.71, 37.73, -3.98, -3.96),
        CityArea("Alcalá la Real", 37.45, 37.47, -3.94, -3.92),
        CityArea("Linares", 38.08, 38.10, -3.64, -3.62),
        CityArea("Úbeda", 38.00, 38.02, -3.38, -3.36),
        CityArea("Baeza", 37.98, 38.00, -3.48, -3.46),
        CityArea("La Carolina", 38.27, 38.29, -3.62, -3.60),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - PROVÍNCIA DE CÓRDOBA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Adamuz", 38.01, 38.03, -4.53, -4.51),
        CityArea("El Carpio", 37.90, 37.92, -4.49, -4.47),
        CityArea("Villafranca de Córdoba", 37.95, 37.97, -4.54, -4.52),
        CityArea("Montoro", 38.00, 38.02, -4.39, -4.37),
        CityArea("Bujalance", 37.89, 37.91, -4.39, -4.37),
        CityArea("Posadas", 37.79, 37.81, -5.11, -5.09),
        CityArea("Almodóvar del Río", 37.80, 37.82, -5.03, -5.01),
        CityArea("Palma del Río", 37.69, 37.71, -5.29, -5.27),
        CityArea("Lucena", 37.40, 37.42, -4.49, -4.47),
        CityArea("Cabra", 37.47, 37.49, -4.45, -4.43),
        CityArea("Priego de Córdoba", 37.43, 37.45, -4.20, -4.18),
        CityArea("Montilla", 37.58, 37.60, -4.65, -4.63),
        CityArea("Aguilar de la Frontera", 37.51, 37.53, -4.67, -4.65),
        CityArea("Puente Genil", 37.38, 37.40, -4.78, -4.76),
        CityArea("Baena", 37.61, 37.63, -4.33, -4.31),
        CityArea("Pozoblanco", 38.37, 38.39, -4.86, -4.84),
        CityArea("Hinojosa del Duque", 38.49, 38.51, -5.14, -5.12),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - PROVÍNCIA DE HUELVA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Aljaraque", 37.25, 37.27, -6.99, -6.97),
        CityArea("Moguer", 37.27, 37.29, -6.84, -6.82),
        CityArea("Palos de la Frontera", 37.22, 37.24, -6.90, -6.88),
        CityArea("Punta Umbría", 37.16, 37.18, -6.97, -6.95),
        CityArea("Cartaya", 37.27, 37.29, -7.15, -7.13),
        CityArea("Lepe", 37.25, 37.27, -7.21, -7.19),
        CityArea("Isla Cristina", 37.19, 37.21, -7.33, -7.31),
        CityArea("Ayamonte", 37.21, 37.23, -7.42, -7.40),
        CityArea("Almonte", 37.26, 37.28, -6.52, -6.50),
        CityArea("Bollullos Par del Condado", 37.33, 37.35, -6.55, -6.53),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - CAMPO DE GIBRALTAR
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Algeciras", 36.12, 36.14, -5.47, -5.45),
        CityArea("La Línea de la Concepción", 36.15, 36.17, -5.36, -5.34),
        CityArea("San Roque", 36.20, 36.22, -5.42, -5.40),
        CityArea("Los Barrios", 36.17, 36.19, -5.50, -5.48),
        CityArea("Tarifa", 36.00, 36.03, -5.62, -5.59),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - MÁLAGA INTERIOR Y AXARQUÍA
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Antequera", 37.01, 37.03, -4.57, -4.55),
        CityArea("Ronda", 36.74, 36.76, -5.18, -5.16),
        CityArea("Alhaurín el Grande", 36.64, 36.66, -4.70, -4.68),
        CityArea("Alhaurín de la Torre", 36.65, 36.67, -4.58, -4.56),
        CityArea("Coín", 36.65, 36.67, -4.76, -4.74),
        CityArea("Cártama", 36.72, 36.74, -4.66, -4.64),
        CityArea("Manilva", 36.37, 36.39, -5.25, -5.23),
        CityArea("Frigiliana", 36.77, 36.79, -3.91, -3.89),
        CityArea("Algarrobo", 36.75, 36.77, -4.05, -4.03),

        // ─────────────────────────────────────────────────────────────────────
        // ANDALUCÍA - SEVILLA INTERIOR (ALJARAFE, CAMPIÑA, SIERRA)
        // ─────────────────────────────────────────────────────────────────────
        CityArea("Castilleja de la Cuesta", 37.37, 37.39, -6.05, -6.03),
        CityArea("Gines", 37.38, 37.40, -6.08, -6.06),
        CityArea("Espartinas", 37.38, 37.40, -6.12, -6.10),
        CityArea("Umbrete", 37.37, 37.39, -6.15, -6.13),
        CityArea("Sanlúcar la Mayor", 37.38, 37.40, -6.22, -6.20),
        CityArea("Olivares", 37.40, 37.42, -6.17, -6.15),
        CityArea("Carmona", 37.46, 37.48, -5.65, -5.63),
        CityArea("Écija", 37.54, 37.56, -5.09, -5.07),
        CityArea("Osuna", 37.23, 37.25, -5.11, -5.09),
        CityArea("Estepa", 37.29, 37.31, -4.88, -4.86),
        CityArea("Marchena", 37.32, 37.34, -5.41, -5.39),
        CityArea("Morón de la Frontera", 37.11, 37.13, -5.47, -5.45),
        CityArea("Utrera", 37.18, 37.20, -5.79, -5.77),
        CityArea("Lebrija", 36.92, 36.94, -6.08, -6.06),
        CityArea("Lora del Río", 37.64, 37.66, -5.53, -5.51),
        CityArea("Constantina", 37.86, 37.88, -5.62, -5.60),

        // ─────────────────────────────────────────────────────────────────────
        // CIUDADES IMPORTANTES DE EUROPA
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // CIUDADES IMPORTANTES DE AMÉRICA
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // CIUDADES IMPORTANTES DE ASIA
        // ─────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Detección de ciudad por coordenadas
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene el nombre de la ciudad basándose en las coordenadas.
     * Cuando varias ciudades coinciden, devuelve la de bounding box más pequeño
     * (más específica) gracias a minByOrNull sobre el área del rectángulo.
     */
    fun getCityName(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        val padding = 0.005 // ~500 metros de margen

        val matchingCities = cityAreas.filter { city ->
            latitude  >= (city.latMin - padding) && latitude  <= (city.latMax + padding) &&
                    longitude >= (city.lonMin - padding) && longitude <= (city.lonMax + padding)
        }
        if (matchingCities.isEmpty()) return null
        return matchingCities.minByOrNull { city ->
            (city.latMax - city.latMin) * (city.lonMax - city.lonMin)
        }?.name
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generación de título de ruta
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera un título para la ruta basándose en las ciudades detectadas.
     *
     * Lógica:
     *  1. Notas manuales           → usarlas siempre
     *  2. Sin puntos               → "Mi ruta"
     *  3. Ruta circular, 1 ciudad  → "Ruta circular por Barcelona"
     *  4. Ruta circular, N ciudad  → "Ruta circular en patinete" / "Ruta circular"
     *  5. Ruta lineal A → B        → "Barcelona → Badalona"
     *  6. Ruta lineal misma ciudad → "Mi ruta por Barcelona"
     *  7. Sin ciudad detectada     → "Mi ruta en patinete" / "Mi ruta"
     */
    fun getRouteTitleText(route: Route, vehicleType: VehicleType? = null): String {
        if (route.notes.isNotBlank()) return route.notes
        if (route.points.isEmpty()) return "Mi ruta"

        val vehicleSuffix = vehicleType?.let { " en ${it.displayName.lowercase()}" } ?: ""

        return if (isCircularRoute(route)) {
            buildCircularTitle(route, vehicleType)
        } else {
            buildLinearTitle(route, vehicleSuffix)
        }
    }

    /**
     * Determina si una ruta es circular comparando la distancia directa
     * entre inicio y fin con la distancia total recorrida.
     * Umbral: 8% de la distancia total, mínimo 100m para rutas muy cortas.
     */
    private fun isCircularRoute(route: Route): Boolean {
        if (route.points.size < 3) return false

        val first = route.points.first()
        val last  = route.points.last()
        val directDistance = haversineMeters(
            first.latitude, first.longitude,
            last.latitude,  last.longitude
        )

        val totalMeters = route.totalDistance * 1000
        val threshold = if (totalMeters < 500) 100.0 else totalMeters * 0.08

        return directDistance < threshold
    }

    /**
     * Fórmula de Haversine: distancia en metros entre dos coordenadas GPS.
     */
    private fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).pow(2)
        return R * 2 * Math.asin(Math.sqrt(a))
    }

    /**
     * Extrae ciudades únicas muestreando puntos distribuidos a lo largo
     * de toda la ruta. Elimina duplicados consecutivos.
     */
    private fun getSampledCities(route: Route): List<String> {
        val points = route.points
        if (points.isEmpty()) return emptyList()

        val indices = linkedSetOf(
            0,
            points.size / 4,
            points.size / 2,
            points.size * 3 / 4,
            points.size - 1
        )

        if (points.size > 50) {
            indices += listOf(
                points.size / 8,
                points.size * 3 / 8,
                points.size * 5 / 8,
                points.size * 7 / 8
            )
        }

        return indices.sorted()
            .mapNotNull { i -> getCityName(points[i].latitude, points[i].longitude) }
            .fold(mutableListOf()) { acc, city ->
                if (acc.lastOrNull() != city) acc.add(city)
                acc
            }
    }

    private fun buildCircularTitle(route: Route, vehicleType: VehicleType?): String {
        val cities = getSampledCities(route)
        val vehicleSuffix = vehicleType?.let { " en ${it.displayName.lowercase()}" } ?: ""

        return when {
            cities.isEmpty() ->
                "Ruta circular$vehicleSuffix"
            cities.size == 1 ->
                "Ruta circular por ${cities.first()}"
            else ->
                "Ruta circular$vehicleSuffix"
        }
    }

    private fun buildLinearTitle(route: Route, vehicleSuffix: String): String {
        val startCity = getCityName(
            route.points.first().latitude,
            route.points.first().longitude
        )
        val endCity = getCityName(
            route.points.last().latitude,
            route.points.last().longitude
        )

        return when {
            startCity != null && endCity != null && startCity != endCity ->
                "$startCity → $endCity"
            startCity != null ->
                "Mi ruta por $startCity"
            else ->
                "Mi ruta$vehicleSuffix"
        }
    }
}