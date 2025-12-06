package com.kairos.app.models

data class Categoria(
    val idCategoria: Int,
    val nombre: String,
    val descripcion: String?,
    val estatus: Boolean = true
)
