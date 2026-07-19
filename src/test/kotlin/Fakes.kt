package testing

import command.SetVelocityCommand
import environment.Environment
import geometry.Pose
import command.RobotActuator
import sensor.RobotSensors
import api.RobotApi
import command.Command
import sensor.Sensor
import api.HeatFinderProgram
import javafx.scene.paint.Color

class FakeRobotActuator : RobotActuator {
    override var leftTrackVelocity: Double = 0.0
        private set
    override var rightTrackVelocity: Double = 0.0
        private set

    /** Every (left, right) pair ever sent, in order. */
    val calls = mutableListOf<Pair<Double, Double>>()

    override fun setTrackVelocities(left: Double, right: Double) {
        leftTrackVelocity = left
        rightTrackVelocity = right
        calls.add(left to right)
    }
}

class FakeRobotApi(
    override val sensors: RobotSensors,
    override val actuator: FakeRobotActuator = FakeRobotActuator(),
) : RobotApi {

    val performed = mutableListOf<Command>()

    override fun perform(command: Command) {
        performed.add(command)
        command.execute()
    }

    override fun perform(commands: List<Command>) {
        commands.forEach { perform(it) }
    }

    override fun undo() {}
    override fun redo() {}
}

class FakeVisionSensor : Sensor<Color>(label = "vision", mountForward = 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): Color =
        throw UnsupportedOperationException("measure() should not be called; tests use notifyObservers directly")
}

class FakeCollisionSensor : Sensor<Boolean>(label = "collision", mountForward = 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): Boolean =
        throw UnsupportedOperationException("measure() should not be called; tests use notifyObservers directly")
}

class FakeGenericSensor<T>(label: String) : Sensor<T>(label = label, mountForward = 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): T =
        throw UnsupportedOperationException("measure() should not be called; tests use notifyObservers directly")
}

class FakeTemperatureSensor : Sensor<Double>(label = "temperature", mountForward = 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): Double =
        throw UnsupportedOperationException("measure() should not be called; tests use notifyObservers directly")
}

class FakeLineSensor(label: String) : Sensor<Boolean>(label = label, mountForward = 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): Boolean =
        throw UnsupportedOperationException("measure() should not be called; tests use notifyObservers directly")
}

class FakeRobotSensors(
    override val lineLeft: FakeLineSensor = FakeLineSensor("lineLeft"),
    override val lineCenter: FakeLineSensor = FakeLineSensor("lineCenter"),
    override val lineRight: FakeLineSensor = FakeLineSensor("lineRight"),
    override val sonar: Sensor<Double> = FakeGenericSensor("sonar"),
    override val temperature: Sensor<Double> = FakeGenericSensor("temperature"),
    override val vision: Sensor<javafx.scene.paint.Color> = FakeGenericSensor("vision"),
    override val collision: Sensor<Boolean> = FakeGenericSensor("collision"),
) : RobotSensors
