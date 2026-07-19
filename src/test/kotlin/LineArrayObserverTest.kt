import observer.LineArrayObserver

import javafx.application.Platform
import javafx.scene.control.Label
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LineArrayObserverTest {

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
    private lateinit var observer: LineArrayObserver

    @BeforeTest
    fun setUp() {
        ensureJavaFxToolkitInitialized()
        label = Label()
        observer = LineArrayObserver(label)
    }

    private val filled = "●"
    private val empty = "○"

    // --- before any update -------------------------------------------------

    @Test
    fun `label is untouched until the first observer update arrives`() {
        assertEquals("", label.text)
    }

    // --- single-sensor updates: exact rendered format ------------------------

    @Test
    fun `left true with center and right still false renders filled-empty-empty`() {
        observer.leftObserver.onUpdate(true)

        assertEquals("$filled  $empty  $empty", label.text)
    }

    @Test
    fun `center true with left and right still false renders empty-filled-empty`() {
        observer.centerObserver.onUpdate(true)

        assertEquals("$empty  $filled  $empty", label.text)
    }

    @Test
    fun `right true with left and center still false renders empty-empty-filled`() {
        observer.rightObserver.onUpdate(true)

        assertEquals("$empty  $empty  $filled", label.text)
    }

    @Test
    fun `all three false renders empty-empty-empty`() {
        // Explicitly push false through all three observers (rather than relying
        // on the default field values) so this exercises the render path itself.
        observer.leftObserver.onUpdate(false)
        observer.centerObserver.onUpdate(false)
        observer.rightObserver.onUpdate(false)

        assertEquals("$empty  $empty  $empty", label.text)
    }

    @Test
    fun `all three true renders filled-filled-filled`() {
        observer.leftObserver.onUpdate(true)
        observer.centerObserver.onUpdate(true)
        observer.rightObserver.onUpdate(true)

        assertEquals("$filled  $filled  $filled", label.text)
    }

    @Test
    fun `rendered text uses exactly two spaces between each circle`() {
        observer.leftObserver.onUpdate(true)
        observer.centerObserver.onUpdate(true)
        observer.rightObserver.onUpdate(true)

        assertEquals("●  ●  ●", label.text)
    }

    // --- exhaustive combinations ---------------------------------------------

    @Test
    fun `every combination of the three sensors renders the correct circles in order`() {
        for (l in listOf(false, true)) {
            for (c in listOf(false, true)) {
                for (r in listOf(false, true)) {
                    val freshLabel = Label()
                    val freshObserver = LineArrayObserver(freshLabel)

                    freshObserver.leftObserver.onUpdate(l)
                    freshObserver.centerObserver.onUpdate(c)
                    freshObserver.rightObserver.onUpdate(r)

                    val expected = "${if (l) filled else empty}  ${if (c) filled else empty}  ${if (r) filled else empty}"
                    assertEquals(expected, freshLabel.text, "mismatch for left=$l center=$c right=$r")
                }
            }
        }
    }

    // --- independence between observers / fields ------------------------------

    @Test
    fun `updating one observer does not affect the other two sensors' remembered state`() {
        observer.leftObserver.onUpdate(true)
        observer.centerObserver.onUpdate(true)
        observer.rightObserver.onUpdate(true)
        assertEquals("$filled  $filled  $filled", label.text)

        // Flip just the center sensor back off; left and right should be untouched.
        observer.centerObserver.onUpdate(false)

        assertEquals("$filled  $empty  $filled", label.text)
    }

    @Test
    fun `repeated updates to the same observer re-render each time`() {
        observer.leftObserver.onUpdate(true)
        assertEquals("$filled  $empty  $empty", label.text)

        observer.leftObserver.onUpdate(false)
        assertEquals("$empty  $empty  $empty", label.text)

        observer.leftObserver.onUpdate(true)
        assertEquals("$filled  $empty  $empty", label.text)
    }

    @Test
    fun `updates via all three observers interleaved accumulate into a consistent final render`() {
        observer.leftObserver.onUpdate(true)
        observer.rightObserver.onUpdate(true)
        observer.centerObserver.onUpdate(false)
        observer.leftObserver.onUpdate(false)
        observer.centerObserver.onUpdate(true)

        // Final state: left=false (last write), center=true (last write), right=true (untouched since set)
        assertEquals("$empty  $filled  $filled", label.text)
    }

    // --- the three observer instances are stable references -------------------

    @Test
    fun `each observer property returns the same instance on repeated access`() {
        // Since these are `val` properties initialized once, repeated access
        // should yield the identical Observer instance each time (relevant if
        // calling code subscribes/unsubscribes using reference equality).
        assertEquals(observer.leftObserver, observer.leftObserver)
        assertEquals(observer.centerObserver, observer.centerObserver)
        assertEquals(observer.rightObserver, observer.rightObserver)
    }

    // --- multiple LineArrayObserver instances don't share state ----------------

    @Test
    fun `two separate LineArrayObserver instances on different labels do not interfere`() {
        val secondLabel = Label()
        val secondObserver = LineArrayObserver(secondLabel)

        observer.leftObserver.onUpdate(true)
        secondObserver.rightObserver.onUpdate(true)

        assertEquals("$filled  $empty  $empty", label.text)
        assertEquals("$empty  $empty  $filled", secondLabel.text)
    }
}