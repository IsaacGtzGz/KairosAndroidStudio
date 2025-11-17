package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.kairos.app.models.Lugar
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : ComponentActivity() {

    // Estados
    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)
    // Lista de lugares traída de la API
    private var lugaresList by mutableStateOf<List<Lugar>>(emptyList())

    private val defaultLocation = LatLng(21.1290, -101.6700) // León, Gto

    // Lanzador para pedir permisos
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getDeviceLocation()
            } else {
                Toast.makeText(this, "Permiso denegado, usando ubicación default", Toast.LENGTH_LONG).show()
                userLocation = defaultLocation
                isLoading = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Iniciar carga de mapa y DATOS de la API
        fetchLugaresFromApi()

        setContent {
            KairosTheme {
                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    lugares = lugaresList, // Pasamos la lista real
                    onNavigateClick = { lat, lng ->
                        // Acción dinámica para navegar al lugar seleccionado
                        val gmmIntentUri = android.net.Uri.parse("google.navigation:q=$lat,$lng")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        startActivity(mapIntent)
                    }
                )
            }
        }

        checkLocationPermission()
    }

    // Función para pedir los datos a la API
    private fun fetchLugaresFromApi() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getLugares()
                if (response.isSuccessful && response.body() != null) {
                    withContext(Dispatchers.Main) {
                        // 👇 CAMBIO AQUÍ: Extraemos la lista usando .values
                        lugaresList = response.body()!!.values

                        // Un pequeño Toast para confirmar que llegaron datos
                        if (lugaresList.isNotEmpty()) {
                            Toast.makeText(this@MapActivity, "¡Lugares cargados!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MapActivity, "No hay lugares en la BD", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MapActivity, "Error al cargar lugares: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Log del error real para debug
                    e.printStackTrace()
                    Toast.makeText(this@MapActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getDeviceLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getDeviceLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    userLocation = if (location != null) {
                        LatLng(location.latitude, location.longitude)
                    } else {
                        defaultLocation
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    userLocation = defaultLocation
                    isLoading = false
                }
        } catch (e: SecurityException) {
            isLoading = false
        }
    }
}

// -----------------------------------------------------------------
// COMPOSABLES
// -----------------------------------------------------------------

@Composable
fun MapScreenRoot(
    isLoading: Boolean,
    userLocation: LatLng,
    lugares: List<Lugar>,
    onNavigateClick: (Double, Double) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        ActualMapScreen(
            userLocation = userLocation,
            lugares = lugares,
            onNavigateClick = onNavigateClick
        )
    }
}

@Composable
fun ActualMapScreen(
    userLocation: LatLng,
    lugares: List<Lugar>,
    onNavigateClick: (Double, Double) -> Unit
) {
    // Estado para guardar el lugar seleccionado actualmente (para el botón de navegar)
    var selectedLugar by remember { mutableStateOf<Lugar?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 14f)
    }

    LaunchedEffect(userLocation) {
        cameraPositionState.animate(
            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(userLocation, 14f)
        )
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false)
            ) {
                // Marcador de TU ubicación (opcional, ya sale el punto azul con isMyLocationEnabled)

                // PINTAR TODOS LOS LUGARES DE LA BD
                lugares.forEach { lugar ->
                    Marker(
                        state = MarkerState(position = LatLng(lugar.latitud, lugar.longitud)),
                        title = lugar.nombre,
                        snippet = lugar.descripcion ?: "Sin descripción",
                        onClick = {
                            selectedLugar = lugar // Guardamos cuál se tocó
                            false // false para permitir el comportamiento default (mostrar info window)
                        }
                    )
                }
            }

            // Botón flotante "Cómo llegar" (Solo aparece si | un lugar)
            if (selectedLugar != null) {
                Button(
                    onClick = {
                        selectedLugar?.let {
                            onNavigateClick(it.latitud, it.longitud)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Text(text = "Ir a: ${selectedLugar?.nombre}")
                }
            }
        }
    }
}