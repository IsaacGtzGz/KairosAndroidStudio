package com.kairos.app.models

data class PreguntaFrecuente(
    val idPregunta: Int,
    val pregunta: String,
    val respuesta: String,
    val categoria: String = "General",
    val orden: Int = 0,
    val estatus: Boolean = true
)
