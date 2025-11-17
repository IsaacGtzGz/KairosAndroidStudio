package com.kairos.app.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kairos_prefs", Context.MODE_PRIVATE)

    fun saveAuthToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun saveEmergencyContact(phone: String) {
        prefs.edit().putString("emergency_contact", phone).apply()
    }

    fun fetchEmergencyContact(): String? {
        return prefs.getString("emergency_contact", null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // --- LÓGICA DE PASOS DIARIOS ---
    fun saveStepsBaseline(steps: Int) {
        prefs.edit().putInt("steps_baseline", steps).apply()
    }

    fun fetchStepsBaseline(): Int {
        return prefs.getInt("steps_baseline", -1) // -1 significa que no hay dato guardado
    }

    fun saveStepsDate(date: String) {
        prefs.edit().putString("steps_date", date).apply()
    }

    fun fetchStepsDate(): String? {
        return prefs.getString("steps_date", null)
    }
}
