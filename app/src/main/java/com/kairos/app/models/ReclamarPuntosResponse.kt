package com.kairos.app.models

data class ReclamarPuntosResponse(
    val exito: Boolean,
    val mensaje: String,
    val puntosGanados: Int = 0
)
