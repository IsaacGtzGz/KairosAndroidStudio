package com.kairos.app.models

data class ActividadFisicaRequest(
    val idUsuario: Int,
    val pasos: Int,
    val fecha: String // Formato "yyyy-MM-dd"
)