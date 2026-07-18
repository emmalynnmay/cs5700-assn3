package api

import command.SetVelocityCommand
import observer.Observer
import javafx.scene.paint.Color
import kotlin.random.Random
import environment.Colors

fun Color.toHex(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

class BallFinderProgram : RobotProgram {

    override val name: String = "Ball Finder Program"

    private lateinit var robot: RobotApi

    /** True while we're turning to clear an obstacle; vision updates are ignored during this. */
    private var recovering: Boolean = false
    private var recoveryChecks: Int = 0

    /** The turn direction committed to for the current recovery episode. */
    private var recoveryTurn: Pair<Double, Double> = SPIN_LEFT

    private val visionObserver = Observer<Color> { current -> onVisionUpdate(current) }
    private val collisionObserver = Observer<Boolean> { current -> onCollisionUpdate(current) }

    /** Called when the program is launched: subscribe to sensors and start driving. */
    override fun startProgram(robot: RobotApi) {
        this.robot = robot
        robot.sensors.vision.subscribe(visionObserver)
        robot.sensors.collision.subscribe(collisionObserver)

        // Start out searching in a random direction until the ball comes into view.
        val startCurve = if (Random.nextBoolean()) CURVE_LEFT else CURVE_RIGHT
        robot.perform(SetVelocityCommand(robot.actuator, startCurve.first, startCurve.second))
    }

    /** Called when the program is stopped: unsubscribe observers and stop the robot. */
    override fun stopProgram(robot: RobotApi) {
        robot.sensors.vision.unsubscribe(visionObserver)
        robot.sensors.collision.unsubscribe(collisionObserver)
        robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
    }

    /**
     * Find the red dot: it is the only thing on screen that will be [Colors.BALL].
     * - Ball in view -> drive straight toward it.
     * - Obstacle in view -> curve away from it (don't drive straight into it).
     * - Otherwise (floor, or anything unrecognized) -> path is clear, keep searching.
     *
     * Ignored entirely while [recovering] from a collision, so the recovery turn
     * isn't immediately overridden by a "drive straight" command.
     */
    private fun onVisionUpdate(current: Color) {
        if (recovering) return

        when (current.toHex()) {
            Colors.BALL -> {
                robot.perform(SetVelocityCommand(robot.actuator, FORWARD_SPEED, FORWARD_SPEED))
            }
            Colors.OBSTACLE -> {
                val curve = if (Random.nextBoolean()) CURVE_LEFT else CURVE_RIGHT
                robot.perform(SetVelocityCommand(robot.actuator, curve.first, curve.second))
            }
            else -> {
                val curve = if (Random.nextBoolean()) CURVE_LEFT else CURVE_RIGHT
                // robot.perform(SetVelocityCommand(robot.actuator, curve.first, curve.second))
                robot.perform(SetVelocityCommand(robot.actuator, FORWARD_SPEED, FORWARD_SPEED))
            }
        }
    }

    /**
     * Handles collision sensor updates, recovering from obstacles by spinning in
     * place for a fixed duration rather than reacting to collision-clear on every
     * reading.
     *
     * - If already [recovering]: keep spinning in the committed [recoveryTurn]
     *   direction for [RECOVERY_TIME] update ticks (tracked via [recoveryChecks]),
     *   ignoring the current collision reading entirely. Once that many ticks have
     *   elapsed, exit recovery and resume driving straight forward.
     * - If not yet recovering and [current] reports a new collision: commit to one
     *   random spin direction ([SPIN_LEFT] or [SPIN_RIGHT]) and begin spinning.
     *
     * Spinning in place (rather than reversing) rotates the robot's heading without
     * translating it, so it cannot drive itself back into the same obstacle while
     * turning. Committing to a fixed recovery duration - rather than exiting the
     * instant collision reports clear - avoids prematurely resuming forward motion
     * before the robot has actually rotated clear of the obstacle.
     */
    private fun onCollisionUpdate(current: Boolean) {
        if (recovering)
        {
            if (recoveryChecks > RECOVERY_TIME)
            {
                // We're done recovering
                recovering = false
                recoveryChecks = 0
                robot.perform(SetVelocityCommand(robot.actuator, FORWARD_SPEED, FORWARD_SPEED))
            }
            else
            {
                // We are still recovering
                recoveryChecks += 1
                robot.perform(SetVelocityCommand(robot.actuator, recoveryTurn.first, recoveryTurn.second))
            }
            return
        }

        // There is a new collision
        if (current) {
            if (!recovering) {
                recovering = true
                recoveryTurn = if (Random.nextBoolean()) SPIN_LEFT else SPIN_RIGHT
            }
            robot.perform(SetVelocityCommand(robot.actuator, recoveryTurn.first, recoveryTurn.second))
        }
    }

    companion object {
        private const val FORWARD_SPEED = 75.0
        private const val SPIN_SPEED = 20.0
        
        private const val RECOVERY_TIME = 30

        private val CURVE_LEFT = FORWARD_SPEED to FORWARD_SPEED * 0.4
        private val CURVE_RIGHT = FORWARD_SPEED * 0.4 to FORWARD_SPEED

        // Pure in-place spin: tracks move in opposite directions, rotating the robot's
        // heading without moving it forward or backward at all.
        private val SPIN_LEFT = -SPIN_SPEED to SPIN_SPEED
        private val SPIN_RIGHT = SPIN_SPEED to -SPIN_SPEED
    }
}