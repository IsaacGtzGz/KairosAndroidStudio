package com.kairos.app.models

data class MensajeContacto(
    val idMensaje: Int? = null,
    val nombre: String,
    val correo: String,
    val asunto: String?,
    val mensaje: String,
    val fechaEnvio: String? = null,
    val estatus: String = "Pendiente"
)
