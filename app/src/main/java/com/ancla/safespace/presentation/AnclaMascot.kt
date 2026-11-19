package com.ancla.safespace.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal enum class MascotMood { GREETING, JOYFUL, CALM }

/** Anclita is drawn as a small 2D character rig so her body, arms, feet and face move independently. */
@Composable
internal fun AnclaMascot(mood: MascotMood, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "Anclita animada")
    val breath by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == MascotMood.CALM) 2_800 else 1_850, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "respiración del cuerpo",
    )
    val sway by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == MascotMood.CALM) 2_500 else 940, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "balanceo de los brazos",
    )
    val armWave by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (mood == MascotMood.CALM) 3_100 else 1_080, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gesto independiente del brazo",
    )
    val footWave by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (mood == MascotMood.CALM) 3_400 else 1_360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "movimiento de los pies",
    )
    val bounce by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == MascotMood.CALM) 2_300 else 780, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pequeños saltos",
    )
    val sparkle by motion.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "brillo",
    )
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_300)
            blinking = true
            delay(115)
            blinking = false
            if ((System.nanoTime() and 3L) == 0L) {
                delay(120)
                blinking = true
                delay(90)
                blinking = false
            }
        }
    }
    var playfulNudge by remember { mutableStateOf(0) }
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(playfulNudge) {
        if (playfulNudge > 0) {
            nudge.snapTo(0f)
            nudge.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
            nudge.animateTo(-0.82f, tween(175, easing = FastOutSlowInEasing))
            nudge.animateTo(0.52f, tween(145, easing = FastOutSlowInEasing))
            nudge.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    val blinkAmount = if (blinking) 0.08f else 1f
    Canvas(
        modifier = modifier
            .size(126.dp)
            .semantics {
                contentDescription = "Anclita, toca para verla hacer un bailecito"
                role = Role.Button
                onClick(label = "Hacer bailar a Anclita") {
                    playfulNudge++
                    true
                }
            }
            .pointerInput(Unit) { detectTapGestures { playfulNudge++ } },
    ) {
        val unit = size.minDimension / 128f
        val moodLift = when (mood) {
            MascotMood.GREETING -> 4.5f
            MascotMood.JOYFUL -> 8f
            MascotMood.CALM -> 1f
        }
        val lift = moodLift * bounce
        val bodyScaleY = 0.97f + breath * 0.07f
        val bodyScaleX = 1.015f - breath * 0.025f
        val waveAmount = armWave + nudge.value * 0.55f
        drawContext.transform.scale(unit, unit, Offset.Zero)

        drawSparkle(20f, 45f - lift * 0.2f, 7f, sparkle, mood != MascotMood.CALM)
        drawSparkle(107f, 52f - lift * 0.1f, 5f, 1f - sparkle * 0.45f, mood != MascotMood.CALM)

        val armBase = when (mood) {
            MascotMood.GREETING -> 14f + waveAmount * 30f
            MascotMood.JOYFUL -> -29f + waveAmount * 24f
            MascotMood.CALM -> 5f + waveAmount * 4f
        }
        withTransform({ rotate(armBase, Offset(35f, 66f)) }) {
            val leftFlipper = Path().apply {
                moveTo(35f, 59f)
                cubicTo(26f, 51f, 15f, 55f, 13f, 65f)
                cubicTo(10f, 77f, 21f, 83f, 30f, 77f)
                cubicTo(36f, 73f, 39f, 67f, 40f, 64f)
                close()
            }
            drawPath(leftFlipper, Brush.linearGradient(listOf(Color(0xFF9BD8BC), Color(0xFF398875)), Offset(12f, 55f), Offset(39f, 80f)))
            drawOval(Color(0xFFB8E4CF).copy(alpha = 0.7f), Offset(16f, 60f), Size(7f, 3f))
        }
        withTransform({ rotate(-armBase * 0.48f + sway * 6f, Offset(93f, 66f)) }) {
            val rightFlipper = Path().apply {
                moveTo(93f, 59f)
                cubicTo(102f, 51f, 113f, 55f, 115f, 65f)
                cubicTo(118f, 77f, 107f, 83f, 98f, 77f)
                cubicTo(92f, 73f, 89f, 67f, 88f, 64f)
                close()
            }
            drawPath(rightFlipper, Brush.linearGradient(listOf(Color(0xFF5EAF91), Color(0xFF347E70)), Offset(90f, 55f), Offset(117f, 80f)))
            drawOval(Color(0xFFB8E4CF).copy(alpha = 0.62f), Offset(105f, 60f), Size(7f, 3f))
        }

        val footKick = if (mood == MascotMood.CALM) footWave * 2f else footWave * 9f
        withTransform({ rotate(-footKick, Offset(49f, 101f)) }) {
            drawOval(Color(0xFF398775), Offset(39f, 98f - lift * 0.35f), Size(21f, 14f))
        }
        withTransform({ rotate(footKick, Offset(78f, 101f)) }) {
            drawOval(Color(0xFF398775), Offset(68f, 98f - lift * 0.35f), Size(21f, 14f))
        }

        drawOval(Color(0x20355F55), Offset(30f, 108f), Size(68f, 9f))
        drawContext.transform.translate(0f, -lift)
        drawContext.transform.scale(bodyScaleX, bodyScaleY, Offset(64f, 70f))

        drawOval(
            Brush.linearGradient(
                listOf(Color(0xFFB9E5D0), Color(0xFF79BFA3), Color(0xFF4D9C87)),
                Offset(38f, 29f), Offset(95f, 109f),
            ),
            Offset(25f, 27f), Size(78f, 83f),
        )
        drawOval(Color(0x55FFFFFF), Offset(35f, 35f), Size(20f, 9f))
        drawOval(Color(0x33FFFFFF), Offset(31f, 48f), Size(8f, 5f))
        drawOval(
            Brush.linearGradient(listOf(Color(0xFFFFF3D8), Color(0xFFFFE5BE)), Offset(50f, 65f), Offset(79f, 105f)),
            Offset(43f, 68f), Size(42f, 39f),
        )

        withTransform({ rotate(sway * 0.8f + nudge.value * 2f, Offset(64f, 39f)) }) {
            val anchor = Color(0xFF397E70)
            drawCircle(anchor, 2.5f, Offset(64f, 31f), style = Stroke(1.7f))
            drawLine(anchor, Offset(64f, 33.5f), Offset(64f, 41f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(anchor, Offset(59.5f, 36.5f), Offset(68.5f, 36.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            val flukes = Path().apply {
                moveTo(64f, 39f)
                cubicTo(63f, 43f, 60f, 45f, 57f, 42f)
                lineTo(56f, 40f)
                moveTo(64f, 39f)
                cubicTo(65f, 43f, 68f, 45f, 71f, 42f)
                lineTo(72f, 40f)
            }
            drawPath(flukes, anchor, style = Stroke(1.8f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            val leftTip = Path().apply { moveTo(56f, 40f); lineTo(53.5f, 38.5f); lineTo(54f, 43f); close() }
            val rightTip = Path().apply { moveTo(72f, 40f); lineTo(74.5f, 38.5f); lineTo(74f, 43f); close() }
            drawPath(leftTip, anchor)
            drawPath(rightTip, anchor)
        }

        drawOval(Color(0x66F49F8C), Offset(34f, 69f), Size(13f, 8f))
        drawOval(Color(0x66F49F8C), Offset(81f, 69f), Size(13f, 8f))
        val eyeHeight = 13f * blinkAmount
        if (blinkAmount > 0.15f) {
            drawOval(Color(0xFF235649), Offset(49f, 55f + (13f - eyeHeight) / 2f), Size(10f, eyeHeight))
            drawOval(Color(0xFF235649), Offset(70f, 55f + (13f - eyeHeight) / 2f), Size(10f, eyeHeight))
            drawCircle(Color.White, 2.2f, Offset(52f, 58f + (13f - eyeHeight) / 2f))
            drawCircle(Color.White, 2.2f, Offset(73f, 58f + (13f - eyeHeight) / 2f))
        } else {
            drawLine(Color(0xFF235649), Offset(48f, 62f), Offset(59f, 62f), strokeWidth = 2.4f, cap = StrokeCap.Round)
            drawLine(Color(0xFF235649), Offset(69f, 62f), Offset(80f, 62f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
        if (mood == MascotMood.CALM) {
            val smile = Path().apply {
                moveTo(57f, 76f)
                cubicTo(61f, 80f, 67f, 80f, 71f, 76f)
            }
            drawPath(smile, Color(0xFF9D5E55), style = Stroke(2.6f, cap = StrokeCap.Round))
        } else {
            val smile = Path().apply {
                moveTo(55f, 74f)
                cubicTo(58f, 70f, 72f, 70f, 75f, 74f)
                cubicTo(74f, 86f, 57f, 87f, 55f, 74f)
                close()
            }
            drawPath(smile, Color(0xFF934E4C))
            drawOval(Color(0xFFFF9B91), Offset(60f, 79f), Size(10f, 4f))
        }
        drawArc(Color(0xFF397E70), 205f, 115f, false, Offset(47f, 48f), Size(14f, 8f), style = Stroke(2.1f, cap = StrokeCap.Round))
        drawArc(Color(0xFF397E70), 220f, 115f, false, Offset(68f, 48f), Size(14f, 8f), style = Stroke(2.1f, cap = StrokeCap.Round))
        drawContext.transform.scale(1f / bodyScaleX, 1f / bodyScaleY, Offset(64f, 70f))
        drawContext.transform.translate(0f, lift)
        drawContext.transform.scale(1f / unit, 1f / unit, Offset.Zero)
    }
}

private fun DrawScope.drawSparkle(x: Float, y: Float, radius: Float, alpha: Float, enabled: Boolean) {
    if (!enabled) return
    val shape = Path().apply {
        moveTo(x, y - radius)
        quadraticTo(x + radius * 0.17f, y - radius * 0.17f, x + radius, y)
        quadraticTo(x + radius * 0.17f, y + radius * 0.17f, x, y + radius)
        quadraticTo(x - radius * 0.17f, y + radius * 0.17f, x - radius, y)
        quadraticTo(x - radius * 0.17f, y - radius * 0.17f, x, y - radius)
        close()
    }
    drawPath(shape, Color(0xFFFFC957).copy(alpha = alpha))
}
