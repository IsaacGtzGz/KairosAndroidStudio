package com.kairos.app.models

data class SocioAfiliado(
    val idSocio: Int,
    val nombreSocio: String,
    val tarifaCPC: Double?,
    val estatus: Boolean = true
)
