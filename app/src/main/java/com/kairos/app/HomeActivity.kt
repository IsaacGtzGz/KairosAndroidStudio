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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlin.math.PI
import kotlin.math.sin
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.pager.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kairos.app.models.ActividadFisicaRequest
import com.kairos.app.models.UsoDigitalRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.formatPoints
import com.kairos.app.utils.*
import com.kairos.app.utils.startActivityWithSlideTransition
import com.kairos.app.utils.finishWithFadeTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.Calendar
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kairos.app.notifications.DailyInsightWorker

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
                Toast.makeText(this, AppConstants.Messages.PERMISSION_NEEDED_SOS, Toast.LENGTH_LONG).show()
            }
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) launchContactPicker()
            else Toast.makeText(this, AppConstants.Messages.PERMISSION_CONTACTS_DENIED, Toast.LENGTH_SHORT).show()
        }

    private val requestActivityPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                permissionGranted.value = true
                setupStepCounter()
            } else {
                Toast.makeText(this, AppConstants.Messages.PERMISSION_ACTIVITY_DENIED, Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this, "${AppConstants.Messages.CONTACT_SAVED}: $number", Toast.LENGTH_LONG).show()
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
                    var aiMessage by remember { mutableStateOf("Cargando tu análisis personalizado...") }
                    
                    // Nombre del usuario desde SessionManager
                    val userName = sessionManager.fetchUserName()?.split(" ")?.firstOrNull() ?: "Aventurero"
                    
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
                        onOpenMap = { startActivityWithSlideTransition(Intent(this, MapActivity::class.java)) },
                        onSosClick = { showSosDialog.value = true },
                        emergencyContact = emergencyContactNumber.value,
                        onSelectContact = { handleSelectContactClick() },
                        userName = userName,
                        showSosDialog = showSosDialog.value,
                        onDismissSosDialog = { showSosDialog.value = false },
                        onConfirmSos = {
                            showSosDialog.value = false
                            handleSosPermissionCheck()
                        },
                        steps = stepsCount.value,
                        onStepsPermissionClick = { checkAndRequestActivityPermission() },
                        hasActivityPermission = permissionGranted.value,
                        onRecompensasClick = { startActivityWithSlideTransition(Intent(this, RecompensasActivity::class.java)) },
                        hasUsagePermission = hasUsagePermission.value,
                        onUsagePermissionClick = {
                            if (hasUsagePermission.value) startActivityWithSlideTransition(Intent(this, UsageDetailActivity::class.java))
                            else requestUsageStatsPermission()
                        },
                        usageTime = socialMediaUsageTime.value,
                        onConfigClick = { startActivityWithSlideTransition(Intent(this, AjustesActivity::class.java)) },
                        insightMessage = aiMessage,
                        onExplorarClick = { startActivityWithSlideTransition(Intent(this, MapActivity::class.java)) },
                        onRutasClick = { startActivityWithSlideTransition(Intent(this, RutasActivity::class.java)) },
                        onNotificacionesClick = { startActivityWithSlideTransition(Intent(this, NotificacionesActivity::class.java)) },
                        onFAQClick = { startActivityWithSlideTransition(Intent(this, FAQActivity::class.java)) },
                        onContactoClick = { startActivityWithSlideTransition(Intent(this, ContactoActivity::class.java)) }
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
        refrescarPuntosUsuario()
    }
    
    private fun refrescarPuntosUsuario() {
        lifecycleScope.launch {
            try {
                val userId = sessionManager.fetchUserId()
                val response = RetrofitClient.instance.getUsuario(userId)
                if (response.isSuccessful) {
                    val puntosActualizados = response.body()?.puntosAcumulados ?: 0
                    sessionManager.saveUserPoints(puntosActualizados)
                }
            } catch (e: Exception) {
                // Si falla, no hacer nada
            }
        }
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
            Toast.makeText(this, AppConstants.Messages.SETTINGS_ERROR, Toast.LENGTH_SHORT).show()
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
                    val mensaje = insight?.mensaje ?: "Sigue explorando y cuidando tu bienestar."
                    // Actualizamos en el hilo principal
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(mensaje)
                    }
                } else {
                    // Fallback si el servidor falla
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult("Recuerda: cada paso cuenta, cada minuto lejos de la pantalla es vida ganada.")
                    }
                }
            } catch (e: Exception) {
                // Fallback si hay error de conexión
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult("No se pudo conectar al servidor. Sigue explorando.")
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
            Toast.makeText(this, AppConstants.Messages.PERMISSION_CALL_ERROR, Toast.LENGTH_SHORT).show()
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
                    "¡AYUDA! Ubicación: https://www.google.com/maps?q=${location.latitude},${location.longitude}"
                } else {
                    "¡AYUDA! Contáctame."
                }
                try {
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, AppConstants.Messages.ALERT_SENT, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { exception ->
                // Si falla obtener ubicación, enviar SMS sin ubicación
                val smsMessage = "¡AYUDA! Contáctame."
                try {
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, AppConstants.Messages.ALERT_SENT, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
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
    insightMessage: String,
    onExplorarClick: () -> Unit,
    onRutasClick: () -> Unit,
    onNotificacionesClick: () -> Unit,
    onFAQClick: () -> Unit,
    onContactoClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val sessionManager = com.kairos.app.utils.SessionManager(context)
    val userPoints = sessionManager.fetchUserPoints() ?: 0

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
                    label = { Text("Mis Puntos") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(context, HistorialPuntosActivity::class.java))
                    },
                    icon = { Icon(Icons.Default.Stars, null) },
                    badge = { Text("$userPoints", fontSize = 12.sp) }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Explorar") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onExplorarClick()
                    },
                    icon = { Icon(Icons.Default.Search, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Recompensas") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onRecompensasClick()
                    },
                    icon = { Icon(Icons.Default.CardGiftcard, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Rutas") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onRutasClick()
                    },
                    icon = { Icon(Icons.Default.Route, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Notificaciones") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNotificacionesClick()
                    },
                    icon = { Icon(Icons.Default.Notifications, null) }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Ayuda") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onFAQClick()
                    },
                    icon = { Icon(Icons.Default.HelpOutline, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Contacto") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onContactoClick()
                    },
                    icon = { Icon(Icons.Default.Email, null) }
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
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    },
                    actions = {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        Surface(
                            onClick = {
                                context.startActivity(android.content.Intent(context, HistorialPuntosActivity::class.java))
                            },
                            color = AppConstants.Colors.DarkGreen,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                                .pressEffect(minScale = 0.92f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Stars,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$userPoints",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = onOpenMap,
                        modifier = Modifier.pressEffect(minScale = 0.90f)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Mapa")
                    }
                    FloatingActionButton(
                        onClick = { onSosClick() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape,
                        modifier = Modifier.pressEffect(minScale = 0.85f)
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

                var contentVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(100)
                    contentVisible = true
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fadeInUp(contentVisible, delayMillis = 0)) {
                        ModernGreeting(userName = userName)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fadeInUp(contentVisible, delayMillis = 100)) {
                        ModernInsightCard(
                            insight = insightMessage,
                            onChatClick = {
                                context.startActivity(Intent(context, CoachChatActivity::class.java))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.slideInFromSide(contentVisible, fromLeft = true, delayMillis = 200)) {
                        Column {
                            Text(
                                "Explora tu ciudad",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Descubre lugares increíbles",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fadeInUp(contentVisible, delayMillis = 250)) {
                        DiscoverCarouselPremium(onOpenMap = onOpenMap)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.slideInFromSide(contentVisible, fromLeft = false, delayMillis = 300)) {
                        Text(
                            "Tu Bienestar Hoy", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f).bounceEntrance(contentVisible, delayMillis = 350)) {
                            StepCounterCardPremiumV2(steps, hasActivityPermission, onStepsPermissionClick)
                        }
                        Box(modifier = Modifier.weight(1f).bounceEntrance(contentVisible, delayMillis = 400)) {
                            UsageMonitorCardPremiumV2(hasUsagePermission, usageTime, onUsagePermissionClick)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.fadeInUp(contentVisible, delayMillis = 450)) {
                        Text("Accesos Rápidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fadeInUp(contentVisible, delayMillis = 500)) {
                        QuickActionsStrip(
                            onRecompensasClick = onRecompensasClick,
                            onMapClick = onOpenMap,
                            onAjustesClick = onConfigClick,
                            onExplorarClick = onExplorarClick,
                            onRutasClick = onRutasClick,
                            onNotificacionesClick = onNotificacionesClick,
                            onFAQClick = onFAQClick,
                            onContactoClick = onContactoClick
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.bounceEntrance(contentVisible, delayMillis = 550)) {
                        EmergencyContactCardPremium(emergencyContact, onSelectContact)
                    }

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
    // Animación de aparición suave
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(insight) {
        visible = false
        kotlinx.coroutines.delay(100)
        visible = true
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(600)) + 
                androidx.compose.animation.slideInVertically(initialOffsetY = { it / 4 })
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Abrir chat con el coach
                    context.startActivity(android.content.Intent(context, CoachChatActivity::class.java))
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(4.dp) // Sombra sutil
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Icono con gradiente visual
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "TU COACH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "🤖",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Toca para chatear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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

// =============================================
// TARJETAS PREMIUM (CROMADAS)
// =============================================

@Composable
fun StepCounterCardPremium(steps: String, hasPermission: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Icono decorativo de fondo
            Icon(
                Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
            )
            
            // Contenido principal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icono con gradiente circular
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Datos
                if (hasPermission) {
                    Column {
                        Text(
                            text = steps,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Pasos Hoy",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Activar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Toca para configurar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageMonitorCardPremium(hasPermission: Boolean, time: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Icono decorativo de fondo
            Icon(
                Icons.Default.QueryStats,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
            )
            
            // Contenido principal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icono con gradiente circular
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.error
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Datos
                if (hasPermission) {
                    Column {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Tiempo Apps",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Activar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Toca para configurar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// =============================================
// COMPONENTES PREMIUM V2
// =============================================

@Composable
fun ModernGreeting(userName: String) {
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        currentHour < 12 -> "Buenos días"
        currentHour < 19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    
    val greetingIcon = when {
        currentHour < 12 -> Icons.Default.WbSunny
        currentHour < 19 -> Icons.Default.WbTwilight
        else -> Icons.Default.Nightlight
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect(minScale = 0.98f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = greetingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ModernInsightCard(insight: String, onChatClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(insight) {
        visible = false
        kotlinx.coroutines.delay(100)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { it / 4 })
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pressEffect(minScale = 0.97f)
                .clickable(onClick = onChatClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Análisis del Día",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Basado en tu actividad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onChatClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Conversar con el Coach",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun DiscoverCarouselPremium(onOpenMap: () -> Unit) {
    // Mapeo de categorías: Nombre, descripción, icono, ID de categoría, colores
    data class CarouselItem(
        val title: String,
        val desc: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val categoriaId: Int,
        val colors: List<Color>
    )
    
    val items = listOf(
        CarouselItem("Naturaleza", "Parques y senderos naturales", Icons.Default.Terrain, 1, 
            listOf(AppConstants.Colors.PrimaryGreen, Color(0xFF81C784))),
        CarouselItem("Cultura", "Museos y patrimonio cultural", Icons.Default.AccountBalance, 2,
            listOf(Color(0xFF5C6BC0), Color(0xFF9FA8DA))),
        CarouselItem("Local", "Mercados y zonas comerciales", Icons.Default.Storefront, 6,
            listOf(Color(0xFFFF7043), Color(0xFFFFAB91)))
    )
    
    val context = LocalContext.current

    val pagerState = rememberPagerState(initialPage = 0)

    HorizontalPager(
        state = pagerState,
        count = items.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) { page ->
        val item = items[page]
        val title = item.title
        val desc = item.desc
        val icon = item.icon
        val categoriaId = item.categoriaId
        val colors = item.colors

        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .pressEffect(minScale = 0.95f)
                .clickable {
                    Toast.makeText(context, "${AppConstants.Messages.EXPLORING_MAP}: $title", Toast.LENGTH_SHORT).show()
                    // Abrir mapa con categoría específica
                    val intent = Intent(context, MapActivity::class.java).apply {
                        putExtra("categoriaId", categoriaId)
                    }
                    context.startActivity(intent)
                },
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = colors.map { it.copy(alpha = 0.15f) },
                                start = Offset(0f, 0f),
                                end = Offset(1000f, 1000f)
                            )
                        )
                )

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors[0].copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 40.dp, y = 40.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.linearGradient(colors),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                colors[0].copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Explorar ahora",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors[0]
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = colors[0],
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepCounterCardPremiumV2(steps: String, hasPermission: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pressEffect(minScale = 0.96f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                if (hasPermission) {
                    Column {
                        Text(
                            text = steps,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pasos hoy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Activar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Contador de pasos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageMonitorCardPremiumV2(hasPermission: Boolean, time: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pressEffect(minScale = 0.96f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.QueryStats,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                if (hasPermission) {
                    Column {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uso digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Activar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Monitoreo de apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyContactCardPremium(contactNumber: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect(minScale = 0.97f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (contactNumber != null) 
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (contactNumber != null)
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = if (contactNumber != null)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Contacto de Emergencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    contactNumber ?: "No configurado • Toca para agregar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (contactNumber != null)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.error
                )
            }
            
            Icon(
                if (contactNumber != null) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = null,
                tint = if (contactNumber != null)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun QuickActionsStrip(
    onRecompensasClick: () -> Unit,
    onMapClick: () -> Unit,
    onAjustesClick: () -> Unit,
    onExplorarClick: () -> Unit,
    onRutasClick: () -> Unit,
    onNotificacionesClick: () -> Unit,
    onFAQClick: () -> Unit,
    onContactoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primera fila
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton("Explorar", Icons.Default.Search, onExplorarClick)
                QuickActionButton("Rutas", Icons.Default.Route, onRutasClick)
                QuickActionButton("Recompensas", Icons.Default.CardGiftcard, onRecompensasClick)
                QuickActionButton("Mapa", Icons.Default.Map, onMapClick)
            }
        }
        
        // Segunda fila
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton("Notificaciones", Icons.Default.Notifications, onNotificacionesClick)
                QuickActionButton("FAQ", Icons.Default.HelpOutline, onFAQClick)
                QuickActionButton("Contacto", Icons.Default.Email, onContactoClick)
                QuickActionButton("Ajustes", Icons.Default.Settings, onAjustesClick)
            }
        }
    }
}

@Composable
fun RowScope.QuickActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .pressEffect(minScale = 0.90f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}