package com.kairos.app.models

data class Lugar(
    val idLugar: Int,
    val nombre: String,
    val descripcion: String?,
    val latitud: Double,
    val longitud: Double,
    val direccion: String?,
    val imagen: String?,
    val idCategoria: Int,
    val horario: String?
)