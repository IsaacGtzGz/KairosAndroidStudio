package com.kairos.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import com.google.accompanist.pager.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.app.AppOpsManager
import android.os.Process
import android.provider.Settings
import androidx.compose.material.icons.filled.QueryStats

import android.app.usage.UsageStatsManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =======================================
// HomeActivity
// =======================================
class HomeActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sessionManager: SessionManager
    private var emergencyContactNumber = mutableStateOf<String?>(null)
    private var showSosDialog = mutableStateOf(false)

    // --- LÓGICA DEL SENSOR DE PASOS ---
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepsCount = mutableStateOf("0")
    private var permissionGranted = mutableStateOf(false)

    // Estado para saber si tenemos el permiso
    private var hasUsagePermission = mutableStateOf(false)

    // otras variables
    private var socialMediaUsageTime = mutableStateOf("0 min")


    // --- LANZADORES DE PERMISOS Y ACTIVIDADES ---
    private val requestSosPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                proceedWithSosActions()
            } else {
                Toast.makeText(this, "Se necesitan todos los permisos para la función SOS", Toast.LENGTH_LONG).show()
            }
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchContactPicker()
            } else {
                Toast.makeText(this, "Permiso de contactos denegado", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestActivityPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                permissionGranted.value = true
                setupStepCounter()
            } else {
                Toast.makeText(this, "Permiso de actividad física denegado. No se pueden contar pasos.", Toast.LENGTH_LONG).show()
            }
        }

    private val usageStatsSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Cuando el usuario regresa, volvemos a checar el permiso
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
                        Toast.makeText(this, "Contacto de emergencia guardado: $number", Toast.LENGTH_LONG).show()
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
                    HomeScreen(
                        onLogout = {
                            sessionManager.clearSession()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onOpenMap = {
                            startActivity(Intent(this, MapActivity::class.java))
                        },
                        onSosClick = {
                            showSosDialog.value = true
                        },
                        emergencyContact = emergencyContactNumber.value,
                        onSelectContact = {
                            handleSelectContactClick()
                        },
                        userName = "Aventurero",
                        showSosDialog = showSosDialog.value,
                        onDismissSosDialog = {
                            showSosDialog.value = false
                        },
                        onConfirmSos = {
                            showSosDialog.value = false
                            handleSosPermissionCheck()
                        },
                        steps = stepsCount.value,
                        onStepsPermissionClick = {
                            checkAndRequestActivityPermission()
                        },
                        hasActivityPermission = permissionGranted.value,
                        onRecompensasClick = {
                            startActivity(Intent(this, RecompensasActivity::class.java))
                        },
                        usageTime = socialMediaUsageTime.value,
                        hasUsagePermission = hasUsagePermission.value,
                        onUsagePermissionClick = {
                            if (hasUsagePermission.value) {
                                // SI YA TIENE PERMISO -> ABRE EL DETALLE
                                startActivity(Intent(this, UsageDetailActivity::class.java))
                            } else {
                                // SI NO TIENE PERMISO -> PÍDELO
                                requestUsageStatsPermission()
                            }
                        }
                    )
                }
            }
        }
        checkAndRequestActivityPermission()
        checkUsageStatsPermission()
    }

    override fun onResume() {
        super.onResume()
        if (permissionGranted.value) {
            setupStepCounter()
        }
        // Checar permiso y calcular tiempo al volver
        checkUsageStatsPermission()
        getSocialMediaUsage()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    // --- FUNCIONES DE LÓGICA ---

    // 1. Inicia la LLAMADA (o el marcador)
    private fun launch911Dialer() {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:911")
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error de permisos para llamar", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:911")
                }
                startActivity(dialIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No se pudo abrir la app de teléfono", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Revisa los 3 permisos SOS
    private fun handleSosPermissionCheck() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            proceedWithSosActions()
        } else {
            requestSosPermissionsLauncher.launch(permissions)
        }
    }

    // --- NUEVAS FUNCIONES PARA EL MONITOR DE USO ---

    // 1. Revisa si tenemos el permiso PACKAGE_USAGE_STATS
    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        // Actualizamos nuestro estado
        hasUsagePermission.value = (mode == AppOpsManager.MODE_ALLOWED)

        // Si ya tenemos permiso, calculamos el tiempo
        if (hasUsagePermission.value) {
            getSocialMediaUsage()
        }
    }

    // 2. Abre la pantalla de Ajustes del sistema para que el usuario dé el permiso
    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la pantalla de ajustes", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Ejecuta las acciones de SOS
    private fun proceedWithSosActions() {
        launch911Dialer()
        val contactNumber = sessionManager.fetchEmergencyContact()
        if (contactNumber.isNullOrBlank()) {
            Toast.makeText(this, "No hay contacto de emergencia seleccionado", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Enviando alerta a $contactNumber...", Toast.LENGTH_SHORT).show()
            sendEmergencySms(contactNumber)
        }
    }

    // 4. Obtiene ubicación y envía el SMS
    @SuppressLint("MissingPermission")
    private fun sendEmergencySms(contactNumber: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ** INICIO DE LA CORRECCIÓN DE SMS **
        val smsManager = SmsManager.getDefault() // Mover la definición aquí arriba
        // ** FIN DE LA CORRECCIÓN DE SMS **

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val smsMessage: String
                if (location != null) {
                    val googleMapsLink = "http://googleusercontent.com/maps/google.com/11?q=${location.latitude},${location.longitude}"
                    smsMessage = "¡AYUDA! Esta es mi última ubicación conocida: $googleMapsLink"
                } else {
                    smsMessage = "¡AYUDA! No pude obtener mi ubicación, por favor contáctame."
                }
                try {
                    // Usar sendMultipartTextMessage para evitar problemas de longitud y filtros de spam
                    val parts = smsManager.divideMessage(smsMessage)
                    smsManager.sendMultipartTextMessage(contactNumber, null, parts, null, null)
                    Toast.makeText(this, "Alerta SMS enviada.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                val smsMessage = "¡AYUDA! No pude obtener mi ubicación, por favor contáctame."
                try {
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, "Alerta SMS enviada (sin ubicación).", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }


    // 5. Lógica del selector de contactos
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
        contactPickerLauncher.launch(intent)
    }

    // 3. Calcula el tiempo en apps de "Scroll Infinito" hoy
    private fun getSocialMediaUsage() {
        if (!hasUsagePermission.value) return

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        // Ponemos el calendario al inicio del día (00:00 hrs)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        // Pedimos las estadísticas diarias
        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        // Lista de paquetes (Scroll Infinito)
        val socialPackages = listOf(
            "com.facebook.katana",      // Facebook
            "com.google.android.youtube", // YouTube
            "com.twitter.android",      // X (Twitter)
            "com.snapchat.android",      // Snapchat
            "com.zhiliaoapp.musically", // TikTok
            "com.instagram.android",    // Instagram
        )

        var totalTimeMillis = 0L

        for (app in queryUsageStats) {
            if (socialPackages.contains(app.packageName)) {
                totalTimeMillis += app.totalTimeInForeground
            }
        }

        // Convertimos milisegundos a texto bonito (Ej: "2h 15m")
        val hours = TimeUnit.MILLISECONDS.toHours(totalTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis) % 60

        socialMediaUsageTime.value = if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }

    private fun handleSelectContactClick() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> {
                launchContactPicker()
            }
            else -> {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    // --- NUEVAS FUNCIONES PARA EL SENSOR DE PASOS ---

    // 6. Revisa el permiso de Actividad Física
    private fun checkAndRequestActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED -> {
                    permissionGranted.value = true
                    setupStepCounter()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) -> {
                    requestActivityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                else -> {
                    requestActivityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        } else {
            permissionGranted.value = true
            setupStepCounter()
        }
    }

    // 7. Configura el listener del sensor
    private fun setupStepCounter() {
        if (stepCounterSensor != null) {
            sensorManager?.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Toast.makeText(this, "Tu dispositivo no tiene sensor de pasos", Toast.LENGTH_LONG).show()
            stepsCount.value = "N/A"
        }
    }

    // 8. OBLIGATORIO: Se llama CADA VEZ que el sensor detecta un paso
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSensor = event.values[0].toInt()

            // 1. Obtener la fecha de hoy (ej. "2025-11-17")
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 2. Obtener lo guardado
            val savedDate = sessionManager.fetchStepsDate()
            val savedBaseline = sessionManager.fetchStepsBaseline()

            var stepsToDisplay = 0

            if (savedDate != todayDate) {
                // ¡ES UN NUEVO DÍA! (O es la primera vez)
                // Reiniciamos el conteo: El valor actual del sensor se vuelve el nuevo "cero" (baseline)
                sessionManager.saveStepsDate(todayDate)
                sessionManager.saveStepsBaseline(totalStepsSensor)
                stepsToDisplay = 0
            } else {
                // ES EL MISMO DÍA
                if (savedBaseline == -1) {
                    // Caso raro: tenemos fecha pero no baseline, reseteamos
                    sessionManager.saveStepsBaseline(totalStepsSensor)
                    stepsToDisplay = 0
                } else if (totalStepsSensor < savedBaseline) {
                    // CASO REINICIO: El celular se apagó y el sensor volvió a 0.
                    // El baseline anterior ya no sirve. Reseteamos el baseline al nuevo valor bajo.
                    sessionManager.saveStepsBaseline(totalStepsSensor)
                    stepsToDisplay = 0
                } else {
                    // CÁLCULO NORMAL: Restamos el valor inicial del día al valor actual
                    stepsToDisplay = totalStepsSensor - savedBaseline
                }
            }

            // Actualizamos la UI
            stepsCount.value = stepsToDisplay.toString()
        }
    }

    // 9. OBLIGATORIO: Se llama si cambia la precisión del sensor (lo ignoramos)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesitamos hacer nada aquí
    }
}

// =============================================
// COMPOSABLES
// =============================================

// -----------------------------
// HomeScreen: layout principal
// -----------------------------
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
    usageTime: String
) {
    val scrollState = rememberScrollState()
    val coroutine = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Kairos", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Descubre el mundo real 🌳", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        },
        bottomBar = { HomeBottomBar() },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = onOpenMap) {
                    Text("Map", modifier = Modifier.padding(6.dp))
                }
                FloatingActionButton(
                    onClick = { onSosClick() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape
                ) {
                    Text(
                        "SOS",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        content = { innerPadding ->

            if (showSosDialog) {
                AlertDialog(
                    onDismissRequest = { onDismissSosDialog() },
                    title = { Text("Confirmación de Alerta SOS") },
                    text = { Text("¿Estás seguro de que quieres activar la alerta? Se llamará automáticamente al 911 y se enviará un SMS de alerta a tu contacto de emergencia.") },
                    confirmButton = {
                        Button(
                            onClick = { onConfirmSos() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("¡SÍ, ACTIVAR!")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onDismissSosDialog() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
            ) {

                Spacer(modifier = Modifier.height(16.dp))
                AnimatedGreeting(userName = userName)
                Spacer(modifier = Modifier.height(18.dp))
                DiscoverCarousel()
                Spacer(modifier = Modifier.height(18.dp))

                EmergencyContactCard(
                    contactNumber = emergencyContact,
                    onClick = onSelectContact
                )

                Spacer(modifier = Modifier.height(18.dp))

                StepCounterCard(
                    steps = steps,
                    hasPermission = hasActivityPermission,
                    onClick = onStepsPermissionClick
                )
                Spacer(modifier = Modifier.height(18.dp))

                // Tarjeta de Monitor de Uso
                UsageMonitorCard(
                    hasPermission = hasUsagePermission,
                    usageTime = usageTime,
                    onClick = onUsagePermissionClick
                )


                Spacer(modifier = Modifier.height(18.dp))
                QuickActionsRow(onOpenMap = onOpenMap, onLogout = onLogout, onRecompensasClick = onRecompensasClick)
                Spacer(modifier = Modifier.height(18.dp))
                NatureStrip()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    )
}

// -----------------------------
// NUEVO COMPOSABLE: Tarjeta de Contador de Pasos
// -----------------------------
@Composable
fun StepCounterCard(
    steps: String,
    hasPermission: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Pasos de Hoy (Total)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (hasPermission) {
                    Text(
                        text = steps,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = "Toca para dar permiso de actividad",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = "Pasos",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}


// -----------------------------
// Tarjeta de Contacto de Emergencia
// -----------------------------
@Composable
fun EmergencyContactCard(contactNumber: String?, onClick: () -> Unit) {
    // (Sin cambios)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Contacto de Emergencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = contactNumber ?: "Toca para seleccionar un contacto",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (contactNumber != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (contactNumber != null) FontWeight.Medium else FontWeight.Normal
                )
            }
            Text(text = "📞", fontSize = 30.sp)
        }
    }
}


// -----------------------------
// Animated Greeting
// -----------------------------
@Composable
fun AnimatedGreeting(userName: String) {
    // (Sin cambios)
    val infinite = rememberInfiniteTransition()
    val bob by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .offset(y = Dp(bob))
                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🐿️", fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = "¡Hola, $userName!", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Hoy es un día perfecto para descubrir algo nuevo 🌿",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -----------------------------
// DiscoverCarousel (Accompanist pager)
// -----------------------------
@OptIn(ExperimentalPagerApi::class)
@Composable
fun DiscoverCarousel() {
    // (Sin cambios)
    val items = listOf(
        Triple("Sendero del Bosque", "Un paseo entre árboles y aves.", "🌲"),
        Triple("Ruta de Murales", "Arte urbano y sorpresas.", "🎨"),
        Triple("Mercado Local", "Sabores, puestos y música.", "🧺")
    )
    val pagerState = rememberPagerState(initialPage = 0)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            count = items.size,
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
        ) { page ->
            val (title, desc, emoji) = items[page]
            DiscoveryCard(title, desc, emoji)
        }
        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            activeColor = MaterialTheme.colorScheme.primary
        )
    }
}


@Composable
fun DiscoveryCard(title: String, desc: String, emoji: String) {
    // (Sin cambios)
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val pulse = rememberInfiniteTransition()
            val scale by pulse.animateFloat(initialValue = 0.9f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse))
            Text(text = emoji, fontSize = 36.sp, modifier = Modifier.scale(scale))
        }
    }
}

// -----------------------------
// Quick actions row
// -----------------------------
@Composable
fun QuickActionsRow(onOpenMap: () -> Unit, onLogout: () -> Unit, onRecompensasClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SmallActionCard(label = "Explorar", emoji = "🗺️", onClick = onOpenMap)
        SmallActionCard(label = "Rutas", emoji = "🧭", onClick = { /* abrir rutas */ })
        SmallActionCard(label = "Recompensas", emoji = "🎟️", onClick = { onRecompensasClick() })    }
}

@Composable
fun SmallActionCard(label: String, emoji: String, onClick: () -> Unit) {
    // (Sin cambios)
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// -----------------------------
// Nature strip
// -----------------------------
@Composable
fun NatureStrip() {
    // (Sin cambios)
    Column {
        Text("Nature Vibes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Row(modifier = Modifier
            .horizontalScroll(scroll)
            .fillMaxWidth()
        ) {
            repeat(8) { idx ->
                MellowPlantCard(idx)
            }
        }
    }
}

@Composable
fun MellowPlantCard(index: Int) {
    // (Sin cambios)
    val infinite = rememberInfiniteTransition()
    val sway by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600 + (index % 3) * 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    Card(modifier = Modifier
        .padding(8.dp)
        .size(width = 120.dp, height = 140.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(modifier = Modifier.size(56.dp)) {
                drawCircle(color = Color(0xFF8BC34A), radius = size.minDimension/2, center = Offset(size.width/2, size.height/3))
                drawRoundRect(color = Color(0xFF6D4C41), topLeft = Offset(size.width/2 - 6, size.height/1.9f), size = Size(12f, 24f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f,4f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Árbol ${index+1}", fontSize = 12.sp)
        }
    }
}

// -----------------------------
// Bottom bar
// -----------------------------
@Composable
fun HomeBottomBar() {
    // (Sin cambios)
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
            label = { Text("Inicio") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* TODO */ },
            // ** INICIO DE LA CORRECCIÓN DE TYPO **
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) }, // Era ExitTooApp
            // ** FIN DE LA CORRECCIÓN DE TYPO **
            label = { Text("Perfil") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
            label = { Text("Ajustes") }
        )
    }
}

// -----------------------------
// NUEVO COMPOSABLE: Tarjeta de Monitor de Uso
// -----------------------------
@Composable
fun UsageMonitorCard(
    hasPermission: Boolean,
    usageTime: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tiempo en Redes (Hoy)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (hasPermission) {
                    Text(
                        text = usageTime,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                            text = "Apps de scroll infinito",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Si no tiene permiso, muestra un botón para ir a Ajustes
                    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = "Toca para dar permiso de uso de apps",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.QueryStats,
                contentDescription = "Monitor de Uso",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// -----------------------------
// Helpers: easing
// -----------------------------
private val EaseInOutSine = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)