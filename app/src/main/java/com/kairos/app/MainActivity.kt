package com.kairos.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.theme.KairosTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kairos.app.models.User
import com.kairos.app.network.RetrofitClient
import com.kairos.app.utils.SessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val sessionManager = SessionManager(this)
        if (sessionManager.fetchAuthToken() != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContent {
            KairosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        onLoginClick = { email, password ->
                            lifecycleScope.launch {
                                try {
                                    val credentials = mapOf(
                                        "correo" to email,
                                        "contrasena" to password
                                    )
                                    val response = RetrofitClient.instance.login(credentials)
                                    if (response.isSuccessful) {
                                        val data = response.body()
                                        if (data?.success == true && !data.token.isNullOrEmpty()) {
                                            val session = SessionManager(this@MainActivity)
                                            session.saveAuthToken(data.token)

                                            // GUARDAR DATOS REALES DEL USUARIO
                                            data.user?.let { user ->
                                                // Usamos el operador Elvis (?: 0)
                                                session.saveUserId(user.id ?: 0)

                                                session.saveUserName("${user.nombre} ${user.apellido}")
                                                session.saveUserEmail(user.correo)
                                                // Si viene foto, la guardamos
                                                if (!user.fotoPerfil.isNullOrEmpty()) {
                                                    session.saveUserProfilePic(user.fotoPerfil)
                                                }
                                            }

                                            Toast.makeText(
                                                this@MainActivity,
                                                "Bienvenido ${data.user?.nombre}",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            val intent = Intent(this@MainActivity, HomeActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            }
                                            startActivity(intent)
                                            finish()
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                data?.message ?: "Credenciales inválidas",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        val errorMessage = when (response.code()) {
                                            401 -> "Correo o contraseña incorrectos"
                                            404 -> "Usuario no encontrado"
                                            500 -> "Error en el servidor. Intenta más tarde"
                                            else -> "Error al iniciar sesión (${response.code()})"
                                        }
                                        Toast.makeText(
                                            this@MainActivity,
                                            errorMessage,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onRegisterClick = {
                            val intent = Intent(this, RegisterActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo/Icono (círculo con gradiente)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "K",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Título
            Text(
                text = "KAIROS",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                text = "Tu compañero de bienestar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Tarjeta con formulario
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Campo de Correo
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo de Contraseña
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (passwordVisible) "Ocultar" else "Mostrar"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, description)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Botón de Iniciar sesión con gradiente
                    Button(
                        onClick = { onLoginClick(email, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(4.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Iniciar Sesión", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de Registrarse
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿No tienes cuenta?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRegisterClick) {
                    Text("Regístrate aquí", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}