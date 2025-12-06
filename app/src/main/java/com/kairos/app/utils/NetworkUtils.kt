package com.kairos.app.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado de carga genérico para pantallas con datos de API
 */
sealed class LoadingState<out T> {
    object Idle : LoadingState<Nothing>()
    object Loading : LoadingState<Nothing>()
    data class Success<T>(val data: T) : LoadingState<T>()
    data class Error(val message: String) : LoadingState<Nothing>()
}

/**
 * Helper para manejar errores de red de forma consistente
 */
fun Context.showNetworkError(message: String = "Error de conexión") {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Extensión para ejecutar llamadas de API con manejo de errores
 */
suspend fun <T> apiCall(
    context: Context,
    call: suspend () -> T
): T? {
    return try {
        call()
    } catch (e: Exception) {
        context.showNetworkError()
        null
    }
}

/**
 * Cache simple en memoria para datos frecuentes
 */
object DataCache {
    private val cache = mutableMapOf<String, Pair<Long, Any>>()
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutos

    fun <T> get(key: String): T? {
        val cached = cache[key] ?: return null
        if (System.currentTimeMillis() - cached.first > CACHE_DURATION) {
            cache.remove(key)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return cached.second as? T
    }

    fun put(key: String, value: Any) {
        cache[key] = System.currentTimeMillis() to value
    }

    fun clear() {
        cache.clear()
    }
}
