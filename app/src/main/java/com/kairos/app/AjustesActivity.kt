package com.kairos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.utils.SessionManager
import com.kairos.app.utils.AppConstants
import com.kairos.app.network.RetrofitClient
import com.kairos.app.models.Categoria
import kotlinx.coroutines.launch

class AjustesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        // Cargar datos guardados
        val savedInterests = sessionManager.fetchInterests() // Carga intereses
        val savedDays = sessionManager.fetchActiveDays()
        val savedIntensity = sessionManager.fetchIntensity()

        setContent {
            KairosTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Configuración") }, // Nombre corregido
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    AjustesScreen(
                        paddingValues = paddingValues,
                        initialInterests = savedInterests,
                        initialDays = savedDays,
                        initialIntensity = savedIntensity,
                        onSave = { interests, days, intensity ->
                            sessionManager.saveInterests(interests) // Guarda intereses
                            sessionManager.saveActiveDays(days)
                            sessionManager.saveIntensity(intensity)
                            Toast.makeText(this, AppConstants.Messages.PREFERENCES_SAVED, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AjustesScreen(
    paddingValues: PaddingValues,
    initialInterests: Set<String>,
    initialDays: Set<String>,
    initialIntensity: Int,
    onSave: (Set<String>, Set<String>, Int) -> Unit
) {
    // Estados locales
    var selectedInterests by remember { mutableStateOf(initialInterests) }
    var selectedDays by remember { mutableStateOf(initialDays) }
    var selectedIntensity by remember { mutableStateOf(initialIntensity) }
    var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
    var isLoadingCategorias by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()

    // Cargar categorías desde el API
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = RetrofitClient.instance.getCategorias()
                if (response.isSuccessful && response.body() != null) {
                    categorias = response.body()!!
                    android.util.Log.d("AjustesActivity", "Categorías cargadas: ${categorias.size}")
                } else {
                    android.util.Log.e("AjustesActivity", "Error al cargar categorías: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AjustesActivity", "Excepción al cargar categorías", e)
            } finally {
                isLoadingCategorias = false
            }
        }
    }

    // Datos para la UI
    val daysOfWeek = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val intensityOptions = listOf("Baja (Solo esencial)", "Media (Recomendado)", "Alta (Coach activo)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- SECCIÓN 1: MIS INTERESES (DINÁMICO DESDE API) ---
        Column {
            Text("Mis Intereses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Selecciona lo que te gusta para personalizar tu mapa.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingCategorias) {
                // Mostrar indicador de carga
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (categorias.isEmpty()) {
                // Sin categorías disponibles
                Text(
                    "No hay categorías disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Grid dinámico de Chips para Intereses (desde API) usando FlowRow
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categorias.forEach { categoria ->
                        val isSelected = selectedInterests.contains(categoria.nombre)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedInterests = if (isSelected) {
                                    selectedInterests - categoria.nombre
                                } else {
                                    selectedInterests + categoria.nombre
                                }
                            },
                            label = { 
                                Text(
                                    text = categoria.nombre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) 
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        Divider()

        // --- SECCIÓN 2: NOTIFICACIONES (ANTES BIENESTAR) ---
        Column {
            Text("Preferencias de Notificación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Elige cuándo quieres recibir sugerencias de exploración.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Días de la semana
            daysOfWeek.chunked(4).forEach { rowDays ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowDays.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDays = if (isSelected) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            label = { Text(day) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }

        // Intensidad (Radio Buttons)
        Column {
            Text("Frecuencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            intensityOptions.forEachIndexed { index, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = (selectedIntensity == index),
                            onClick = { selectedIntensity = index },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedIntensity == index),
                        onClick = null
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Info de notificaciones (DINÁMICO según intensidad)
        val notificationText = when (selectedIntensity) {
            0 -> "Notificaciones desactivadas. No recibirás mensajes del coach."
            1 -> "Notificaciones mínimas a las 8 PM: solo pasos del día."
            2 -> "Notificaciones moderadas a las 8 PM: pasos + motivación corta."
            3 -> "Notificaciones completas a las 8 PM: análisis detallado personalizado."
            else -> "Configura tu frecuencia de notificaciones."
        }
        
        val diasTexto = if (selectedDays.isEmpty()) "ningún día" else selectedDays.joinToString(", ")
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (selectedIntensity) {
                    0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    1 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    3 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Tu configuración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    notificationText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (selectedDays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Días activos: $diasTexto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Botón Guardar
        Button(
            onClick = { onSave(selectedInterests, selectedDays, selectedIntensity) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardar Preferencias")
        }
    }
}