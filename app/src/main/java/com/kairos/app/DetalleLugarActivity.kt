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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

        setContent {
            KairosTheme {
                DetalleScreen(
                    nombre = nombre,
                    descripcion = descripcion,
                    direccion = direccion,
                    horario = horario,
                    imagenUrl = imagenUrl,
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
    onBackClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateClick,
                icon = { Icon(Icons.Default.Directions, "Cómo llegar") },
                text = { Text("Cómo llegar") },
                containerColor = MaterialTheme.colorScheme.primary
            )
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