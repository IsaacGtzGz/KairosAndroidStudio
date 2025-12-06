package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Lugar
import com.kairos.app.models.Ruta
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import kotlinx.coroutines.launch

class RutaDetalleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rutaId = intent.getIntExtra("rutaId", -1)
        val rutaNombre = intent.getStringExtra("rutaNombre") ?: "Ruta"
        val rutaDescripcion = intent.getStringExtra("rutaDescripcion")

        setContent {
            KairosTheme {
                var ruta by remember { mutableStateOf<Ruta?>(null) }
                var lugares by remember { mutableStateOf<List<Lugar>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(rutaId) {
                    scope.launch {
                        try {
                            // Obtener detalles de la ruta
                            val response = RetrofitClient.instance.getRuta(rutaId)
                            if (response.isSuccessful && response.body() != null) {
                                ruta = response.body()
                                
                                // Obtener lugares de la ruta
                                val lugaresIds = ruta?.rutasLugares?.map { it.idLugar } ?: emptyList()
                                if (lugaresIds.isNotEmpty()) {
                                    val lugaresResponse = RetrofitClient.instance.getLugares()
                                    if (lugaresResponse.isSuccessful && lugaresResponse.body() != null) {
                                        lugares = lugaresResponse.body()!!.filter { it.idLugar in lugaresIds }
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    this@RutaDetalleActivity,
                                    "Error al cargar la ruta",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@RutaDetalleActivity,
                                "Error de conexión",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(rutaNombre) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
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
                            LoadingState(message = "Cargando ruta...")
                        } else {
                            if (lugares.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Esta ruta no tiene lugares agregados",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    rutaDescripcion?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                RutaMap(lugares = lugares, rutaNombre = rutaNombre)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RutaMap(lugares: List<Lugar>, rutaNombre: String) {
    // Calcular el centro del mapa basado en los lugares
    val center = if (lugares.isNotEmpty()) {
        val avgLat = lugares.map { it.latitud }.average()
        val avgLng = lugares.map { it.longitud }.average()
        LatLng(avgLat, avgLng)
    } else {
        LatLng(21.1290, -101.6700) // León, Gto (default)
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
                zoomControlsEnabled = true,
                compassEnabled = true
            )
        ) {
            // Dibujar marcadores para cada lugar
            lugares.forEachIndexed { index, lugar ->
                Marker(
                    state = MarkerState(position = LatLng(lugar.latitud, lugar.longitud)),
                    title = "${index + 1}. ${lugar.nombre}",
                    snippet = lugar.descripcion ?: "Lugar en la ruta",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
                    )
                )
            }

            // Dibujar línea conectando los puntos
            if (lugares.size >= 2) {
                Polyline(
                    points = lugares.map { LatLng(it.latitud, it.longitud) },
                    color = Color(0xFF2196F3),
                    width = 10f
                )
            }
        }

        // Información en la parte inferior
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = rutaNombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${lugares.size} lugares en esta ruta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
