package com.kairos.app.models

data class ReclamarPuntosRequest(
    val idUsuario: Int,
    val idLugar: Int,
    val latitudUsuario: Double = 0.0,
    val longitudUsuario: Double = 0.0
)
