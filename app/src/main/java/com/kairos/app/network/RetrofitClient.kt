package com.kairos.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // CAMBIAR ESTA URL CUANDO SE SUBA A AZURE
    // Cuando tengan la URL de producción, reemplazar la línea activa por la URL de Azure
    
    //x     private const val BASE_URL = "http://192.168.1.67:5219/api/" // RED LOCAL (DESARROLLO)
    //private const val BASE_URL = "http://192.168.64.193:5219/api/" // RED Cel
    private const val BASE_URL = "https://kairos-api-deleon-cwffh5augvctfyb7.westus-01.azurewebsites.net/api/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}

