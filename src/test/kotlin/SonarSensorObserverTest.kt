import observer.SonarSensorObserver

import javafx.application.Platform
import javafx.scene.control.Label
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SonarSensorObserverTest {

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
    private lateinit var observer: SonarSensorObserver

    @BeforeTest
    fun setUp() {
        ensureJavaFxToolkitInitialized()
        label = Label()
        observer = SonarSensorObserver(label)
    }

    // --- before any update -------------------------------------------------

    @Test
    fun `label is untouched until the first update arrives`() {
        assertEquals("", label.text)
    }

    // --- straightforward whole and fractional values -------------------------

    @Test
    fun `a whole number value renders without a decimal point`() {
        observer.onUpdate(42.0)

        assertEquals("42", label.text)
    }

    @Test
    fun `zero renders as 0`() {
        observer.onUpdate(0.0)

        assertEquals("0", label.text)
    }

    @Test
    fun `a value that rounds down renders the floor`() {
        observer.onUpdate(3.2)

        assertEquals("3", label.text)
    }

    @Test
    fun `a value that rounds up renders the ceiling`() {
        observer.onUpdate(3.7)

        assertEquals("4", label.text)
    }

    // --- negative values -----------------------------------------------------

    @Test
    fun `a negative value renders with a minus sign`() {
        observer.onUpdate(-12.0)

        assertEquals("-12", label.text)
    }

    @Test
    fun `a negative fractional value rounds toward the nearer integer`() {
        observer.onUpdate(-3.7)

        assertEquals("-4", label.text)
    }

    @Test
    fun `negative zero renders as plain 0, not negative zero`() {
        observer.onUpdate(-0.0)

        assertEquals("0", label.text)
    }

    // --- exact .5 ties: Kotlin's roundToInt rounds ties toward +infinity -----

    @Test
    fun `a positive half-value tie rounds up (toward positive infinity)`() {
        observer.onUpdate(2.5)

        assertEquals("3", label.text)
    }

    @Test
    fun `a negative half-value tie rounds toward positive infinity, not away from zero`() {
        // roundToInt ties round toward +infinity, so -2.5 becomes -2 (not -3).
        // This is easy to get backwards if you assume "round half away from zero".
        observer.onUpdate(-2.5)

        assertEquals("-2", label.text)
    }

    @Test
    fun `negative one-half rounds to zero, not negative one`() {
        observer.onUpdate(-0.5)

        assertEquals("0", label.text)
    }

    // --- repeated updates ------------------------------------------------------

    @Test
    fun `each update overwrites the previous label text`() {
        observer.onUpdate(10.0)
        assertEquals("10", label.text)

        observer.onUpdate(25.0)
        assertEquals("25", label.text)

        observer.onUpdate(-5.0)
        assertEquals("-5", label.text)
    }

    // --- extreme / boundary values -------------------------------------------

    @Test
    fun `positive infinity clamps to Int MAX_VALUE rather than throwing`() {
        observer.onUpdate(Double.POSITIVE_INFINITY)

        assertEquals(Int.MAX_VALUE.toString(), label.text)
    }

    @Test
    fun `negative infinity clamps to Int MIN_VALUE rather than throwing`() {
        observer.onUpdate(Double.NEGATIVE_INFINITY)

        assertEquals(Int.MIN_VALUE.toString(), label.text)
    }

    @Test
    fun `a value far beyond Int range clamps to Int MAX_VALUE`() {
        observer.onUpdate(1.0e30)

        assertEquals(Int.MAX_VALUE.toString(), label.text)
    }

    @Test
    fun `a value far below Int range clamps to Int MIN_VALUE`() {
        observer.onUpdate(-1.0e30)

        assertEquals(Int.MIN_VALUE.toString(), label.text)
    }

    // --- NaN: documents a real crash risk if sonar ever reports "no echo" ----

    @Test
    fun `NaN throws IllegalArgumentException instead of rendering anything`() {
        // This documents CURRENT behavior. If the real sonar sensor can ever
        // emit NaN (e.g. as a "no echo detected" sentinel), this observer will
        // throw rather than degrade gracefully -- worth guarding against
        // upstream if that's a real possibility for this sensor.
        assertFailsWith<IllegalArgumentException> {
            observer.onUpdate(Double.NaN)
        }
    }

    @Test
    fun `a normal update after a NaN throw still renders correctly`() {
        // Confirms the observer/label aren't left in a broken state after
        // catching the NaN exception -- a subsequent valid reading works fine.
        try {
            observer.onUpdate(Double.NaN)
        } catch (e: IllegalArgumentException) {
            // expected, see test above
        }

        observer.onUpdate(7.0)

        assertEquals("7", label.text)
    }
}