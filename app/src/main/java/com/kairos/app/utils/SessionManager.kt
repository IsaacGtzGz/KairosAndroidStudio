package com.kairos.app.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kairos_prefs", Context.MODE_PRIVATE)

    // --- DATOS DE USUARIO (NUEVO) ---
    fun saveUserId(id: Int) {
        prefs.edit().putInt("user_id", id).apply()
    }

    fun fetchUserId(): Int {
        // Retorna 1 por defecto si no hay nada guardado (para tus pruebas actuales)
        // Cuando arregles el Login, esto leerá el ID real.
        return prefs.getInt("user_id", 1)
    }

    // --- AUTENTICACIÓN ---
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
        return prefs.getInt("steps_baseline", -1)
    }

    fun saveStepsDate(date: String) {
        prefs.edit().putString("steps_date", date).apply()
    }

    fun fetchStepsDate(): String? {
        return prefs.getString("steps_date", null)
    }

    // --- AJUSTES DE BIENESTAR ---
    fun saveActiveDays(days: Set<String>) {
        prefs.edit().putStringSet("active_days", days).apply()
    }

    fun fetchActiveDays(): Set<String> {
        return prefs.getStringSet("active_days", emptySet()) ?: emptySet()
    }

    fun saveIntensity(level: Int) {
        prefs.edit().putInt("notification_intensity", level).apply()
    }

    fun fetchIntensity(): Int {
        return prefs.getInt("notification_intensity", 1)
    }

    fun saveInterests(interests: Set<String>) {
        prefs.edit().putStringSet("user_interests", interests).apply()
    }

    fun fetchInterests(): Set<String> {
        return prefs.getStringSet("user_interests", emptySet()) ?: emptySet()
    }


    // --- PERFIL DE USUARIO ---
    fun saveUserName(name: String) { prefs.edit().putString("user_name", name).apply() }
    fun fetchUserName(): String? { return prefs.getString("user_name", "Aventurero") }

    fun saveUserEmail(email: String) { prefs.edit().putString("user_email", email).apply() }
    fun fetchUserEmail(): String? { return prefs.getString("user_email", "usuario@kairos.com") }

    fun saveUserProfilePic(url: String) { prefs.edit().putString("user_pic", url).apply() }
    fun fetchUserProfilePic(): String? { return prefs.getString("user_pic", null) }
}