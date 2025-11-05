package com.kairos.app

// 👇 IMPORTS NUEVOS AÑADIDOS
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
// ---------------------------------
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.launch
import com.google.accompanist.pager.*
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
// 👇 IMPORT NUEVO AÑADIDO
import androidx.core.content.ContextCompat

// =======================================
// HomeActivity: UI animada, amigable y viva
// =======================================
class HomeActivity : ComponentActivity() {

    // 👇 LANZADOR DE PERMISO DE LLAMADA (MOVIDO AQUÍ)
    private val requestCallPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido, ¡lanzar el 911!
                launch911Dialer()
            } else {
                Toast.makeText(this, "Permiso de llamada denegado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
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
                        onSosClick = { // 👈 CONEXIÓN AÑADIDA
                            handleSosClick()
                        },
                        userName = "Aventurero" // aquí luego reemplaza por data.user?.nombre
                    )
                }
            }
        }
    }

    // 👇 FUNCIÓN DE LLAMADA (MOVIDA AQUÍ)
    private fun launch911Dialer() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:911")
        }
        startActivity(intent)
    }

    // 👇 FUNCIÓN DE REVISIÓN DE PERMISO (MOVIDA AQUÍ)
    private fun handleSosClick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tienes permiso, marca directo
                launch911Dialer()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE) -> {
                // Opcional: Muestra un diálogo explicando por qué necesitas el permiso
                requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
            else -> {
                // Pide el permiso
                requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
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
    onSosClick: () -> Unit, // 👈 PARÁMETRO NUEVO AÑADIDO
    userName: String
) {
    val scaffoldState = rememberTopAppBarState()
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
                verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio entre botones
            ) {
                // Botón de Mapa (el que ya tenías)
                FloatingActionButton(onClick = onOpenMap) {
                    // Puedes cambiar el Text por un Icon si prefieres
                    Text("Map", modifier = Modifier.padding(6.dp))
                }

                // ¡NUEVO! Botón de Pánico (SOS)
                FloatingActionButton(
                    onClick = { onSosClick() }, // 👈 CONEXIÓN AÑADIDA
                    containerColor = MaterialTheme.colorScheme.errorContainer, // Color rojo
                    shape = CircleShape // Bien redondo
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
                .padding(16.dp)) {

                // Greeting animado con ardilla (emoji)
                AnimatedGreeting(userName = userName)

                Spacer(modifier = Modifier.height(18.dp))

                // Carrusel de descubrimientos
                DiscoverCarousel()

                Spacer(modifier = Modifier.height(18.dp))

                // Tarjetas de acción rápida
                QuickActionsRow(onOpenMap = onOpenMap, onLogout = onLogout)

                Spacer(modifier = Modifier.height(18.dp))

                // Sección de "Nature vibes" con micro-animaciones (plantas que se mecen)
                NatureStrip()
            }
        }
    )
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
        // Ardilla-bubble: dibujo simple con Canvas + emoji
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
            count = items.size, // ✅ ahora se usa 'count' en lugar de 'pageCount'
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
            // emoji grande que hace "pulse"
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
// Nature strip: pequenas plantitas que se mecen
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
                // small friendly tree using simple shapes
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