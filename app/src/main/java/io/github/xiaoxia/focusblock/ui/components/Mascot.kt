package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

enum class MascotKind { FOCUS, REST, CELEBRATE, DAZE, CONFUSED }

/**
 * 五个形态的矢量小番茄：专注 / 休息 / 庆祝 / 发呆 / 疑惑。
 * 全部用 Canvas 画，不占包体。
 */
@Composable
fun Mascot(
    kind: MascotKind,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val tones = LocalTones.current
    val extra = LocalExtra.current
    val body = when (kind) {
        MascotKind.REST -> tones.shortBreak
        MascotKind.CELEBRATE -> tones.warn
        MascotKind.DAZE -> tones.longBreak
        MascotKind.CONFUSED -> tones.warn
        MascotKind.FOCUS -> tones.focus
    }
    val ink = extra.text

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val cy = h * 0.58f
            val r = w * 0.33f

            drawBody(body, cx, cy, r, ink, tones)
            drawFace(kind, cx, cy, r, ink, tones.success)
            drawDecor(kind, cx, cy, r, ink, tones)
        }
    }
}

private fun DrawScope.drawBody(
    body: Color,
    cx: Float,
    cy: Float,
    r: Float,
    ink: Color,
    tones: io.github.xiaoxia.focusblock.ui.theme.ToneColors,
) {
    // 身体
    drawCircle(color = body, radius = r, center = Offset(cx, cy))

    // 叶子
    val leafTop = cy - r - r * 0.06f
    val leaf = Path().apply {
        moveTo(cx, leafTop + r * 0.16f)
        lineTo(cx - r * 0.30f, leafTop - r * 0.20f)
        lineTo(cx - r * 0.04f, leafTop - r * 0.06f)
        lineTo(cx + r * 0.16f, leafTop - r * 0.30f)
        lineTo(cx + r * 0.18f, leafTop - r * 0.04f)
        lineTo(cx + r * 0.36f, leafTop - r * 0.14f)
        close()
    }
    drawPath(leaf, tones.success)
    // 梗
    drawLine(
        color = tones.leaf,
        start = Offset(cx, leafTop + r * 0.18f),
        end = Offset(cx, leafTop + r * 0.02f),
        strokeWidth = r * 0.10f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawFace(
    kind: MascotKind,
    cx: Float,
    cy: Float,
    r: Float,
    ink: Color,
    success: Color,
) {
    val eyeDx = r * 0.34f
    val eyeDy = -r * 0.10f
    val eyeR = r * 0.10f
    val stroke = Stroke(width = r * 0.09f, cap = StrokeCap.Round)

    when (kind) {
        MascotKind.FOCUS -> {
            drawCircle(ink, eyeR, Offset(cx - eyeDx, cy + eyeDy))
            drawCircle(ink, eyeR, Offset(cx + eyeDx, cy + eyeDy))
            drawArc(
                color = ink,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.22f, cy + r * 0.16f),
                size = Size(r * 0.44f, r * 0.30f),
                style = stroke,
            )
        }

        MascotKind.REST -> {
            // 闭着的眼睛
            drawLine(ink, Offset(cx - eyeDx - eyeR, cy + eyeDy), Offset(cx - eyeDx + eyeR, cy + eyeDy), stroke.width, StrokeCap.Round)
            drawLine(ink, Offset(cx + eyeDx - eyeR, cy + eyeDy), Offset(cx + eyeDx + eyeR, cy + eyeDy), stroke.width, StrokeCap.Round)
            drawArc(
                color = ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.14f, cy + r * 0.14f),
                size = Size(r * 0.28f, r * 0.22f),
                style = stroke,
            )
        }

        MascotKind.CELEBRATE -> {
            // ^ ^
            drawArc(
                color = ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(cx - eyeDx - eyeR, cy + eyeDy - eyeR * 0.4f),
                size = Size(eyeR * 2f, eyeR * 1.6f), style = stroke,
            )
            drawArc(
                color = ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(cx + eyeDx - eyeR, cy + eyeDy - eyeR * 0.4f),
                size = Size(eyeR * 2f, eyeR * 1.6f), style = stroke,
            )
            // 张嘴笑
            val mouth = Path().apply {
                moveTo(cx - r * 0.22f, cy + r * 0.18f)
                quadraticTo(cx, cy + r * 0.52f, cx + r * 0.22f, cy + r * 0.18f)
                close()
            }
            drawPath(mouth, ink)
        }

        MascotKind.DAZE -> {
            drawCircle(ink, eyeR * 0.95f, Offset(cx - eyeDx, cy + eyeDy))
            drawCircle(ink, eyeR * 0.95f, Offset(cx + eyeDx, cy + eyeDy))
            // 一条直线嘴
            drawLine(ink, Offset(cx - r * 0.14f, cy + r * 0.30f), Offset(cx + r * 0.14f, cy + r * 0.30f), stroke.width, StrokeCap.Round)
            // 头顶小问号点
            drawCircle(ink.copy(alpha = 0.55f), r * 0.05f, Offset(cx + r * 0.42f, cy - r * 0.72f))
        }

        MascotKind.CONFUSED -> {
            drawCircle(ink, eyeR, Offset(cx - eyeDx, cy + eyeDy))
            drawCircle(ink, eyeR * 0.72f, Offset(cx + eyeDx, cy + eyeDy + r * 0.02f))
            // 斜眉毛
            drawLine(
                ink, Offset(cx - eyeDx - eyeR, cy + eyeDy - r * 0.30f),
                Offset(cx - eyeDx + eyeR, cy + eyeDy - r * 0.20f), stroke.width * 0.85f, StrokeCap.Round
            )
            drawLine(
                ink, Offset(cx + eyeDx - eyeR, cy + eyeDy - r * 0.16f),
                Offset(cx + eyeDx + eyeR, cy + eyeDy - r * 0.34f), stroke.width * 0.85f, StrokeCap.Round
            )
            // 波浪嘴
            val mouth = Path().apply {
                moveTo(cx - r * 0.18f, cy + r * 0.30f)
                quadraticTo(cx - r * 0.06f, cy + r * 0.18f, cx + r * 0.04f, cy + r * 0.30f)
                quadraticTo(cx + r * 0.12f, cy + r * 0.40f, cx + r * 0.20f, cy + r * 0.28f)
            }
            drawPath(mouth, ink, style = stroke)
        }
    }
}

private fun DrawScope.drawDecor(
    kind: MascotKind,
    cx: Float,
    cy: Float,
    r: Float,
    ink: Color,
    tones: io.github.xiaoxia.focusblock.ui.theme.ToneColors,
) {
    when (kind) {
        MascotKind.CELEBRATE -> {
            // 三颗小星星
            listOf(
                Offset(cx - r * 0.95f, cy - r * 0.75f) to r * 0.075f,
                Offset(cx + r * 0.98f, cy - r * 0.45f) to r * 0.055f,
                Offset(cx - r * 0.80f, cy + r * 0.55f) to r * 0.05f,
            ).forEach { (o, s) ->
                drawCircle(tones.focus, s, o)
            }
        }

        MascotKind.DAZE -> {
            rotate(degrees = 0f) {
                drawCircle(ink.copy(alpha = 0.25f), r * 0.035f, Offset(cx - r * 0.62f, cy - r * 0.55f))
                drawCircle(ink.copy(alpha = 0.18f), r * 0.03f, Offset(cx + r * 0.72f, cy + r * 0.20f))
            }
        }

        MascotKind.FOCUS -> {
            // 一滴汗，表示在使劲
            val drop = Path().apply {
                moveTo(cx + r * 0.72f, cy - r * 0.52f)
                quadraticTo(cx + r * 0.90f, cy - r * 0.22f, cx + r * 0.72f, cy - r * 0.16f)
                quadraticTo(cx + r * 0.54f, cy - r * 0.22f, cx + r * 0.72f, cy - r * 0.52f)
                close()
            }
            drawPath(drop, tones.longBreak)
        }

        else -> Unit
    }
}
