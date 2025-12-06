package com.kairos.app.models

// Request para registrar clic en promoción
data class RegistrarClicRequest(
    val idPromocion: Int,
    val idUsuario: Int
)

// Response genérico
data class GenericResponse(
    val mensaje: String
)
