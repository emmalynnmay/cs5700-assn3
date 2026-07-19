package api

import command.SetVelocityCommand
import observer.Observer

class LineFollowerProgram : RobotProgram {

    override val name: String = "Line Follower Program"

    private lateinit var robot: RobotApi

    private var left: Boolean = false
    private var center: Boolean = false
    private var right: Boolean = false

    private enum class Phase { SWING_LEFT, SWING_RIGHT }

    private var phase: Phase = Phase.SWING_LEFT
    private var recoveryPhase: Phase = Phase.SWING_LEFT

    private val leftLineObserver = Observer<Boolean> { current -> onLeftLineUpdate(current) }
    private val centerLineObserver = Observer<Boolean> { current -> onCenterLineUpdate(current) }
    private val rightLineObserver = Observer<Boolean> { current -> onRightLineUpdate(current) }

    /** Called when the program is launched: subscribe to sensors and start driving. */
    override fun startProgram(robot: RobotApi) {
        this.robot = robot
        robot.sensors.lineLeft.subscribe(leftLineObserver)
        robot.sensors.lineCenter.subscribe(centerLineObserver)
        robot.sensors.lineRight.subscribe(rightLineObserver)

        phase = Phase.SWING_LEFT

        // Start off swinging left, as if we just finished a right-swing.
        driveSwing(phase)
    }

    /** Called when the program is stopped: unsubscribe observers and stop the robot. */
    override fun stopProgram(robot: RobotApi) {
        robot.sensors.lineLeft.unsubscribe(leftLineObserver)
        robot.sensors.lineCenter.unsubscribe(centerLineObserver)
        robot.sensors.lineRight.unsubscribe(rightLineObserver)
        robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
    }

    private fun onLeftLineUpdate(current: Boolean) {
        left = current
        drive()
    }

    private fun onCenterLineUpdate(current: Boolean) {
        center = current
        drive()
    }

    private fun onRightLineUpdate(current: Boolean) {
        right = current
        drive()
    }

    private fun drive() {
        if (right && center && left)
        {
            // Continue whatever we had been doing
        }
        else if (!left && center && right)
        {
            // We have lost the left sensor- time to swing right
            if (phase == Phase.SWING_LEFT) {
                phase = Phase.SWING_RIGHT
            }
        }
        else if (left && center && !right)
        {
            // We have lost the right sensor - time to swing left
            if (phase == Phase.SWING_RIGHT) {
                phase = Phase.SWING_LEFT
            }
        }
        else if (left && !center && !right)
        {
            // We have lost the middle and right sensors. 
            // Stop and turn left until the center sensor is back
            driveRecovery()
            return
        }
        else if (!left && !center && right)
        {
            // We have lost the middle and right sensors. 
            // Stop and turn right until the center sensor is back
            driveRecovery()
            return
        }
        else if (!left && !center && !right)
        {
            // Game over (or... reached the end of the line!)
            robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
            return
        }
        driveSwing(phase)
    }

    private fun driveRecovery() {
        if (center) {
            // Reacquired the line - resume normal zigzag tracking, continuing in the
            // same direction we were pivoting.
            phase = recoveryPhase
            driveSwing(phase)
            return
        }

        // Stop
        robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
        // Turn towards the sensor that still has the line
        if (right)
        {
            robot.perform(SetVelocityCommand(robot.actuator, -PIVOT_SPEED, PIVOT_SPEED))
        }
        else
        {
            robot.perform(SetVelocityCommand(robot.actuator, PIVOT_SPEED, -PIVOT_SPEED))
        }
    }

    /** Issues the diagonal swing command for the given phase (slower inner wheel, faster outer wheel). */
    private fun driveSwing(swingPhase: Phase) {
        when (swingPhase) {
            Phase.SWING_RIGHT -> robot.perform(
                SetVelocityCommand(robot.actuator, SWING_INNER_SPEED, SWING_OUTER_SPEED)
            )
            Phase.SWING_LEFT -> robot.perform(
                SetVelocityCommand(robot.actuator, SWING_OUTER_SPEED, SWING_INNER_SPEED)
            )
        }
    }

    companion object {
        private const val FORWARD_SPEED = 30.0
        private const val SWING_OUTER_SPEED = FORWARD_SPEED
        private const val SWING_INNER_SPEED = FORWARD_SPEED * 0.5
        private const val PIVOT_SPEED = 30.0
        private const val MAX_RECOVERY_TICKS = 50
    }
}