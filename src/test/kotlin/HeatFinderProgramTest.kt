import command.SetVelocityCommand
import environment.Environment
import geometry.Pose
import command.RobotActuator
import sensor.RobotSensors
import api.RobotApi
import command.Command
import sensor.Sensor
import api.HeatFinderProgram
import testing.FakeRobotActuator
import testing.FakeRobotApi
import testing.FakeRobotSensors

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Local mirrors of HeatFinderProgram's private companion constants, so tests
// can assert exact expected values without needing reflection. Keep these in
// sync with HeatFinderProgram if those constants ever change.
// ---------------------------------------------------------------------------
private const val FORWARD_SPEED = 25.0
private val CURVE_LEFT = FORWARD_SPEED to FORWARD_SPEED * 0.4
private val CURVE_RIGHT = FORWARD_SPEED * 0.4 to FORWARD_SPEED

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class HeatFinderProgramTest {

    private lateinit var sensors: FakeRobotSensors
    private lateinit var robot: FakeRobotApi
    private lateinit var program: HeatFinderProgram

    @BeforeTest
    fun setUp() {
        sensors = FakeRobotSensors()
        robot = FakeRobotApi(sensors)
        program = HeatFinderProgram()
    }

    private fun lastVelocity(): Pair<Double, Double> = robot.actuator.calls.last()
    private fun isEitherCurve(v: Pair<Double, Double>) = v == CURVE_LEFT || v == CURVE_RIGHT

    // --- startProgram --------------------------------------------------

    @Test
    fun `startProgram subscribes the temperature observer`() {
        program.startProgram(robot)

        val before = robot.actuator.calls.size
        sensors.temperature.notifyObservers(20.0)
        // First reading only sets a baseline and issues no command, so subscribe
        // it twice to confirm the observer is actually wired up.
        sensors.temperature.notifyObservers(25.0)
        assertEquals(before + 1, robot.actuator.calls.size, "temperature observer should be subscribed")
    }

    @Test
    fun `startProgram issues an initial curve command in one of the two search directions`() {
        program.startProgram(robot)

        assertEquals(1, robot.actuator.calls.size)
        assertTrue(isEitherCurve(lastVelocity()), "expected CURVE_LEFT or CURVE_RIGHT, got ${lastVelocity()}")
    }

    @Test
    fun `startProgram eventually exercises both initial curve directions`() {
        val seen = mutableSetOf<Pair<Double, Double>>()
        repeat(200) {
            setUp()
            program.startProgram(robot)
            seen.add(lastVelocity())
        }
        assertEquals(setOf(CURVE_LEFT, CURVE_RIGHT), seen)
    }

    // --- stopProgram -----------------------------------------------------

    @Test
    fun `stopProgram stops the robot and unsubscribes the observer`() {
        program.startProgram(robot)
        program.stopProgram(robot)

        assertEquals(0.0 to 0.0, lastVelocity(), "stopProgram should command a full stop")

        val callsAfterStop = robot.actuator.calls.size
        sensors.temperature.notifyObservers(50.0)
        sensors.temperature.notifyObservers(60.0)

        assertEquals(
            callsAfterStop,
            robot.actuator.calls.size,
            "observer should be unsubscribed; no further commands expected",
        )
    }

    // --- first reading: no baseline yet -----------------------------------

    @Test
    fun `first temperature reading issues no command`() {
        program.startProgram(robot)
        val before = robot.actuator.calls.size

        sensors.temperature.notifyObservers(30.0)

        assertEquals(before, robot.actuator.calls.size, "first reading should only set a baseline")
    }

    // --- temperature rising: drive straight --------------------------------

    @Test
    fun `temperature increase drives straight forward`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(20.0) // baseline

        sensors.temperature.notifyObservers(21.0)

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    @Test
    fun `a small temperature increase still counts as rising`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(20.0)

        sensors.temperature.notifyObservers(20.0001)

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    @Test
    fun `multiple consecutive increases keep driving straight`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(10.0)

        listOf(11.0, 12.0, 13.5, 20.0).forEach { reading ->
            sensors.temperature.notifyObservers(reading)
            assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
        }
    }

    // --- temperature falling: curve -----------------------------------------

    @Test
    fun `temperature decrease curves in one of the two directions`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(30.0)

        sensors.temperature.notifyObservers(29.0)

        assertTrue(isEitherCurve(lastVelocity()), "expected CURVE_LEFT or CURVE_RIGHT, got ${lastVelocity()}")
    }

    @Test
    fun `temperature decrease eventually exercises both curve directions`() {
        val seen = mutableSetOf<Pair<Double, Double>>()
        repeat(200) {
            setUp()
            program.startProgram(robot)
            sensors.temperature.notifyObservers(30.0)
            sensors.temperature.notifyObservers(29.0)
            seen.add(lastVelocity())
        }
        assertEquals(setOf(CURVE_LEFT, CURVE_RIGHT), seen)
    }

    // --- temperature unchanged: treated as "not rising" -> curve ------------

    @Test
    fun `temperature staying exactly the same is treated as not rising and curves`() {
        // current > prev is strictly-greater, so a tie falls into the else branch.
        program.startProgram(robot)
        sensors.temperature.notifyObservers(22.0)

        sensors.temperature.notifyObservers(22.0)

        assertTrue(isEitherCurve(lastVelocity()), "a tied reading should curve, not drive straight")
    }

    // --- negative / boundary-ish values -------------------------------------

    @Test
    fun `negative temperatures compare correctly when rising`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(-10.0)

        sensors.temperature.notifyObservers(-5.0)

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    @Test
    fun `negative temperatures compare correctly when falling`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(-5.0)

        sensors.temperature.notifyObservers(-10.0)

        assertTrue(isEitherCurve(lastVelocity()))
    }

    @Test
    fun `crossing from negative to positive counts as rising`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(-1.0)

        sensors.temperature.notifyObservers(1.0)

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    @Test
    fun `zero to zero is treated as not rising`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(0.0)

        sensors.temperature.notifyObservers(0.0)

        assertTrue(isEitherCurve(lastVelocity()))
    }

    // --- NaN: documents current behavior rather than asserting "correctness" -

    @Test
    fun `a NaN reading does not crash and falls back to curving`() {
        // Double.NaN > anything is always false, and anything > Double.NaN is
        // always false, so both a NaN current reading and a NaN previous
        // reading fall through to the "curve" branch rather than throwing.
        program.startProgram(robot)
        sensors.temperature.notifyObservers(20.0)

        sensors.temperature.notifyObservers(Double.NaN)
        assertTrue(isEitherCurve(lastVelocity()), "NaN current reading should curve, not crash")

        sensors.temperature.notifyObservers(20.0)
        assertTrue(isEitherCurve(lastVelocity()), "reading after a NaN baseline should curve, not crash")
    }

    // --- rising then falling then rising: previousTemp tracks correctly ----

    @Test
    fun `previousTemp updates every reading so direction can flip repeatedly`() {
        program.startProgram(robot)
        sensors.temperature.notifyObservers(10.0) // baseline

        sensors.temperature.notifyObservers(15.0) // rising
        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())

        sensors.temperature.notifyObservers(12.0) // falling
        assertTrue(isEitherCurve(lastVelocity()))

        sensors.temperature.notifyObservers(18.0) // rising again
        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())

        sensors.temperature.notifyObservers(18.0) // tied -> not rising
        assertTrue(isEitherCurve(lastVelocity()))
    }
}