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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
                                        // PASO 1: Aún enviamos 'null' como fotoPerfil.
                                        // La lógica de subir la imagen se hará después.
                                        val user = User(
                                            nombre = nombre,
                                            apellido = apellido,
                                            correo = email,
                                            contrasena = password,
                                            fotoPerfil = null // Backend no está listo para la imagen
                                        )

                                        val response = RetrofitClient.instance.register(user)

                                        if (response.isSuccessful) {
                                            val data = response.body()
                                            if (data?.success == true && !data.token.isNullOrEmpty()) {
                                                SessionManager(this@RegisterActivity).saveAuthToken(data.token)
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
}

@Composable
fun RegisterScreen(
    imageUri: Uri?, // Parámetro para recibir la URI de la imagen
    onImagePickerClick: () -> Unit, // Parámetro para manejar el click
    onRegisterClick: (String, String, String, String) -> Unit, // Firma actualizada
    onLoginClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, // Centra el picker
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Completa tus datos",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // --- Selector de Imagen de Perfil ---
        ProfileImagePicker(
            imageUri = imageUri,
            onClick = onImagePickerClick
        )
        // ------------------------------------

        Spacer(modifier = Modifier.height(12.dp))

        // Campo Nombre
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Campo Apellido
        OutlinedTextField(
            value = apellido,
            onValueChange = { apellido = it },
            label = { Text("Apellido") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Campo Correo
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Campo Contraseña
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // El campo de texto de fotoPerfil se eliminó

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onRegisterClick(name, apellido, email, password) }, // Firma actualizada
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Registrarse", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón para regresar al Login
        TextButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text("¿Ya tienes cuenta? Inicia sesión")
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