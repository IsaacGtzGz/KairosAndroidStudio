package com.kairos.app.models

data class PuntoInteres(
    val idPunto: Int,
    val idLugar: Int,
    val etiqueta: String,
    val descripcion: String?,
    val prioridad: Int = 0,
    val estatus: Boolean = true,
    val idLugarNavigation: Lugar? = null
)
