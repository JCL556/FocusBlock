package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 单行等比缩放的文字。
 * 倒计时字数会变（25:00 → 129:00），固定字号必然撑破圆环换行，
 * 这里按可用宽度实测，从上限往下一档档试，取第一个放得下的字号。
 */
@Composable
fun AutoFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxFontSize: Int = 80,
    minFontSize: Int = 36,
    step: Int = 2,
    textAlign: TextAlign = TextAlign.Center,
) {
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        val measurer = rememberTextMeasurer()

        val fitted = remember(text, maxWidthPx, style, maxFontSize, minFontSize, step) {
            var candidate = maxFontSize
            while (candidate > minFontSize) {
                val layout = measurer.measure(
                    AnnotatedString(text),
                    style = style.copy(fontSize = candidate.sp, lineHeight = (candidate * 1.08f).sp),
                    maxLines = 1,
                    softWrap = false,
                )
                if (layout.size.width <= maxWidthPx) break
                candidate -= step
            }
            candidate
        }

        Text(
            text = text,
            style = style.copy(
                fontSize = fitted.sp,
                lineHeight = (fitted * 1.08f).sp,
            ),
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
        )
    }
}
