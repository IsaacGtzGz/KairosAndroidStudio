package com.kairos.app.models

data class HistorialVisita(
    val idVisita: Int,
    val idUsuario: Int,
    val idLugar: Int,
    val puntosGanados: Int,
    val fechaVisita: String
)