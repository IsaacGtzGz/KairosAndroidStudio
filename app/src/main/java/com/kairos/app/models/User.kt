package com.kairos.app.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("idUsuario")
    val id: Int? = null,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val contrasena: String,
    val fotoPerfil: String? = null
)