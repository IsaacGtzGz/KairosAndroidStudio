package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator // 👈 IMPORT NUEVO
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.kairos.app.ui.theme.KairosTheme

class MapActivity : ComponentActivity() {

    // El estado de la ubicación Y el estado de carga
    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)

    private val defaultLocation = LatLng(21.1290, -101.6700) // León, Gto

    // Lanzador para pedir permisos DE UBICACIÓN
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido, obtenemos ubicación
                getDeviceLocation()
            } else {
                // Permiso denegado, usamos default y dejamos de cargar
                Toast.makeText(this, "Permiso denegado, usando ubicación default", Toast.LENGTH_LONG).show()
                userLocation = defaultLocation
                isLoading = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. SETCONTENT SE LLAMA INMEDIATAMENTE
        setContent {
            KairosTheme {
                // El POI simulado
                val poiLocation = LatLng(21.1305, -101.6720)

                // Este Composable ahora decide si muestra "Cargando" o el Mapa
                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    poiLocation = poiLocation,
                    onNavigateClick = {
                        // Acción para el botón "Cómo llegar"
                        val gmmIntentUri = Uri.parse("google.navigation:q=${poiLocation.latitude},${poiLocation.longitude}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        startActivity(mapIntent)
                    }
                )
            }
        }

        // 2. DESPUÉS de dibujar, pedimos el permiso
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tienes permiso
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

    // 3. Esta función AHORA SOLO actualiza los estados
    private fun getDeviceLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    userLocation = if (location != null) {
                        LatLng(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(this, "No se pudo obtener ubicación, usando default", Toast.LENGTH_SHORT).show()
                        defaultLocation
                    }
                    isLoading = false // 👈 AVISAMOS QUE DEJE DE CARGAR
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al obtener ubicación, usando default", Toast.LENGTH_SHORT).show()
                    userLocation = defaultLocation
                    isLoading = false // 👈 AVISAMOS QUE DEJE DE CARGAR
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error de seguridad: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

// -----------------------------------------------------------------
// NUEVO COMPOSABLE "ROOT" (RAÍZ)
// Decide si mostrar "Cargando" o el mapa real
// -----------------------------------------------------------------
@Composable
fun MapScreenRoot(
    isLoading: Boolean,
    userLocation: LatLng,
    poiLocation: LatLng,
    onNavigateClick: () -> Unit
) {
    if (isLoading) {
        // Muestra un círculo de "Cargando..." en el centro
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Ya no está cargando, muestra el mapa
        ActualMapScreen(
            userLocation = userLocation,
            poiLocation = poiLocation,
            onNavigateClick = onNavigateClick
        )
    }
}


// -----------------------------------------------------------------
// Este es el Composable que ANTES se llamaba "MapScreen"
// Ahora solo se encarga de dibujar el mapa
// -----------------------------------------------------------------
@Composable
fun ActualMapScreen(
    userLocation: LatLng,
    poiLocation: LatLng,
    onNavigateClick: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 15f)
    }

    // Anima la cámara a la posición del usuario cuando se carga
    LaunchedEffect(userLocation) {
        cameraPositionState.animate(
            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(userLocation, 15f)
        )
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
            ) {
                // Marcador de tu ubicación
                Marker(
                    state = MarkerState(position = userLocation),
                    title = "Tu ubicación"
                )
                // Marcador del POI simulado
                Marker(
                    state = MarkerState(position = poiLocation),
                    title = "Parque Explora (Simulado)",
                    snippet = "¡Un lugar genial!"
                )
            }

            // Botón flotante para "Cómo llegar"
            Button(
                onClick = onNavigateClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Text(text = "Cómo llegar")
            }
        }
    }
}