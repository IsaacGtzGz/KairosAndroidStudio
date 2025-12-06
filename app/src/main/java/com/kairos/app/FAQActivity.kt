package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kairos.app.models.PreguntaFrecuente
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper

class FAQActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                var preguntas by remember { mutableStateOf<List<PreguntaFrecuente>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var expandedId by remember { mutableStateOf<Int?>(null) }

                LaunchedEffect(Unit) {
                    try {
                        val response = RetrofitClient.instance.getPreguntasFrecuentes()
                        if (response.isSuccessful && response.body() != null) {
                            preguntas = response.body()!!
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@FAQActivity, AppConstants.Messages.ERROR_NETWORK, Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Preguntas Frecuentes") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Atrás")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (isLoading) {
                            LoadingState(message = "Cargando preguntas...")
                        } else {
                            if (preguntas.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.HelpOutline,
                                    title = "No hay preguntas disponibles",
                                    subtitle = "Las preguntas frecuentes aparecerán aquí"
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(preguntas) { pregunta ->
                                        FAQItem(
                                            pregunta = pregunta,
                                            isExpanded = expandedId == pregunta.idPregunta,
                                            onClick = {
                                                expandedId = if (expandedId == pregunta.idPregunta) {
                                                    null
                                                } else {
                                                    pregunta.idPregunta
                                                }
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
fun FAQItem(
    pregunta: PreguntaFrecuente,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) Color(0xFFF0F8F0) else Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pregunta.pregunta,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    color = if (isExpanded) AppConstants.Colors.DarkGreen else Color.Black
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppConstants.Colors.DarkGreen
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color(0xFF90EE90))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = pregunta.respuesta,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
