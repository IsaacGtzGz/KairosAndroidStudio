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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kairos.app.models.PerfilUpdateRequest
import com.kairos.app.network.RetrofitClient
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import kotlinx.coroutines.launch

class PerfilActivity : ComponentActivity() {

    // Estado de la imagen seleccionada
    private var selectedImageUri = mutableStateOf<Uri?>(null)

    // Lanzadores para la galería (Igual que en Registro)
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri.value = it }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) galleryLauncher.launch("image/*")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        // Cargar datos iniciales
        val currentId = sessionManager.fetchUserId()
        val currentNameFull = sessionManager.fetchUserName() ?: "Usuario Kairos"
        val currentEmail = sessionManager.fetchUserEmail() ?: "correo@ejemplo.com"
        val currentPic = sessionManager.fetchUserProfilePic()

        // Separar nombre y apellido para editar (Lógica simple)
        val nameParts = currentNameFull.split(" ")
        val initialNombre = nameParts.getOrElse(0) { "" }
        val initialApellido = if (nameParts.size > 1) nameParts.subList(1, nameParts.size).joinToString(" ") else ""

        setContent {
            KairosTheme {
                var isEditing by remember { mutableStateOf(false) }

                // Campos de texto editables
                var nombre by remember { mutableStateOf(initialNombre) }
                var apellido by remember { mutableStateOf(initialApellido) }

                val scope = rememberCoroutineScope()

                // Si seleccionamos una imagen nueva local, usamos esa. Si no, la guardada (URL).
                val displayImage = selectedImageUri.value ?: currentPic

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (isEditing) "Editando Perfil" else "Mi Perfil") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            },
                            actions = {
                                if (!isEditing) {
                                    IconButton(onClick = { isEditing = true }) {
                                        Icon(Icons.Default.Edit, "Editar")
                                    }
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))

                        // --- FOTO DE PERFIL ---
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable(enabled = isEditing) {
                                    checkAndRequestGalleryPermission()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayImage != null) {
                                AsyncImage(
                                    model = displayImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    null,
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Icono de camarita si está editando
                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Edit, null, tint = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // --- CAMPOS DE DATOS ---

                        if (isEditing) {
                            // MODO EDICIÓN
                            OutlinedTextField(
                                value = nombre,
                                onValueChange = { nombre = it },
                                label = { Text("Nombre") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = apellido,
                                onValueChange = { apellido = it },
                                label = { Text("Apellido") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = currentEmail,
                                onValueChange = { },
                                label = { Text("Correo (no editable)") },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            )

                        } else {
                            // MODO VISUALIZACIÓN
                            Text(
                                text = "$nombre $apellido",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentEmail,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // --- BOTÓN DE ACCIÓN ---
                        if (isEditing) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // ✅ 1. Convertir imagen a Base64 si hay nueva
                                            // 1. Convertir imagen a Base64 solo si hay nueva
                                            val fotoBase64 = selectedImageUri.value?.let { uri ->
                                                uriToBase64(uri)
                                            }
                                            
                                            // 2. Crear objeto de actualización (sin idUsuario, va en la URL)
                                            val request = PerfilUpdateRequest(
                                                nombre = nombre.ifBlank { null },
                                                apellido = apellido.ifBlank { null },
                                                correo = null, // No se edita el correo aquí
                                                fotoPerfil = fotoBase64 // Solo se envía si hay nueva foto
                                            )

                                            // 3. Llamar a la API
                                            val response = RetrofitClient.instance.updateProfile(currentId, request)

                                            if (response.isSuccessful) {
                                                // 4. Actualizar sesión local
                                                sessionManager.saveUserName("$nombre $apellido")
                                                if (fotoBase64 != null) {
                                                    sessionManager.saveUserProfilePic(fotoBase64)
                                                }
                                                Toast.makeText(this@PerfilActivity, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                                                isEditing = false
                                                selectedImageUri.value = null // Limpiar imagen temporal
                                            } else {
                                                val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                                                Toast.makeText(this@PerfilActivity, "Error al guardar: ${response.code()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            // Fallback para la demo si la API falla: Guardar localmente
                                            sessionManager.saveUserName("$nombre $apellido")
                                            if (selectedImageUri.value != null) {
                                                sessionManager.saveUserProfilePic(selectedImageUri.value.toString())
                                            }
                                            Toast.makeText(this@PerfilActivity, "Guardado localmente (Error API)", Toast.LENGTH_SHORT).show()
                                            isEditing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar Cambios")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestGalleryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                galleryLauncher.launch("image/*")
            }
            else -> requestPermissionLauncher.launch(permission)
        }
    }
    
    // Función para convertir URI de imagen a Base64 con compresión
    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Comprimir imagen para reducir tamaño (calidad 70%, max 800px)
            val outputStream = java.io.ByteArrayOutputStream()
            val maxDimension = 800
            val scale = minOf(
                maxDimension.toFloat() / bitmap.width,
                maxDimension.toFloat() / bitmap.height,
                1f
            )
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap
            
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            
            // Convertir a Base64 con prefijo
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al procesar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}