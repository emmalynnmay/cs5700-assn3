import observer.CollisionSensorObserver

import javafx.application.Platform
import javafx.scene.control.Label
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CollisionSensorObserverTest {

    companion object {
        private var toolkitInitialized = false

        /**
         * Label (and other JavaFX controls) require the JavaFX toolkit to be
         * running before they can be constructed/mutated. Platform.startup boots
         * the toolkit headlessly (no visible window, no Application subclass
         * needed) using only javafx.graphics/javafx.controls -- unlike JFXPanel,
         * it doesn't require the separate javafx.swing module. Only needs to
         * happen once per test process; startup() throws IllegalStateException
         * if the toolkit is already running (e.g. a prior test already started
         * it), which we treat as success.
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
    private lateinit var observer: CollisionSensorObserver

    @BeforeTest
    fun setUp() {
        ensureJavaFxToolkitInitialized()
        label = Label()
        observer = CollisionSensorObserver(label)
    }

    // --- basic behavior --------------------------------------------------

    @Test
    fun `a true update sets the label text to COLLISION!`() {
        observer.onUpdate(true)

        assertEquals("COLLISION!", label.text)
    }

    @Test
    fun `a false update sets the label text to clear`() {
        observer.onUpdate(false)

        assertEquals("clear", label.text)
    }

    @Test
    fun `label text is untouched until the first update arrives`() {
        // Default Label() text is empty string until set; this documents that
        // constructing the observer doesn't itself write anything to the label.
        assertEquals("", label.text)
    }

    // --- toggling / repeated updates ---------------------------------------

    @Test
    fun `repeated true updates keep the label at COLLISION!`() {
        observer.onUpdate(true)
        observer.onUpdate(true)
        observer.onUpdate(true)

        assertEquals("COLLISION!", label.text)
    }

    @Test
    fun `repeated false updates keep the label at clear`() {
        observer.onUpdate(false)
        observer.onUpdate(false)

        assertEquals("clear", label.text)
    }

    @Test
    fun `toggling true then false then true updates the label each time`() {
        observer.onUpdate(true)
        assertEquals("COLLISION!", label.text)

        observer.onUpdate(false)
        assertEquals("clear", label.text)

        observer.onUpdate(true)
        assertEquals("COLLISION!", label.text)
    }

    // --- multiple observers sharing / not sharing a label -------------------

    @Test
    fun `two independent observers on two different labels do not affect each other`() {
        val secondLabel = Label()
        val secondObserver = CollisionSensorObserver(secondLabel)

        observer.onUpdate(true)
        secondObserver.onUpdate(false)

        assertEquals("COLLISION!", label.text)
        assertEquals("clear", secondLabel.text)
    }

    @Test
    fun `two observers sharing the same label both write to it, last write wins`() {
        val secondObserver = CollisionSensorObserver(label)

        observer.onUpdate(true)
        secondObserver.onUpdate(false)

        assertEquals("clear", label.text, "the most recent onUpdate call, regardless of which observer, should win")
    }
}