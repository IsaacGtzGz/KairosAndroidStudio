package com.kairos.app.models

data class Lugar(
    val idLugar: Int,
    val nombre: String,
    val descripcion: String?,
    val latitud: Double,
    val longitud: Double,
    val direccion: String?,
    val imagen: String?,
    val imagenUrl: String?,
    val ciudad: String?,
    val idCategoria: Int,
    val horario: String?,
    val puntosOtorgados: Int = 10,
    val esPatrocinado: Boolean = false,
    val estatus: Boolean = true
)