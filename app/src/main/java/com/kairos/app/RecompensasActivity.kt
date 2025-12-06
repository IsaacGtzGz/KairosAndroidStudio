package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kairos.app.components.EmptyState
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Promocion
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.*

class RecompensasActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                var promociones by remember { mutableStateOf<List<Promocion>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                val sessionManager = com.kairos.app.utils.SessionManager(this)
                val puntosUsuario = sessionManager.fetchUserPoints() ?: 0

                LaunchedEffect(Unit) {
                    if (!NetworkHelper.isNetworkAvailable(this@RecompensasActivity)) {
                        Toast.makeText(this@RecompensasActivity, AppConstants.Messages.NO_INTERNET, Toast.LENGTH_SHORT).show()
                        isLoading = false
                        return@LaunchedEffect
                    }
                    
                    try {
                        val response = RetrofitClient.instance.getPromociones()
                        if (response.isSuccessful && response.body() != null) {
                            // Filtramos para obtener solo las que tengan título válido
                            promociones = response.body()!!.filter { !it.titulo.isNullOrEmpty() }
                            android.util.Log.d("RecompensasActivity", "Promociones cargadas: ${promociones.size}")
                        } else {
                            android.util.Log.e("RecompensasActivity", "Error en respuesta: ${response.code()}")
                            Toast.makeText(this@RecompensasActivity, AppConstants.Messages.ERROR_LOADING, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RecompensasActivity", "Excepción al cargar promociones", e)
                        e.printStackTrace()
                        Toast.makeText(this@RecompensasActivity, AppConstants.Messages.CONNECTION_ERROR, Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Recompensas Disponibles") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        if (isLoading) {
                            LoadingState(message = "Cargando recompensas...")
                        } else {
                            if (promociones.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.CardGiftcard,
                                    title = "No hay recompensas activas",
                                    subtitle = "Vuelve pronto para ver nuevas promociones"
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(promociones) { promo ->
                                        val titulo = promo.titulo ?: "Sin título"

                                        // LÓGICA MÁGICA DE IMÁGENES
                                        // Asignamos una imagen diferente dependiendo de qué diga el título
                                        val imagenDinamica = when {
                                            titulo.contains("Parque", ignoreCase = true) || titulo.contains("Entrada", ignoreCase = true) ->
                                                "https://cdn-icons-png.flaticon.com/512/433/433102.png" // Icono de Parque/Naturaleza

                                            titulo.contains("Refresco", ignoreCase = true) || titulo.contains("Cine", ignoreCase = true) ->
                                                "https://cdn-icons-png.flaticon.com/512/3076/3076134.png" // Icono de Refresco/Comida

                                            titulo.contains("Descuento", ignoreCase = true) || titulo.contains("Starbucks", ignoreCase = true) || titulo.contains("Café", ignoreCase = true) ->
                                                "https://cdn-icons-png.flaticon.com/512/590/590836.png" // Icono de Café

                                            else -> "https://cdn-icons-png.flaticon.com/512/2534/2534196.png" // Tu imagen default
                                        }

                                        RecompensaCard(
                                            titulo = titulo,
                                            descripcion = promo.descripcion ?: "Sin descripción",
                                            imageUrl = imagenDinamica,
                                            onClick = {
                                                val intent = android.content.Intent(this@RecompensasActivity, PromocionDetalleActivity::class.java).apply {
                                                    putExtra("idPromocion", promo.idPromocion)
                                                    putExtra("titulo", titulo)
                                                    putExtra("descripcion", promo.descripcion)
                                                    putExtra("imagen", imagenDinamica)
                                                    putExtra("puntosRequeridos", 50)
                                                    putExtra("puntosUsuario", puntosUsuario)
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
}

@Composable
fun RecompensaCard(titulo: String, descripcion: String, imageUrl: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect(minScale = 0.97f),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = titulo,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = descripcion, style = MaterialTheme.typography.bodyMedium)
            }

            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}