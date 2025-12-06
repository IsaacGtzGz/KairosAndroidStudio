package com.kairos.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.LocationUtils
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.LaunchedEffect

class DetalleLugarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recibimos los datos del Intent
        val nombre = intent.getStringExtra("nombre") ?: "Lugar"
        val descripcion = intent.getStringExtra("descripcion") ?: "Sin descripción"
        val direccion = intent.getStringExtra("direccion") ?: "Ubicación desconocida"
        val horario = intent.getStringExtra("horario") ?: "Horario no disponible"
        val imagenUrl = intent.getStringExtra("imagen")
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)
        val idLugar = intent.getIntExtra("idLugar", 0)
        val puntosOtorgados = intent.getIntExtra("puntosOtorgados", 10)

        setContent {
            KairosTheme {
                DetalleScreen(
                    nombre = nombre,
                    descripcion = descripcion,
                    direccion = direccion,
                    horario = horario,
                    imagenUrl = imagenUrl,
                    idLugar = idLugar,
                    puntosOtorgados = puntosOtorgados,
                    lat = lat,
                    lng = lng,
                    context = this,
                    onBackClick = { finish() },
                    onNavigateClick = {
                        // Abrir Google Maps
                        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        startActivity(mapIntent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    nombre: String,
    descripcion: String,
    direccion: String,
    horario: String,
    imagenUrl: String?,
    idLugar: Int,
    puntosOtorgados: Int,
    lat: Double,
    lng: Double,
    context: android.content.Context,
    onBackClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    val sessionManager = com.kairos.app.utils.SessionManager(context)
    val userId = sessionManager.fetchUserId()
    var showCheckInDialog by remember { mutableStateOf(false) }
    var isCheckingIn by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
    }

    Scaffold(
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (userId != null && idLugar > 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                isCheckingIn = true
                                try {
                                    val request = com.kairos.app.models.ReclamarPuntosRequest(
                                        idUsuario = userId,
                                        idLugar = idLugar,
                                        latitudUsuario = lat,
                                        longitudUsuario = lng
                                    )
                                    val response = com.kairos.app.network.RetrofitClient.instance.reclamarPuntos(request)
                                    if (response.isSuccessful && response.body() != null) {
                                        val resultado = response.body()!!
                                        val puntosActuales = sessionManager.fetchUserPoints() ?: 0
                                        sessionManager.saveUserPoints(puntosActuales + resultado.puntosGanados)
                                        showCheckInDialog = true
                                    } else {
                                        android.widget.Toast.makeText(context, AppConstants.Messages.ERROR_CLAIM_POINTS, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, AppConstants.Messages.CONNECTION_ERROR, android.widget.Toast.LENGTH_SHORT).show()
                                } finally {
                                    isCheckingIn = false
                                }
                            }
                        },
                        icon = { 
                            if (isCheckingIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.CheckCircle, "Check-In")
                            }
                        },
                        text = { Text("Check-In (+$puntosOtorgados pts)") },
                        containerColor = AppConstants.Colors.DarkGreen
                    )
                }
                
                ExtendedFloatingActionButton(
                    onClick = onNavigateClick,
                    icon = { Icon(Icons.Default.Directions, "Cómo llegar") },
                    text = { Text("Cómo llegar") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp) // Espacio para el botón flotante
        ) {
            // 1. Imagen de Cabecera (Header)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                if (imagenUrl != null) {
                    AsyncImage(
                        model = imagenUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder si no hay imagen
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }

                // Gradiente para que se vea el botón de atrás y el texto
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                                startY = 0f,
                                endY = 300f
                            )
                        )
                )

                // Botón de Regreso
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(top = 32.dp, start = 8.dp) // Ajuste para barra de estado
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Regresar",
                        tint = Color.White
                    )
                }
            }

            // 2. Contenido
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Badges / Categoría (Simulado por ahora)
                SuggestionChip(
                    onClick = { },
                    label = { Text("Punto de Interés") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Información con Iconos
                InfoRow(icon = Icons.Default.LocationOn, text = direccion)
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(icon = Icons.Default.Schedule, text = horario)

                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(24.dp))

                // Descripción
                Text(
                    text = "Acerca de este lugar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Diálogo de éxito
        if (showCheckInDialog) {
            AlertDialog(
                onDismissRequest = { showCheckInDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Stars,
                        null,
                        tint = AppConstants.Colors.DarkGreen,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = { Text("¡Check-In Exitoso!") },
                text = { Text("Has ganado $puntosOtorgados puntos por visitar $nombre") },
                confirmButton = {
                    Button(onClick = { showCheckInDialog = false }) {
                        Text("¡Genial!")
                    }
                }
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}