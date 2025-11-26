package com.kairos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kairos.app.ui.theme.KairosTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kairos.app.models.User
import com.kairos.app.network.RetrofitClient
import com.kairos.app.utils.SessionManager

class RegisterActivity : ComponentActivity() {

    // --- Estado ---
    // Guardará la URI (la dirección local) de la imagen seleccionada
    private var imageUri = mutableStateOf<Uri?>(null)

    // --- Lanzadores de Permisos y Galería ---

    // Lanzador que abre la galería
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri.value = it // Actualiza el estado con la URI de la imagen
        }
    }

    // Lanzador que pide el permiso de almacenamiento/medios
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Si el permiso se concede, abre la galería
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permiso de galería denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para revisar el permiso y abrir la galería
    private fun checkAndRequestGalleryPermission() {
        // Decide qué permiso pedir basado en la versión de Android
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido, abre la galería
                galleryLauncher.launch("image/*")
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // Opcional: Mostrar un diálogo explicando por qué se necesita
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                // Pide el permiso
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KairosTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Crear Cuenta Nueva") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        RegisterScreen(
                            // Pasa el estado de la URI y el click handler al Composable
                            imageUri = imageUri.value,
                            onImagePickerClick = {
                                checkAndRequestGalleryPermission()
                            },
                            onRegisterClick = { nombre, apellido, email, password ->
                                lifecycleScope.launch {
                                    try {
                                        // ✅ Convertir imagen a Base64 si existe
                                        val fotoBase64 = imageUri.value?.let { uri ->
                                            uriToBase64(uri)
                                        }
                                        
                                        val user = User(
                                            nombre = nombre,
                                            apellido = apellido,
                                            correo = email,
                                            contrasena = password,
                                            fotoPerfil = fotoBase64 // ✅ Enviamos la foto en Base64
                                        )

                                        val response = RetrofitClient.instance.register(user)

                                        if (response.isSuccessful) {
                                            val data = response.body()
                                            if (data?.success == true && !data.token.isNullOrEmpty()) {
                                                val sessionManager = SessionManager(this@RegisterActivity)
                                                sessionManager.saveAuthToken(data.token)
                                                
                                                // ✅ CRÍTICO: Guardar los datos del usuario registrado
                                                data.user?.let { user ->
                                                    sessionManager.saveUserId(user.id ?: 0)
                                                    sessionManager.saveUserName(user.nombre)
                                                    sessionManager.saveUserEmail(user.correo)
                                                    // ✅ Guardar la foto si existe
                                                    if (!user.fotoPerfil.isNullOrEmpty()) {
                                                        sessionManager.saveUserProfilePic(user.fotoPerfil)
                                                    }
                                                }
                                                
                                                Toast.makeText(this@RegisterActivity, "Cuenta creada con éxito", Toast.LENGTH_LONG).show()

                                                val intent = Intent(this@RegisterActivity, HomeActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                                startActivity(intent)
                                                finish()
                                            } else {
                                                Toast.makeText(this@RegisterActivity, data?.message ?: "Error en el registro", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(
                                                this@RegisterActivity,
                                                "Error en el registro: ${response.code()}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onLoginClick = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
    
    // ✅ Función para convertir URI de imagen a Base64
    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            // Convertir a Base64 con prefijo para imágenes
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Composable
fun RegisterScreen(
    imageUri: Uri?,
    onImagePickerClick: () -> Unit,
    onRegisterClick: (String, String, String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Únete a Kairos",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Crea tu cuenta en segundos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Selector de Imagen con diseño mejorado
            ProfileImagePicker(
                imageUri = imageUri,
                onClick = onImagePickerClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tarjeta con formulario
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Campo Nombre
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Campo Apellido
                    OutlinedTextField(
                        value = apellido,
                        onValueChange = { apellido = it },
                        label = { Text("Apellido") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Campo Correo
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Campo Contraseña
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de Registrarse
            Button(
                onClick = { onRegisterClick(name, apellido, email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear Cuenta", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón para regresar al Login
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Ya tienes cuenta?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLoginClick) {
                    Text("Inicia sesión aquí", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- NUEVO COMPOSABLE: Selector de Imagen de Perfil ---
@Composable
fun ProfileImagePicker(imageUri: Uri?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape) // Lo hace redondo
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() } // Maneja el click
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri != null) {
            // Si hay una imagen, la muestra
            AsyncImage(
                model = imageUri,
                contentDescription = "Foto de perfil seleccionada",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop // Recorta la imagen para que llene el círculo
            )
        } else {
            // Si no hay imagen, muestra el ícono de lápiz
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Seleccionar foto de perfil",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}