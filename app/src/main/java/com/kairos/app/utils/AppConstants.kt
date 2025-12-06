package com.kairos.app.utils

import androidx.compose.ui.graphics.Color

/**
 * Constantes de la aplicación Kairos
 */
object AppConstants {
    // Colores de marca
    object Colors {
        val PrimaryGreen = Color(0xFF90EE90)
        val DarkGreen = Color(0xFF4A7C59)
        val BlueGreen = Color(0xFF5F9EA0)
        val Gold = Color(0xFFFFD700)
    }
    
    // Duración de animaciones
    object Animation {
        const val FAST = 300
        const val NORMAL = 500
        const val SLOW = 1000
    }
    
    // Cache
    object Cache {
        const val LUGARES_KEY = "lugares_cache"
        const val CATEGORIAS_KEY = "categorias_cache"
        const val PROMOCIONES_KEY = "promociones_cache"
        const val RUTAS_KEY = "rutas_cache"
        const val DURATION_MS = 5 * 60 * 1000L // 5 minutos
    }
    
    // Puntos
    object Points {
        const val MIN_CHECK_IN_DISTANCE = 100.0 // metros
        const val DEFAULT_POINTS = 10
        const val BONUS_POINTS_SPONSORED = 20
    }
    
    // Mensajes
    object Messages {
        const val LOADING = "Cargando..."
        const val ERROR_NETWORK = "Error de conexión"
        const val ERROR_GENERIC = "Ocurrió un error"
        const val SUCCESS_CHECK_IN = "¡Check-In exitoso!"
        const val SUCCESS_REDEEM = "¡Promoción canjeada!"
        const val NO_DATA = "No hay datos disponibles"
        
        // Permisos
        const val PERMISSION_DENIED = "Permiso denegado"
        const val PERMISSION_LOCATION_DENIED = "Permiso de ubicación denegado, usando ubicación por defecto"
        const val PERMISSION_GALLERY_DENIED = "Permiso de galería denegado"
        const val PERMISSION_NEEDED_SOS = "Se necesitan permisos para función SOS"
        const val PERMISSION_CONTACTS_DENIED = "Permiso de contactos denegado"
        const val PERMISSION_ACTIVITY_DENIED = "Permiso de actividad física denegado"
        const val PERMISSION_CALL_ERROR = "Error al obtener permisos para llamadas"
        
        // GPS
        const val GPS_DISABLED_TITLE = "GPS Desactivado"
        const val GPS_DISABLED_MESSAGE = "Para ver tu ubicación en el mapa, necesitas activar el GPS. ¿Deseas activarlo ahora?"
        const val GPS_ENABLE = "Activar GPS"
        const val GPS_CANCEL = "Ahora no"
        
        // Registro y login
        const val REGISTER_SUCCESS = "Cuenta creada con éxito"
        const val REGISTER_ERROR = "Error en el registro"
        const val LOGIN_ERROR = "Credenciales incorrectas"
        const val LOGIN_ERROR_CONNECTION = "Error de conexión"
        
        // Perfil
        const val PROFILE_UPDATED = "Perfil actualizado"
        const val PROFILE_SAVED_OFFLINE = "Guardado localmente (sin conexión)"
        const val PROFILE_ERROR_SERVER = "Error al guardar en servidor"
        const val IMAGE_ERROR = "Error al procesar imagen"
        
        // Notificaciones y Contacto
        const val NOTIFICATION_DELETED = "Notificación eliminada"
        const val NOTIFICATIONS_CLEARED = "Todas las notificaciones eliminadas"
        const val CONTACT_SAVED = "Contacto guardado"
        const val MESSAGE_SENT = "Mensaje enviado con éxito"
        const val MESSAGE_ERROR = "Error al enviar mensaje"
        const val ALERT_SENT = "Alerta SOS enviada"
        
        // Rutas
        const val ROUTE_NAME_REQUIRED = "Ingresa un nombre para la ruta"
        const val ROUTE_CREATED = "Ruta creada con éxito"
        const val ROUTE_ERROR = "Error al crear ruta"
        
        // General
        const val SETTINGS_ERROR = "No se pudo abrir configuración"
        const val PREFERENCES_SAVED = "Preferencias guardadas"
        const val FIELD_REQUIRED = "Este campo es obligatorio"
        const val EXPLORING_MAP = "Explorando en el mapa"
        
        // Mensajes anteriores que aún se usan
        const val NO_INTERNET = "Sin conexión a internet"
        const val CONNECTION_ERROR = "Error de conexión"
        const val ERROR_LOADING = "Error al cargar datos"
        const val ERROR_CLAIM_POINTS = "Error al reclamar puntos"
    }
    
    // Endpoints (relativos al BASE_URL)
    object Endpoints {
        const val LUGARES = "Lugares"
        const val PROMOCIONES = "Promociones"
        const val RUTAS = "Rutas"
        const val NOTIFICACIONES = "Notificaciones"
        const val FAQ = "PreguntasFrecuentes"
        const val CATEGORIAS = "Categorias"
        const val INTERESES = "Intereses"
    }
}

/**
 * Extensión para formatear puntos
 */
fun Int.formatPoints(): String = when {
    this >= 1000 -> "${this / 1000}k"
    else -> this.toString()
}

/**
 * Extensión para validar email
 */
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

/**
 * Extensión para capitalizar primera letra
 */
fun String.capitalizeFirst(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
