import command.SetVelocityCommand
import testing.FakeRobotActuator

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetVelocityCommandTest {

    private lateinit var actuator: FakeRobotActuator

    @BeforeTest
    fun setUp() {
        actuator = FakeRobotActuator()
    }

    // --- execute -------------------------------------------------------

    @Test
    fun `execute sets the actuator to the requested velocities`() {
        val cmd = SetVelocityCommand(actuator, 40.0, -20.0)

        cmd.execute()

        assertEquals(40.0, actuator.leftTrackVelocity)
        assertEquals(-20.0, actuator.rightTrackVelocity)
    }

    @Test
    fun `execute with zero velocities stops the actuator`() {
        actuator.setTrackVelocities(50.0, 50.0) // give it some non-zero starting state
        val cmd = SetVelocityCommand(actuator, 0.0, 0.0)

        cmd.execute()

        assertEquals(0.0 to 0.0, actuator.calls.last())
    }

    @Test
    fun `execute with equal left and right drives straight`() {
        val cmd = SetVelocityCommand(actuator, 33.3, 33.3)

        cmd.execute()

        assertEquals(33.3, actuator.leftTrackVelocity)
        assertEquals(33.3, actuator.rightTrackVelocity)
    }

    @Test
    fun `execute with negative velocities is passed through unchanged`() {
        val cmd = SetVelocityCommand(actuator, -75.0, -75.0)

        cmd.execute()

        assertEquals(-75.0 to -75.0, actuator.calls.last())
    }

    @Test
    fun `executing the same command twice re-applies the same velocities`() {
        val cmd = SetVelocityCommand(actuator, 10.0, 20.0)

        cmd.execute()
        cmd.execute()

        assertEquals(listOf(10.0 to 20.0, 10.0 to 20.0), actuator.calls)
    }

    // --- undo: default prevLeft/prevRight (captured at construction) --------

    @Test
    fun `undo restores the velocities the actuator had when the command was constructed`() {
        actuator.setTrackVelocities(5.0, 15.0)
        val cmd = SetVelocityCommand(actuator, 40.0, -20.0) // captures prev = (5.0, 15.0) right now

        cmd.execute()
        assertEquals(40.0 to -20.0, actuator.calls.last())

        cmd.undo()
        assertEquals(5.0 to 15.0, actuator.calls.last())
    }

    @Test
    fun `undo before execute still restores the captured previous velocities`() {
        actuator.setTrackVelocities(8.0, 9.0)
        val cmd = SetVelocityCommand(actuator, 100.0, 100.0)

        cmd.undo() // never executed, but undo should still work off the captured snapshot

        assertEquals(8.0 to 9.0, actuator.calls.last())
    }

    @Test
    fun `undo after multiple executes still restores the original pre-construction velocities`() {
        actuator.setTrackVelocities(1.0, 2.0)
        val cmd = SetVelocityCommand(actuator, 50.0, 50.0)

        cmd.execute()
        cmd.execute()
        cmd.execute()
        cmd.undo()

        assertEquals(1.0 to 2.0, actuator.calls.last())
    }

    // --- undo: explicit prevLeft/prevRight override -------------------------

    @Test
    fun `explicit prevLeft and prevRight override the actuator's actual state at construction`() {
        actuator.setTrackVelocities(99.0, 99.0) // actual state at construction time
        val cmd = SetVelocityCommand(actuator, 10.0, 10.0, prevLeft = 0.0, prevRight = 0.0)

        cmd.execute()
        cmd.undo()

        // Should restore the explicitly-provided prev values, not the actuator's
        // real velocity (99.0, 99.0) at the time the command was built.
        assertEquals(0.0 to 0.0, actuator.calls.last())
    }

    // --- the construction-time capture "staleness" edge case ----------------

    @Test
    fun `prevLeft and prevRight are captured at construction time, not at execute time`() {
        // Build the command while the actuator is at (1.0, 1.0)...
        actuator.setTrackVelocities(1.0, 1.0)
        val cmd = SetVelocityCommand(actuator, 90.0, 90.0)

        // ...then something else changes the actuator's velocity before this
        // command ever runs (e.g. another command executed first).
        actuator.setTrackVelocities(200.0, 200.0)

        cmd.execute()
        assertEquals(90.0 to 90.0, actuator.calls.last())

        cmd.undo()

        // Undo restores the STALE snapshot from construction time (1.0, 1.0),
        // not the (200.0, 200.0) that was actually overwritten just before execute().
        assertEquals(
            1.0 to 1.0,
            actuator.calls.last(),
            "undo uses the velocity captured at construction time, which can be stale " +
                "if other commands ran on the actuator between construction and execute",
        )
    }

    // --- independence from a shared actuator --------------------------------

    @Test
    fun `two commands built back-to-back capture independent previous-velocity snapshots`() {
        actuator.setTrackVelocities(1.0, 1.0)
        val first = SetVelocityCommand(actuator, 10.0, 10.0) // prev = (1.0, 1.0)

        first.execute() // actuator now at (10.0, 10.0)
        val second = SetVelocityCommand(actuator, 20.0, 20.0) // prev = (10.0, 10.0)
        second.execute() // actuator now at (20.0, 20.0)

        second.undo()
        assertEquals(10.0 to 10.0, actuator.calls.last(), "second command should restore to first command's result")

        first.undo()
        assertEquals(1.0 to 1.0, actuator.calls.last(), "first command should restore to the original pre-construction state")
    }
}