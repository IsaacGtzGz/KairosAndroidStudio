package com.kairos.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kairos.app.HomeActivity
import com.kairos.app.R
import com.kairos.app.network.RetrofitClient
import com.kairos.app.utils.SessionManager

/**
 * Worker que se ejecuta periódicamente (ej. diariamente a las 8 PM)
 * para enviar una notificación con el resumen del día
 */
class DailyInsightWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionManager = SessionManager(applicationContext)
            val userId = sessionManager.fetchUserId()
            
            // ✅ VERIFICAR PREFERENCIAS DEL USUARIO
            val activeDays = sessionManager.fetchActiveDays()
            val intensidad = sessionManager.fetchIntensity()
            
            // Verificar si hoy es un día activo
            val dayAbbreviations = mapOf(
                1 to "Dom", 2 to "Lun", 3 to "Mar", 4 to "Mié",
                5 to "Jue", 6 to "Vie", 7 to "Sáb"
            )
            val todayAbbr = dayAbbreviations[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)]
            
            if (activeDays.isNotEmpty() && !activeDays.contains(todayAbbr)) {
                // Usuario no quiere notificaciones hoy
                return Result.success()
            }

            // Obtener insight del servidor
            val response = RetrofitClient.instance.getInsight(userId)
            
            val mensaje = if (response.isSuccessful) {
                val insight = response.body()
                when (intensidad) {
                    1 -> "Pasos: ${insight?.pasosHoy ?: 0}" // Baja: solo datos
                    2 -> "Pasos: ${insight?.pasosHoy ?: 0} | ${insight?.mensaje?.take(50) ?: ""}..." // Media: resumen
                    else -> insight?.mensaje ?: "Revisa tu progreso" // Alta: mensaje completo
                }
            } else {
                "Revisa tu progreso en Kairos"
            }

            // Enviar notificación solo si intensidad > 0
            if (intensidad > 0) {
                enviarNotificacion(mensaje)
            }
            
            Result.success()
        } catch (e: Exception) {
            // Si falla, solo enviar si intensidad es alta
            val sessionManager = SessionManager(applicationContext)
            if (sessionManager.fetchIntensity() >= 2) {
                enviarNotificacion("¡Revisa tu progreso en Kairos!")
            }
            Result.success()
        }
    }

    private fun enviarNotificacion(mensaje: String) {
        val channelId = "kairos_daily_insights"
        val notificationId = 1001

        // Crear canal de notificación (solo para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Insights Diarios",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones con tu resumen diario"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir la app al tocar la notificación
        val intent = Intent(applicationContext, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Construir la notificación
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Cambiar por tu icono
            .setContentTitle("Tu Coach Kairos")
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Enviar la notificación (solo si hay permiso en Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            }
        } else {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        }
    }
}
