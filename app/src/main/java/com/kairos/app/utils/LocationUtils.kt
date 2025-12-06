package com.kairos.app.utils

import android.location.Location
import kotlin.math.*

/**
 * Utilidades para cálculos de geolocalización
 */
object LocationUtils {
    
    /**
     * Calcula la distancia entre dos puntos en metros usando la fórmula de Haversine
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // Radio de la Tierra en metros
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Verifica si el usuario está cerca de un lugar (100m por defecto)
     */
    fun isNearLocation(
        userLat: Double,
        userLon: Double,
        placeLat: Double,
        placeLon: Double,
        maxDistanceMeters: Double = AppConstants.Points.MIN_CHECK_IN_DISTANCE
    ): Boolean {
        val distance = calculateDistance(userLat, userLon, placeLat, placeLon)
        return distance <= maxDistanceMeters
    }
    
    /**
     * Formatea la distancia para mostrar al usuario
     */
    fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            meters < 10000 -> "${String.format("%.1f", meters / 1000)} km"
            else -> "${(meters / 1000).toInt()} km"
        }
    }
    
    /**
     * Obtiene la dirección cardinal aproximada
     */
    fun getCardinalDirection(bearing: Float): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "Norte"
            bearing < 67.5 -> "Noreste"
            bearing < 112.5 -> "Este"
            bearing < 157.5 -> "Sureste"
            bearing < 202.5 -> "Sur"
            bearing < 247.5 -> "Suroeste"
            bearing < 292.5 -> "Oeste"
            bearing < 337.5 -> "Noroeste"
            else -> "Norte"
        }
    }
    
    /**
     * Calcula el bearing (orientación) entre dos puntos
     */
    fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing.toFloat()
    }
}
