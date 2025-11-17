package com.kairos.app.models

import com.google.gson.annotations.SerializedName

data class LugarResponse(
    @SerializedName("\$values") // Esto le dice a Gson que lea el campo "$values"
    val values: List<Lugar>
)