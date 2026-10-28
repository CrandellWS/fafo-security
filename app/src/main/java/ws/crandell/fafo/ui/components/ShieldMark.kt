package ws.crandell.fafo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ws.crandell.fafo.ui.theme.FafoBrand

/** The traced shield layers, parsed once per process (only fills change when the status color animates). */
private object ShieldGeometry {
    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }
    val silhouette by lazy { parse(ShieldPaths.SILHOUETTE) }
    val navy by lazy { parse(ShieldPaths.NAVY) }
    val stripes by lazy { parse(ShieldPaths.STRIPES) }
    val beak by lazy { parse(ShieldPaths.BEAK) }
    val eye by lazy { parse(ShieldPaths.EYE) }
}

/** Width / height of the traced shield art. */
const val SHIELD_MARK_ASPECT = 0.908f

/**
 * The visible art inside a [ShieldMark] of height H: the silhouette spans x 79..943 and y 36..984 of the
 * 1024 viewport, so it is about 0.843 H wide and 0.926 H tall (the rest is transparent margin). Sizing for
 * "75% of the screen" uses these, not the canvas size.
 */
const val SHIELD_ART_WIDTH_PER_HEIGHT = 0.843f
const val SHIELD_ART_HEIGHT_PER_HEIGHT = 0.926f

/**
 * The approved navy / red / white eagle shield as vector layers (round 3), so the status screen can recolor
 * it: [stripes] take the status color (brand red by default) and [eye] is white when disarmed and red when
 * armed (Bill 2026-10-28). The beak is always red. Non-red stripes get a thin navy keyline so yellow and
 * green still read against the white bands. The launcher icon keeps its bitmap.
 */
@Composable
fun ShieldMark(
    height: Dp,
    modifier: Modifier = Modifier,
    stripes: Color = FafoBrand.Red,
    eye: Color = FafoBrand.Red,
    contentDescription: String? = "FAFO Security shield",
) {
    val m = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    Canvas(m.size(height * SHIELD_MARK_ASPECT, height)) { drawShieldMark(stripes, eye) }
}

/** Draws the layered shield into this scope's size (also used to render it off screen, e.g. for email). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShieldMark(stripes: Color = FafoBrand.Red, eye: Color = FafoBrand.Red) {
    // The art is centered in a square 1024 viewport; fit its height and center it horizontally.
    val k = size.height / ShieldPaths.VIEWPORT
    val dx = (size.width - ShieldPaths.VIEWPORT * k) / 2f
    translateAndScale(dx, k) {
        drawPath(ShieldGeometry.silhouette, FafoBrand.White)
        drawPath(ShieldGeometry.navy, FafoBrand.Navy)
        if (stripes != FafoBrand.Red) drawPath(ShieldGeometry.stripes, FafoBrand.Navy, style = Stroke(width = 16f))
        drawPath(ShieldGeometry.stripes, stripes)
        drawPath(ShieldGeometry.beak, FafoBrand.Red)
        drawPath(ShieldGeometry.eye, eye)
    }
}

/** Renders the shield to a bitmap [heightPx] tall (email test message, golden test). */
fun renderShieldMark(heightPx: Int, stripes: Color = FafoBrand.Red, eye: Color = FafoBrand.Red): androidx.compose.ui.graphics.ImageBitmap {
    val w = (heightPx * SHIELD_MARK_ASPECT).toInt()
    val img = androidx.compose.ui.graphics.ImageBitmap(w, heightPx)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(img), androidx.compose.ui.geometry.Size(w.toFloat(), heightPx.toFloat()),
    ) { drawShieldMark(stripes, eye) }
    return img
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translateAndScale(
    dx: Float, k: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    drawContext.transform.translate(dx, 0f)
    scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { block() }
    drawContext.transform.translate(-dx, 0f)
}
