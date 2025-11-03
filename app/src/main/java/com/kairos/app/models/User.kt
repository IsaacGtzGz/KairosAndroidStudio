package com.kairos.app.models

data class User(
    val id: Int? = null,
    val nombre: String,
    val correo: String,
    val contrasena: String,
    val edad: Int,
    val preferencias: String
)
