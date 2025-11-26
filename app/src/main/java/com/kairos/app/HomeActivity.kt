package com.kairos.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext
import com.google.accompanist.pager.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kairos.app.models.ActividadFisicaRequest
import com.kairos.app.models.UsoDigitalRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.*
import com.kairos.app.notifications.DailyInsightWorker
import java.util.Calendar

class HomeActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sessionManager: SessionManager
    private var emergencyContactNumber = mutableStateOf<String?>(null)
    private var showSosDialog = mutableStateOf(false)

    // Lógica Sensores y Permisos
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepsCount = mutableStateOf("0")
    private var permissionGranted = mutableStateOf(false)
    private var hasUsagePermission = mutableStateOf(false)
    private var socialMediaUsageTime = mutableStateOf("0 min")

    // --- LANZADORES ---
    private val requestSosPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                proceedWithSosActions()
            } else {
                Toast.makeText(this, "Se necesitan permisos para SOS", Toast.LENGTH_LONG).show()
            }
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) launchContactPicker()
            else Toast.makeText(this, "Permiso de contactos denegado", Toast.LENGTH_SHORT).show()
        }

    private val requestActivityPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                permissionGranted.value = true
                setupStepCounter()
            } else {
                Toast.makeText(this, "Permiso de actividad denegado", Toast.LENGTH_LONG).show()
            }
        }

    private val usageStatsSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkUsageStatsPermission()
        }

    @SuppressLint("Range")
    private val contactPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contactUri: Uri? = result.data?.data
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                contentResolver.query(contactUri!!, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        sessionManager.saveEmergencyContact(number)
                        emergencyContactNumber.value = number
                        Toast.makeText(this, "Contacto guardado: $number", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    // --- CICLO DE VIDA ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        emergencyContactNumber.value = sessionManager.fetchEmergencyContact()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val token = sessionManager.fetchAuthToken()
        if (token == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

                setContent {
            KairosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Estado del insight (se carga del servidor)
                    var aiMessage by remember { mutableStateOf("🌅 Cargando tu análisis personalizado...") }
                    
                    // Efecto para cargar el insight
                    LaunchedEffect(Unit) {
                        cargarInsightDelServidor { mensaje -> aiMessage = mensaje }
                    }

                    HomeScreen(
                        onLogout = {
                            sessionManager.clearSession()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onOpenMap = { startActivity(Intent(this, MapActivity::class.java)) },
                        onSosClick = { showSosDialog.value = true },
                        emergencyContact = emergencyContactNumber.value,
                        onSelectContact = { handleSelectContactClick() },
                        userName = "Aventurero",
                        showSosDialog = showSosDialog.value,
                        onDismissSosDialog = { showSosDialog.value = false },
                        onConfirmSos = {
                            showSosDialog.value = false
                            handleSosPermissionCheck()
                        },
                        steps = stepsCount.value,
                        onStepsPermissionClick = { checkAndRequestActivityPermission() },
                        hasActivityPermission = permissionGranted.value,
                        onRecompensasClick = { startActivity(Intent(this, RecompensasActivity::class.java)) },
                        hasUsagePermission = hasUsagePermission.value,
                        onUsagePermissionClick = {
                            if (hasUsagePermission.value) startActivity(Intent(this, UsageDetailActivity::class.java))
                            else requestUsageStatsPermission()
                        },
                        usageTime = socialMediaUsageTime.value,
                        onConfigClick = { startActivity(Intent(this, AjustesActivity::class.java)) },
                        insightMessage = aiMessage // 👇 AHORA SÍ FUNCIONARÁ
                    )
                }
            }
        }
        checkAndRequestActivityPermission()
        checkUsageStatsPermission()
        programarNotificacionDiaria()
    }

    override fun onResume() {
        super.onResume()
        if (permissionGranted.value) setupStepCounter()
        checkUsageStatsPermission()
        getSocialMediaUsage()
        sincronizarDatosConBackend()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    // --- LÓGICA NUEVA: SINCRONIZACIÓN CON BACKEND ---
    private fun sincronizarDatosConBackend() {
        val pasosHoy = stepsCount.value.toIntOrNull() ?: 0
        val tiempoTexto = socialMediaUsageTime.value
        var minutosTotales = 0
        try {
            if (tiempoTexto.contains("h")) {
                val partes = tiempoTexto.split("h")
                val horas = partes[0].trim().toInt()
                val minutos = partes[1].replace("m", "").trim().toInt()
                minutosTotales = (horas * 60) + minutos
            } else {
                minutosTotales = tiempoTexto.replace("min", "").replace(" ", "").toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            minutosTotales = 0
        }

        val userId = sessionManager.fetchUserId()
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (pasosHoy > 0) {
                    RetrofitClient.instance.enviarPasos(
                        ActividadFisicaRequest(userId, pasosHoy, fechaHoy)
                    )
                }
                if (minutosTotales > 0) {
                    RetrofitClient.instance.enviarUsoDigital(
                        UsoDigitalRequest(userId, minutosTotales, fechaHoy)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- LÓGICA SENSORES Y PERMISOS ---
    private fun checkAndRequestActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED -> {
                    permissionGranted.value = true
                    setupStepCounter()
                }
                else -> requestActivityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            permissionGranted.value = true
            setupStepCounter()
        }
    }

    private fun setupStepCounter() {
        if (stepCounterSensor != null) {
            sensorManager?.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            stepsCount.value = "N/A"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSensor = event.values[0].toInt()
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val savedDate = sessionManager.fetchStepsDate()
            val savedBaseline = sessionManager.fetchStepsBaseline()
            var stepsToDisplay = 0

            if (savedDate != todayDate) {
                sessionManager.saveStepsDate(todayDate)
                sessionManager.saveStepsBaseline(totalStepsSensor)
                stepsToDisplay = 0
            } else {
                if (savedBaseline == -1 || totalStepsSensor < savedBaseline) {
                    sessionManager.saveStepsBaseline(totalStepsSensor)
                    stepsToDisplay = 0
                } else {
                    stepsToDisplay = totalStepsSensor - savedBaseline
                }
            }
            stepsCount.value = stepsToDisplay.toString()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        hasUsagePermission.value = (mode == AppOpsManager.MODE_ALLOWED)
        if (hasUsagePermission.value) getSocialMediaUsage()
    }

    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir ajustes", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LÓGICA DE INSIGHTS (DESDE EL SERVIDOR - IA REAL) ---
    private fun cargarInsightDelServidor(onResult: (String) -> Unit) {
        val userId = sessionManager.fetchUserId()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getInsight(userId)
                if (response.isSuccessful) {
                    val insight = response.body()
                    val mensaje = insight?.mensaje ?: "💙 Sigue explorando y cuidando tu bienestar."
                    // Actualizamos en el hilo principal
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(mensaje)
                    }
                } else {
                    // Fallback si el servidor falla
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult("🌞 Recuerda: cada paso cuenta, cada minuto lejos de la pantalla es vida ganada.")
                    }
                }
            } catch (e: Exception) {
                // Fallback si hay error de conexión
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult("💡 No se pudo conectar al servidor. Sigue explorando.")
                }
            }
        }
    }

    private fun getSocialMediaUsage() {
        if (!hasUsagePermission.value) return
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = java.util.Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val queryUsageStats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val socialPackages = listOf("com.zhiliaoapp.musically", "com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.twitter.android", "com.snapchat.android", "com.whatsapp")
        var totalTimeMillis = 0L
        for (app in queryUsageStats) {
            if (socialPackages.contains(app.packageName)) {
                totalTimeMillis += app.totalTimeInForeground
            }
        }
        val hours = TimeUnit.MILLISECONDS.toHours(totalTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis) % 60
        socialMediaUsageTime.value = if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }

    // --- LÓGICA SOS ---
    private fun launch911Dialer() {
        try {
            startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:911") })
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error permisos llamada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:911") })
            } catch (e2: Exception) { }
        }
    }

    private fun handleSosPermissionCheck() {
        val permissions = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            proceedWithSosActions()
        } else {
            requestSosPermissionsLauncher.launch(permissions)
        }
    }

    private fun proceedWithSosActions() {
        launch911Dialer()
        val contactNumber = sessionManager.fetchEmergencyContact()
        if (!contactNumber.isNullOrBlank()) sendEmergencySms(contactNumber)
    }

    @SuppressLint("MissingPermission")
    private fun sendEmergencySms(contactNumber: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val smsManager = SmsManager.getDefault()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val smsMessage = if (location != null) {
                    "¡AYUDA! Ubicación: http://googleusercontent.com/maps/google.com/9?q=${location.latitude},${location.longitude}"
                } else {
                    "¡AYUDA! Contáctame."
                }
                try {
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, "Alerta enviada", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { }
            }
    }

    private fun launchContactPicker() {
        contactPickerLauncher.launch(Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE })
    }

    private fun handleSelectContactClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) launchContactPicker()
        else requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    // --- NOTIFICACIONES PROGRAMADAS ---
    private fun programarNotificacionDiaria() {
        // Calcular el delay inicial (próximas 8:00 PM)
        val ahora = Calendar.getInstance()
        val proximaEjecucion = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20) // 8 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            // Si ya pasaron las 8 PM hoy, programar para mañana
            if (before(ahora)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delayInicial = proximaEjecucion.timeInMillis - ahora.timeInMillis

        // Crear la solicitud periódica (cada 24 horas)
        val workRequest = PeriodicWorkRequestBuilder<DailyInsightWorker>(
            24, TimeUnit.HOURS // Se repite cada 24 horas
        )
            .setInitialDelay(delayInicial, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con internet
                    .build()
            )
            .build()

        // Programar el trabajo (reemplaza cualquier trabajo previo con el mismo nombre)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_insight_notification",
            ExistingPeriodicWorkPolicy.KEEP, // No duplicar si ya existe
            workRequest
        )
    }
}

// =============================================
// UI CON NAVIGATION DRAWER (MENÚ LATERAL)
// =============================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenMap: () -> Unit,
    onSosClick: () -> Unit,
    userName: String,
    emergencyContact: String?,
    onSelectContact: () -> Unit,
    showSosDialog: Boolean,
    onDismissSosDialog: () -> Unit,
    onConfirmSos: () -> Unit,
    steps: String,
    onStepsPermissionClick: () -> Unit,
    hasActivityPermission: Boolean,
    onRecompensasClick: () -> Unit,
    hasUsagePermission: Boolean,
    onUsagePermissionClick: () -> Unit,
    usageTime: String,
    onConfigClick: () -> Unit,
    insightMessage: String // 👈 PARÁMETRO AGREGADO AQUÍ
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "KAIROS",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Divider()
                NavigationDrawerItem(
                    label = { Text("Inicio") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Home, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Perfil") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        val intent = Intent(context, PerfilActivity::class.java)
                        context.startActivity(intent)
                    },
                    icon = { Icon(Icons.Default.Person, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Configuración") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onConfigClick()
                    },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
                Spacer(modifier = Modifier.weight(1f))
                Divider()
                NavigationDrawerItem(
                    label = { Text("Cerrar Sesión") },
                    selected = false,
                    onClick = onLogout,
                    icon = { Icon(Icons.Default.ExitToApp, null) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Kairos", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🌿", fontSize = 20.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(onClick = onOpenMap) {
                        Icon(Icons.Default.Map, contentDescription = "Mapa")
                    }
                    FloatingActionButton(
                        onClick = { onSosClick() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text("SOS", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            },
            content = { innerPadding ->
                if (showSosDialog) {
                    AlertDialog(
                        onDismissRequest = { onDismissSosDialog() },
                        title = { Text("Confirmación de Alerta SOS") },
                        text = { Text("¿Estás seguro? Se llamará al 911 y se enviará tu ubicación a tu contacto.") },
                        confirmButton = {
                            Button(onClick = { onConfirmSos() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("¡SÍ, ACTIVAR!")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { onDismissSosDialog() }) { Text("Cancelar") }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AnimatedGreeting(userName = userName)

                    // 👇 AQUÍ VA LA NUEVA TARJETA
                    Spacer(modifier = Modifier.height(16.dp))
                    InsightCard(insight = insightMessage)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Explora tu ciudad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DiscoverCarousel()

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Tu Bienestar Hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            StepCounterCardMini(steps, hasActivityPermission, onStepsPermissionClick)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            UsageMonitorCardMini(hasUsagePermission, usageTime, onUsagePermissionClick)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Accesos Rápidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    EmergencyContactCard(emergencyContact, onSelectContact)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRecompensasClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.CardGiftcard, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Recompensas Disponibles")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    NatureStrip()
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        )
    }
}

// -----------------------------
// NUEVO COMPOSABLE: Tarjeta de Insights (Coach IA)
// -----------------------------
@Composable
fun InsightCard(insight: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // Bordes más curvos, más moderno
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) // Un poco transparente para blending
        ),
        elevation = CardDefaults.cardElevation(0.dp) // Flat design
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icono Vectorial
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy, // Icono de Robot/IA nativo
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    "KAIROS COACH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// --- COMPOSABLES MINIMALISTAS ---

@Composable
fun StepCounterCardMini(steps: String, hasPermission: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.DirectionsWalk, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            if (hasPermission) {
                Text(steps, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Pasos", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Activar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UsageMonitorCardMini(hasPermission: Boolean, time: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.QueryStats, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasPermission) {
                Text(time, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tiempo Apps", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Activar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun DiscoverCarousel() {
    // Usamos Iconos vectoriales en lugar de Strings de emojis
    val items = listOf(
        Triple("Naturaleza", "Parques y Rutas", Icons.Default.Terrain),
        Triple("Cultura", "Museos y Arte", Icons.Default.AccountBalance), // AccountBalance parece museo
        Triple("Local", "Mercados y Plazas", Icons.Default.Storefront)
    )

    val pagerState = rememberPagerState(initialPage = 0)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Aumentamos la altura para que quepa "más info" y se vea imponente
        HorizontalPager(
            state = pagerState,
            count = items.size,
            modifier = Modifier.height(180.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp) // Espacio a los lados
        ) { page ->
            val (title, desc, icon) = items[page]

            Card(
                modifier = Modifier
                    .padding(horizontal = 8.dp) // Separación entre tarjetas
                    .fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Icono gigante de fondo (marca de agua decorativa)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        modifier = Modifier
                            .size(140.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 30.dp, y = 30.dp) // Desplazado a la esquina
                    )

                    // Contenido
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .align(Alignment.TopStart)
                    ) {
                        // Icono pequeño principal
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        // Botoncito falso de "Ver más"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Explorar",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedGreeting(userName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hola, $userName", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmergencyContactCard(contactNumber: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Contacto Emergencia", fontWeight = FontWeight.Bold)
                Text(contactNumber ?: "Configurar ahora", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.Phone, null)
        }
    }
}

@Composable
fun NatureStrip() {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { Card(modifier = Modifier.size(60.dp, 80.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("🌳") } } }
    }
}