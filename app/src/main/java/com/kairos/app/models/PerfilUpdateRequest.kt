package com.kairos.app.models

data class PerfilUpdateRequest(
    val nombre: String?,
    val apellido: String?,
    val correo: String?,
    val fotoPerfil: String?
)