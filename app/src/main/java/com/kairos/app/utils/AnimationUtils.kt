package com.kairos.app.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 🎯 MOTION DESIGN UTILITIES
 * Colección de animaciones y micro-interacciones reutilizables
 */

object AnimationSpecs {
    // Duraciones estándar (Material Design)
    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 500
    
    // Easing curves personalizadas
    val FastOutSlowIn = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val EaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
    val BounceEasing = CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)
    
    // Spring configurations
    val SpringStiff = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    val SpringSoft = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * 🎨 PRESS EFFECT - Feedback visual al presionar
 */
fun Modifier.pressEffect(
    minScale: Float = 0.95f,
    onPress: () -> Unit = {}
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) minScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    
    this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    onPress()
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

/**
 * 🌟 PULSE EFFECT - Animación de pulso continuo
 */
@Composable
fun rememberPulseAnimation(
    minScale: Float = 0.9f,
    maxScale: Float = 1.1f,
    duration: Int = 1000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = AnimationSpecs.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return scale
}

/**
 * ✨ SHIMMER EFFECT - Efecto de brillo animado
 */
@Composable
fun rememberShimmerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    return offset
}

/**
 * 🎪 BOUNCE ENTRANCE - Entrada con rebote
 */
fun Modifier.bounceEntrance(
    visible: Boolean,
    delayMillis: Int = 0
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_MEDIUM,
            delayMillis = delayMillis,
            easing = AnimationSpecs.BounceEasing
        ),
        label = "bounceScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_SHORT,
            delayMillis = delayMillis
        ),
        label = "bounceAlpha"
    )
    
    this
        .scale(scale)
        .graphicsLayer { this.alpha = alpha }
}

/**
 * 📍 SLIDE IN FROM SIDE - Deslizamiento lateral
 */
fun Modifier.slideInFromSide(
    visible: Boolean,
    fromLeft: Boolean = true,
    delayMillis: Int = 0
): Modifier = composed {
    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else (if (fromLeft) -300f else 300f),
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_MEDIUM,
            delayMillis = delayMillis,
            easing = AnimationSpecs.FastOutSlowIn
        ),
        label = "slideX"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_SHORT,
            delayMillis = delayMillis
        ),
        label = "slideAlpha"
    )
    
    this.graphicsLayer {
        translationX = offsetX
        this.alpha = alpha
    }
}

/**
 * 🎯 FADE IN UP - Aparición con elevación
 */
fun Modifier.fadeInUp(
    visible: Boolean,
    delayMillis: Int = 0
): Modifier = composed {
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_MEDIUM,
            delayMillis = delayMillis,
            easing = AnimationSpecs.FastOutSlowIn
        ),
        label = "fadeY"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationSpecs.DURATION_MEDIUM,
            delayMillis = delayMillis
        ),
        label = "fadeAlpha"
    )
    
    this.graphicsLayer {
        translationY = offsetY
        this.alpha = alpha
    }
}

/**
 * 🔄 ROTATE ANIMATION - Rotación continua
 */
@Composable
fun rememberRotateAnimation(
    duration: Int = 2000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateAngle"
    )
    return angle
}

/**
 * 🎨 HOVER EFFECT - Elevación al hover (para tablets)
 */
fun Modifier.hoverEffect(): Modifier = composed {
    var isHovered by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 4.dp,
        animationSpec = tween(AnimationSpecs.DURATION_SHORT),
        label = "hoverElevation"
    )
    
    // En Android móvil, esto no se activa, pero está listo para tablets/ChromeOS
    this.graphicsLayer {
        shadowElevation = elevation.toPx()
    }
}

/**
 * 💫 STAGGER ANIMATION - Animación escalonada para listas
 */
@Composable
fun rememberStaggeredVisibility(
    itemCount: Int,
    baseDelay: Int = 50
): List<Boolean> {
    return (0 until itemCount).map { index ->
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay((index * baseDelay).toLong())
            visible = true
        }
        visible
    }
}

/**
 * 🎪 SHAKE ANIMATION - Vibración para errores
 */
@Composable
fun rememberShakeAnimation(trigger: Boolean): Float {
    var shakeOffset by remember { mutableStateOf(0f) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            val shakeSequence = listOf(0f, -10f, 10f, -10f, 10f, -5f, 5f, 0f)
            shakeSequence.forEach { offset ->
                shakeOffset = offset
                kotlinx.coroutines.delay(50)
            }
        }
    }
    
    return shakeOffset
}

/**
 * 🌊 WAVE ANIMATION - Onda para fondos
 */
@Composable
fun rememberWaveAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    return phase
}

/**
 * 📋 LIST ITEM ANIMATION - Para items de LazyColumn con delay escalonado
 */
@Composable
fun rememberListItemAnimation(index: Int, baseDelay: Int = 50): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        kotlinx.coroutines.delay((index * baseDelay).toLong())
        visible = true
    }
    return visible
}

// Extension functions para fácil uso
