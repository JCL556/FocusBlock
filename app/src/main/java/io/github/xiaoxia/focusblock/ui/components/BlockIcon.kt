package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

/**
 * 时间块左侧的图标圆底。五个 key 对应五种水果，纯矢量。
 */
@Composable
fun BlockIcon(
    iconKey: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val tones = LocalTones.current
    val fruit = when (iconKey) {
        "apple" -> tones.focus
        "pear" -> tones.success
        "lemon" -> tones.lemon
        "grape" -> tones.longBreak
        else -> tones.focus
    }
    Box(
        modifier = modifier
            .size(size)
            .background(fruit.copy(alpha = 0.20f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val cx = w * 0.5f
            val cy = w * 0.56f
            val r = w * 0.22f
            drawFruit(iconKey, fruit, cx, cy, r, tones.leaf)
        }
    }
}

private fun DrawScope.drawFruit(key: String, color: Color, cx: Float, cy: Float, r: Float, leaf: Color) {
    when (key) {
        "apple" -> {
            drawCircle(color, r, Offset(cx, cy + r * 0.10f))
            drawLine(leaf, Offset(cx, cy - r * 0.85f), Offset(cx, cy - r * 1.25f), r * 0.18f)
            drawCircle(leaf, r * 0.26f, Offset(cx + r * 0.45f, cy - r * 1.05f))
        }

        "pear" -> {
            drawCircle(color, r * 0.62f, Offset(cx, cy - r * 0.55f))
            drawCircle(color, r * 0.95f, Offset(cx, cy + r * 0.40f))
            drawLine(leaf, Offset(cx, cy - r * 1.05f), Offset(cx, cy - r * 1.45f), r * 0.16f)
        }

        "lemon" -> {
            rotate(degrees = -20f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = color,
                    topLeft = Offset(cx - r * 1.05f, cy - r * 0.72f),
                    size = Size(r * 2.10f, r * 1.44f),
                )
            }
        }

        "grape" -> {
            drawCircle(color, r * 0.45f, Offset(cx - r * 0.42f, cy - r * 0.33f))
            drawCircle(color, r * 0.45f, Offset(cx + r * 0.42f, cy - r * 0.33f))
            drawCircle(color, r * 0.45f, Offset(cx, cy + r * 0.35f))
            drawLine(leaf, Offset(cx, cy - r * 0.85f), Offset(cx, cy - r * 1.30f), r * 0.14f)
        }

        else -> {
            // tomato
            drawCircle(color, r, Offset(cx, cy))
            val p = Path().apply {
                moveTo(cx, cy - r * 0.95f)
                lineTo(cx - r * 0.55f, cy - r * 1.35f)
                lineTo(cx - r * 0.08f, cy - r * 1.15f)
                lineTo(cx + r * 0.30f, cy - r * 1.55f)
                lineTo(cx + r * 0.32f, cy - r * 1.10f)
                lineTo(cx + r * 0.65f, cy - r * 1.30f)
                close()
            }
            drawPath(p, leaf)
        }
    }
}

/** 底部 Tab：今日 / 自由专注 / 日历 / 设置 */
@Composable
fun BottomTabs(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtra.current
    Column(modifier.fillMaxWidth()) {
        // 一条发丝线，把导航区跟上面的内容分开
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(extra.outline.copy(alpha = 0.55f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("今日", BookTabIcon, current == Tab.TODAY, Modifier.weight(1f)) { onSelect(Tab.TODAY) }
            TabItem("自由专注", TomatoTabIcon, current == Tab.FOCUS, Modifier.weight(1f)) { onSelect(Tab.FOCUS) }
            TabItem("日历", CalendarTabIcon, current == Tab.HISTORY, Modifier.weight(1f)) { onSelect(Tab.HISTORY) }
            TabItem("设置", Icons.Filled.Settings, current == Tab.SETTINGS, Modifier.weight(1f)) { onSelect(Tab.SETTINGS) }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 当前所在的这一栏是一整颗实心药丸，不用凑近看颜色差别
        Box(
            modifier = Modifier
                .height(30.dp)
                .widthIn(min = 54.dp)
                .background(
                    color = if (selected) tones.primaryButton else Color.Transparent,
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) tones.onPrimaryButton else extra.textAux,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) extra.text else extra.textAux,
            fontSize = 10.5.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
