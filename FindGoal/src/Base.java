/**
 * James Tanner
 * COS399: Programming Autonomous Robots
 * 
 * Find the Goal MKII
 */
import java.util.Queue;

import lejos.nxt.Button;
import lejos.nxt.ColorSensor;
import lejos.nxt.ColorSensor.Color;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;
import lejos.robotics.RegulatedMotor;

/**
 * The FindGoal class contains all of the sub-classes for this assignment.
 * <p>
 * 
 * @author James Tanner
 */
public class Base {
	// Construction Constants:
	private static final int CENTER_TO_SENSOR = 5; // TODO DETERMINE

	// Movement constants:
	private static final int DRIVE_SPEED = 900;
	private static final int DRIVE_ACCEL = 500;
	private static final int ESTOP_ACCEL = 2700;
	private static final int ROTATE_SPEED = 300;
	private static final int REVERSE_DIST = -15; //
	private static final int GRID_SIZE = 7 + CENTER_TO_SENSOR;
	private static final int SEARCH_SIZE = 25;
	private static final int DEGREES_PER_METER = -11345;
	private static final int DEGREE_PER_360 = 6070;

	// Light Sensor Constants:
	private static final SensorPort PORT_LIGHT = SensorPort.S3;
	private static final int COLOR_EDGE = ColorSensor.BLACK;
	private static final int COLOR_GOAL = ColorSensor.GREEN;
	private static final int COLOR_HOME = ColorSensor.BLUE;
	
	// Touch Sensor constants
	private static final SensorPort PORT_TOUCH_LEFT = SensorPort.S4;
	private static final SensorPort PORT_TOUCH_RIGHT = SensorPort.S1;

	// Objects:
	private static final RegulatedMotor motorLeft = Motor.B;
	private static final RegulatedMotor motorRight = Motor.C;
	private static final ColorSensor sense = new ColorSensor(PORT_LIGHT);
	private static final TouchSensor touchLeft = new TouchSensor(PORT_TOUCH_LEFT);
	private static final TouchSensor touchRight = new TouchSensor(PORT_TOUCH_RIGHT);
	private static Pilot pilot;

	// Communications:
	static Comms comms;
	static String turretName = "Alien";
	static boolean hasTurret;

	/**
	 * This sets up the necessary threads and controls execution of the main
	 * thread.<br>
	 * Little to no actual work is performed here. See
	 * {@link #searchForGoal(Pilot, int)} and {@link #goHome(Pilot)} for search
	 * algorithms or the Pilot class for movement controls.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Calibrate sensor.

		// TODO REMOVE
		// Comms.openDebugging();

		// Start pilot.
		pilot = new Pilot();
		pilot.setDaemon(true);
		pilot.start();

		// Get connection from turret.
		LCD.drawString("Starting Comms...", 0, 0);
		comms = new Comms();
		LCD.clear(0);
		LCD.drawString("Waiting....", 0, 0);
		//hasTurret = connectToTurret();
		hasTurret = false;

		LCD.drawString("Turret Connected.", 0, 0);

		// Wait to start.
		LCD.clear(4);
		LCD.drawString("Waiting...", 0, 4);
		Comms.Message msg;
		boolean waiting = true;
		do {
			if (!comms.isConnected()){
				LCD.clear(4);
				LCD.drawString("Ready?", 0, 4);
				Button.ENTER.waitForPressAndRelease();
				waiting = false;
			}
			msg = comms.receive();
			if (msg != null && msg.type == Comms.Message.TYPE_COMMAND) {
				Comms.Command command = msg.readAsCommand();
				waiting = command.type != Comms.Command.CMD_START;
			}

		} while (waiting);
		LCD.clear(4);

		// Start sonar.
		comms.send(new Comms.Message("Searching..."));
		sendStart();

		// Find the goal.
		randomSearch(COLOR_GOAL);

		// Sound victory.
		Comms.Command command = new Comms.Command(Comms.Command.CMD_FIRE, Comms.intToBytes(9));
		comms.send(new Comms.Message(command));
		Sound.beepSequenceUp();
		Sound.beepSequenceUp();
		Sound.beepSequenceUp();

		// Find home.
		randomSearch(COLOR_HOME);
		Sound.beepSequence();
		Sound.beepSequence();
		Sound.beepSequence();

		command = new Comms.Command(Comms.Command.CMD_TERM, new byte[0]);
		comms.send(new Comms.Message(command));
		Thread.yield();
		comms.close();
	}

	private static boolean connectToTurret() {
		// Attempt to connect to turret.
		comms = new Comms();
		return comms.listen();
	}

	private static Comms.Command checkForCommand() {
		Comms.Message msg = comms.receive();
		if (msg != null && msg.type == Comms.Message.TYPE_COMMAND)
			return msg.readAsCommand();
		return null;

	}

	private static void sendHalt() {
		comms.send(new Comms.Message(new Comms.Command(Comms.Command.CMD_HALT,
				new byte[0])));
	}

	private static void sendStart() {
		comms.send(new Comms.Message(new Comms.Command(Comms.Command.CMD_START,
				new byte[0])));
	}

	private static void randomSearch(int target) {
		pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
		while (true) {

			int currColor = sense.getColor().getColor();
			if (currColor == target) {
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_ROTATE, 180, null);
				pilot.resumeFromStop();
				return;
			} else if (currColor == COLOR_EDGE) {
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
				pilot.resumeFromStop();

				do {
					Thread.yield();
				} while (pilot.performingTasks);

				int angle = (int) (Math.random() * 360) - 180;
				pilot.pushTask(Task.TASK_ROTATE, angle, null);
				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
				continue;
			}
			
			boolean leftTouched = touchLeft.isPressed();
			boolean rightTouched = touchRight.isPressed();
			
			if (leftTouched && rightTouched){
				// Ran into something.
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
				pilot.resumeFromStop();
				do {
					Thread.yield();
				} while (pilot.performingTasks);
				sendStart();
				int angle = (int) (Math.random() * 360) - 180;
				pilot.pushTask(Task.TASK_ROTATE, angle, null);
				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
			}
			else if(leftTouched){
				// Ran into something.
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
				pilot.resumeFromStop();
				do {
					Thread.yield();
				} while (pilot.performingTasks);
				sendStart();
				int angle = 0 - (int) (Math.random() * 180);
				pilot.pushTask(Task.TASK_ROTATE, angle, null);
				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
			}
			else if(rightTouched){
				// Ran into something.
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
				pilot.resumeFromStop();
				do {
					Thread.yield();
				} while (pilot.performingTasks);
				sendStart();
				int angle = (int) (Math.random() * 180);
				pilot.pushTask(Task.TASK_ROTATE, angle, null);
				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
			}
			
			Comms.Command command = checkForCommand();
			if (command != null) {
				switch (command.type) {
				case Comms.Command.CMD_HALT:
					// About to run into something.
					pilot.emergencyStop();
					pilot.eraseTasks();
					int angle = (int) (Math.random() * 345) + 15;
					pilot.pushTask(Task.TASK_ROTATE, angle, null);
					pilot.resumeFromStop();
					do {
						Thread.yield();
					} while (pilot.performingTasks);
					sendStart();
					pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
				}
			}

			Thread.yield();
		}
	}

	private static void searchForGoal() {

		pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
		@SuppressWarnings("unused")
		boolean foundWall = false;

		while (true) {
			Color currColor = sense.getColor();
			switch (currColor.getColor()) {

			// case COLOR_GOAL:
			// pilot.emergencyStop();
			// pilot.eraseTasks();
			// pilot.resumeFromStop();
			// return;

			case COLOR_EDGE:
				pilot.emergencyStop();
				pilot.eraseTasks();

				if (!pilot.alignToEdge(sense))
					Sound.beepSequence();
				else
					Sound.beepSequenceUp();

				// TEMP: Turn around
				pilot.pushTask(Task.TASK_DRIVE, -GRID_SIZE, null);
				pilot.pushTask(Task.TASK_ROTATE, 180, null);
				pilot.resumeFromStop();

				do {
					Thread.yield();
				} while (pilot.performingTasks);

				// TODO Continue search
				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
				break;
			}

			Thread.yield();
		}
	}

	private static boolean goHome() {
		pilot.pushTask(Task.TASK_GOTO, 0, new Position(0, 0));
		int colorVal;

		do {
			colorVal = sense.getColor().getColor();

			// If home found...
			if (colorVal == COLOR_HOME) {
				pilot.emergencyStop();
				return true;
			}

			// If edge found...
			if (colorVal == COLOR_EDGE) {
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
				pilot.resumeFromStop();
				break;
			}

			// Otherwise...
			Thread.yield();
		} while (pilot.performingTasks);

		// Check in a circle.
		for (int i = 0; i < 12; i++) {

			// Check SEACH_SIZE forward with sensor on.
			pilot.pushTask(Task.TASK_DRIVE, SEARCH_SIZE, null);
			do {
				colorVal = sense.getColor().getColor();
				if (colorVal == COLOR_HOME) {
					pilot.emergencyStop();
					pilot.eraseTasks();
					return true;
				} else if (colorVal == ColorSensor.BLACK) {
					pilot.emergencyStop();
					pilot.resumeFromStop();
					break;
				}
				Thread.yield();
			} while (pilot.performingTasks);

			// Backup and rotate 90 degrees with sensor off.
			pilot.pushTask(Task.TASK_DRIVE, -SEARCH_SIZE, null);
			pilot.pushTask(Task.TASK_ROTATE, 30, null);
			do {
				Thread.yield();
			} while (pilot.performingTasks);
		}
		pilot.resumeFromStop();

		// Watch for home.
		do {
			colorVal = sense.getColor().getColor();
			if (colorVal == COLOR_HOME) {
				pilot.emergencyStop();
				pilot.eraseTasks();
				return true;
			} else if (colorVal == ColorSensor.BLACK) {
				pilot.emergencyStop();
				pilot.resumeFromStop();
			}
			Thread.yield();
		} while (pilot.performingTasks);

		// TODO Create alternate search pattern.
		return false;

	}

	private static class Task {
		protected static final int TASK_GOTO = 0;
		protected static final int TASK_STOP = 1;
		protected static final int TASK_FULLFORWARD = 2;
		protected static final int TASK_DRIVE = 3;
		protected static final int TASK_ROTATE = 4;

		private final int taskID;
		private final int intVal;
		private final Position posVal;

		protected Task(int taskID, int intVal, Position posVal) {
			this.taskID = taskID;
			this.intVal = intVal;
			this.posVal = posVal;
		}
	}

	private static class Pilot extends Thread {
		Position lastWP = new Position(0, 0);
		int currHeading = 90;
		boolean performingTasks = false;
		Flag.BoolFlag eStopped = new Flag.BoolFlag(false);
		Flag.BoolFlag adjustingMotors = new Flag.BoolFlag(false);
		Flag.BoolFlag accessingWP = new Flag.BoolFlag(false);
		Flag.BoolFlag calculatingPos = new Flag.BoolFlag(false);
		Flag.BoolFlag inTask = new Flag.BoolFlag(false);

		Queue<Task> taskQueue = new Queue<Task>();

		private Position updateWaypoint(Position posPreTurn,
				int headingPreTurn, int angleRotated) {

			// Block while calculations are running.
			while (calculatingPos.getAndSet(true))
				Thread.yield();

			// Calculate center position.
			double radHeading = Math.toRadians(headingPreTurn);
			int centerx = posPreTurn.x
					- (int) (CENTER_TO_SENSOR * Math.cos(radHeading));
			int centery = posPreTurn.y
					- (int) (CENTER_TO_SENSOR * Math.sin(radHeading));

			// Calculate heading.
			int newHeading = (angleRotated + headingPreTurn) % 360;

			// Calculate new sensor position.
			int x = centerx + (int) (CENTER_TO_SENSOR * Math.cos(newHeading));
			int y = centery + (int) (CENTER_TO_SENSOR * Math.sin(newHeading));

			// Get locks on waypoint and motors.
			while (accessingWP.getAndSet(true))
				Thread.yield();

			// Update position
			lastWP = new Position(x, y);
			currHeading = newHeading;

			// Reset tachos
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.resetTachoCount();
			motorRight.resetTachoCount();

			// Release locks.
			adjustingMotors.set(false);
			accessingWP.set(false);
			calculatingPos.set(false);

			return lastWP;
		}

		protected synchronized void pushTask(int taskID, int intVal,
				Position posVal) {
			// TODO Validate new task.
			taskQueue.push(new Task(taskID, intVal, posVal));
			performingTasks = true;
		}

		/**
		 * TODO JavaDoc alignToEdge
		 * <p>
		 * <b>Note:</b> This method also calls {@link #emergencyStop()} if not
		 * already in the emergency stopped state.<br>
		 * <b>Warning:</b> {@link #resumeFromStop()} must NOT be called while
		 * this method is running.
		 * 
		 * @param sense
		 *            ColorSensor object to use for alignment.
		 */
		protected boolean alignToEdge(ColorSensor sense) {

			// Report process.
			LCD.drawString("Aligning...", 0, 4);

			// Emergency stop, if not already done.
			if (!eStopped.get())
				emergencyStop();

			// TODO Add alignment system.

			LCD.clear(4);
			resumeFromStop();

			return true;
		}

		private synchronized Task popTask() {
			// Return null if no tasks available.
			if (taskQueue.empty())
				return null;

			// Otherwise, return next task.
			return (Task) taskQueue.pop();
		}

		private synchronized void eraseTasks() {
			// TODO Assert stopped.
			taskQueue.clear();
		}

		public void run() {

			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.setSpeed(DRIVE_SPEED);
			motorRight.setSpeed(DRIVE_SPEED);
			motorLeft.setAcceleration(DRIVE_ACCEL);
			motorRight.setAcceleration(DRIVE_ACCEL);
			adjustingMotors.set(false);

			while (true) {
				// If stop requested, do not run through tasks.
				if (eStopped.get() || calculatingPos.get()) {
					Thread.yield();
					continue;
				}

				// If not task loaded, do nothing.
				LCD.drawString("Tasks: " + taskQueue.size(), 0, 5);
				Task currTask = popTask();
				if (currTask == null) {
					performingTasks = false;
					Thread.yield();
					continue;
				}

				// Set inTask
				inTask.set(true);
				performingTasks = true;
				Sound.beep();

				// Otherwise, handle task.
				switch (currTask.taskID) {

				case Task.TASK_GOTO:
					goTo(currTask.posVal);
					break;

				case Task.TASK_STOP:
					stop();
					break;

				case Task.TASK_FULLFORWARD:
					fullForward();
					break;

				case Task.TASK_DRIVE:
					drive(currTask.intVal);
					break;

				case Task.TASK_ROTATE:
					rotate(currTask.intVal);
					break;

				default:
					// TODO Throw exception.

				}

				// Set inTask
				inTask.set(false);
			}
		}

		protected Position emergencyStop() {
			// Flag pilot to stop.
			eStopped.set(true);

			// Do not stop until current task stops safely..
			while (inTask.get())
				Thread.yield();

			// Halt.
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.setAcceleration(ESTOP_ACCEL);
			motorRight.setAcceleration(ESTOP_ACCEL);
			adjustingMotors.set(false);
			stop();
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.setAcceleration(DRIVE_ACCEL);
			motorRight.setAcceleration(DRIVE_ACCEL);
			adjustingMotors.set(false);
			return getPosition();
		}

		protected void resumeFromStop() {
			// Release stop.
			eStopped.set(false);
		}

		private Position getPosition() {
			// TODO Assert stopped.

			// Wait for other calculation, then lock until complete.
			while (calculatingPos.getAndSet(true))
				Thread.yield();

			// Get current positional data.
			while (accessingWP.getAndSet(true))
				Thread.yield();
			Position startPos = lastWP;

			// Get distance
			int degTravelled = motorLeft.getTachoCount()
					+ motorRight.getTachoCount();
			degTravelled >>= 1;
			int dist = (degTravelled * 100) / DEGREES_PER_METER;

			// Calculate new x and y coordinates.
			double radHeading = Math.toRadians(currHeading);
			int x = startPos.x + (int) (dist * Math.cos(radHeading));
			int y = startPos.y + (int) (dist * Math.sin(radHeading));

			// Release lock.
			accessingWP.set(false);
			calculatingPos.set(false);

			LCD.drawString("Last Position:", 0, 0);
			LCD.drawString("X: " + x, 0, 1);
			LCD.drawString("y: " + y, 0, 2);
			LCD.drawString("Head: " + currHeading, 0, 3);

			return new Position(x, y);
		}

		private void goTo(Position destPos) {
			// TODO ASSERT stopped

			// Get current position data.
			Position currPos = getPosition();
			while (accessingWP.getAndSet(true))
				Thread.yield();
			int startHeading = currHeading;
			accessingWP.set(false);

			// Calculate distance to destination.
			long deltaX = destPos.x - currPos.x;
			long deltaY = destPos.y - currPos.y;
			long sqX = deltaX * deltaX;
			long sqY = deltaY * deltaY;
			int dist = (int) Math.sqrt(sqX + sqY);

			// Calculate angle.
			int newHeading = (int) Math.toDegrees(Math.atan2(deltaY, deltaX));
			int adjustHeading = newHeading - startHeading;

			// Go to new position.
			rotate(adjustHeading);
			drive(dist);
		}

		private void stop() {
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.stop(true);
			motorRight.stop(true);
			adjustingMotors.set(false);

			// Wait until operation completes.
			waitUntilStopped(false);
		}

		private void fullForward() {
			// TODO ASSERT stopped

			// Drive to new position.
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.backward();
			motorRight.backward();
			adjustingMotors.set(false);

			// Continue until emergency stop.
			waitUntilStopped(true);
		}

		private void drive(int dist) {
			// TODO ASSERT stopped

			LCD.drawString("Moving " + dist + "cm", 0, 4);

			// Drive to new position.
			int rotDegree = (dist * DEGREES_PER_METER) / 100;
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.rotate(rotDegree, true);
			motorRight.rotate(rotDegree, true);
			adjustingMotors.set(false);

			// Wait until operation completes.
			waitUntilStopped(true);
			LCD.clear(4);
		}

		private void rotate(int angle) {
			// TODO: ASSERT stopped

			if (angle > 180)
				angle -= 360;
			else if (angle < -180)
				angle += 360;

			LCD.drawString("Rotating " + angle, 0, 4);

			// Update position.
			Position startPos = getPosition();
			int startHeading = currHeading;

			// Turn.
			int degreesToRotate = (angle * DEGREE_PER_360) / 360;
			int left = 0 - degreesToRotate;
			int right = degreesToRotate;

			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.setSpeed(ROTATE_SPEED);
			motorRight.setSpeed(ROTATE_SPEED);
			motorLeft.rotate(left, true);
			motorRight.rotate(right, true);
			adjustingMotors.set(false);

			// Wait until rotation completes.
			waitUntilStopped(false);

			// Set new waypoint following rotation.
			updateWaypoint(startPos, startHeading, angle);

			// Reset speeds
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.setSpeed(DRIVE_SPEED);
			motorRight.setSpeed(DRIVE_SPEED);
			adjustingMotors.set(false);
			LCD.clear(4);
		}

		private void waitUntilStopped(boolean interruptOnStopFlag) {
			while (motorLeft.isMoving() || motorRight.isMoving()) {
				// Return early, if necessary.
				if (interruptOnStopFlag && eStopped.get())
					return;

				// Otherwise, wait.
				Thread.yield();
			}
		}
	}
}