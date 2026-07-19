import command.SetVelocityCommand
import environment.Colors
import environment.Environment
import geometry.Pose
import command.RobotActuator
import sensor.RobotSensors
import api.RobotApi
import command.Command
import sensor.Sensor
import api.BallFinderProgram
import testing.FakeRobotActuator
import testing.FakeRobotApi
import testing.FakeRobotSensors

import javafx.scene.paint.Color
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Local mirrors of BallFinderProgram's private companion constants, so tests
// can assert exact expected values without needing reflection. Keep these in
// sync with BallFinderProgram if those constants ever change.
// ---------------------------------------------------------------------------
private const val FORWARD_SPEED = 75.0
private const val SPIN_SPEED = 20.0
private const val RECOVERY_TIME = 30

private val CURVE_LEFT = FORWARD_SPEED to FORWARD_SPEED * 0.4
private val CURVE_RIGHT = FORWARD_SPEED * 0.4 to FORWARD_SPEED
private val SPIN_LEFT = -SPIN_SPEED to SPIN_SPEED
private val SPIN_RIGHT = SPIN_SPEED to -SPIN_SPEED

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class BallFinderProgramTest {

    private lateinit var sensors: FakeRobotSensors
    private lateinit var robot: FakeRobotApi
    private lateinit var program: BallFinderProgram

    @BeforeTest
    fun setUp() {
        sensors = FakeRobotSensors()
        robot = FakeRobotApi(sensors)
        program = BallFinderProgram()
    }

    private fun lastVelocity(): Pair<Double, Double> = robot.actuator.calls.last()

    private fun isEitherCurve(v: Pair<Double, Double>) = v == CURVE_LEFT || v == CURVE_RIGHT
    private fun isEitherSpin(v: Pair<Double, Double>) = v == SPIN_LEFT || v == SPIN_RIGHT

    // --- startProgram --------------------------------------------------

    @Test
    fun `startProgram subscribes vision and collision observers`() {
        program.startProgram(robot)

        // If subscription worked, pushing a value triggers a new command.
        val before = robot.actuator.calls.size
        sensors.vision.notifyObservers(Color.web(Colors.BALL))
        assertEquals(before + 1, robot.actuator.calls.size, "vision observer should be subscribed")

        val beforeCollision = robot.actuator.calls.size
        sensors.collision.notifyObservers(true)
        assertEquals(beforeCollision + 1, robot.actuator.calls.size, "collision observer should be subscribed")
    }

    @Test
    fun `startProgram issues an initial curve command in one of the two search directions`() {
        program.startProgram(robot)

        assertEquals(1, robot.actuator.calls.size)
        assertTrue(isEitherCurve(lastVelocity()), "expected CURVE_LEFT or CURVE_RIGHT, got ${lastVelocity()}")
    }

    // --- stopProgram -----------------------------------------------------

    @Test
    fun `stopProgram stops the robot and unsubscribes both observers`() {
        program.startProgram(robot)
        program.stopProgram(robot)

        assertEquals(0.0 to 0.0, lastVelocity(), "stopProgram should command a full stop")

        val callsAfterStop = robot.actuator.calls.size
        sensors.vision.notifyObservers(Color.web(Colors.BALL))
        sensors.collision.notifyObservers(true)

        assertEquals(
            callsAfterStop,
            robot.actuator.calls.size,
            "observers should be unsubscribed; no further commands expected",
        )
    }

    // --- vision: ball in view --------------------------------------------

    @Test
    fun `vision update with ball drives straight forward`() {
        program.startProgram(robot)

        sensors.vision.notifyObservers(Color.web(Colors.BALL))

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    // --- vision: obstacle in view ------------------------------------------

    @Test
    fun `vision update with obstacle curves away in one of the two directions`() {
        program.startProgram(robot)

        sensors.vision.notifyObservers(Color.web(Colors.OBSTACLE))

        assertTrue(isEitherCurve(lastVelocity()), "expected CURVE_LEFT or CURVE_RIGHT, got ${lastVelocity()}")
    }

    @Test
    fun `vision update with obstacle eventually exercises both curve directions`() {
        // Random.nextBoolean() backs the direction choice; run enough trials that
        // both branches are overwhelmingly likely to appear at least once.
        val seen = mutableSetOf<Pair<Double, Double>>()
        repeat(200) {
            setUp()
            program.startProgram(robot)
            sensors.vision.notifyObservers(Color.web(Colors.OBSTACLE))
            seen.add(lastVelocity())
        }
        assertEquals(setOf(CURVE_LEFT, CURVE_RIGHT), seen)
    }

    // --- vision: unrecognized / floor color --------------------------------

    @Test
    fun `vision update with an unrecognized color still drives straight forward`() {
        // Covers the floor / anything-not-ball-or-obstacle branch. Note: the
        // production code computes a random curve here but never sends it --
        // the actual command issued is always FORWARD_SPEED straight ahead.
        program.startProgram(robot)

        sensors.vision.notifyObservers(Color.web(Colors.FLOOR))

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    @Test
    fun `vision update with a color matching no known constant drives straight forward`() {
        program.startProgram(robot)

        sensors.vision.notifyObservers(Color.web("#123456"))

        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())
    }

    // --- vision ignored while recovering ------------------------------------

    @Test
    fun `vision updates are ignored entirely while recovering from a collision`() {
        program.startProgram(robot)
        sensors.collision.notifyObservers(true) // enters recovery

        val callsDuringRecovery = robot.actuator.calls.size
        sensors.vision.notifyObservers(Color.web(Colors.BALL))
        sensors.vision.notifyObservers(Color.web(Colors.OBSTACLE))
        sensors.vision.notifyObservers(Color.web(Colors.FLOOR))

        assertEquals(
            callsDuringRecovery,
            robot.actuator.calls.size,
            "vision updates must not produce commands while recovering",
        )
    }

    // --- collision: no collision, not recovering --------------------------

    @Test
    fun `collision update with false while not recovering issues no command`() {
        program.startProgram(robot)
        val before = robot.actuator.calls.size

        sensors.collision.notifyObservers(false)

        assertEquals(before, robot.actuator.calls.size)
    }

    // --- collision: entering recovery --------------------------------------

    @Test
    fun `collision update with true commits to a single spin direction`() {
        program.startProgram(robot)

        sensors.collision.notifyObservers(true)

        assertTrue(isEitherSpin(lastVelocity()), "expected SPIN_LEFT or SPIN_RIGHT, got ${lastVelocity()}")
    }

    @Test
    fun `recovery eventually exercises both spin directions across trials`() {
        val seen = mutableSetOf<Pair<Double, Double>>()
        repeat(200) {
            setUp()
            program.startProgram(robot)
            sensors.collision.notifyObservers(true)
            seen.add(lastVelocity())
        }
        assertEquals(setOf(SPIN_LEFT, SPIN_RIGHT), seen)
    }

    @Test
    fun `recovery direction stays committed across repeated collision readings`() {
        program.startProgram(robot)
        sensors.collision.notifyObservers(true)
        val committedDirection = lastVelocity()
        assertTrue(isEitherSpin(committedDirection))

        // Feed a bunch more readings (mixing true/false -- current is ignored
        // once recovering) and confirm the direction never changes mid-recovery.
        repeat(10) { i ->
            sensors.collision.notifyObservers(i % 2 == 0)
            assertEquals(committedDirection, lastVelocity(), "recovery direction must not change mid-recovery")
        }
    }

    // --- collision: exact recovery boundary ---------------------------------

    @Test
    fun `recovery holds the spin for exactly RECOVERY_TIME plus one ticks then resumes forward`() {
        program.startProgram(robot)

        // Call 1: triggers recovery (recoveryChecks stays 0).
        sensors.collision.notifyObservers(true)
        val committedDirection = lastVelocity()
        assertTrue(isEitherSpin(committedDirection))

        // Calls 2..32 (31 calls): recoveryChecks climbs 0->1->...->31, check
        // "recoveryChecks > RECOVERY_TIME" (i.e. > 30) is false the whole way,
        // so every one of these still spins in the committed direction.
        repeat(31) {
            sensors.collision.notifyObservers(true)
            assertEquals(
                committedDirection,
                lastVelocity(),
                "still within recovery window, should keep spinning",
            )
        }

        // Call 33 overall (the 32nd call after the initial trigger): recoveryChecks
        // is now 31 going into the check, 31 > 30 is true -> recovery ends and the
        // robot resumes driving straight forward.
        sensors.collision.notifyObservers(true)
        assertEquals(
            FORWARD_SPEED to FORWARD_SPEED,
            lastVelocity(),
            "recovery should have ended by the 33rd collision update and resumed forward motion",
        )
    }

    @Test
    fun `after recovery ends a new collision can start a fresh recovery episode`() {
        program.startProgram(robot)

        // Drive the first recovery episode all the way to completion (33 calls total).
        sensors.collision.notifyObservers(true)
        repeat(32) { sensors.collision.notifyObservers(true) }
        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())

        // A brand new collision should be able to start recovery again.
        sensors.collision.notifyObservers(true)
        assertTrue(isEitherSpin(lastVelocity()), "a fresh collision should re-enter recovery")
    }

    @Test
    fun `vision updates resume affecting driving once recovery has ended`() {
        program.startProgram(robot)

        // Complete a full recovery cycle.
        sensors.collision.notifyObservers(true)
        repeat(32) { sensors.collision.notifyObservers(true) }
        assertEquals(FORWARD_SPEED to FORWARD_SPEED, lastVelocity())

        // Now vision updates should be honored again.
        sensors.vision.notifyObservers(Color.web(Colors.OBSTACLE))
        assertTrue(isEitherCurve(lastVelocity()), "vision should be responsive again after recovery ends")
    }
}