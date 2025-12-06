package com.kairos.app

import com.kairos.app.utils.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.components.EmptyState
import com.kairos.app.components.LoadingState
import com.kairos.app.models.Ruta
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RutasActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                var rutas by remember { mutableStateOf<List<Ruta>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    try {
                        val response = RetrofitClient.instance.getRutas()
                        if (response.isSuccessful && response.body() != null) {
                            rutas = response.body()!!
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RutasActivity, AppConstants.Messages.ERROR_NETWORK, Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Rutas Turísticas") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                // Abrir Activity para crear ruta con selección de lugares en mapa
                                val intent = Intent(this@RutasActivity, CrearRutaActivity::class.java)
                                startActivity(intent)
                            },
                            containerColor = AppConstants.Colors.DarkGreen
                        ) {
                            Icon(Icons.Default.Add, "Crear Ruta", tint = Color.White)
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (isLoading) {
                            LoadingState(message = "Cargando rutas...")
                        } else {
                            if (rutas.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.Route,
                                    title = "No hay rutas disponibles",
                                    subtitle = "Crea tu primera ruta turística"
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(rutas) { ruta ->
                                        RutaCard(ruta, onClick = {
                                            // Abrir Activity para visualizar la ruta en el mapa
                                            val intent = Intent(this@RutasActivity, RutaDetalleActivity::class.java)
                                            intent.putExtra("rutaId", ruta.idRuta)
                                            intent.putExtra("rutaNombre", ruta.nombre)
                                            intent.putExtra("rutaDescripcion", ruta.descripcion)
                                            startActivity(intent)
                                        })
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
fun RutaCard(ruta: Ruta, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = AppConstants.Colors.PrimaryGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Route,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ruta.nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (ruta.descripcion != null) {
                    Text(
                        text = ruta.descripcion!!,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 2
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Route,
                        null,
                        modifier = Modifier.size(40.dp),
                        tint = AppConstants.Colors.DarkGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${ruta.rutasLugares?.size ?: 0} lugares",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color.Gray
            )
        }
    }
}
