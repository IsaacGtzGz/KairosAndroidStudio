package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kairos.app.models.RegistrarClicRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.formatPoints
import kotlinx.coroutines.launch

class PromocionDetalleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titulo = intent.getStringExtra("titulo") ?: "Promoción"
        val descripcion = intent.getStringExtra("descripcion") ?: "Sin descripción"
        val imagenUrl = intent.getStringExtra("imagen")
        val puntosRequeridos = intent.getIntExtra("puntosRequeridos", 0)
        val idPromocion = intent.getIntExtra("idPromocion", 0)
        val puntosUsuario = intent.getIntExtra("puntosUsuario", 0)

        setContent {
            KairosTheme {
                val sessionManager = SessionManager(this)
                val userId = sessionManager.fetchUserId()
                val scope = rememberCoroutineScope()
                var showDialog by remember { mutableStateOf(false) }
                var canCanjear by remember { mutableStateOf(puntosUsuario >= puntosRequeridos) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Detalle de Promoción") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(paddingValues)
                            .padding(bottom = 80.dp)
                    ) {
                        // IMAGEN HEADER
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            if (imagenUrl != null) {
                                AsyncImage(
                                    model = imagenUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(AppConstants.Colors.PrimaryGreen, AppConstants.Colors.DarkGreen)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CardGiftcard,
                                        null,
                                        modifier = Modifier.size(100.dp),
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // CONTENIDO
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = titulo,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 32.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // PUNTOS REQUERIDOS
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Puntos requeridos", fontSize = 14.sp, color = Color.Gray)
                                        Text(
                                            text = "$puntosRequeridos puntos",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppConstants.Colors.DarkGreen
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Tus puntos", fontSize = 14.sp, color = Color.Gray)
                                        Text(
                                            text = "$puntosUsuario pts",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canCanjear) AppConstants.Colors.PrimaryGreen else Color.Red
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Descripción",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = descripcion,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // BOTÓN CANJEAR
                            Button(
                                onClick = {
                                    if (canCanjear && userId != null) {
                                        scope.launch {
                                            try {
                                                val clicRequest = RegistrarClicRequest(
                                                    idUsuario = userId,
                                                    idPromocion = idPromocion
                                                )
                                                val response = RetrofitClient.instance.registrarClic(clicRequest)
                                                if (response.isSuccessful) {
                                                    // Recargar puntos desde el servidor
                                                    try {
                                                        val userResponse = RetrofitClient.instance.getUsuario(userId)
                                                        if (userResponse.isSuccessful) {
                                                            val puntosActualizados = userResponse.body()?.puntosAcumulados ?: 0
                                                            sessionManager.saveUserPoints(puntosActualizados)
                                                        }
                                                    } catch (e: Exception) {
                                                        // Si falla la recarga, usar valor calculado
                                                        val nuevosPuntos = puntosUsuario - puntosRequeridos
                                                        sessionManager.saveUserPoints(nuevosPuntos)
                                                    }
                                                    canCanjear = false
                                                    showDialog = true
                                                } else {
                                                    Toast.makeText(
                                                        this@PromocionDetalleActivity,
                                                        AppConstants.Messages.ERROR_GENERIC,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    this@PromocionDetalleActivity,
                                                    AppConstants.Messages.ERROR_NETWORK,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = canCanjear,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppConstants.Colors.DarkGreen,
                                    disabledContainerColor = Color.Gray
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Redeem, null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (canCanjear) "Canjear Promoción" else "Puntos insuficientes",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // DIÁLOGO DE ÉXITO
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        icon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = AppConstants.Colors.DarkGreen,
                                modifier = Modifier.size(64.dp)
                            )
                        },
                        title = { Text("¡Promoción Canjeada!", textAlign = TextAlign.Center) },
                        text = { Text("Has canjeado exitosamente esta promoción. Revisa tu correo para más detalles.") },
                        confirmButton = {
                            Button(onClick = { finish() }) {
                                Text("Entendido")
                            }
                        }
                    )
                }
            }
        }
    }
}
