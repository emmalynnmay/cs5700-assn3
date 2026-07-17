package observer

import javafx.scene.control.Label
import javafx.scene.paint.Color
import environment.Colors

fun Color.toHex(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

/**
 * An [Observer] that displays the latest value it receives as text on a [Label].
 *
 * @param label the UI label whose text is updated on each notification.
 */
class VisionSensorObserver(private val label: Label) : Observer<Color> {
    override fun onUpdate(value: Color)
    {
        val hex: String = value.toHex()
        if (hex == Colors.FLOOR || hex == Colors.FLOOR_ALT)
        {
            label.text = "floor"
        }
        else if (hex == Colors.LINE || hex == Colors.LINE_ALT)
        {
            label.text = "line"
        }
        else
        {
            label.text = hex
        }
    }
}
