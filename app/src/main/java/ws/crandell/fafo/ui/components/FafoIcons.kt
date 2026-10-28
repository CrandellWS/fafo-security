package ws.crandell.fafo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few glyphs material-icons-core does not have, drawn on the 24-unit grid. Filled shapes use even-odd
 * so holes need no second color; they tint like any other icon.
 */
object FafoIcons {
    private fun icon(name: String, vararg fills: String, strokes: List<String> = emptyList(), stroke: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fills.forEach { d ->
            b.addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
        }
        strokes.forEach { d ->
            b.addPath(
                PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private const val SHIELD = "M12,1.8L20.4,4.6V11C20.4,16.2 16.9,20.6 12,22.3C7.1,20.6 3.6,16.2 3.6,11V4.6Z"

    /** Disarmed: an empty shield outline. */
    val ShieldOutline = icon("ShieldOutline", SHIELD + " M12,4L18.4,6.1V11C18.4,15.1 15.8,18.6 12,20.1C8.2,18.6 5.6,15.1 5.6,11V6.1Z")
    /** Armed: a filled shield with a check cut out. */
    val ShieldCheck = icon("ShieldCheck", SHIELD + " M10.6,15.6L6.9,11.9L8.3,10.5L10.6,12.8L15.7,7.7L17.1,9.1Z")
    /** Pre-alarm / alarm: a filled shield with an exclamation mark cut out. */
    val ShieldAlert = icon("ShieldAlert", SHIELD + " M11,6.5H13V13.5H11Z M11,15.5H13V17.5H11Z")

    val Mic = icon(
        "Mic",
        "M12,14.5C13.9,14.5 15.3,13 15.3,11.2V5.3C15.3,3.5 13.9,2 12,2C10.1,2 8.7,3.5 8.7,5.3V11.2C8.7,13 10.1,14.5 12,14.5Z",
        "M18.2,11.2H16.4C16.4,13.6 14.4,15.6 12,15.6C9.6,15.6 7.6,13.6 7.6,11.2H5.8C5.8,14.3 8.1,16.9 11,17.3V21H13V17.3C15.9,16.9 18.2,14.3 18.2,11.2Z",
    )
    val Devices = icon(
        "Devices",
        "M3,4.5C3,3.7 3.7,3 4.5,3H10.5C11.3,3 12,3.7 12,4.5V19.5C12,20.3 11.3,21 10.5,21H4.5C3.7,21 3,20.3 3,19.5Z M5,5.5V17H10V5.5Z",
        "M13.5,8.5C13.5,7.7 14.2,7 15,7H19.5C20.3,7 21,7.7 21,8.5V19.5C21,20.3 20.3,21 19.5,21H15C14.2,21 13.5,20.3 13.5,19.5Z M15.5,9.5V17H19V9.5Z",
    )
    val Setup = icon(
        "Setup",
        "M10.5,5.5H21V7.5H10.5Z M10.5,11H21V13H10.5Z M10.5,16.5H21V18.5H10.5Z",
        "M3,6.6L4.4,5.2L5.5,6.3L7.9,3.9L9.3,5.3L5.5,9.1Z",
        "M3,12.1L4.4,10.7L5.5,11.8L7.9,9.4L9.3,10.8L5.5,14.6Z",
        "M3.6,15.6H8.4V20.4H3.6Z M5,17V19H7V17Z",
    )
    val Help = icon(
        "Help",
        "M12,2A10,10 0 1,0 12,22A10,10 0 1,0 12,2Z M12,4A8,8 0 1,1 12,20A8,8 0 1,1 12,4Z",
        "M10.8,16H13.2V18.3H10.8Z",
        strokes = listOf("M9.3,9.4C9.3,7.9 10.5,6.8 12,6.8C13.5,6.8 14.7,7.9 14.7,9.3C14.7,11.3 12,11.4 12,13.8"),
        stroke = 2.2f,
    )
    val Camera = icon(
        "Camera",
        "M4,6.5H7.6L9.2,4.5H14.8L16.4,6.5H20C20.8,6.5 21.5,7.2 21.5,8V18C21.5,18.8 20.8,19.5 20,19.5H4C3.2,19.5 2.5,18.8 2.5,18V8C2.5,7.2 3.2,6.5 4,6.5Z M12,8.7A4.1,4.1 0 1,0 12,16.9A4.1,4.1 0 1,0 12,8.7Z",
        "M12,10.6A2.2,2.2 0 1,0 12,15A2.2,2.2 0 1,0 12,10.6Z",
    )
    val Bolt = icon("Bolt", "M13.5,2L4.5,13.5H11L10,22L19.5,10H13Z")
    val Battery = icon("Battery", "M9,2.5H15V4H17C17.6,4 18,4.4 18,5V21C18,21.6 17.6,22 17,22H7C6.4,22 6,21.6 6,21V5C6,4.4 6.4,4 7,4H9Z M8,6V20H16V6Z M9.5,13H14.5V18.5H9.5Z")
    val Moon = icon("Moon", "M14.5,2.5C9.2,3.2 5.5,7.6 5.5,12.8C5.5,18.4 10,22.5 15.5,22.5C17.4,22.5 19.2,21.9 20.7,20.9C15.8,20.2 12.2,16.1 12.2,11.2C12.2,7.9 13.5,4.8 14.5,2.5Z")
    val Siren = icon(
        "Siren",
        "M7,20V13.5C7,10.7 9.2,8.5 12,8.5C14.8,8.5 17,10.7 17,13.5V20Z M5,20.5H19V22.5H5Z",
        "M11,2H13V5.5H11Z M3.3,5.7L4.7,4.3L7.2,6.8L5.8,8.2Z M16.8,6.8L19.3,4.3L20.7,5.7L18.2,8.2Z",
    )
    val Motion = icon(
        "Motion",
        "M9,2.5H15C15.8,2.5 16.5,3.2 16.5,4V20C16.5,20.8 15.8,21.5 15,21.5H9C8.2,21.5 7.5,20.8 7.5,20V4C7.5,3.2 8.2,2.5 9,2.5Z M9.5,5V18H14.5V5Z",
        strokes = listOf("M4.5,8C3.5,9.2 3.5,14.8 4.5,16", "M19.5,8C20.5,9.2 20.5,14.8 19.5,16"),
    )
    val Power = icon(
        "Power",
        "M11,2H13V12H11Z",
        strokes = listOf("M7.1,5.6C5,7.1 3.8,9.4 3.8,12C3.8,16.5 7.5,20.2 12,20.2C16.5,20.2 20.2,16.5 20.2,12C20.2,9.4 19,7.1 16.9,5.6"),
    )
    val Privacy = icon(
        "Privacy",
        SHIELD,
    )
    val Wifi = icon(
        "Wifi",
        "M12,18A1.8,1.8 0 1,0 12,21.6A1.8,1.8 0 1,0 12,18Z",
        strokes = listOf("M2.5,9C7.9,4.1 16.1,4.1 21.5,9", "M5.8,12.5C9.3,9.3 14.7,9.3 18.2,12.5", "M9,15.8C10.7,14.3 13.3,14.3 15,15.8"),
    )
}
