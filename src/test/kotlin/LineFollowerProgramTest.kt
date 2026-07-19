import command.SetVelocityCommand
import environment.Environment
import geometry.Pose
import api.LineFollowerProgram
import testing.FakeRobotActuator
import testing.FakeRobotApi
import testing.FakeRobotSensors

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Local mirrors of LineFollowerProgram's private companion constants.
// Keep these in sync with LineFollowerProgram if those constants ever change.
// ---------------------------------------------------------------------------
private const val FORWARD_SPEED = 30.0
private const val SWING_OUTER_SPEED = FORWARD_SPEED
private const val SWING_INNER_SPEED = FORWARD_SPEED * 0.5
private const val PIVOT_SPEED = 30.0

private val SWING_LEFT_CMD = SWING_OUTER_SPEED to SWING_INNER_SPEED   // (30.0, 15.0)
private val SWING_RIGHT_CMD = SWING_INNER_SPEED to SWING_OUTER_SPEED  // (15.0, 30.0)
private val PIVOT_TOWARD_LEFT = PIVOT_SPEED to -PIVOT_SPEED           // used when only `left` still sees the line
private val PIVOT_TOWARD_RIGHT = -PIVOT_SPEED to PIVOT_SPEED          // used when only `right` still sees the line
private val STOP = 0.0 to 0.0

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class LineFollowerProgramTest {

    private lateinit var sensors: FakeRobotSensors
    private lateinit var robot: FakeRobotApi
    private lateinit var program: LineFollowerProgram

    @BeforeTest
    fun setUp() {
        sensors = FakeRobotSensors()
        robot = FakeRobotApi(sensors)
        program = LineFollowerProgram()
    }

    private fun lastVelocity(): Pair<Double, Double> = robot.actuator.calls.last()
    private fun setLeft(v: Boolean) = sensors.lineLeft.notifyObservers(v)
    private fun setCenter(v: Boolean) = sensors.lineCenter.notifyObservers(v)
    private fun setRight(v: Boolean) = sensors.lineRight.notifyObservers(v)

    // --- startProgram --------------------------------------------------

    @Test
    fun `startProgram subscribes all three line observers`() {
        program.startProgram(robot)

        val before = robot.actuator.calls.size
        setLeft(false)
        setCenter(false)
        setRight(false)
        assertEquals(before + 3, robot.actuator.calls.size, "all three line observers should be subscribed")
    }

    @Test
    fun `startProgram resets phase to SWING_LEFT and immediately swings left`() {
        program.startProgram(robot)

        assertEquals(1, robot.actuator.calls.size)
        assertEquals(SWING_LEFT_CMD, lastVelocity())
    }

    // --- stopProgram -----------------------------------------------------

    @Test
    fun `stopProgram stops the robot and unsubscribes all observers`() {
        program.startProgram(robot)
        program.stopProgram(robot)

        assertEquals(STOP, lastVelocity(), "stopProgram should command a full stop")

        val callsAfterStop = robot.actuator.calls.size
        setLeft(true)
        setCenter(true)
        setRight(true)

        assertEquals(
            callsAfterStop,
            robot.actuator.calls.size,
            "observers should be unsubscribed; no further commands expected",
        )
    }

    // --- all three sensors see the line: hold current phase -----------------

    @Test
    fun `all three sensors on the line continues the current SWING_LEFT phase`() {
        program.startProgram(robot) // phase defaults to SWING_LEFT

        setCenter(true) // (F,T,F) -- no explicit branch, falls through to driveSwing
        setLeft(true)   // (T,T,F) -- branch3, phase already SWING_LEFT so no change
        setRight(true)  // (T,T,T) -- branch1, explicitly a no-op

        assertEquals(SWING_LEFT_CMD, lastVelocity())
    }

    @Test
    fun `all three sensors on the line preserves SWING_RIGHT phase too`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true) // now (T,T,T), phase == SWING_LEFT

        // Force a switch to SWING_RIGHT by losing the left sensor.
        setLeft(false) // (F,T,T) -- branch2, phase switches to SWING_RIGHT
        assertEquals(SWING_RIGHT_CMD, lastVelocity())

        // Regain all three -- branch1 should preserve SWING_RIGHT, not reset to default.
        setLeft(true) // (T,T,T)
        assertEquals(SWING_RIGHT_CMD, lastVelocity(), "branch1 must preserve the current phase, not just default to SWING_LEFT")
    }

    // --- losing left / losing right: phase switches -------------------------

    @Test
    fun `losing the left sensor while swinging left switches to SWING_RIGHT`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true) // (T,T,T), phase == SWING_LEFT

        setLeft(false) // (F,T,T)

        assertEquals(SWING_RIGHT_CMD, lastVelocity())
    }

    @Test
    fun `losing the right sensor while swinging right switches back to SWING_LEFT`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true)
        setLeft(false) // now phase == SWING_RIGHT, state (F,T,T)
        setLeft(true)  // back to (T,T,T), phase preserved as SWING_RIGHT

        setRight(false) // (T,T,F) -- branch3, phase switches back to SWING_LEFT

        assertEquals(SWING_LEFT_CMD, lastVelocity())
    }

    // --- recovery: left only (TFF) ------------------------------------------

    @Test
    fun `losing center and right while left still sees the line stops then pivots toward left`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true) // (T,T,T)

        val before = robot.actuator.calls.size
        setCenter(false) // (T,F,T) -- case 8, no explicit branch, falls through to driveSwing
        setRight(false)  // (T,F,F) -- branch4, enters recovery

        // driveRecovery issues two commands: stop, then pivot.
        val newCalls = robot.actuator.calls.drop(before + 1) // +1 to skip the (T,F,T) fallthrough swing
        assertEquals(listOf(STOP, PIVOT_TOWARD_LEFT), newCalls)
    }

    // --- recovery: right only (FFT) ------------------------------------------

    @Test
    fun `losing center and left while right still sees the line stops then pivots toward right`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true) // (T,T,T)

        setLeft(false) // (F,T,T) -- branch2
        val before = robot.actuator.calls.size
        setCenter(false) // (F,F,T) -- branch5, enters recovery directly

        val newCalls = robot.actuator.calls.drop(before)
        assertEquals(listOf(STOP, PIVOT_TOWARD_RIGHT), newCalls)
    }

    // --- all sensors lost: stop, no swing ------------------------------------

    @Test
    fun `losing all three line sensors stops the robot`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true)

        val before = robot.actuator.calls.size
        setCenter(false) // (T,F,T)
        setLeft(false)   // (F,F,T) -- recovery (branch5)
        setRight(false)  // (F,F,F) -- branch6, stop, single command only

        assertEquals(STOP, lastVelocity())
        // Confirm the very last update produced exactly one command (the stop),
        // not a stop-then-pivot pair like recovery does.
        assertEquals(STOP, robot.actuator.calls.last())
    }

    // --- unmatched combinations: center-only and left+right-without-center --

    @Test
    fun `center sensor alone (no explicit branch) falls through to the current swing phase`() {
        program.startProgram(robot) // phase == SWING_LEFT, all sensors false

        setCenter(true) // (F,T,F) -- matches none of the explicit if/else branches

        assertEquals(SWING_LEFT_CMD, lastVelocity())
    }

    @Test
    fun `left and right without center (no explicit branch) falls through to the current swing phase`() {
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true)
        setLeft(false) // (F,T,T) -- phase switches to SWING_RIGHT
        setLeft(true)  // (T,T,T) -- phase preserved as SWING_RIGHT

        setCenter(false) // (T,F,T) -- case 8, no explicit branch; phase (SWING_RIGHT) is untouched

        assertEquals(SWING_RIGHT_CMD, lastVelocity())
    }

    // --- documents the dead "reacquired the line" path in driveRecovery ------

    @Test
    fun `regaining center after recovery re-dispatches through drive() rather than driveRecovery's reacquisition branch`() {
        // driveRecovery()'s `if (center) { phase = recoveryPhase; ... }` branch can
        // only be reached with center == true, but driveRecovery is only ever called
        // from drive() branches that require center == false to get there. So in
        // practice that branch never executes, and recoveryPhase (permanently
        // SWING_LEFT, since nothing ever assigns it) has no observable effect.
        //
        // This test shows what actually happens instead: once center comes back,
        // drive() simply re-dispatches through its normal branches using whatever
        // `phase` already held -- it is NOT forced back to SWING_LEFT.
        program.startProgram(robot)
        setCenter(true)
        setLeft(true)
        setRight(true)
        setLeft(false) // (F,T,T) -- phase switches to SWING_RIGHT
        setLeft(true)  // (T,T,T) -- phase preserved as SWING_RIGHT

        setCenter(false) // (T,F,T) -- fallthrough, phase still SWING_RIGHT
        setRight(false)  // (T,F,F) -- branch4, enters recovery; phase still SWING_RIGHT (driveRecovery didn't touch it)

        // Regain center while left is still up and right is still down: (T,T,F).
        setCenter(true) // -- branch3 (left && center && !right): since phase == SWING_RIGHT, this SWITCHES to SWING_LEFT

        assertEquals(
            SWING_LEFT_CMD,
            lastVelocity(),
            "should resume via drive()'s normal branch3 dispatch, not via driveRecovery's unreachable reacquisition logic",
        )
    }
}