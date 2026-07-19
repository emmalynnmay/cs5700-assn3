import observer.TempSensorObserver

import javafx.application.Platform
import javafx.scene.control.Label
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TempSensorObserverTest {

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
    private lateinit var observer: TempSensorObserver

    @BeforeTest
    fun setUp() {
        ensureJavaFxToolkitInitialized()
        label = Label()
        observer = TempSensorObserver(label)
    }

    // --- before any update -------------------------------------------------

    @Test
    fun `label is untouched until the first update arrives`() {
        assertEquals("", label.text)
    }

    // --- straightforward whole and fractional values, with degree suffix -----

    @Test
    fun `a whole number value renders with the degree suffix`() {
        observer.onUpdate(72.0)

        assertEquals("72°", label.text)
    }

    @Test
    fun `zero renders as 0 degrees`() {
        observer.onUpdate(0.0)

        assertEquals("0°", label.text)
    }

    @Test
    fun `a value that rounds down renders the floor with the degree suffix`() {
        observer.onUpdate(68.2)

        assertEquals("68°", label.text)
    }

    @Test
    fun `a value that rounds up renders the ceiling with the degree suffix`() {
        observer.onUpdate(68.7)

        assertEquals("69°", label.text)
    }

    // --- negative values (e.g. sub-freezing readings) -------------------------

    @Test
    fun `a negative value renders with a minus sign before the number, degree suffix still after`() {
        observer.onUpdate(-15.0)

        assertEquals("-15°", label.text)
    }

    @Test
    fun `a negative fractional value rounds toward the nearer integer`() {
        observer.onUpdate(-15.7)

        assertEquals("-16°", label.text)
    }

    @Test
    fun `negative zero renders as plain 0 degrees, not negative zero`() {
        observer.onUpdate(-0.0)

        assertEquals("0°", label.text)
    }

    // --- exact .5 ties: Kotlin's roundToInt rounds ties toward +infinity -----

    @Test
    fun `a positive half-value tie rounds up (toward positive infinity)`() {
        observer.onUpdate(20.5)

        assertEquals("21°", label.text)
    }

    @Test
    fun `a negative half-value tie rounds toward positive infinity, not away from zero`() {
        // roundToInt ties round toward +infinity, so -20.5 becomes -20 (not -21).
        observer.onUpdate(-20.5)

        assertEquals("-20°", label.text)
    }

    @Test
    fun `negative one-half rounds to zero degrees, not negative one`() {
        observer.onUpdate(-0.5)

        assertEquals("0°", label.text)
    }

    // --- repeated updates ------------------------------------------------------

    @Test
    fun `each update overwrites the previous label text`() {
        observer.onUpdate(70.0)
        assertEquals("70°", label.text)

        observer.onUpdate(32.0)
        assertEquals("32°", label.text)

        observer.onUpdate(-10.0)
        assertEquals("-10°", label.text)
    }

    // --- extreme / boundary values -------------------------------------------

    @Test
    fun `positive infinity clamps to Int MAX_VALUE rather than throwing`() {
        observer.onUpdate(Double.POSITIVE_INFINITY)

        assertEquals("${Int.MAX_VALUE}°", label.text)
    }

    @Test
    fun `negative infinity clamps to Int MIN_VALUE rather than throwing`() {
        observer.onUpdate(Double.NEGATIVE_INFINITY)

        assertEquals("${Int.MIN_VALUE}°", label.text)
    }

    @Test
    fun `a value far beyond Int range clamps to Int MAX_VALUE`() {
        observer.onUpdate(1.0e30)

        assertEquals("${Int.MAX_VALUE}°", label.text)
    }

    @Test
    fun `a value far below Int range clamps to Int MIN_VALUE`() {
        observer.onUpdate(-1.0e30)

        assertEquals("${Int.MIN_VALUE}°", label.text)
    }

    // --- NaN: documents a real crash risk if the sensor ever reports "no reading" --

    @Test
    fun `NaN throws IllegalArgumentException instead of rendering anything`() {
        // This documents CURRENT behavior. If the real temperature sensor can
        // ever emit NaN (e.g. as an "invalid reading" sentinel), this observer
        // will throw rather than degrade gracefully -- worth guarding against
        // upstream if that's a real possibility for this sensor.
        assertFailsWith<IllegalArgumentException> {
            observer.onUpdate(Double.NaN)
        }
    }

    @Test
    fun `a normal update after a NaN throw still renders correctly`() {
        try {
            observer.onUpdate(Double.NaN)
        } catch (e: IllegalArgumentException) {
            // expected, see test above
        }

        observer.onUpdate(75.0)

        assertEquals("75°", label.text)
    }
}