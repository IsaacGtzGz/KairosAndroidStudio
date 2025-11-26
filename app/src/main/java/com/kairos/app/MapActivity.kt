package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
// Layouts básicos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
// Material 3 Componentes
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
// Runtime y UI
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
// Google Maps y Utils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
// Tus Clases
import com.kairos.app.models.Lugar
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : ComponentActivity() {

    // El estado de la ubicación Y el estado de carga
    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)
    private var lugaresList by mutableStateOf<List<Lugar>>(emptyList())

    // Variable de estado para los intereses
    private var savedInterests by mutableStateOf<Set<String>>(emptySet())

    private val defaultLocation = LatLng(21.1290, -101.6700) // León, Gto
    private lateinit var sessionManager: SessionManager

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
        sessionManager = SessionManager(this)

        // Carga inicial de datos
        fetchLugaresFromApi()
        checkLocationPermission()

        setContent {
            KairosTheme {
                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    lugares = lugaresList,
                    savedInterests = savedInterests,
                    onNavigateClick = { lugar ->
                        // CORREGIDO: Ahora abre TU pantalla de detalles, no el GPS directo
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
                        val intent = Intent(this, AjustesActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recargamos los intereses por si cambiaron en Ajustes
        savedInterests = sessionManager.fetchInterests()
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
                withContext(Dispatchers.Main) { e.printStackTrace() }
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

    // ESTADO DE FILTRO: -1 = Mis Preferencias (Default), 0 = Todos
    var currentFilterId by remember { mutableStateOf(-1) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // LÓGICA DE FILTRADO REAL
    val lugaresFiltrados = when (currentFilterId) {
        0 -> lugares // Todos
        -1 -> {
            // Filtrar por Preferencias guardadas
            val idsInteres = mutableListOf<Int>()

            if (savedInterests.contains("Parques")) idsInteres.add(1)
            if (savedInterests.contains("Museos")) idsInteres.add(2)
            if (savedInterests.contains("Cafeterías")) idsInteres.add(3)
            if (savedInterests.contains("Senderismo")) idsInteres.add(4)
            if (savedInterests.contains("Arte")) idsInteres.add(5)
            if (savedInterests.contains("Comida")) idsInteres.add(6)

            // Si está vacío, devuelve lista vacía (como pediste)
            if (idsInteres.isEmpty()) emptyList()
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
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
            ) {
                lugaresFiltrados.forEach { lugar ->
                    // ✅ Icono personalizado según categoría
                    val iconoColor = when (lugar.idCategoria) {
                        1 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN // Parques
                        2 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_VIOLET // Museos
                        3 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE // Cafeterías
                        4 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN // Senderismo
                        5 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_MAGENTA // Arte
                        6 -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED // Comida
                        else -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE // Default
                    }
                    
                    Marker(
                        state = MarkerState(position = LatLng(lugar.latitud, lugar.longitud)),
                        title = lugar.nombre,
                        snippet = lugar.descripcion ?: "Ver detalles",
                        icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(iconoColor),
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
                        // Agrega aquí más opciones si quieres ver "Solo Cafeterías", etc.
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

                // 3. Chip Auxiliar (Muestra qué estás viendo si NO son preferencias)
                if (currentFilterId != -1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = when(currentFilterId) {
                        0 -> "Todos"
                        1 -> "Parques"
                        2 -> "Museos"
                        else -> "Categoría $currentFilterId"
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