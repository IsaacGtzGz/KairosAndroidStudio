package com.kairos.app

import com.kairos.app.utils.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.components.EmptyState
import com.kairos.app.components.LoadingState
import kotlinx.coroutines.launch

class CoachChatActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionManager = SessionManager(this)
        val userId = sessionManager.fetchUserId()
        
        setContent {
            KairosTheme {
                var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
                var inputText by remember { mutableStateOf("") }
                var isLoading by remember { mutableStateOf(false) }
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                
                // Mensaje inicial del coach (dinámico según datos del usuario)
                LaunchedEffect(Unit) {
                    val userName = sessionManager.fetchUserName()?.split(" ")?.firstOrNull() ?: "Explorador"
                    
                    messages = listOf(
                        ChatMessage(
                            text = "¡Hola, $userName! Soy tu Coach Kairos.\n\nEstoy aquí para ayudarte con:\n\n• Motivación personalizada según tus datos\n• Recomendaciones de lugares por tus intereses\n• Análisis de tu progreso (pasos y tiempo digital)\n• Consejos para mejorar tu bienestar\n\nEscribe cualquier cosa y te responderé con información basada en tus actividades reales. ¿Listo para empezar?",
                            isFromUser = false
                        )
                    )
                }
                
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Coach Kairos", fontWeight = FontWeight.Bold)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Volver")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // Lista de mensajes
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(messages) { message ->
                                ChatBubble(message)
                            }
                            
                            if (isLoading) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Coach escribiendo...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        
                        // Campo de entrada
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Escribe tu mensaje...") },
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val isButtonEnabled = !isLoading && inputText.isNotBlank()
                            FloatingActionButton(
                                onClick = {
                                    if (isButtonEnabled) {
                                        val userMessage = inputText
                                        inputText = ""
                                        
                                        // Agregar mensaje del usuario
                                        messages = messages + ChatMessage(userMessage, isFromUser = true)
                                        
                                        // Scroll al final
                                        scope.launch {
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                        
                                        // Obtener respuesta del coach
                                        isLoading = true
                                        lifecycleScope.launch {
                                            try {
                                                val response = RetrofitClient.instance.getInsight(userId)
                                                val coachResponse = if (response.isSuccessful) {
                                                    response.body()?.mensaje ?: "Lo siento, no pude generar una respuesta."
                                                } else {
                                                    "Parece que tengo problemas de conexión. Intenta de nuevo."
                                                }
                                                
                                                messages = messages + ChatMessage(coachResponse, isFromUser = false)
                                                
                                                scope.launch {
                                                    listState.animateScrollToItem(messages.size - 1)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    this@CoachChatActivity,
                                                    "Error de conexión",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                containerColor = if (isButtonEnabled) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    Icons.Default.Send, 
                                    "Enviar",
                                    tint = if (isButtonEnabled) 
                                        MaterialTheme.colorScheme.onPrimary
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean
)

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 16.dp
            ),
            color = if (message.isFromUser) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
