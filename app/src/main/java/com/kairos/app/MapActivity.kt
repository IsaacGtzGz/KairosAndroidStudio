package com.kairos.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
// Layouts básicos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.CheckCircle
// Material 3 Componentes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import com.kairos.app.components.LoadingState
import com.kairos.app.components.EmptyState
import com.kairos.app.utils.AppConstants
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
// Runtime y UI
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kairos.app.models.ReclamarPuntosRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.CheckInManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.location.Location

class MapActivity : ComponentActivity() {

    // El estado de la ubicación Y el estado de carga
    private var userLocation by mutableStateOf<LatLng?>(null)
    private var isLoading by mutableStateOf(true)
    private var lugaresList by mutableStateOf<List<Lugar>>(emptyList())
    private var categoriasList by mutableStateOf<List<com.kairos.app.models.Categoria>>(emptyList())

    // Variable de estado para los intereses
    private var savedInterests by mutableStateOf<Set<String>>(emptySet())
    
    // Estado para el diálogo de GPS
    private var showGpsDialog by mutableStateOf(false)

    private val defaultLocation = LatLng(21.1290, -101.6700) // León, Gto
    private lateinit var sessionManager: SessionManager
    private lateinit var checkInManager: CheckInManager

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                checkGpsAndGetLocation()
            } else {
                Toast.makeText(this, AppConstants.Messages.PERMISSION_LOCATION_DENIED, Toast.LENGTH_LONG).show()
                userLocation = defaultLocation
                isLoading = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        checkInManager = CheckInManager(this)

        // Obtener categoría inicial desde Intent (desde carrusel)
        val categoriaInicial = intent.getIntExtra("categoriaId", -1)
        
        // Iniciar carga de ubicación
        checkLocationPermission()

        setContent {
            KairosTheme {
                // Cargar lugares y categorías cuando se crea el composable
                LaunchedEffect(Unit) {
                    try {
                        android.util.Log.d("MapActivity", "Iniciando carga de datos...")
                        
                        // Cargar lugares
                        val responseLugares = RetrofitClient.instance.getLugares()
                        android.util.Log.d("MapActivity", "Response lugares: ${responseLugares.isSuccessful}, body size: ${responseLugares.body()?.size}")
                        
                        if (responseLugares.isSuccessful && responseLugares.body() != null) {
                            lugaresList = responseLugares.body()!!
                            android.util.Log.d("MapActivity", "Lugares cargados: ${lugaresList.size}")
                        } else {
                            android.util.Log.e("MapActivity", "Error al cargar lugares: ${responseLugares.code()}")
                            Toast.makeText(this@MapActivity, "Error al cargar lugares: ${responseLugares.code()}", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Cargar categorías
                        val responseCategorias = RetrofitClient.instance.getCategorias()
                        android.util.Log.d("MapActivity", "Response categorias: ${responseCategorias.isSuccessful}")
                        
                        if (responseCategorias.isSuccessful && responseCategorias.body() != null) {
                            categoriasList = responseCategorias.body()!!
                            android.util.Log.d("MapActivity", "Categorías cargadas: ${categoriasList.size}")
                            categoriasList.forEach { cat ->
                                android.util.Log.d("MapActivity", "Categoría: ID=${cat.idCategoria}, Nombre=${cat.nombre}")
                            }
                        } else {
                            android.util.Log.e("MapActivity", "Error al cargar categorías: ${responseCategorias.code()}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MapActivity", "Excepción al cargar datos", e)
                        e.printStackTrace()
                        Toast.makeText(this@MapActivity, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                
                MapScreenRoot(
                    isLoading = isLoading,
                    userLocation = userLocation ?: defaultLocation,
                    lugares = lugaresList,
                    categorias = categoriasList,
                    savedInterests = savedInterests,
                    categoriaInicial = categoriaInicial,
                    showGpsDialog = showGpsDialog,
                    onDismissGpsDialog = { showGpsDialog = false },
                    onOpenGpsSettings = { openLocationSettings() },
                    context = this@MapActivity,
                    sessionManager = sessionManager,
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



    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkGpsAndGetLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
    
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    private fun checkGpsAndGetLocation() {
        if (isLocationEnabled()) {
            getDeviceLocation()
        } else {
            showGpsDialog = true
            userLocation = defaultLocation
            isLoading = false
        }
    }
    
    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
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

// Función auxiliar para calcular distancia en metros entre dos coordenadas
fun calcularDistancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

@Composable
fun MapScreenRoot(
    isLoading: Boolean,
    userLocation: LatLng,
    lugares: List<Lugar>,
    categorias: List<com.kairos.app.models.Categoria>,
    savedInterests: Set<String>,
    categoriaInicial: Int = -1,
    showGpsDialog: Boolean,
    onDismissGpsDialog: () -> Unit,
    onOpenGpsSettings: () -> Unit,
    onNavigateClick: (Lugar) -> Unit,
    onProfileSettingsClick: () -> Unit,
    context: Context,
    sessionManager: SessionManager
) {
    if (showGpsDialog) {
        AlertDialog(
            onDismissRequest = onDismissGpsDialog,
            icon = {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(text = AppConstants.Messages.GPS_DISABLED_TITLE)
            },
            text = {
                Text(text = AppConstants.Messages.GPS_DISABLED_MESSAGE)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissGpsDialog()
                    onOpenGpsSettings()
                }) {
                    Text(AppConstants.Messages.GPS_ENABLE)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissGpsDialog) {
                    Text(AppConstants.Messages.GPS_CANCEL)
                }
            }
        )
    }
    
    if (isLoading) {
        LoadingState(message = "Cargando mapa...")
    } else {
        ActualMapScreen(
            userLocation = userLocation,
            lugares = lugares,
            categorias = categorias,
            savedInterests = savedInterests,
            categoriaInicial = categoriaInicial,
            onNavigateClick = onNavigateClick,
            onProfileSettingsClick = onProfileSettingsClick,
            context = context,
            sessionManager = sessionManager
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActualMapScreen(
    userLocation: LatLng,
    lugares: List<Lugar>,
    categorias: List<com.kairos.app.models.Categoria>,
    savedInterests: Set<String>,
    categoriaInicial: Int = -1,
    onNavigateClick: (Lugar) -> Unit,
    onProfileSettingsClick: () -> Unit,
    context: Context,
    sessionManager: SessionManager
) {
    var selectedLugar by remember { mutableStateOf<Lugar?>(null) }
    var isCheckingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ESTADO DE FILTRO: 0 = Todos (Default cuando viene de carrusel), -1 = Mis Preferencias
    var currentFilterId by remember { mutableStateOf(if (categoriaInicial > 0) categoriaInicial else 0) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // Log para debugging
    LaunchedEffect(lugares, categorias, currentFilterId) {
        android.util.Log.d("ActualMapScreen", "===== ESTADO ACTUAL =====")
        android.util.Log.d("ActualMapScreen", "Total lugares: ${lugares.size}")
        android.util.Log.d("ActualMapScreen", "Total categorías: ${categorias.size}")
        android.util.Log.d("ActualMapScreen", "Filtro actual: $currentFilterId")
        android.util.Log.d("ActualMapScreen", "Categoría inicial desde Intent: $categoriaInicial")
        lugares.take(5).forEach { lugar ->
            android.util.Log.d("ActualMapScreen", "Lugar: ${lugar.nombre}, Cat: ${lugar.idCategoria}, Lat: ${lugar.latitud}, Lng: ${lugar.longitud}")
        }
    }

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

            // Si está vacío, mostrar todos
            if (idsInteres.isEmpty()) lugares
            else lugares.filter { it.idCategoria in idsInteres }
        }
        else -> lugares.filter { it.idCategoria == currentFilterId }
    }
    
    // Log de lugares filtrados
    LaunchedEffect(lugaresFiltrados) {
        android.util.Log.d("ActualMapScreen", "Lugares FILTRADOS: ${lugaresFiltrados.size}")
        lugaresFiltrados.take(3).forEach { lugar ->
            android.util.Log.d("ActualMapScreen", "Lugar filtrado: ${lugar.nombre}")
        }
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
                        // Mostrar TODAS las categorías dinámicamente
                        categorias.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria.nombre) },
                                onClick = { currentFilterId = categoria.idCategoria; showCategoryMenu = false }
                            )
                        }
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

                // 3. Chip Auxiliar (Muestra qué estás viendo si NO son preferencias ni todos)
                if (currentFilterId > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val categoria = categorias.find { it.idCategoria == currentFilterId }
                    val label = categoria?.nombre ?: "Cargando..."
                    
                    FilterChip(
                        selected = true,
                        onClick = { /* No hace nada, es informativo */ },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    )
                }
                
                // 4. Chip "Todos" cuando está en modo todos
                if (currentFilterId == 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = true,
                        onClick = { /* No hace nada, es informativo */ },
                        label = { Text("Todos") },
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

            // Tarjeta flotante inferior con Check-in
            selectedLugar?.let { lugar ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = lugar.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        lugar.direccion?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (lugar.puntosOtorgados > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🏆 ${lugar.puntosOtorgados} puntos disponibles",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppConstants.Colors.DarkGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val userId = sessionManager.fetchUserId()
                        val yaReclamado = checkInManager.yaHizoCheckIn(userId, lugar.idLugar ?: 0)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            // Botón Check-in
                            if (lugar.puntosOtorgados > 0) {
                                if (yaReclamado) {
                                    // Mostrar estado de ya reclamado
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "✓ Reclamado",
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isCheckingIn = true
                                                val distancia = calcularDistancia(
                                                    userLocation.latitude,
                                                    userLocation.longitude,
                                                    lugar.latitud,
                                                    lugar.longitud
                                                )
                                                
                                                if (distancia > 100) {
                                                    Toast.makeText(
                                                        context,
                                                        "Debes estar a menos de 100 metros del lugar. Distancia actual: ${distancia.toInt()}m",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    isCheckingIn = false
                                                    return@launch
                                                }
                                                
                                                try {
                                                    val request = ReclamarPuntosRequest(
                                                        idUsuario = userId,
                                                        idLugar = lugar.idLugar ?: 0,
                                                        latitudUsuario = userLocation.latitude,
                                                        longitudUsuario = userLocation.longitude
                                                    )
                                                    
                                                    val response = RetrofitClient.instance.reclamarPuntos(request)
                                                    
                                                    if (response.isSuccessful && response.body()?.exito == true) {
                                                        val puntosGanados = response.body()?.puntosGanados ?: 0
                                                        
                                                        // Guardar check-in localmente
                                                        checkInManager.marcarCheckInRealizado(userId, lugar.idLugar ?: 0)
                                                        
                                                        // Refrescar puntos del usuario
                                                        try {
                                                            val userResponse = RetrofitClient.instance.getUsuario(userId)
                                                            if (userResponse.isSuccessful) {
                                                                val puntosActualizados = userResponse.body()?.puntosAcumulados ?: 0
                                                                sessionManager.saveUserPoints(puntosActualizados)
                                                            }
                                                        } catch (e: Exception) {
                                                            // Si falla la actualización, no hacer nada crítico
                                                        }
                                                        
                                                        Toast.makeText(
                                                            context,
                                                            "¡Check-in exitoso! +${puntosGanados} puntos 🎉",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            response.body()?.mensaje ?: "Error al hacer check-in",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Error: ${e.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } finally {
                                                    isCheckingIn = false
                                                }
                                            }
                                        },
                                        enabled = !isCheckingIn,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AppConstants.Colors.DarkGreen
                                        )
                                    ) {
                                        if (isCheckingIn) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.width(16.dp).height(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(Icons.Default.CheckCircle, null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Check-in")
                                        }
                                    }
                                }
                            }
                            
                            // Botón Ver Detalle
                            Button(
                                onClick = { onNavigateClick(lugar) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ver Detalle")
                            }
                        }
                    }
                }
            }
        }
    }
}