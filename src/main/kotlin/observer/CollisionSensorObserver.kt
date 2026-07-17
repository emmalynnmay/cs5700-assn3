package observer

import javafx.scene.control.Label

/**
 * An [Observer] that displays the latest value it receives as text on a [Label].
 *
 * @param label the UI label whose text is updated on each notification.
 */
class CollisionSensorObserver(private val label: Label) : Observer<Boolean> {
    override fun onUpdate(value: Boolean)
    {
        if (value)
        {
            label.text = "COLLISION!"
        }
        else
        {
            label.text = "clear"
        }
    }
}
