package com.kairos.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Error: ${response.code()}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
    var passwordVisible by remember { mutableStateOf(false) } // Para mostrar/ocultar contraseña

    // Usamos Box para centrar el contenido en la pantalla
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Permite scroll si el teclado aparece
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "KAIROS",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Inicia sesión para continuar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 48.dp) // Espacio extra
            )

            // Campo de Correo
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Campo de Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { // Icono para mostrar/ocultar
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de Iniciar sesión
            Button(
                onClick = { onLoginClick(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Iniciar sesión", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Registrarse
            TextButton(onClick = onRegisterClick) {
                Text("¿No tienes cuenta? Crear cuenta nueva")
            }
        }
    }
}