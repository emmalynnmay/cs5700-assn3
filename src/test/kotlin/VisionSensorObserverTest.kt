import observer.VisionSensorObserver
import environment.Colors
import javafx.scene.paint.Color

import javafx.application.Platform
import javafx.scene.control.Label
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisionSensorObserverTest {

    companion object {
        private var toolkitInitialized = false

        /**
         * Label requires the JavaFX toolkit to be running before it can be
         * constructed/mutated. Platform.startup boots the toolkit headlessly
         * (no visible window needed) using only javafx.graphics/javafx.controls.
         * Only needs to happen once per test process; startup() throws
         * IllegalStateException if the toolkit is already running, which we
         * treat as success.
         */
        private fun ensureJavaFxToolkitInitialized() {
            if (toolkitInitialized) return
            try {
                Platform.startup {}
            } catch (e: IllegalStateException) {
                // Toolkit already initialized -- fine, that's what we wanted anyway.
            }
            toolkitInitialized = true
        }
    }

    private lateinit var label: Label
    private lateinit var observer: VisionSensorObserver

    @BeforeTest
    fun setUp() {
        ensureJavaFxToolkitInitialized()
        label = Label()
        observer = VisionSensorObserver(label)
    }

    @Test
    fun `label is untouched until the first update arrives`() {
        assertEquals("", label.text)
    }

    @Test
    fun `detected floor color`() {
        observer.onUpdate(Color.valueOf(Colors.FLOOR))
        assertEquals("floor", label.text)
    }

    @Test
    fun `detected alt floor color`() {
        observer.onUpdate(Color.valueOf(Colors.FLOOR_ALT))
        assertEquals("floor", label.text)
    }

    @Test
    fun `detected line color`() {
        observer.onUpdate(Color.valueOf(Colors.LINE))
        assertEquals("line", label.text)
    }

    @Test
    fun `detected alt line color`() {
        observer.onUpdate(Color.valueOf(Colors.LINE_ALT))
        assertEquals("line", label.text)
    }

    @Test
    fun `detected ball color`() {
        observer.onUpdate(Color.valueOf(Colors.BALL))
        assertEquals("ball", label.text)
    }

    @Test
    fun `detected obstacle color`() {
        observer.onUpdate(Color.valueOf(Colors.OBSTACLE))
        assertEquals("obstacle", label.text)
    }

    @Test
    fun `detected wall color`() {
        observer.onUpdate(Color.valueOf(Colors.WALL))
        assertEquals("wall", label.text)
    }

    @Test
    fun `detected unknown color`() {
        observer.onUpdate(Color.valueOf("#FFFFFF"))
        assertEquals("#FFFFFF", label.text)
    }
}