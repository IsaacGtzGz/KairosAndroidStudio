package com.kairos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kairos.app.ui.theme.KairosTheme
import androidx.compose.foundation.background

class RecompensasActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KairosTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Recompensas") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    // Esta acción simula el botón "atrás"
                                    finish()
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    // Usamos LazyColumn para que sea "scrolleable"
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                "Recompensas Disponibles",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Tarjeta de Recompensa Simulada 1
                        item {
                            RecompensaCard(
                                titulo = "Café Gratis en 'El Gato'",
                                descripcion = "Válido al visitar 5 lugares este mes.",
                                imageUrl = "https://images.unsplash.com/photo-1511920183303-6235b5ef46c0?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3wzNTU2MTR8MHwxfGFsbHx8fHx8fHx8fDE3MzE0NTMxMDZ8&ixlib=rb-4.0.3&q=80&w=400"
                            )
                        }

                        // Tarjeta de Recompensa Simulada 2
                        item {
                            RecompensaCard(
                                titulo = "2x1 en Senderismo",
                                descripcion = "Recompensa por 10,000 pasos.",
                                imageUrl = "https://images.unsplash.com/photo-1458040491910-3309e3e3b733?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3wzNTU2MTR8MHwxfGFsbHx8fHx8fHx8fDE3MzE0NTMxMzV8&ixlib=rb-4.0.3&q=80&w=400"
                            )
                        }

                        // Tarjeta de Recompensa Simulada 3
                        item {
                            RecompensaCard(
                                titulo = "Postre de cortesía",
                                descripcion = "Al explorar la 'Ruta de Murales'.",
                                imageUrl = "https://images.unsplash.com/photo-1587314168485-3236d6710814?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3wzNTU2MTR8MHwxfGFsbHx8fHx8fHx8fDE3MzE0NTMyMjR8&ixlib=rb-4.0.3&q=80&w=400"
                            )
                        }
                    }
                }
            }
        }
    }
}

// Composable reutilizable para las tarjetas de recompensa
@Composable
fun RecompensaCard(titulo: String, descripcion: String, imageUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Usamos AsyncImage de Coil (que ya instalamos)
            AsyncImage(
                model = imageUrl,
                contentDescription = titulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodyMedium
                )
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