package command

class SetVelocityCommand(
    private val actuator: RobotActuator, 
    private val left: Double, 
    private val right: Double,
    private val prevLeft: Double = actuator.leftTrackVelocity,
    private val prevRight: Double = actuator.rightTrackVelocity,
    ) : Command
{
    override fun execute()
    {
        actuator.setTrackVelocities(left, right)
    }

    override fun undo()
    {
        actuator.setTrackVelocities(prevLeft, prevRight)
    }
} 