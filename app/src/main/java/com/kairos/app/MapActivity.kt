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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : ComponentActivity() {

    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)
    private var lugaresList by mutableStateOf<List<Lugar>>(emptyList())
    private val defaultLocation = LatLng(21.1290, -101.6700)

    // Variable para acceder a las preferencias
    private lateinit var sessionManager: SessionManager

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

        sessionManager = SessionManager(this) // Inicializamos

        fetchLugaresFromApi()

        setContent {
            KairosTheme {
                // Leemos las preferencias guardadas
                val savedInterests = sessionManager.fetchInterests()

                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    lugares = lugaresList,
                    savedInterests = savedInterests, // Pasamos los intereses
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
                        // Al dar clic en la tuerca, abrimos Ajustes
                        val intent = Intent(this, AjustesActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }

        checkLocationPermission()
    }

    // (El resto de funciones fetchLugaresFromApi, checkLocationPermission, getDeviceLocation quedan IGUAL)
    // ... COPIA TUS FUNCIONES PRIVADAS AQUÍ SI ES NECESARIO O DÉJALAS COMO ESTABAN ...

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
                withContext(Dispatchers.Main) { e.printStackTrace() }
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
    savedInterests: Set<String>,
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
            savedInterests = savedInterests,
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
    savedInterests: Set<String>,
    onNavigateClick: (Lugar) -> Unit,
    onProfileSettingsClick: () -> Unit
) {
    var selectedLugar by remember { mutableStateOf<Lugar?>(null) }

    // ESTADO DE FILTRO: 
    // -1 = Mis Preferencias (Default)
    // 0 = Todos
    // 1 = Parques, 2 = Museos, etc.
    var currentFilterId by remember { mutableStateOf(-1) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // LÓGICA DE FILTRADO REAL
    val lugaresFiltrados = when (currentFilterId) {
        0 -> lugares // Todos
        -1 -> {
            // Filtrar por Preferencias guardadas
            // Mapeo rápido (esto debería venir de la BD idealmente, pero para MVP hardcodeamos)
            // Parques = 1, Museos = 2
            val idsInteres = mutableListOf<Int>()
            if (savedInterests.contains("Parques")) idsInteres.add(1)
            if (savedInterests.contains("Museos")) idsInteres.add(2)

            if (idsInteres.isEmpty()) lugares // Si no seleccionó nada, mostramos todo
            else lugares.filter { it.idCategoria in idsInteres }
        }
        else -> lugares.filter { it.idCategoria == currentFilterId }
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

            // --- BARRA DE HERRAMIENTAS SUPERIOR ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Botón OJO (Menú de Filtros)
                Box {
                    SmallFloatingActionButton(
                        onClick = { showCategoryMenu = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Ver...")
                    }

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ver Todos") },
                            onClick = { currentFilterId = 0; showCategoryMenu = false }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Solo Parques") },
                            onClick = { currentFilterId = 1; showCategoryMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Solo Museos") },
                            onClick = { currentFilterId = 2; showCategoryMenu = false }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Chip Principal: PREFERENCIAS (Default)
                FilterChip(
                    selected = currentFilterId == -1,
                    onClick = { currentFilterId = -1 },
                    label = { Text("Mis Preferencias") },
                    leadingIcon = if (currentFilterId == -1) {
                        { Icon(Icons.Default.Favorite, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                // 3. Chip Auxiliar: Muestra qué estás viendo si NO son preferencias
                if (currentFilterId != -1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = when(currentFilterId) {
                        0 -> "Todos"
                        1 -> "Parques"
                        2 -> "Museos"
                        else -> ""
                    }
                    FilterChip(
                        selected = true,
                        onClick = { /* No hace nada, es informativo */ },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 4. Botón TUERCA (Configuración)
                SmallFloatingActionButton(
                    onClick = onProfileSettingsClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurar")
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