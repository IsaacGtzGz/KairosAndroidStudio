package com.kairos.app.models

data class Notificacion(
    val idNotificacion: Int,
    val idUsuario: Int?,
    val titulo: String?,
    val mensaje: String?,
    val fechaEnvio: String,
    val leido: Boolean = false
)
