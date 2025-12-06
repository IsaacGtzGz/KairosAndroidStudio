package com.kairos.app.models

data class Resena(
    val idResena: Int? = null,
    val usuarioNombre: String,
    val rol: String = "Explorador",
    val comentario: String,
    val estrellas: Int,
    val fechaRegistro: String? = null,
    val estatus: Boolean = true
)
