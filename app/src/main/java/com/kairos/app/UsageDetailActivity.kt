package com.kairos.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.theme.KairosTheme
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.compose.material3.ExperimentalMaterial3Api

class UsageDetailActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usageList = getAppUsageBreakdown()
        val totalTime = calculateTotalTime(usageList)

        setContent {
            KairosTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Detalle de Uso") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                    ) {
                        // Tarjeta de Resumen Total
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Tiempo Total Hoy", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = totalTime,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Desglose por App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Lista de Apps
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(usageList) { app ->
                                AppUsageCard(app)
                            }
                        }
                    }
                }
            }
        }
    }

    // Datos para la UI
    data class AppUsageInfo(val name: String, val timeMillis: Long, val timeText: String)

    private fun getAppUsageBreakdown(): List<AppUsageInfo> {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val queryUsageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        // Mapeo manual de nombres para la demo (Más rápido que buscar íconos)
        val appNames = mapOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.google.android.youtube" to "YouTube",
            "com.twitter.android" to "X (Twitter)",
            "com.snapchat.android" to "Snapchat",
            "com.whatsapp" to "WhatsApp" // Agregué WhatsApp por si acaso
        )

        val result = ArrayList<AppUsageInfo>()

        for (app in queryUsageStats) {
            if (appNames.containsKey(app.packageName) && app.totalTimeInForeground > 0) {
                val name = appNames[app.packageName] ?: "App"
                val millis = app.totalTimeInForeground
                val hours = TimeUnit.MILLISECONDS.toHours(millis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
                val text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"

                result.add(AppUsageInfo(name, millis, text))
            }
        }
        // Ordenar de mayor a menor uso
        return result.sortedByDescending { it.timeMillis }
    }

    private fun calculateTotalTime(list: List<AppUsageInfo>): String {
        var totalMillis = 0L
        list.forEach { totalMillis += it.timeMillis }
        val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }
}

@Composable
fun AppUsageCard(app: UsageDetailActivity.AppUsageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono genérico
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.name.take(1), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text(
                app.timeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}