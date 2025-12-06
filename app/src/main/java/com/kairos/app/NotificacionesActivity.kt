package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.components.EmptyState
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Notificacion
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.launch

class NotificacionesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                val sessionManager = SessionManager(this)
                val userId = sessionManager.fetchUserId()
                var notificaciones by remember { mutableStateOf<List<Notificacion>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(userId) {
                    if (userId != null) {
                        try {
                            val response = RetrofitClient.instance.getNotificaciones(userId)
                            if (response.isSuccessful && response.body() != null) {
                                notificaciones = response.body()!!.sortedByDescending { it.fechaEnvio }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@NotificacionesActivity, AppConstants.Messages.ERROR_NETWORK, Toast.LENGTH_SHORT).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Notificaciones") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            },
                            actions = {
                                if (notificaciones.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                notificaciones.forEach { notif ->
                                                    try {
                                                        RetrofitClient.instance.deleteNotificacion(notif.idNotificacion)
                                                    } catch (e: Exception) {
                                                        // Ignorar errores individuales
                                                    }
                                                }
                                                notificaciones = emptyList()
                                                Toast.makeText(
                                                    this@NotificacionesActivity,
                                                    AppConstants.Messages.NOTIFICATIONS_CLEARED,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, "Borrar todas")
                                    }
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (isLoading) {
                            LoadingState(message = "Cargando notificaciones...")
                        } else {
                            if (notificaciones.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.NotificationsNone,
                                    title = "No tienes notificaciones",
                                    subtitle = "Aquí aparecerán tus alertas y avisos"
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(notificaciones) { notif ->
                                        NotificacionCard(notif) {
                                            scope.launch {
                                                try {
                                                    RetrofitClient.instance.deleteNotificacion(notif.idNotificacion)
                                                    notificaciones = notificaciones.filter { it.idNotificacion != notif.idNotificacion }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        this@NotificacionesActivity,
                                                        AppConstants.Messages.NOTIFICATION_DELETED,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionCard(notif: Notificacion, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = when {
                    notif.titulo?.contains("promo", ignoreCase = true) == true -> AppConstants.Colors.Gold
                    notif.titulo?.contains("punto", ignoreCase = true) == true -> AppConstants.Colors.DarkGreen
                    else -> AppConstants.Colors.PrimaryGreen
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            notif.titulo?.contains("promo", ignoreCase = true) == true -> Icons.Default.CardGiftcard
                            notif.titulo?.contains("punto", ignoreCase = true) == true -> Icons.Default.Stars
                            else -> Icons.Default.Notifications
                        },
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notif.titulo ?: "Notificación",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notif.mensaje ?: "",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notif.fechaEnvio ?: "",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, "Eliminar", tint = Color.Gray)
            }
        }
    }
}
