package ws.crandell.fafo.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Font size (sp) AutoFitText settled on; tests read it through semantics. */
val FittedFontSize = SemanticsPropertyKey<Float>("FittedFontSize")
var SemanticsPropertyReceiver.fittedFontSize by FittedFontSize

/** Lines and ellipsis of the last layout; tests assert one line and no ellipsis. */
data class FitLayout(val lines: Int, val ellipsized: Boolean, val fontSize: Float)
val FittedLayout = SemanticsPropertyKey<() -> FitLayout>("FittedLayout")
var SemanticsPropertyReceiver.fittedLayout by FittedLayout

/** Holder written during measure and read during draw/semantics of the same frame. */
private class FitState { var result: TextLayoutResult? = null; var fontSize = 0f }

/**
 * One line, never wrapped (Bill 2026-10-28: "DISARMED" wrapped its last D onto a second line on the A17 at a
 * large font size). Measures [text] (or the wider [sample], e.g. "88" for a countdown so the digits don't
 * jump) at [style]'s size in the current density, which includes the user's font scale, and shrinks it by
 * binary search until it fits the available width, down to [minFontSize]. (Android 14+ scales Font size
 * non-linearly, so big headlines grow little with it; a large Display size, which narrows the screen in
 * dp, is what usually pushes them over.) Text that still does not fit at
 * the minimum ends in an ellipsis rather than wrapping. Text only gets smaller when it would not fit.
 *
 * A plain Layout (not BoxWithConstraints), so it works inside intrinsic-size parents such as cards.
 */
@Composable
fun AutoFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 12.sp,
    sample: String? = null,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fg = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
    }
    val state = remember { FitState() }
    Layout(
        content = {},
        modifier = modifier
            .semantics {
                this.text = AnnotatedString(text)
                fittedFontSize = state.fontSize
                fittedLayout = {
                    val r = state.result
                    FitLayout(r?.lineCount ?: 0, r?.let { it.isLineEllipsized(0) || it.hasVisualOverflow } ?: false, state.fontSize)
                }
            }
            .drawBehind {
                val r = state.result ?: return@drawBehind
                val x = when (textAlign) {
                    TextAlign.Center -> (size.width - r.size.width) / 2f
                    TextAlign.End, TextAlign.Right -> size.width - r.size.width
                    else -> 0f
                }
                drawText(r, color = fg, topLeft = Offset(x.coerceAtLeast(0f), (size.height - r.size.height) / 2f))
            },
        measurePolicy = remember(text, sample, style, minFontSize, density, textAlign) {
            object : MeasurePolicy {
                fun layoutFor(maxW: Int): Pair<Float, TextLayoutResult> {
                    val size = fittedSize(measurer, text, sample, style, minFontSize, maxW, density)
                    val r = measurer.measure(
                        AnnotatedString(text), scaled(style, size), overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                        constraints = if (maxW == Constraints.Infinity) Constraints() else Constraints(maxWidth = maxW), density = density,
                    )
                    return size to r
                }

                override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                    val (size, r) = layoutFor(constraints.maxWidth)
                    // Only the real measure pass records the result (intrinsic queries have no side effects).
                    state.result = r
                    state.fontSize = size
                    val centered = textAlign == TextAlign.Center || textAlign == TextAlign.End || textAlign == TextAlign.Right
                    val w = if (centered && constraints.hasBoundedWidth) constraints.maxWidth else r.size.width
                    return layout(w.coerceIn(constraints.minWidth, constraints.maxWidth), r.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
                }

                override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
                    layoutFor(Constraints.Infinity).second.size.width
                override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
                    layoutFor(Constraints.Infinity).second.size.width
                override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
                    layoutFor(width).second.size.height
                override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
                    layoutFor(width).second.size.height
            }
        },
    )
}

private fun scaled(style: TextStyle, size: Float): TextStyle {
    val base = if (style.fontSize.isSpecified) style.fontSize.value else 16f
    return style.copy(
        fontSize = size.sp,
        lineHeight = if (style.lineHeight.isSpecified) (style.lineHeight.value * size / base).sp else style.lineHeight,
    )
}

/** Largest size in [minFontSize, style size] at which the text fits [maxW] on one line (8-step binary search). */
private fun fittedSize(
    measurer: TextMeasurer, text: String, sample: String?, style: TextStyle, minFontSize: TextUnit, maxW: Int, density: Density,
): Float {
    val base = if (style.fontSize.isSpecified) style.fontSize.value else 16f
    if (maxW == Constraints.Infinity) return base
    val probe = sample?.takeIf { it.length >= text.length } ?: text
    fun fits(size: Float) = measurer.measure(probe, scaled(style, size), maxLines = 1, softWrap = false, density = density).size.width <= maxW
    if (fits(base)) return base
    var lo = minFontSize.value.coerceAtMost(base)
    var hi = base
    repeat(8) {
        val mid = (lo + hi) / 2
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}
