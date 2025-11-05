package com.kairos.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    private var userLocation by mutableStateOf<LatLng?>(null)
    private val defaultLocation = LatLng(21.1290, -101.6700) // Ubicación por defecto (León, Gto)

    // Lanzador para pedir permisos
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido, obtenemos ubicación
                getDeviceLocation()
            } else {
                // Permiso denegado
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
                userLocation = defaultLocation
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkLocationPermission()

        setContent {
            KairosTheme {
                // El POI (Point of Interest) simulado
                val poiLocation = LatLng(21.1305, -101.6720) // Un punto simulado cercano

                MapScreen(
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
                // Muestra un dialogo explicativo (opcional)
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                // Pide el permiso directamente
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getDeviceLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = LatLng(location.latitude, location.longitude)
                    } else {
                        userLocation = defaultLocation
                        Toast.makeText(this, "No se pudo obtener ubicación, usando default", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error de seguridad: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MapScreen(
    userLocation: LatLng,
    poiLocation: LatLng,
    onNavigateClick: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 15f)
    }

    // Actualiza la cámara cuando la ubicación del usuario cambie
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
                // Habilita el punto azul de "Mi Ubicación"
                properties = MapProperties(isMyLocationEnabled = true),
                // Deshabilita controles que no queremos (como el de centrar)
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
                Text(text = "Cómo llegar al POI")
            }
        }
    }
}