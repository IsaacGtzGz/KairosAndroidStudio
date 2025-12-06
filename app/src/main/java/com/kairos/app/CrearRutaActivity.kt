package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Lugar
import com.kairos.app.models.Ruta
import com.kairos.app.models.RutaLugar
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CrearRutaActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                var lugares by remember { mutableStateOf<List<Lugar>>(emptyList()) }
                var lugaresSeleccionados by remember { mutableStateOf<List<Lugar>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var showSaveDialog by remember { mutableStateOf(false) }
                var nombreRuta by remember { mutableStateOf("") }
                var descripcionRuta by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    try {
                        val response = RetrofitClient.instance.getLugares()
                        if (response.isSuccessful && response.body() != null) {
                            lugares = response.body()!!
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@CrearRutaActivity,
                            "Error al cargar lugares",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isLoading = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { 
                                Column {
                                    Text("Crear Ruta Turística")
                                    Text(
                                        "${lugaresSeleccionados.size} lugares seleccionados",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            },
                            actions = {
                                if (lugaresSeleccionados.isNotEmpty()) {
                                    IconButton(onClick = { showSaveDialog = true }) {
                                        Icon(
                                            Icons.Default.Save,
                                            "Guardar Ruta",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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
                            LoadingState(message = "Cargando lugares...")
                        } else {
                            CrearRutaMapScreen(
                                lugares = lugares,
                                lugaresSeleccionados = lugaresSeleccionados,
                                onLugarClick = { lugar ->
                                    lugaresSeleccionados = if (lugaresSeleccionados.contains(lugar)) {
                                        lugaresSeleccionados - lugar
                                    } else {
                                        lugaresSeleccionados + lugar
                                    }
                                },
                                onRemoveLugar = { lugar ->
                                    lugaresSeleccionados = lugaresSeleccionados - lugar
                                }
                            )
                        }

                        // Diálogo para guardar la ruta
                        if (showSaveDialog) {
                            AlertDialog(
                                onDismissRequest = { showSaveDialog = false },
                                title = { Text("Guardar Ruta") },
                                text = {
                                    Column {
                                        OutlinedTextField(
                                            value = nombreRuta,
                                            onValueChange = { nombreRuta = it },
                                            label = { Text("Nombre de la ruta") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = descripcionRuta,
                                            onValueChange = { descripcionRuta = it },
                                            label = { Text("Descripción (opcional)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            maxLines = 5
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "La ruta tendrá ${lugaresSeleccionados.size} lugares",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (nombreRuta.isBlank()) {
                                                Toast.makeText(
                                                    this@CrearRutaActivity,
                                                    "El nombre es obligatorio",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }

                                            scope.launch {
                                                try {
                                                    // Crear la ruta
                                                    val primeraLugar = lugaresSeleccionados.first()
                                                    val ultimaLugar = lugaresSeleccionados.last()
                                                    
                                                    val nuevaRuta = Ruta(
                                                        idRuta = 0,
                                                        idUsuario = null,
                                                        nombre = nombreRuta,
                                                        descripcion = descripcionRuta.ifBlank { null },
                                                        latitudInicio = primeraLugar.latitud,
                                                        longitudInicio = primeraLugar.longitud,
                                                        latitudFin = ultimaLugar.latitud,
                                                        longitudFin = ultimaLugar.longitud,
                                                        idLugarInicio = primeraLugar.idLugar,
                                                        idLugarFin = ultimaLugar.idLugar,
                                                        fechaCreacion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                                        rutasLugares = lugaresSeleccionados.mapIndexed { index, lugar ->
                                                            RutaLugar(
                                                                idRuta = 0,
                                                                idLugar = lugar.idLugar,
                                                                orden = index + 1
                                                            )
                                                        }
                                                    )

                                                    val response = RetrofitClient.instance.createRuta(nuevaRuta)
                                                    if (response.isSuccessful) {
                                                        Toast.makeText(
                                                            this@CrearRutaActivity,
                                                            "✓ Ruta creada exitosamente",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        finish()
                                                    } else {
                                                        Toast.makeText(
                                                            this@CrearRutaActivity,
                                                            "Error al guardar la ruta",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    Toast.makeText(
                                                        this@CrearRutaActivity,
                                                        "Error de conexión: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        enabled = nombreRuta.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Check, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Guardar")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSaveDialog = false }) {
                                        Text("Cancelar")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrearRutaMapScreen(
    lugares: List<Lugar>,
    lugaresSeleccionados: List<Lugar>,
    onLugarClick: (Lugar) -> Unit,
    onRemoveLugar: (Lugar) -> Unit
) {
    val center = if (lugares.isNotEmpty()) {
        LatLng(lugares.first().latitud, lugares.first().longitud)
    } else {
        LatLng(21.1290, -101.6700)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 13f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = true
            )
        ) {
            // Mostrar todos los lugares disponibles
            lugares.forEach { lugar ->
                val isSelected = lugaresSeleccionados.contains(lugar)
                val color = if (isSelected) {
                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
                } else {
                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                }

                Marker(
                    state = MarkerState(position = LatLng(lugar.latitud, lugar.longitud)),
                    title = lugar.nombre,
                    snippet = if (isSelected) "Toca para quitar" else "Toca para agregar",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(color),
                    onClick = {
                        onLugarClick(lugar)
                        true
                    }
                )
            }

            // Dibujar línea conectando los lugares seleccionados
            if (lugaresSeleccionados.size >= 2) {
                Polyline(
                    points = lugaresSeleccionados.map { LatLng(it.latitud, it.longitud) },
                    color = Color(0xFF2196F3),
                    width = 10f
                )
            }
        }

        // Lista de lugares seleccionados en la parte inferior
        if (lugaresSeleccionados.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Lugares en la ruta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "(${lugaresSeleccionados.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lugaresSeleccionados) { lugar ->
                            LugarChip(
                                lugar = lugar,
                                orden = lugaresSeleccionados.indexOf(lugar) + 1,
                                onRemove = { onRemoveLugar(lugar) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Toca los marcadores en el mapa para agregar o quitar lugares",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Instrucciones cuando no hay lugares seleccionados
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Toca los marcadores en el mapa para agregar lugares a tu ruta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun LugarChip(lugar: Lugar, orden: Int, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Número de orden
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = orden.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = lugar.nombre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
