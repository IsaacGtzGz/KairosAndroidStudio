package com.kairos.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager // IMPORT NUEVO
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
import androidx.compose.foundation.verticalScroll // IMPORTADO AHORA
import androidx.compose.material.icons.Icons
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
import com.google.android.gms.location.LocationServices // IMPORT NUEVO
import com.google.android.gms.location.Priority // IMPORT NUEVO

// =======================================
// HomeActivity
// =======================================
class HomeActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private var emergencyContactNumber = mutableStateOf<String?>(null)

    // --- LANZADORES DE PERMISOS Y ACTIVIDADES ---

    // 👇 NUEVO LANZADOR MÚLTIPLE PARA TODOS LOS PERMISOS DE SOS
    private val requestSosPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Revisa si TODOS los permisos fueron concedidos
            if (permissions.all { it.value }) {
                // Todos concedidos, proceder con las acciones
                proceedWithSosActions()
            } else {
                Toast.makeText(this, "Se necesitan todos los permisos para la función SOS", Toast.LENGTH_LONG).show()
            }
        }

    // Lanzador para permiso de CONTACTOS
    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchContactPicker()
            } else {
                Toast.makeText(this, "Permiso de contactos denegado", Toast.LENGTH_SHORT).show()
            }
        }

    // Lanzador que ABRE el selector de contactos
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
                            handleSosClick() // Esta función ahora es más potente
                        },
                        emergencyContact = emergencyContactNumber.value,
                        onSelectContact = {
                            handleSelectContactClick()
                        },
                        userName = "Aventurero"
                    )
                }
            }
        }
    }

    // --- FUNCIONES DE LÓGICA ---

    // 1. Inicia el marcado al 911
    private fun launch911Dialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:911")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar la llamada", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Revisa los 3 permisos
    private fun handleSosClick() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Revisa si ya tiene los 3 permisos
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            proceedWithSosActions()
        } else {
            // Si falta al menos uno, pide todos
            requestSosPermissionsLauncher.launch(permissions)
        }
    }

    // 3. Ejecuta las acciones de SOS (si se tienen permisos)
    private fun proceedWithSosActions() {
        // Acción 1: Llamar al 911
        launch911Dialer()

        // Acción 2: Enviar SMS al contacto de emergencia
        val contactNumber = sessionManager.fetchEmergencyContact()
        if (contactNumber.isNullOrBlank()) {
            Toast.makeText(this, "No hay contacto de emergencia seleccionado", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Enviando alerta a $contactNumber...", Toast.LENGTH_SHORT).show()
            sendEmergencySms(contactNumber)
        }
    }

    // 4. Obtiene ubicación y envía el SMS
    @SuppressLint("MissingPermission") // Está bien, porque ya revisamos los permisos en handleSosClick
    private fun sendEmergencySms(contactNumber: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Pide la ubicación actual con alta precisión
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val smsMessage: String
                if (location != null) {
                    val googleMapsLink = "http://maps.google.com/maps?q=${location.latitude},${location.longitude}"
                    smsMessage = "¡AYUDA! Esta es mi última ubicación conocida: $googleMapsLink"
                } else {
                    smsMessage = "¡AYUDA! No pude obtener mi ubicación, por favor contáctame."
                }

                try {
                    // Envía el SMS
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, "Alerta SMS enviada.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                // Si falla la ubicación, envía el SMS sin ella
                val smsMessage = "¡AYUDA! No pude obtener mi ubicación, por favor contáctame."
                try {
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(contactNumber, null, smsMessage, null, null)
                    Toast.makeText(this, "Alerta SMS enviada (sin ubicación).", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }


    // 5. Lógica del selector de contactos (sin cambios)
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
        contactPickerLauncher.launch(intent)
    }

    private fun handleSelectContactClick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchContactPicker()
            }
            else -> {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}

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
    onSelectContact: () -> Unit
) {
    val scrollState = rememberScrollState() // ARREGLO DEL SCROLL
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
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState) // ARREGLO DEL SCROLL
            ) {

                Spacer(modifier = Modifier.height(16.dp)) // Espacio superior
                AnimatedGreeting(userName = userName)
                Spacer(modifier = Modifier.height(18.dp))
                DiscoverCarousel()
                Spacer(modifier = Modifier.height(18.dp))

                EmergencyContactCard(
                    contactNumber = emergencyContact,
                    onClick = onSelectContact
                )

                Spacer(modifier = Modifier.height(18.dp))
                QuickActionsRow(onOpenMap = onOpenMap, onLogout = onLogout)
                Spacer(modifier = Modifier.height(18.dp))
                NatureStrip()
                Spacer(modifier = Modifier.height(16.dp)) // Espacio inferior
            }
        }
    )
}

// -----------------------------
// Tarjeta de Contacto de Emergencia
// -----------------------------
@Composable
fun EmergencyContactCard(contactNumber: String?, onClick: () -> Unit) {
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
fun QuickActionsRow(onOpenMap: () -> Unit, onLogout: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SmallActionCard(label = "Explorar", emoji = "🗺️", onClick = onOpenMap)
        SmallActionCard(label = "Rutas", emoji = "🧭", onClick = { /* abrir rutas */ })
        SmallActionCard(label = "Recompensas", emoji = "🎟️", onClick = { /* recompensas */ })
    }
}

@Composable
fun SmallActionCard(label: String, emoji: String, onClick: () -> Unit) {
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
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
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
// Helpers: easing
// -----------------------------
private val EaseInOutSine = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)