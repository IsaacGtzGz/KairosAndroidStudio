package com.kairos.app.models

data class InsightResponse(
    val mensaje: String,
    val tipo: String, // "success", "warning", "info"
    val pasosHoy: Int,
    val tiempoDigitalHoy: Int,
    val pasosPromedio: Int,
    val tiempoDigitalPromedio: Int
)
