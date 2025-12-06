package com.kairos.app.models

data class Ruta(
    val idRuta: Int,
    val idUsuario: Int?,
    val nombre: String,
    val descripcion: String?,
    val latitudInicio: Double?,
    val longitudInicio: Double?,
    val latitudFin: Double?,
    val longitudFin: Double?,
    val idLugarInicio: Int?,
    val idLugarFin: Int?,
    val fechaCreacion: String,
    val estatus: String = "Planificada",
    val rutasLugares: List<RutaLugar>? = null
)

data class RutaLugar(
    val idRuta: Int,
    val idLugar: Int,
    val orden: Int
)
