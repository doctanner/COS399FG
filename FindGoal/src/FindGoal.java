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
import lejos.robotics.RegulatedMotor;

/**
 * The FindGoal class contains all of the sub-classes for this assignment.
 * <p>
 * 
 * @author James Tanner
 */
public class FindGoal {
	// Construction Constants:
	private static final int CENTER_TO_SENSOR = 5; // TODO DETERMINE

	// Movement constants:
	private static final int DRIVE_SPEED = 800; // Known Good: 850
	private static final int DRIVE_ACCEL = 400; // Known Good: 500
	private static final int ESTOP_ACCEL = 1500; // Known Good: 1500
	private static final int ROTATE_SPEED = 350; // Known Good: 350
	private static final int REVERSE_DIST = -10; // Known Good: -10
	private static final int GRID_SIZE = 7 + CENTER_TO_SENSOR; // Known Good: 7
	private static final int SEARCH_SIZE = 25; // Known Good: 20
	private static final int DEGREES_PER_METER = -11345; // TODO Fine tune.
	private static final int DEGREE_PER_360 = 6070; // Determined: 6077

	// Light Sensor Constants:
	private static final SensorPort SENSE_PORT = SensorPort.S3;
	private static final int COLOR_EDGE = ColorSensor.BLACK;
	private static final int COLOR_GOAL = ColorSensor.GREEN;
	private static final int COLOR_HOME = ColorSensor.BLUE;

	// Search Constants:
	private static final int MODE_NORMAL = 0;
	private static final int MODE_FAR = 1;
	private static final int MODE_CURRENT = MODE_NORMAL;

	// Objects:
	protected static final RegulatedMotor motorLeft = Motor.B;
	protected static final RegulatedMotor motorRight = Motor.C;
	private static final ColorSensor sense = new ColorSensor(SENSE_PORT);

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

		// Start pilot.
		Pilot pilot = new Pilot();
		pilot.setDaemon(true);
		pilot.start();

		// Wait to start.
		Button.ENTER.waitForPressAndRelease();

		// Find the goal.
		searchForGoal(pilot, MODE_CURRENT);

		// Sound victory.
		Sound.beepSequenceUp();
		Sound.beepSequenceUp();
		Sound.beepSequenceUp();

		// Find home.
		boolean foundHome = goHome(pilot);

		// Sound victory.
		pilot.getPosition();

		if (foundHome) {
			Sound.beepSequence();
			Sound.beepSequence();
			Sound.beepSequence();

			LCD.clear(0);
			LCD.drawString("FOUND GOAL!", 0, 0);
		} else {
			LCD.clear(0);
			LCD.drawString("Failed. :(", 0, 0);
		}

		// Wait for permission to stop.
		Button.ESCAPE.waitForPressAndRelease();
	}

	private static void searchForGoal(Pilot pilot, int mode) {

		pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
		boolean foundWall = false;
		int turnDir = 1;

		while (true) {
			Color currColor = sense.getColor();
			switch (currColor.getColor()) {

			case COLOR_GOAL:
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.resumeFromStop();
				return;

			case COLOR_EDGE:
				pilot.emergencyStop();
				pilot.eraseTasks();
				pilot.resumeFromStop();
				Thread.yield();

				if (!foundWall && mode == MODE_FAR) {
					pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
					do {
						Thread.yield();
					} while (pilot.performingTasks);

					pilot.pushTask(Task.TASK_ROTATE, turnDir * 90, null);
					foundWall = true;

				} else {
					pilot.pushTask(Task.TASK_DRIVE, REVERSE_DIST, null);
					do {
						Thread.yield();
					} while (pilot.performingTasks);

					pilot.pushTask(Task.TASK_ROTATE, turnDir * 90, null);
					pilot.pushTask(Task.TASK_DRIVE, GRID_SIZE, null);
					pilot.pushTask(Task.TASK_ROTATE, turnDir * 90, null);
					turnDir *= -1;
				}

				pilot.pushTask(Task.TASK_FULLFORWARD, 0, null);
				break;
			}

			Thread.yield();
		}
	}

	private static boolean goHome(Pilot pilot) {
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

	private static class Position {
		/** X-coordinate. */
		final int x;

		/** y-coordinate. */
		final int y;

		Position(int x, int y) {
			this.x = x;
			this.y = y;
		}
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
			// Emergency stop, if not already done.
			if (!eStopped.get())
				emergencyStop();

			// If not starting on edge, don't attempt to align.
			if (sense.getColor().getColor() != COLOR_EDGE)
				return false;
			// TODO Additional alert on alignToEdgeFail

			// If heading is not multiple of 90 degrees, don't align.
			if (currHeading % 90 != 0)
				return false;
			// TODO Add ability to use off-set when not at right angles.

			// Report process.
			LCD.drawString("Aligning...", 0, 4);

			// Create new waypoint.
			Position currPos = getPosition();
			while (accessingWP.getAndSet(true))
				Thread.yield();
			lastWP = currPos;

			// Get lock on motors.
			while (adjustingMotors.getAndSet(true))
				Thread.yield();

			// Turn left
			motorLeft.setSpeed(ROTATE_SPEED);
			motorRight.setSpeed(ROTATE_SPEED);
			motorLeft.forward();
			motorRight.backward();

			// Wait until edge lost.
			int currColor;
			do {
				currColor = sense.getColor().getColor();
			} while (currColor != COLOR_EDGE);

			// Turn right
			motorLeft.resetTachoCount();
			motorRight.resetTachoCount();
			motorLeft.backward();
			motorRight.forward();

			// Wait until edge found and lost.
			boolean edgeSeen = false;
			do {
				currColor = sense.getColor().getColor();
				if (currColor == COLOR_EDGE)
					edgeSeen = true;
			} while (currColor != COLOR_EDGE || !edgeSeen);

			// Get raw angles rotated.
			int rawLeft = motorLeft.getTachoCount();
			int rawRight = motorRight.getTachoCount();

			// Rotate to center.
			motorLeft.rotate(0 - rawLeft / 2);
			motorRight.rotate(0 - rawRight / 2);

			// Reset speeds and tachos
			motorLeft.setSpeed(DRIVE_SPEED);
			motorRight.setSpeed(DRIVE_SPEED);
			motorLeft.resetTachoCount();
			motorRight.resetTachoCount();

			// Release locks
			accessingWP.set(false);
			adjustingMotors.set(false);
			LCD.clear(4);

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