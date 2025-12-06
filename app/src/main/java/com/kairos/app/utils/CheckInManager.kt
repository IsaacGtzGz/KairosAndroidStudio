package com.kairos.app.utils

import android.content.Context
import android.content.SharedPreferences

class CheckInManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "kairos_checkins",
        Context.MODE_PRIVATE
    )
    
    /**
     * Guarda que el usuario hizo check-in en un lugar específico
     */
    fun marcarCheckInRealizado(idUsuario: Int, idLugar: Int) {
        val key = getKey(idUsuario, idLugar)
        prefs.edit().putBoolean(key, true).apply()
    }
    
    /**
     * Verifica si el usuario ya hizo check-in en un lugar
     */
    fun yaHizoCheckIn(idUsuario: Int, idLugar: Int): Boolean {
        val key = getKey(idUsuario, idLugar)
        return prefs.getBoolean(key, false)
    }
    
    /**
     * Limpia todos los check-ins de un usuario (útil al cerrar sesión)
     */
    fun limpiarCheckInsUsuario(idUsuario: Int) {
        val editor = prefs.edit()
        val allEntries = prefs.all
        
        allEntries.keys.forEach { key ->
            if (key.startsWith("user_${idUsuario}_")) {
                editor.remove(key)
            }
        }
        
        editor.apply()
    }
    
    /**
     * Genera la clave única para cada combinación usuario-lugar
     */
    private fun getKey(idUsuario: Int, idLugar: Int): String {
        return "user_${idUsuario}_lugar_${idLugar}"
    }
}
