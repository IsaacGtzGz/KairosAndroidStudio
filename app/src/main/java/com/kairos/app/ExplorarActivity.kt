package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.kairos.app.components.EmptyState
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Categoria
import com.kairos.app.models.Lugar
import com.kairos.app.models.ReclamarPuntosRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch

class ExplorarActivity : ComponentActivity() {
    
    private var userLocation by mutableStateOf<LatLng?>(null)
    private val defaultLocation = LatLng(21.1290, -101.6700) // León, Gto
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getDeviceLocation()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Solicitar ubicación
        checkLocationPermission()

        setContent {
            KairosTheme {
                var lugares by remember { mutableStateOf<List<Lugar>>(emptyList()) }
                var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var filtroTexto by remember { mutableStateOf("") }
                var categoriaSeleccionada by remember { mutableStateOf<Int?>(null) }
                var ciudadSeleccionada by remember { mutableStateOf<String?>(null) }
                var showFiltros by remember { mutableStateOf(false) }
                
                val sessionManager = remember { SessionManager(this@ExplorarActivity) }
                val currentLocation = userLocation ?: defaultLocation

                // Ciudades únicas del backend
                val ciudades = remember(lugares) {
                    lugares.mapNotNull { it.ciudad }.distinct().sorted()
                }

                LaunchedEffect(Unit) {
                    if (!NetworkHelper.isNetworkAvailable(this@ExplorarActivity)) {
                        Toast.makeText(this@ExplorarActivity, AppConstants.Messages.NO_INTERNET, Toast.LENGTH_SHORT).show()
                        isLoading = false
                        return@LaunchedEffect
                    }
                    
                    try {
                        val responseLugares = RetrofitClient.instance.getLugares()
                        val responseCategorias = RetrofitClient.instance.getCategorias()

                        if (responseLugares.isSuccessful && responseLugares.body() != null) {
                            lugares = responseLugares.body()!!
                        }
                        if (responseCategorias.isSuccessful && responseCategorias.body() != null) {
                            categorias = responseCategorias.body()!!
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ExplorarActivity, AppConstants.Messages.CONNECTION_ERROR, Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }

                // Filtrado
                val lugaresFiltrados = lugares.filter { lugar ->
                    val matchTexto = filtroTexto.isEmpty() || 
                        lugar.nombre.contains(filtroTexto, ignoreCase = true) ||
                        lugar.descripcion?.contains(filtroTexto, ignoreCase = true) == true
                    val matchCategoria = categoriaSeleccionada == null || lugar.idCategoria == categoriaSeleccionada
                    val matchCiudad = ciudadSeleccionada == null || lugar.ciudad == ciudadSeleccionada
                    matchTexto && matchCategoria && matchCiudad
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Explorar Lugares") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showFiltros = !showFiltros }) {
                                    Icon(
                                        if (showFiltros) Icons.Default.FilterAltOff else Icons.Default.FilterAlt,
                                        "Filtros"
                                    )
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // BARRA DE BÚSQUEDA
                        OutlinedTextField(
                            value = filtroTexto,
                            onValueChange = { filtroTexto = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            placeholder = { Text("Buscar lugares...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (filtroTexto.isNotEmpty()) {
                                    IconButton(onClick = { filtroTexto = "" }) {
                                        Icon(Icons.Default.Clear, "Limpiar")
                                    }
                                }
                            },
                            singleLine = true
                        )

                        // PANEL DE FILTROS EXPANDIBLE
                        AnimatedVisibility(visible = showFiltros) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Filtro por Categoría
                                    Text("Categoría", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = categoriaSeleccionada == null,
                                            onClick = { categoriaSeleccionada = null },
                                            label = { Text("Todas") }
                                        )
                                        categorias.forEach { cat ->
                                            FilterChip(
                                                selected = categoriaSeleccionada == cat.idCategoria,
                                                onClick = { categoriaSeleccionada = cat.idCategoria },
                                                label = { Text(cat.nombre) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Filtro por Ciudad
                                    Text("Ciudad", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = ciudadSeleccionada == null,
                                            onClick = { ciudadSeleccionada = null },
                                            label = { Text("Todas") }
                                        )
                                        ciudades.forEach { ciudad ->
                                            FilterChip(
                                                selected = ciudadSeleccionada == ciudad,
                                                onClick = { ciudadSeleccionada = ciudad },
                                                label = { Text(ciudad) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // LISTA DE RESULTADOS
                        if (isLoading) {
                            LoadingState(message = "Cargando lugares...")
                        } else {
                            if (lugaresFiltrados.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.SearchOff,
                                    title = "No se encontraron lugares",
                                    subtitle = "Intenta ajustar los filtros de búsqueda"
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(lugaresFiltrados) { lugar ->
                                        LugarCard(
                                            lugar = lugar,
                                            userLocation = currentLocation,
                                            sessionManager = sessionManager,
                                            context = this@ExplorarActivity,
                                            onClick = {
                                                val intent = Intent(this@ExplorarActivity, DetalleLugarActivity::class.java).apply {
                                                    putExtra("idLugar", lugar.idLugar)
                                                    putExtra("nombre", lugar.nombre)
                                                    putExtra("descripcion", lugar.descripcion)
                                                    putExtra("direccion", lugar.direccion)
                                                    putExtra("horario", lugar.horario)
                                                    putExtra("imagen", lugar.imagenUrl)
                                                    putExtra("lat", lugar.latitud)
                                                    putExtra("lng", lugar.longitud)
                                                    putExtra("puntosOtorgados", lugar.puntosOtorgados)
                                                }
                                                startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                }
                .addOnFailureListener {
                    userLocation = defaultLocation
                }
        } catch (e: SecurityException) {
            userLocation = defaultLocation
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LugarCard(
    lugar: Lugar, 
    userLocation: LatLng,
    sessionManager: SessionManager,
    context: android.content.Context,
    onClick: () -> Unit
) {
    var isCheckingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect(minScale = 0.97f),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(120.dp)
                    .clickable(onClick = onClick)
            ) {
            // Imagen
            if (lugar.imagenUrl != null) {
                AsyncImage(
                    model = lugar.imagenUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .background(AppConstants.Colors.PrimaryGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            // Información
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = lugar.nombre,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    lugar.ciudad?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (lugar.puntosOtorgados > 0) {
                        Surface(
                            color = AppConstants.Colors.DarkGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${lugar.puntosOtorgados} pts",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (lugar.esPatrocinado) {
                        Surface(
                            color = AppConstants.Colors.Gold,
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.Star,
                                "Patrocinado",
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(4.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
            
            // Botón Check-in (solo si tiene puntos)
            if (lugar.puntosOtorgados > 0) {
                Divider(modifier = Modifier.fillMaxWidth())
                
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
                                val userId = sessionManager.fetchUserId()
                                val request = ReclamarPuntosRequest(
                                    idUsuario = userId,
                                    idLugar = lugar.idLugar ?: 0,
                                    latitudUsuario = userLocation.latitude,
                                    longitudUsuario = userLocation.longitude
                                )
                                
                                val response = RetrofitClient.instance.reclamarPuntos(request)
                                
                                if (response.isSuccessful && response.body()?.exito == true) {
                                    val puntosGanados = response.body()?.puntosGanados ?: 0
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppConstants.Colors.DarkGreen
                    )
                ) {
                    if (isCheckingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Validando...")
                    } else {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hacer Check-in (${lugar.puntosOtorgados} pts)")
                    }
                }
            }
        }
    }
}
