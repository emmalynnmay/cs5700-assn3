package api

import command.SetVelocityCommand
import observer.Observer
import kotlin.random.Random

class HeatFinderProgram : RobotProgram {

    override val name: String = "Heat Finder Program"

    private var previousTemp: Double? = null
    private lateinit var robot: RobotApi

    private val tempObserver = Observer<Double> { current -> onTemperatureUpdate(current) }

    /** Called when the program is launched: subscribe to sensors and start driving. */
    override fun startProgram(robot: RobotApi) {
        this.robot = robot
        robot.sensors.temperature.subscribe(tempObserver)

        // Kick off in a random direction so we don't always probe the same heading first.
        val startTurn = if (Random.nextBoolean()) CURVE_LEFT else CURVE_RIGHT
        robot.perform(SetVelocityCommand(robot.actuator, startTurn.first, startTurn.second))
    }

    /** Called when the program is stopped: unsubscribe observers and stop the robot. */
    override fun stopProgram(robot: RobotApi) {
        robot.sensors.temperature.unsubscribe(tempObserver)
        robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
    }

    /**
     * Hill-climbing heuristic: if temperature rose since the last reading, keep driving
     * straight (we're headed toward the heat source). Otherwise, curve toward a new
     * heading while still moving forward, so we keep sampling new locations instead of
     * spinning in place.
     */
    private fun onTemperatureUpdate(current: Double) {
        val prev = previousTemp
        previousTemp = current

        if (prev == null) return // first reading - nothing to compare yet

        if (current > prev) {
            robot.perform(SetVelocityCommand(robot.actuator, FORWARD_SPEED, FORWARD_SPEED))
        } else {
            val curve = if (Random.nextBoolean()) CURVE_LEFT else CURVE_RIGHT
            robot.perform(SetVelocityCommand(robot.actuator, curve.first, curve.second))
        }
    }

    companion object {
        private const val FORWARD_SPEED = 25.0

        // Curves keep both tracks moving forward (unlike a spin-in-place turn), so the
        // robot keeps translating and sampling new temperature readings while it steers.
        private val CURVE_LEFT = FORWARD_SPEED to FORWARD_SPEED * 0.4
        private val CURVE_RIGHT = FORWARD_SPEED * 0.4 to FORWARD_SPEED
    }
}