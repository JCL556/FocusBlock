package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 倒计时圆环：已过段实色，未过段 20% 透明，分界处一个 8dp 圆点当进度头。
 * 这样「还剩多少」一眼能看出来。
 */
@Composable
fun TimerRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Int = 260,
    strokeWidth: Int = 16,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val diameterPx = size.minDimension
            val inset = stroke / 2f
            val arcSize = Size(diameterPx - stroke, diameterPx - stroke)
            val topLeft = Offset(inset, inset)

            // 未过段：主题色 20% 透明
            drawArc(
                color = color.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // 已过段：实色
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0.1f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // 进度头
            val radius = diameterPx / 2f - stroke / 2f
            val angleRad = Math.toRadians((-90f + sweep).toDouble())
            val headX = center.x + (radius * Math.cos(angleRad)).toFloat()
            val headY = center.y + (radius * Math.sin(angleRad)).toFloat()
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(headX, headY))
        }
        content()
    }
}
