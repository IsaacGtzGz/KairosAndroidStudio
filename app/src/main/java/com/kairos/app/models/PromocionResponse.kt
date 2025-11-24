package com.kairos.app.models
import com.google.gson.annotations.SerializedName

data class PromocionResponse(
    @SerializedName("\$values")
    val values: List<Promocion>
)