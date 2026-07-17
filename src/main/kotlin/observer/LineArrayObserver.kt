package observer

import javafx.scene.control.Label

/**
 * Aggregates three independent boolean line-sensor readings (left, center, right)
 * into a single circle readout on a [Label], e.g. "● ● ○".
 *
 * Each sensor gets its own [Observer] (via [leftObserver], [centerObserver],
 * [rightObserver]) but they all share this instance's state and re-render the
 * same label together.
 */
class LineArrayObserver(private val label: Label) {

    private var left: Boolean = false
    private var center: Boolean = false
    private var right: Boolean = false

    val leftObserver: Observer<Boolean> = Observer { value ->
        left = value
        render()
    }

    val centerObserver: Observer<Boolean> = Observer { value ->
        center = value
        render()
    }

    val rightObserver: Observer<Boolean> = Observer { value ->
        right = value
        render()
    }

    private fun render() {
        label.text = "${circle(left)}  ${circle(center)}  ${circle(right)}"
    }

    private fun circle(detected: Boolean): String = if (detected) "●" else "○"
}