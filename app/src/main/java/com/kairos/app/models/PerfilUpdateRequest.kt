package com.kairos.app.models

data class PerfilUpdateRequest(
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val fotoPerfil: String? // Enviaremos la URL o string base64 si llegamos a eso
)