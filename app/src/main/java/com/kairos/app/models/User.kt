package com.kairos.app.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("idUsuario")
    val idUsuario: Int? = null,
    val nombre: String = "",
    val apellido: String = "",
    val correo: String = "",
    val contrasena: String? = null,
    val fotoPerfil: String? = null,
    val puntosAcumulados: Int = 0,
    val rol: String? = null
)