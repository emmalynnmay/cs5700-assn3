// package api

// import command.SetVelocityCommand
// import observer.Observer
// import javafx.scene.paint.Color

// class BallFinderProgram : RobotProgram {

//     override val name: String = "Ball Finder Program"

//     private lateinit var robot: RobotApi

//     private val visionObserver = Observer<Color> { current -> onVisionUpdate(current) }

//     /** Called when the program is launched: subscribe to sensors and start driving. */
//     override fun startProgram(robot: RobotApi) {
//         this.robot = robot
//         robot.sensors.vision.subscribe(visionObserver)

//         // TODO: find the red dot. It is the only thing on the screen that will be the color Colors.BALL
//     }

//     /** Called when the program is stopped: unsubscribe observers and stop the robot. */
//     override fun stopProgram(robot: RobotApi) {
//         robot.sensors.vision.unsubscribe(visionObserver)
//         robot.perform(SetVelocityCommand(robot.actuator, 0.0, 0.0))
//     }

//     private fun onVisionUpdate(current: Double) {
//         // TODO
//     }
// }