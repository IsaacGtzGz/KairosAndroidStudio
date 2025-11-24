package com.kairos.app.models

data class UsoDigitalRequest(
    val idUsuario: Int,
    val tiempoMinutos: Int,
    val fecha: String // Formato "yyyy-MM-dd"
)