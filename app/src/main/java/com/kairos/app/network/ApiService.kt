package com.kairos.app.network

import com.kairos.app.models.AuthResponse
import com.kairos.app.models.LugarResponse
import com.kairos.app.models.PromocionResponse
import com.kairos.app.models.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("Auth/register")
    suspend fun register(@Body user: User): Response<AuthResponse>

    @POST("Auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @GET("Lugares")
    suspend fun getLugares(): Response<LugarResponse>

    @GET("Promociones")
    suspend fun getPromociones(): Response<PromocionResponse>
}