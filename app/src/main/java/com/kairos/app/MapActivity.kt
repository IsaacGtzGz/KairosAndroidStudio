package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings // 👈 Ícono de Tuerca
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)
    private var lugaresList by mutableStateOf<List<Lugar>>(emptyList())
    private val defaultLocation = LatLng(21.1290, -101.6700)

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getDeviceLocation()
            } else {
                Toast.makeText(this, "Ubicación denegada", Toast.LENGTH_LONG).show()
                userLocation = defaultLocation
                isLoading = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fetchLugaresFromApi()

        setContent {
            KairosTheme {
                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    lugares = lugaresList,
                    onNavigateClick = { lugar ->
                        val intent = Intent(this, DetalleLugarActivity::class.java).apply {
                            putExtra("nombre", lugar.nombre)
                            putExtra("descripcion", lugar.descripcion)
                            putExtra("direccion", lugar.direccion)
                            putExtra("horario", lugar.horario)
                            putExtra("imagen", lugar.imagen)
                            putExtra("lat", lugar.latitud)
                            putExtra("lng", lugar.longitud)
                        }
                        startActivity(intent)
                    },
                    onProfileSettingsClick = {
                        Toast.makeText(this, "Ir a Configuración de Intereses (Próximamente)", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        checkLocationPermission()
    }

    private fun fetchLugaresFromApi() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getLugares()
                if (response.isSuccessful && response.body() != null) {
                    withContext(Dispatchers.Main) {
                        lugaresList = response.body()!!.values
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
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
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                userLocation = if (location != null) LatLng(location.latitude, location.longitude) else defaultLocation
                isLoading = false
            }.addOnFailureListener {
                userLocation = defaultLocation
                isLoading = false
            }
        } catch (e: SecurityException) {
            isLoading = false
        }
    }
}

@Composable
fun MapScreenRoot(
    isLoading: Boolean,
    userLocation: LatLng,
    lugares: List<Lugar>,
    onNavigateClick: (Lugar) -> Unit,
    onProfileSettingsClick: () -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ActualMapScreen(
            userLocation = userLocation,
            lugares = lugares,
            onNavigateClick = onNavigateClick,
            onProfileSettingsClick = onProfileSettingsClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActualMapScreen(
    userLocation: LatLng,
    lugares: List<Lugar>,
    onNavigateClick: (Lugar) -> Unit,
    onProfileSettingsClick: () -> Unit
) {
    var selectedLugar by remember { mutableStateOf<Lugar?>(null) }

    // ESTADO PARA EL FILTRO: null = Todos, 1 = Parques, 2 = Museos
    var currentFilterId by remember { mutableStateOf<Int?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val lugaresFiltrados = if (currentFilterId == null) {
        lugares
    } else {
        lugares.filter { it.idCategoria == currentFilterId }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 14f)
    }

    LaunchedEffect(userLocation) {
        cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(userLocation, 14f))
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false)
            ) {
                lugaresFiltrados.forEach { lugar ->
                    Marker(
                        state = MarkerState(position = LatLng(lugar.latitud, lugar.longitud)),
                        title = lugar.nombre,
                        snippet = lugar.descripcion ?: "Ver detalles",
                        onClick = {
                            selectedLugar = lugar
                            false
                        }
                    )
                }
            }

            // --- BARRA DE HERRAMIENTAS SUPERIOR (REDISEÑADA) ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Botón OJO (Menú de Filtros) - AHORA A LA IZQUIERDA
                Box {
                    SmallFloatingActionButton(
                        onClick = { showCategoryMenu = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Filtrar Categoría")
                    }

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Parques") },
                            onClick = {
                                currentFilterId = 1
                                showCategoryMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Museos") },
                            onClick = {
                                currentFilterId = 2
                                showCategoryMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp)) // Espacio pequeño "pegado"

                // 2. Chips Dinámicos
                // Chip "Todos" (Siempre visible, seleccionado si id es null)
                FilterChip(
                    selected = currentFilterId == null,
                    onClick = { currentFilterId = null },
                    label = { Text("Todos") },
                    leadingIcon = if (currentFilterId == null) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                // Chip de Categoría Específica (Solo aparece si seleccionaste algo del menú)
                if (currentFilterId != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val categoryName = if (currentFilterId == 1) "Parques" else "Museos"
                    FilterChip(
                        selected = true, // Siempre seleccionado porque es el filtro activo
                        onClick = { showCategoryMenu = true }, // Al tocarlo abre el menú otra vez
                        label = { Text(categoryName) },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }

                Spacer(modifier = Modifier.weight(1f)) // Empuja la tuerca al final

                // 3. Botón TUERCA (Configuración)
                SmallFloatingActionButton(
                    onClick = onProfileSettingsClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurar Intereses") // 👈 ÍCONO TUERCA
                }
            }

            // Botón flotante inferior "Ver Detalle"
            if (selectedLugar != null) {
                Button(
                    onClick = { selectedLugar?.let { onNavigateClick(it) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                ) {
                    Text(text = "Ver: ${selectedLugar?.nombre}")
                }
            }
        }
    }
}