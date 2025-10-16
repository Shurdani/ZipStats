package com.example.patineta.model

enum class VehicleType(val displayName: String, val emoji: String) {
    PATINETE("Patinete", "🛴"),
    BICICLETA("Bicicleta", "🚲"),
    E_BIKE("E-Bike", "🚴"),
    MONOCICLO("Monociclo", "🛸");
    
    companion object {
        fun fromString(value: String): VehicleType {
            return values().find { it.name == value } ?: PATINETE
        }
    }
}

