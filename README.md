# 🌟 Kairos Android App

**Aplicación móvil para explorar lugares turísticos y ganar puntos por visitarlos.**

---

## 📱 Características Principales

### 🎮 Gamificación
- ✅ Sistema de puntos por check-in en lugares
- ✅ Niveles de explorador (Principiante → Experto)
- ✅ Historial de visitas y puntos ganados
- ✅ Badge animado de puntos en tiempo real

### 🗺️ Exploración
- ✅ Búsqueda avanzada de lugares
- ✅ Filtros por categoría y ciudad
- ✅ Mapa interactivo con marcadores
- ✅ Geolocalización GPS para check-in

### 🎁 Recompensas
- ✅ Catálogo de promociones
- ✅ Canje con puntos acumulados
- ✅ Registro de clics y tracking
- ✅ Sistema de validación de puntos

### 📍 Rutas Turísticas
- ✅ Crear y guardar rutas personalizadas
- ✅ Ver rutas recomendadas
- ✅ Navegación turn-by-turn

### 🔔 Notificaciones
- ✅ Centro de notificaciones
- ✅ Alertas de nuevas promociones
- ✅ Recordatorios de lugares cercanos

### 🤖 Coach IA
- ✅ Insights personalizados basados en actividad
- ✅ Chat con recomendaciones
- ✅ Análisis de hábitos digitales

### 🏃 Bienestar Digital
- ✅ Contador de pasos (podómetro)
- ✅ Tracking de uso de redes sociales
- ✅ Estadísticas de actividad física
- ✅ Botón de emergencia SOS

---

## 🛠️ Tecnologías Utilizadas

### Stack Principal
- **Lenguaje**: Kotlin 100%
- **UI**: Jetpack Compose (Material 3)
- **Arquitectura**: Activity-based con Compose
- **Red**: Retrofit + OkHttp
- **JSON**: Gson
- **Imágenes**: Coil
- **Mapas**: Google Maps Compose
- **Permisos**: Activity Result API

### Dependencias Clave
```kotlin
// Compose
androidx.compose.material3:material3
androidx.activity:activity-compose

// Networking
com.squareup.retrofit2:retrofit
com.squareup.retrofit2:converter-gson

// Images
io.coil-kt:coil-compose

// Maps
com.google.maps.android:maps-compose
com.google.android.gms:play-services-location

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android
```

---

## 📦 Estructura del Proyecto

```
app/src/main/java/com/kairos/app/
├── activities/
│   ├── SplashActivity.kt         # Pantalla inicial
│   ├── MainActivity.kt            # Login
│   ├── RegisterActivity.kt        # Registro
│   ├── HomeActivity.kt            # Dashboard principal
│   ├── ExplorarActivity.kt        # Búsqueda de lugares
│   ├── DetalleLugarActivity.kt    # Detalle y check-in
│   ├── MapActivity.kt             # Mapa interactivo
│   ├── RecompensasActivity.kt     # Lista de promociones
│   ├── PromocionDetalleActivity.kt # Canje de promociones
│   ├── RutasActivity.kt           # Rutas turísticas
│   ├── NotificacionesActivity.kt  # Centro de notificaciones
│   ├── HistorialPuntosActivity.kt # Historial de puntos
│   ├── PerfilActivity.kt          # Perfil de usuario
│   ├── AjustesActivity.kt         # Configuración
│   ├── FAQActivity.kt             # Preguntas frecuentes
│   ├── ContactoActivity.kt        # Formulario de contacto
│   ├── CoachChatActivity.kt       # Chat con IA
│   └── UsageDetailActivity.kt     # Uso digital
│
├── models/
│   ├── User.kt
│   ├── Lugar.kt
│   ├── Promocion.kt
│   ├── Ruta.kt
│   ├── Notificacion.kt
│   ├── Categoria.kt
│   ├── Interes.kt
│   ├── MensajeContacto.kt
│   ├── PreguntaFrecuente.kt
│   ├── HistorialVisita.kt
│   └── ... (23 modelos totales)
│
├── network/
│   ├── RetrofitClient.kt
│   └── ApiService.kt (78+ endpoints)
│
├── utils/
│   ├── SessionManager.kt          # Manejo de sesión
│   ├── NetworkUtils.kt            # Utilidades de red
│   ├── NetworkHelper.kt           # Validación de conectividad
│   ├── LocationUtils.kt           # Cálculos GPS
│   └── AppConstants.kt            # Constantes globales
│
├── components/
│   └── StateComponents.kt         # Componentes reutilizables
│
├── notifications/
│   └── DailyInsightWorker.kt      # Notificaciones diarias
│
└── ui/theme/
    └── Theme.kt                   # Tema Material 3
```

---

## 🚀 Configuración Inicial

### 1. Prerequisitos
- Android Studio Hedgehog o superior
- JDK 17+
- Android SDK 34 (API 34)
- Dispositivo físico o emulador con Google Play Services

### 2. Clonar el Repositorio
```bash
git clone https://github.com/IsaacGtzGz/KairosAndroidStudio.git
cd KairosAndroidStudio
```

### 3. Configurar API Key de Google Maps
1. Obtener API Key en [Google Cloud Console](https://console.cloud.google.com)
2. Habilitar: Maps SDK for Android y Places API
3. Crear archivo `local.properties` en la raíz:
```properties
MAPS_API_KEY=TU_API_KEY_AQUI
```

### 4. Configurar URL del Backend
Editar `RetrofitClient.kt`:
```kotlin
private const val BASE_URL = "http://TU_IP:5219/api/"
```

### 5. Compilar y Ejecutar
```bash
./gradlew clean
./gradlew assembleDebug
```

---

## 🔧 Configuración de la API Backend

La app requiere que la **KairosAPI** esté corriendo. Asegúrate de:

1. Tener SQL Server con la base de datos `Kairos`
2. Ejecutar el script `KairosDB_Azure_Script.sql`
3. Configurar `appsettings.json` con tu connection string
4. Ejecutar la API:
```bash
cd KairosAPI/KairosAPI
dotnet run
```

La API debe estar en: `http://localhost:5219` o tu IP local.

---

## 📱 Uso de la Aplicación

### Primera Vez
1. **Splash Screen** → Verifica sesión
2. **Login/Registro** → Crea cuenta o inicia sesión
3. **Permisos** → Acepta ubicación, actividad física, notificaciones

### Navegación Principal
- **Home**: Dashboard con estadísticas y accesos rápidos
- **Explorar**: Busca lugares con filtros
- **Mapa**: Ve lugares cercanos
- **Recompensas**: Canjea puntos por promociones
- **Perfil**: Edita tu información

### Sistema de Puntos
1. Busca un lugar en **Explorar**
2. Ve al lugar físicamente
3. Abre **Detalle del Lugar**
4. Presiona **Check-In** cuando estés cerca (100m)
5. ¡Ganas puntos! 🎉

### Canjear Promociones
1. Ve a **Recompensas**
2. Selecciona una promoción
3. Verifica que tengas suficientes puntos
4. Presiona **Canjear Promoción**
5. Recibe confirmación

---

## 🎨 Paleta de Colores

```kotlin
Primary Green:   #90EE90
Dark Green:      #4A7C59
Blue Green:      #5F9EA0
Gold:            #FFD700
```

---

## 📄 Licencia

Este proyecto es parte del Proyecto Kairos - UTL Ingeniería en Desarrollo de Software.

---

## 👥 Equipo de Desarrollo

- **Isaac González** - Desarrollo Android
- **Nava** - Desarrollo Backend (API)
- **Equipo Kairos** - Diseño y PWA

---

## 📞 Soporte

Para reportar bugs o solicitar features:
- Email: soporte@kairosx.com
- Issues: [GitHub Issues](https://github.com/IsaacGtzGz/KairosAndroidStudio/issues)

---

## 🔄 Changelog

### v1.0.0 (Diciembre 2025)
- ✨ Lanzamiento inicial
- 🎮 Sistema de gamificación completo
- 🗺️ Exploración con filtros avanzados
- 🎁 Canje de promociones
- 📍 Rutas turísticas
- 🔔 Centro de notificaciones
- 🤖 Coach IA con insights
- 🏃 Tracking de bienestar digital
- 🆘 Botón de emergencia SOS

---

**¡Gracias por usar Kairos! Explora tu ciudad y gana recompensas. 🌟**
