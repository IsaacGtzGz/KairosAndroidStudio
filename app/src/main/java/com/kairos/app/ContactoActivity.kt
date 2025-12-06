package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.models.MensajeContacto
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.AppConstants
import com.kairos.app.utils.NetworkHelper
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.isValidEmail
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ContactoActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KairosTheme {
                val sessionManager = SessionManager(this)
                val userId = sessionManager.fetchUserId()
                val userEmail = sessionManager.fetchEmail()

                var nombre by remember { mutableStateOf("") }
                var email by remember { mutableStateOf(userEmail ?: "") }
                var asunto by remember { mutableStateOf("") }
                var mensaje by remember { mutableStateOf("") }
                var isLoading by remember { mutableStateOf(false) }
                var showSuccessDialog by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Contacto") },
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
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        // HEADER
                        Icon(
                            Icons.Default.Email,
                            null,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.CenterHorizontally),
                            tint = AppConstants.Colors.DarkGreen
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "¿Necesitas ayuda?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Envíanos un mensaje y te responderemos pronto",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // FORMULARIO
                        OutlinedTextField(
                            value = nombre,
                            onValueChange = { nombre = it },
                            label = { Text("Nombre completo") },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Correo electrónico") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = asunto,
                            onValueChange = { asunto = it },
                            label = { Text("Asunto") },
                            leadingIcon = { Icon(Icons.Default.Subject, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = mensaje,
                            onValueChange = { mensaje = it },
                            label = { Text("Mensaje") },
                            leadingIcon = { Icon(Icons.Default.Message, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            minLines = 5,
                            maxLines = 8
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // BOTÓN ENVIAR
                        Button(
                            onClick = {
                                if (nombre.isEmpty() || email.isEmpty() || asunto.isEmpty() || mensaje.isEmpty()) {
                                    Toast.makeText(
                                        this@ContactoActivity,
                                        AppConstants.Messages.FIELD_REQUIRED,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val mensajeContacto = MensajeContacto(
                                                idMensaje = 0,
                                                nombre = nombre,
                                                correo = email,
                                                asunto = asunto,
                                                mensaje = mensaje,
                                                fechaEnvio = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                                                estatus = "Pendiente"
                                            )
                                            val response = RetrofitClient.instance.enviarMensajeContacto(mensajeContacto)
                                            if (response.isSuccessful) {
                                                showSuccessDialog = true
                                                nombre = ""
                                                asunto = ""
                                                mensaje = ""
                                            } else {
                                                Toast.makeText(
                                                    this@ContactoActivity,
                                                    AppConstants.Messages.MESSAGE_SENT,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                this@ContactoActivity,
                                                AppConstants.Messages.MESSAGE_ERROR,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppConstants.Colors.DarkGreen
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Send, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enviar Mensaje", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // INFO ADICIONAL
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF0F8F0)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Otros medios de contacto",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("(999) 123-4567", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("soporte@kairosx.com", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // DIÁLOGO DE ÉXITO
                if (showSuccessDialog) {
                    AlertDialog(
                        onDismissRequest = { showSuccessDialog = false },
                        icon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = AppConstants.Colors.DarkGreen,
                                modifier = Modifier.size(64.dp)
                            )
                        },
                        title = { Text("¡Mensaje Enviado!") },
                        text = { Text("Hemos recibido tu mensaje. Te responderemos pronto a tu correo electrónico.") },
                        confirmButton = {
                            Button(onClick = {
                                showSuccessDialog = false
                                finish()
                            }) {
                                Text("Entendido")
                            }
                        }
                    )
                }
            }
        }
    }
}
