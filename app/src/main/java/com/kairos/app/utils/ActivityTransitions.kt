package com.kairos.app.utils

import android.app.Activity
import android.content.Intent
import com.kairos.app.R

/**
 * 🎬 TRANSITION UTILITIES
 * Extensiones para agregar transiciones suaves entre Activities
 */

/**
 * Inicia una Activity con transición de deslizamiento desde la derecha
 */
fun Activity.startActivityWithSlideTransition(intent: Intent) {
    startActivity(intent)
    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
}

/**
 * Finaliza la Activity actual con transición de deslizamiento hacia la derecha
 */
fun Activity.finishWithSlideTransition() {
    finish()
    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
}

/**
 * Inicia una Activity con transición de fade y escala
 */
fun Activity.startActivityWithFadeTransition(intent: Intent) {
    startActivity(intent)
    overridePendingTransition(R.anim.fade_scale_in, R.anim.fade_scale_out)
}

/**
 * Finaliza la Activity actual con transición de fade
 */
fun Activity.finishWithFadeTransition() {
    finish()
    overridePendingTransition(R.anim.fade_scale_in, R.anim.fade_scale_out)
}

/**
 * Transición predeterminada para Activities principales
 */
fun Activity.applyDefaultTransition() {
    overridePendingTransition(R.anim.fade_scale_in, R.anim.fade_scale_out)
}
