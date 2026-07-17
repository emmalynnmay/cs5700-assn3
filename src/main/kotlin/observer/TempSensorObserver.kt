package observer

import javafx.scene.control.Label
import kotlin.math.*

/**
 * An [Observer] that displays the latest value it receives as text on a [Label].
 *
 * @param label the UI label whose text is updated on each notification.
 */
class TempSensorObserver(private val label: Label) : Observer<Double> {
    override fun onUpdate(value: Double)
    {
        label.text = value.roundToInt().toString() + "°"
    }
}
