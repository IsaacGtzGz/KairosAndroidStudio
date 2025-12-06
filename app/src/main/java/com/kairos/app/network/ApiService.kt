package com.kairos.app.network

import com.kairos.app.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ==================== AUTH ====================
    @POST("Auth/register")
    suspend fun register(@Body user: User): Response<AuthResponse>

    @POST("Auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    // ==================== USUARIOS ====================
    @GET("Usuarios")
    suspend fun getUsuarios(): Response<List<User>>

    @GET("Usuarios/{id}")
    suspend fun getUsuario(@Path("id") id: Int): Response<User>

    @PUT("Usuarios/{id}")
    suspend fun updateProfile(
        @Path("id") id: Int,
        @Body request: PerfilUpdateRequest
    ): Response<Void>

    @DELETE("Usuarios/{id}")
    suspend fun deleteUsuario(@Path("id") id: Int): Response<Void>

    // ==================== LUGARES ====================
    @GET("Lugares")
    suspend fun getLugares(): Response<List<Lugar>>

    @GET("Lugares/{id}")
    suspend fun getLugar(@Path("id") id: Int): Response<Lugar>

    @POST("Lugares/reclamar-puntos")
    suspend fun reclamarPuntos(@Body request: ReclamarPuntosRequest): Response<ReclamarPuntosResponse>

    // ==================== PROMOCIONES ====================
    @GET("Promociones")
    suspend fun getPromociones(): Response<List<Promocion>>

    @GET("Promociones/{id}")
    suspend fun getPromocion(@Path("id") id: Int): Response<Promocion>

    @POST("Promociones/registrar-clic")
    suspend fun registrarClic(@Body request: RegistrarClicRequest): Response<GenericResponse>

    // ==================== RUTAS ====================
    @GET("Rutas")
    suspend fun getRutas(): Response<List<Ruta>>

    @GET("Rutas/{id}")
    suspend fun getRuta(@Path("id") id: Int): Response<Ruta>

    @POST("Rutas")
    suspend fun createRuta(@Body ruta: Ruta): Response<Ruta>

    @PUT("Rutas/{id}")
    suspend fun updateRuta(@Path("id") id: Int, @Body ruta: Ruta): Response<Void>

    @DELETE("Rutas/{id}")
    suspend fun deleteRuta(@Path("id") id: Int): Response<Void>

    // ==================== NOTIFICACIONES ====================
    @GET("Notificaciones/{idUsuario}")
    suspend fun getNotificaciones(@Path("idUsuario") idUsuario: Int): Response<List<Notificacion>>

    @POST("Notificaciones")
    suspend fun createNotificacion(@Body notificacion: Notificacion): Response<Notificacion>

    @DELETE("Notificaciones/{id}")
    suspend fun deleteNotificacion(@Path("id") id: Int): Response<Void>

    // ==================== CATEGORIAS ====================
    @GET("Categorias")
    suspend fun getCategorias(): Response<List<Categoria>>

    @GET("Categorias/{id}")
    suspend fun getCategoria(@Path("id") id: Int): Response<Categoria>

    // ==================== INTERESES ====================
    @GET("Intereses")
    suspend fun getIntereses(): Response<List<Interes>>

    @GET("Intereses/{id}")
    suspend fun getInteres(@Path("id") id: Int): Response<Interes>

    // ==================== MENSAJES CONTACTO ====================
    @GET("MensajesContacto")
    suspend fun getMensajesContacto(): Response<List<MensajeContacto>>

    @POST("MensajesContacto")
    suspend fun enviarMensajeContacto(@Body mensaje: MensajeContacto): Response<MensajeContacto>

    // ==================== PREGUNTAS FRECUENTES ====================
    @GET("PreguntasFrecuentes")
    suspend fun getPreguntasFrecuentes(): Response<List<PreguntaFrecuente>>

    @GET("PreguntasFrecuentes/{id}")
    suspend fun getPreguntaFrecuente(@Path("id") id: Int): Response<PreguntaFrecuente>

    // ==================== PUNTOS DE INTERES ====================
    @GET("PuntosInteres")
    suspend fun getPuntosInteres(): Response<List<PuntoInteres>>

    @GET("PuntosInteres/{id}")
    suspend fun getPuntoInteres(@Path("id") id: Int): Response<PuntoInteres>

    // ==================== RESEÑAS ====================
    @GET("Resenas")
    suspend fun getResenas(): Response<List<Resena>>

    @POST("Resenas")
    suspend fun createResena(@Body resena: Resena): Response<Resena>

    // ==================== ACTIVIDADES ====================
    @POST("Actividades")
    suspend fun enviarPasos(@Body request: ActividadFisicaRequest): Response<Void>

    // ==================== USO DIGITAL ====================
    @POST("UsoDigital")
    suspend fun enviarUsoDigital(@Body request: UsoDigitalRequest): Response<Void>

    // ==================== INSIGHTS (IA) ====================
    @GET("Insights/{userId}")
    suspend fun getInsight(@Path("userId") userId: Int): Response<InsightResponse>
}