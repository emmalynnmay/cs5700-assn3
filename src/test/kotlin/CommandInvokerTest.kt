import command.Command
import command.CommandInvoker

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A fake Command that records every execute()/undo() call in order, and also
 * maintains a tiny bit of "state" (a counter) so tests can assert not just
 * that methods were called, but that they were called in the right order and
 * had the right net effect.
 */
private class RecordingCommand(private val name: String, private val log: MutableList<String>) : Command {
    var executeCount = 0
        private set
    var undoCount = 0
        private set

    override fun execute() {
        executeCount++
        log.add("$name:execute")
    }

    override fun undo() {
        undoCount++
        log.add("$name:undo")
    }
}

class CommandInvokerTest {

    private lateinit var invoker: CommandInvoker
    private lateinit var log: MutableList<String>

    @BeforeTest
    fun setUp() {
        invoker = CommandInvoker()
        log = mutableListOf()
    }

    private fun command(name: String) = RecordingCommand(name, log)

    // --- initial state -------------------------------------------------

    @Test
    fun `a freshly created invoker cannot undo or redo`() {
        assertFalse(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `undo on an empty invoker is a no-op`() {
        invoker.undo() // should not throw
        assertFalse(invoker.canUndo())
        assertFalse(invoker.canRedo())
        assertTrue(log.isEmpty())
    }

    @Test
    fun `redo on an empty invoker is a no-op`() {
        invoker.redo() // should not throw
        assertFalse(invoker.canUndo())
        assertFalse(invoker.canRedo())
        assertTrue(log.isEmpty())
    }

    // --- run -------------------------------------------------------------

    @Test
    fun `run executes the command immediately`() {
        val cmd = command("a")
        invoker.run(cmd)

        assertEquals(1, cmd.executeCount)
        assertEquals(0, cmd.undoCount)
        assertEquals(listOf("a:execute"), log)
    }

    @Test
    fun `run makes undo available but not redo`() {
        invoker.run(command("a"))

        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    // --- undo --------------------------------------------------------------

    @Test
    fun `undo calls undo on the most recently run command`() {
        val a = command("a")
        val b = command("b")
        invoker.run(a)
        invoker.run(b)

        invoker.undo()

        assertEquals(1, b.undoCount)
        assertEquals(0, a.undoCount, "only the most recent command should be undone")
        assertEquals(listOf("a:execute", "b:execute", "b:undo"), log)
    }

    @Test
    fun `multiple undos unwind commands in LIFO order`() {
        val a = command("a")
        val b = command("b")
        val c = command("c")
        invoker.run(a)
        invoker.run(b)
        invoker.run(c)

        invoker.undo()
        invoker.undo()
        invoker.undo()

        assertEquals(
            listOf("a:execute", "b:execute", "c:execute", "c:undo", "b:undo", "a:undo"),
            log,
        )
    }

    @Test
    fun `undoing everything then undoing again is a safe no-op`() {
        invoker.run(command("a"))
        invoker.undo()
        assertFalse(invoker.canUndo())

        invoker.undo() // extra undo beyond history

        assertFalse(invoker.canUndo())
        assertEquals(listOf("a:execute", "a:undo"), log)
    }

    @Test
    fun `undo moves the command onto the redo stack`() {
        invoker.run(command("a"))
        invoker.undo()

        assertTrue(invoker.canRedo())
        assertFalse(invoker.canUndo())
    }

    // --- redo --------------------------------------------------------------

    @Test
    fun `redo re-executes the most recently undone command`() {
        val a = command("a")
        invoker.run(a)
        invoker.undo()

        invoker.redo()

        assertEquals(2, a.executeCount, "redo should execute again, not just restore state some other way")
        assertEquals(1, a.undoCount)
        assertEquals(listOf("a:execute", "a:undo", "a:execute"), log)
    }

    @Test
    fun `redo puts the command back on the undo stack so it can be undone again`() {
        invoker.run(command("a"))
        invoker.undo()
        invoker.redo()

        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `multiple redos replay commands in the order they were undone`() {
        val a = command("a")
        val b = command("b")
        val c = command("c")
        invoker.run(a)
        invoker.run(b)
        invoker.run(c)
        invoker.undo() // undo c
        invoker.undo() // undo b
        invoker.undo() // undo a

        invoker.redo() // redo a
        invoker.redo() // redo b
        invoker.redo() // redo c

        assertEquals(
            listOf(
                "a:execute", "b:execute", "c:execute",
                "c:undo", "b:undo", "a:undo",
                "a:execute", "b:execute", "c:execute",
            ),
            log,
        )
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `redoing everything then redoing again is a safe no-op`() {
        invoker.run(command("a"))
        invoker.undo()
        invoker.redo()
        assertFalse(invoker.canRedo())

        invoker.redo() // extra redo beyond history

        assertFalse(invoker.canRedo())
        assertEquals(listOf("a:execute", "a:undo", "a:execute"), log)
    }

    // --- interleaved run / undo / redo ---------------------------------------

    @Test
    fun `canUndo and canRedo track stack contents precisely through a long sequence`() {
        assertFalse(invoker.canUndo())
        assertFalse(invoker.canRedo())

        invoker.run(command("a"))
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())

        invoker.run(command("b"))
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())

        invoker.undo()
        assertTrue(invoker.canUndo())
        assertTrue(invoker.canRedo())

        invoker.undo()
        assertFalse(invoker.canUndo())
        assertTrue(invoker.canRedo())

        invoker.redo()
        assertTrue(invoker.canUndo())
        assertTrue(invoker.canRedo())

        invoker.redo()
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }
}