package com.kairos.app.models

data class Promocion(
    val idPromocion: Int,
    val titulo: String?,
    val descripcion: String?,
    val fechaInicio: String?,
    val fechaFin: String?,
    val estatus: Boolean
)