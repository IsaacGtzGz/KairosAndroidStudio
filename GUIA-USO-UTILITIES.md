# 🔧 Guía de Uso Avanzado - Utility Classes

## 📖 Índice
1. [NetworkUtils - Estado de Red y Caché](#networkutils)
2. [StateComponents - UI Reutilizable](#statecomponents)
3. [AppConstants - Constantes y Extensions](#appconstants)
4. [NetworkHelper - Validación de Conectividad](#networkhelper)
5. [LocationUtils - Geolocalización](#locationutils)
6. [Patrones de Uso Recomendados](#patrones)

---

## 1. NetworkUtils - Estado de Red y Caché {#networkutils}

### LoadingState<T> - Type-Safe State Management

```kotlin
// Definición
sealed class LoadingState<out T> {
    object Idle : LoadingState<Nothing>()
    object Loading : LoadingState<Nothing>()
    data class Success<T>(val data: T) : LoadingState<T>()
    data class Error(val message: String, val exception: Exception? = null) : LoadingState<Nothing>()
}
```

#### Uso Básico
```kotlin
var state by remember { mutableStateOf<LoadingState<List<Lugar>>>(LoadingState.Idle) }

LaunchedEffect(Unit) {
    state = LoadingState.Loading
    try {
        val response = RetrofitClient.instance.getLugares()
        if (response.isSuccessful && response.body() != null) {
            state = LoadingState.Success(response.body()!!.values)
        } else {
            state = LoadingState.Error("Error al cargar lugares")
        }
    } catch (e: Exception) {
        state = LoadingState.Error("Error de conexión", e)
    }
}

// Renderizar según el estado
when (state) {
    is LoadingState.Idle -> { /* Mostrar placeholder */ }
    is LoadingState.Loading -> LoadingState(message = "Cargando...")
    is LoadingState.Success -> {
        val lugares = (state as LoadingState.Success<List<Lugar>>).data
        LazyColumn { items(lugares) { lugar -> LugarCard(lugar) } }
    }
    is LoadingState.Error -> {
        ErrorState(
            message = (state as LoadingState.Error).message,
            onRetry = { /* Reintentar */ }
        )
    }
}
```

### apiCall - Wrapper Genérico para API

```kotlin
// Uso en cualquier Activity
suspend fun cargarDatos() {
    val result = apiCall {
        RetrofitClient.instance.getLugares()
    }
    
    when (result) {
        is LoadingState.Success -> {
            lugares = result.data.values
            Toast.makeText(context, "Datos cargados", LENGTH_SHORT).show()
        }
        is LoadingState.Error -> {
            Toast.makeText(context, result.message, LENGTH_SHORT).show()
        }
        else -> {}
    }
}
```

### DataCache - Sistema de Caché con TTL

```kotlin
// Guardar en caché
DataCache.put("lugares_cache", lugaresList)

// Recuperar de caché (válido por 5 minutos)
val cachedLugares = DataCache.get<List<Lugar>>("lugares_cache")
if (cachedLugares != null) {
    // Usar datos en caché
    lugares = cachedLugares
} else {
    // Cargar desde API
    cargarDatos()
}

// Limpiar caché
DataCache.clear("lugares_cache")

// Limpiar todo el caché
DataCache.clearAll()
```

#### Implementación Completa con Caché
```kotlin
@Composable
fun ExplorarScreen() {
    var lugares by remember { mutableStateOf<List<Lugar>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Intentar cargar desde caché primero
        val cached = DataCache.get<List<Lugar>>(AppConstants.Cache.LUGARES_KEY)
        if (cached != null) {
            lugares = cached
            isLoading = false
        } else {
            // Cargar desde API
            scope.launch {
                val result = apiCall { RetrofitClient.instance.getLugares() }
                when (result) {
                    is LoadingState.Success -> {
                        lugares = result.data.values
                        DataCache.put(AppConstants.Cache.LUGARES_KEY, lugares)
                    }
                    is LoadingState.Error -> {
                        Toast.makeText(context, result.message, LENGTH_SHORT).show()
                    }
                }
                isLoading = false
            }
        }
    }
}
```

---

## 2. StateComponents - UI Reutilizable {#statecomponents}

### EmptyState - Estado Vacío Personalizable

```kotlin
// Uso básico
EmptyState(
    icon = Icons.Default.SearchOff,
    title = "No hay resultados",
    subtitle = "Intenta con otros términos"
)

// Con botón de acción
EmptyState(
    icon = Icons.Default.LocationOn,
    title = "No has visitado lugares",
    subtitle = "Explora y gana puntos",
    actionLabel = "Explorar Ahora",
    onAction = {
        val intent = Intent(context, ExplorarActivity::class.java)
        context.startActivity(intent)
    }
)

// Ejemplos por escenario
EmptyState(
    icon = Icons.Default.NotificationsNone,
    title = "Sin notificaciones",
    subtitle = "Aquí aparecerán tus alertas"
)

EmptyState(
    icon = Icons.Default.Route,
    title = "No hay rutas guardadas",
    subtitle = "Crea tu primera ruta turística",
    actionLabel = "Crear Ruta",
    onAction = { showCreateDialog = true }
)
```

### LoadingState - Indicador de Carga

```kotlin
// Uso básico
LoadingState(message = "Cargando datos...")

// Sin mensaje
LoadingState()

// Diferentes mensajes según contexto
if (isLoadingLugares) {
    LoadingState(message = "Buscando lugares cercanos...")
}

if (isCheckingIn) {
    LoadingState(message = "Registrando check-in...")
}

if (isCanjearPromo) {
    LoadingState(message = "Canjeando promoción...")
}
```

### ErrorState - Manejo de Errores

```kotlin
// Con retry
ErrorState(
    message = "No se pudieron cargar los datos",
    onRetry = {
        scope.launch {
            cargarDatos()
        }
    }
)

// Errores específicos
if (error != null) {
    ErrorState(
        message = when (error) {
            is NetworkException -> "Error de red"
            is TimeoutException -> "Tiempo de espera agotado"
            is ApiException -> "Error del servidor"
            else -> "Error desconocido"
        },
        onRetry = { retry() }
    )
}
```

---

## 3. AppConstants - Constantes y Extensions {#appconstants}

### Colores Centralizados

```kotlin
// En cualquier Composable
Card(
    colors = CardDefaults.cardColors(
        containerColor = AppConstants.Colors.PrimaryGreen
    )
)

FloatingActionButton(
    containerColor = AppConstants.Colors.DarkGreen,
    onClick = { }
) { }

Text(
    text = "Puntos",
    color = AppConstants.Colors.BlueGreen
)

Icon(
    tint = AppConstants.Colors.Gold
)

// Gradientes
Box(
    modifier = Modifier.background(
        Brush.verticalGradient(
            colors = listOf(
                AppConstants.Colors.PrimaryGreen,
                AppConstants.Colors.DarkGreen
            )
        )
    )
)
```

### Animaciones Consistentes

```kotlin
// Duración de animaciones
AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn(animationSpec = tween(AppConstants.Animation.MEDIUM))
)

// Diferentes duraciones
val shortAnim = AppConstants.Animation.SHORT    // 200ms
val mediumAnim = AppConstants.Animation.MEDIUM  // 500ms
val longAnim = AppConstants.Animation.LONG      // 1000ms
```

### Mensajes Unificados

```kotlin
// Mensajes de error
Toast.makeText(context, AppConstants.Messages.NO_INTERNET, LENGTH_SHORT).show()
Toast.makeText(context, AppConstants.Messages.CONNECTION_ERROR, LENGTH_SHORT).show()
Toast.makeText(context, AppConstants.Messages.ERROR_LOADING, LENGTH_SHORT).show()

// Mensajes de éxito
Toast.makeText(context, AppConstants.Messages.CLAIM_SUCCESS, LENGTH_SHORT).show()
Toast.makeText(context, AppConstants.Messages.SAVE_SUCCESS, LENGTH_SHORT).show()

// Validaciones
if (campo.isEmpty()) {
    Toast.makeText(context, AppConstants.Messages.REQUIRED_FIELD, LENGTH_SHORT).show()
}
```

### Extension Functions

#### formatPoints() - Formateo de Puntos
```kotlin
val puntos = 1234
Text(text = puntos.formatPoints()) // "1,234 pts"

// Casos de uso
Card {
    Text(text = "Tienes ${userPoints.formatPoints()}")
}

Row {
    Icon(Icons.Default.Stars)
    Text(text = lugar.puntosOtorgados.formatPoints())
}
```

#### isValidEmail() - Validación de Email
```kotlin
val email = "usuario@ejemplo.com"
if (email.isValidEmail()) {
    // Email válido
} else {
    // Mostrar error
}

// En formularios
OutlinedTextField(
    value = email,
    onValueChange = { email = it },
    isError = email.isNotEmpty() && !email.isValidEmail(),
    supportingText = {
        if (email.isNotEmpty() && !email.isValidEmail()) {
            Text("Email inválido", color = Color.Red)
        }
    }
)

// Con validación completa
fun validarFormulario(): Boolean {
    if (!email.isValidEmail()) {
        Toast.makeText(context, "Email inválido", LENGTH_SHORT).show()
        return false
    }
    return true
}
```

#### capitalizeFirst() - Capitalizar Primera Letra
```kotlin
val nombre = "juan pérez"
Text(text = nombre.capitalizeFirst()) // "Juan pérez"

// Casos de uso
Text(text = userName.capitalizeFirst())
Text(text = ciudad.capitalizeFirst())
Text(text = categoria.nombre.capitalizeFirst())
```

---

## 4. NetworkHelper - Validación de Conectividad {#networkhelper}

### isNetworkAvailable() - Check de Conectividad

```kotlin
// Antes de cualquier llamada API
LaunchedEffect(Unit) {
    if (!NetworkHelper.isNetworkAvailable(context)) {
        Toast.makeText(context, AppConstants.Messages.NO_INTERNET, LENGTH_SHORT).show()
        return@LaunchedEffect
    }
    
    // Proceder con carga de datos
    cargarDatos()
}

// En un botón
Button(
    onClick = {
        if (NetworkHelper.isNetworkAvailable(context)) {
            canjearPromocion()
        } else {
            Toast.makeText(context, "Sin conexión a internet", LENGTH_SHORT).show()
        }
    }
) {
    Text("Canjear")
}
```

### getConnectionType() - Tipo de Conexión

```kotlin
val connectionType = NetworkHelper.getConnectionType(context)

when (connectionType) {
    ConnectionType.WIFI -> {
        // Cargar imágenes de alta calidad
        loadHighQualityImages()
    }
    ConnectionType.CELLULAR -> {
        // Cargar imágenes optimizadas
        loadOptimizedImages()
    }
    ConnectionType.ETHERNET -> {
        // Conexión más rápida
        loadFullData()
    }
    ConnectionType.NONE -> {
        // Modo offline
        showCachedData()
    }
}

// Mostrar indicador de conexión
TopAppBar(
    title = { Text("Kairos") },
    actions = {
        when (NetworkHelper.getConnectionType(context)) {
            ConnectionType.WIFI -> Icon(Icons.Default.Wifi, "WiFi")
            ConnectionType.CELLULAR -> Icon(Icons.Default.SignalCellular4Bar, "Móvil")
            ConnectionType.NONE -> Icon(Icons.Default.WifiOff, "Sin conexión")
            else -> {}
        }
    }
)
```

### withNetwork() - Extension Function

```kotlin
// Ejecutar código solo con conexión
suspend fun cargarDatos() = withNetwork(context) {
    val response = RetrofitClient.instance.getLugares()
    if (response.isSuccessful) {
        lugares = response.body()!!.values
    }
}

// Con manejo de error personalizado
suspend fun enviarMensaje() {
    withNetwork(context) {
        RetrofitClient.instance.enviarMensajeContacto(mensaje)
        Toast.makeText(context, "Mensaje enviado", LENGTH_SHORT).show()
    } ?: run {
        // No hay conexión
        Toast.makeText(context, "Mensaje guardado para enviar después", LENGTH_SHORT).show()
    }
}
```

---

## 5. LocationUtils - Geolocalización {#locationutils}

### calculateDistance() - Cálculo de Distancia (Haversine)

```kotlin
val distance = LocationUtils.calculateDistance(
    lat1 = userLat,
    lon1 = userLng,
    lat2 = lugarLat,
    lon2 = lugarLng
)

// Mostrar distancia
Text(text = "A ${LocationUtils.formatDistance(distance)}")

// Ordenar lugares por distancia
val lugaresCercanos = lugares.sortedBy { lugar ->
    LocationUtils.calculateDistance(userLat, userLng, lugar.latitud, lugar.longitud)
}
```

### isNearLocation() - Validación de Proximidad

```kotlin
// Validar check-in (100m radius)
val canCheckIn = LocationUtils.isNearLocation(
    userLat = userLocation.latitude,
    userLon = userLocation.longitude,
    targetLat = lugar.latitud,
    targetLon = lugar.longitud
)

if (canCheckIn) {
    // Permitir check-in
    reclamarPuntos()
} else {
    val distance = LocationUtils.calculateDistance(
        userLocation.latitude, userLocation.longitude,
        lugar.latitud, lugar.longitud
    )
    Toast.makeText(
        context,
        "Debes estar a menos de 100m (estás a ${LocationUtils.formatDistance(distance)})",
        LENGTH_LONG
    ).show()
}

// Con radio personalizado
val isNear = LocationUtils.isNearLocation(
    userLat, userLon, targetLat, targetLon,
    maxDistanceMeters = 500.0 // 500 metros
)
```

### formatDistance() - Formato Legible

```kotlin
val distancia1 = LocationUtils.formatDistance(50.0)    // "50 m"
val distancia2 = LocationUtils.formatDistance(850.0)   // "850 m"
val distancia3 = LocationUtils.formatDistance(1500.0)  // "1.5 km"
val distancia4 = LocationUtils.formatDistance(5230.0)  // "5.2 km"

// En UI
Card {
    Row {
        Icon(Icons.Default.LocationOn)
        Text(text = LocationUtils.formatDistance(distance))
    }
}
```

### getCardinalDirection() - Dirección Cardinal

```kotlin
val direccion = LocationUtils.getCardinalDirection(
    fromLat = userLat,
    fromLon = userLng,
    toLat = lugarLat,
    toLon = lugarLng
)

Text(text = "El lugar está al $direccion") // "El lugar está al NE"

// Con icono
val iconDirection = when (direccion) {
    "N" -> Icons.Default.NorthEast
    "S" -> Icons.Default.SouthEast
    "E" -> Icons.Default.East
    "W" -> Icons.Default.West
    else -> Icons.Default.Navigation
}

Icon(iconDirection, direccion)
```

### calculateBearing() - Ángulo de Dirección

```kotlin
val bearing = LocationUtils.calculateBearing(
    fromLat = userLat,
    fromLon = userLng,
    toLat = lugarLat,
    toLon = lugarLng
)

// Bearing es un ángulo entre 0-360°
// 0° = Norte, 90° = Este, 180° = Sur, 270° = Oeste

// Rotar icono de navegación
Icon(
    Icons.Default.Navigation,
    contentDescription = null,
    modifier = Modifier.rotate(bearing.toFloat())
)
```

---

## 6. Patrones de Uso Recomendados {#patrones}

### Patrón 1: Carga de Datos con Caché y Validación

```kotlin
@Composable
fun DataScreen() {
    var data by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loadingState by remember { mutableStateOf<LoadingState<List<Item>>>(LoadingState.Idle) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            // 1. Validar conectividad
            if (!NetworkHelper.isNetworkAvailable(context)) {
                Toast.makeText(context, AppConstants.Messages.NO_INTERNET, LENGTH_SHORT).show()
                return@launch
            }

            // 2. Intentar caché primero
            val cached = DataCache.get<List<Item>>(AppConstants.Cache.DATA_KEY)
            if (cached != null) {
                data = cached
                return@launch
            }

            // 3. Cargar desde API
            loadingState = LoadingState.Loading
            val result = apiCall { RetrofitClient.instance.getData() }
            
            when (result) {
                is LoadingState.Success -> {
                    data = result.data
                    DataCache.put(AppConstants.Cache.DATA_KEY, data)
                    loadingState = LoadingState.Success(data)
                }
                is LoadingState.Error -> {
                    loadingState = result
                    Toast.makeText(context, result.message, LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // UI según estado
    when (loadingState) {
        is LoadingState.Loading -> LoadingState(message = "Cargando...")
        is LoadingState.Error -> ErrorState(
            message = (loadingState as LoadingState.Error).message,
            onRetry = { loadData() }
        )
        else -> {
            if (data.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No hay datos",
                    subtitle = "Intenta recargar",
                    actionLabel = "Recargar",
                    onAction = { loadData() }
                )
            } else {
                LazyColumn { items(data) { item -> ItemCard(item) } }
            }
        }
    }
}
```

### Patrón 2: Check-In con Validación de Distancia

```kotlin
fun realizarCheckIn(
    lugar: Lugar,
    userLocation: Location,
    onSuccess: (Int) -> Unit,
    onError: (String) -> Unit
) {
    // 1. Validar conectividad
    if (!NetworkHelper.isNetworkAvailable(context)) {
        onError(AppConstants.Messages.NO_INTERNET)
        return
    }

    // 2. Validar proximidad
    val isNear = LocationUtils.isNearLocation(
        userLocation.latitude,
        userLocation.longitude,
        lugar.latitud,
        lugar.longitud
    )

    if (!isNear) {
        val distance = LocationUtils.calculateDistance(
            userLocation.latitude, userLocation.longitude,
            lugar.latitud, lugar.longitud
        )
        onError("Debes estar cerca del lugar (estás a ${LocationUtils.formatDistance(distance)})")
        return
    }

    // 3. Realizar check-in
    scope.launch {
        val result = apiCall {
            RetrofitClient.instance.reclamarPuntos(
                ReclamarPuntosRequest(
                    idUsuario = userId,
                    idLugar = lugar.idLugar,
                    latitudUsuario = userLocation.latitude,
                    longitudUsuario = userLocation.longitude
                )
            )
        }

        when (result) {
            is LoadingState.Success -> {
                val puntosGanados = result.data.puntosGanados
                onSuccess(puntosGanados)
            }
            is LoadingState.Error -> {
                onError(result.message)
            }
        }
    }
}
```

### Patrón 3: Formulario con Validación Completa

```kotlin
@Composable
fun ContactForm() {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun validarYEnviar() {
        // Validaciones
        if (nombre.isEmpty()) {
            Toast.makeText(context, "El nombre es requerido", LENGTH_SHORT).show()
            return
        }
        
        if (!email.isValidEmail()) {
            Toast.makeText(context, "Email inválido", LENGTH_SHORT).show()
            return
        }
        
        if (mensaje.length < 10) {
            Toast.makeText(context, "El mensaje es muy corto", LENGTH_SHORT).show()
            return
        }

        // Validar conectividad y enviar
        scope.launch {
            withNetwork(context) {
                isLoading = true
                val result = apiCall {
                    RetrofitClient.instance.enviarMensaje(
                        MensajeContacto(
                            nombre = nombre.capitalizeFirst(),
                            email = email,
                            mensaje = mensaje
                        )
                    )
                }
                
                isLoading = false
                when (result) {
                    is LoadingState.Success -> {
                        Toast.makeText(context, AppConstants.Messages.SAVE_SUCCESS, LENGTH_SHORT).show()
                        // Limpiar formulario
                        nombre = ""
                        email = ""
                        mensaje = ""
                    }
                    is LoadingState.Error -> {
                        Toast.makeText(context, result.message, LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column {
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre") }
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            isError = email.isNotEmpty() && !email.isValidEmail()
        )
        
        OutlinedTextField(
            value = mensaje,
            onValueChange = { mensaje = it },
            label = { Text("Mensaje") },
            minLines = 3
        )
        
        Button(
            onClick = { validarYEnviar() },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Enviar")
            }
        }
    }
}
```

---

## ✅ Checklist de Mejores Prácticas

### Antes de Llamar API
- [ ] Validar `NetworkHelper.isNetworkAvailable()`
- [ ] Verificar caché con `DataCache.get()`
- [ ] Usar `LoadingState` para estado de carga
- [ ] Manejar errores con `apiCall` wrapper

### En UI States
- [ ] Usar `LoadingState` component para carga
- [ ] Usar `EmptyState` para listas vacías
- [ ] Usar `ErrorState` para errores con retry
- [ ] Agregar mensajes descriptivos

### Con Constantes
- [ ] Colores desde `AppConstants.Colors`
- [ ] Mensajes desde `AppConstants.Messages`
- [ ] Duraciones desde `AppConstants.Animation`
- [ ] Usar extensions (formatPoints, isValidEmail, etc.)

### En Geolocalización
- [ ] Usar `isNearLocation` para validar proximidad
- [ ] Mostrar distancia con `formatDistance`
- [ ] Indicar dirección con `getCardinalDirection`
- [ ] Calcular bearing para iconos de navegación

---

**Última actualización:** 4 de diciembre de 2025  
**Versión:** 1.0.0  
**Autor:** GitHub Copilot
