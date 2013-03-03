import java.util.Queue;

import lejos.nxt.Button;
import lejos.nxt.ColorSensor;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.robotics.RegulatedMotor;

/**
 * James Tanner
 * COS399: Programming Autonomous Robots
 * 
 * Find the Goal MKI
 */

/**
 * @author James Tanner
 * 
 */
public class FindGoal {
	// Movement constants:
	private static final int DRIVE_SPEED = 700;
	private static final int GRID_SIZE = 15;
	private static final int DEGREES_PER_METER = 11345; // TODO Fine tune.
	private static final int DEGREE_PER_360 = 5892; // TODO DETERMINE

	// Light Sensor Constants:
	private static final SensorPort SENSE_PORT = SensorPort.S1;
	private static final int FLOOD_COLOR = ColorSensor.BLUE;
	private static final int TARGET_COLOR = ColorSensor.GREEN;
	private static final int HOME_COLOR = ColorSensor.RED;

	// Construction Constants:
	private static final int CENTER_TO_SENSOR = -1; // TODO DETERMINE

	// Objects:
	protected static final RegulatedMotor motorLeft = Motor.B;
	protected static final RegulatedMotor motorRight = Motor.C;
	private static final ColorSensor sense = new ColorSensor(SENSE_PORT);

	/**
	 * 
	 */
	public static void main(String[] args) {
		// TODO Calibrate sensor.
		// TODO Wait to start.
		// TODO Search for goal.
		// TODO Return home.
		
		/** TESTCODE: */
		// Start pilot.
		Pilot pilot = new Pilot();
		pilot.setDaemon(true);
		pilot.start();
		
		// Wait to start.
		Button.ENTER.waitForPressAndRelease();
		
		// Run tests:
		pilot.pushTask(Task.TASK_DRIVE, 28, null);
		pilot.pushTask(Task.TASK_DRIVE, -28, null);
		Button.ENTER.waitForPressAndRelease();
		
		pilot.pushTask(Task.TASK_ROTATE, 90, null);
		pilot.pushTask(Task.TASK_DRIVE, GRID_SIZE, null);
		pilot.pushTask(Task.TASK_DRIVE, 90, null);
		pilot.pushTask(Task.TASK_STOP, 0, null);
		Button.ENTER.waitForPressAndRelease();
		
		// Wait for complete.
		do{
			Thread.yield();
		} while (pilot.performingTasks);
		
		Position endPos = pilot.getPosition();
		LCD.drawString("Stopped position:", 0, 0);
		LCD.drawString("X: " + endPos.x, 0, 1);
		LCD.drawString("Y: " + endPos.y, 0, 2);
		
		Button.ESCAPE.waitForPressAndRelease();
		
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
		Position lastWP;
		int currHeading;
		boolean performingTasks = false;
		Flag.BoolFlag eStopped = new Flag.BoolFlag(false);
		Flag.BoolFlag adjustingMotors = new Flag.BoolFlag(false);
		Flag.BoolFlag acessinggWP = new Flag.BoolFlag(false);
		Flag.BoolFlag calculatingPos = new Flag.BoolFlag(false);
		Flag.BoolFlag inTask = new Flag.BoolFlag(false);

		Queue<Task> taskQueue = new Queue<Task>();

		protected synchronized void pushTask(int taskID, int intVal,
				Position posVal) {
			// TODO Validate new task.
			taskQueue.push(new Task(taskID, intVal, posVal));
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
			stop();
			return getPosition();
		}

		protected void resumeFromStop() {
			// Release stop.
			eStopped.set(false);
		}

		private Position getPosition() {
			// TODO return current position
			// TODO Assert stopped and synchronize.
			
			// Wait for other calculation, then lock until complete.
			while(calculatingPos.getAndSet(true)) Thread.yield();
			
			// Get current positional data.
			while (acessinggWP.getAndSet(true)) Thread.yield();
			Position startPos = lastWP;
			int startHeading = currHeading;
			acessinggWP.set(false);
			
			// Get distance
			int dist = motorLeft.getTachoCount() + motorRight.getTachoCount();
			dist = ((dist >> 1) * 100) / DEGREES_PER_METER;

			// Calculate new x and y coordinates.
			int x = startPos.x + (int) (dist * Math.cos(startHeading));
			int y = startPos.y + (int) (dist * Math.sin(startHeading));

			// Release lock.
			calculatingPos.set(false);
			
			return new Position(x, y);
		}

		private void goTo(Position wayPoint) {
			// TODO ASSERT stopped

			// TODO Use inTask and stop flags to synchronize and exit safely.
			// TODO Use flag to protect motor commands.
			// TODO Go to waypoint.
			// TODO Use stop flag to exit.
		}

		private void stop() {
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.stop(true);
			motorLeft.stop(true);
			adjustingMotors.set(false);

			// Wait until operation completes.
			waitUntilStopped(false);
		}

		private void fullForward() {
			// TODO ASSERT stopped
			// TODO Use stop flag to exit.

			// Drive to new position.
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.backward();
			motorRight.backward();
			adjustingMotors.set(false);
		}

		private void drive(int dist) {
			// TODO ASSERT stopped

			// Drive to new position.
			int rotDegree = ((-1 * dist) * DEGREES_PER_METER) / 100;
			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.rotate(rotDegree, true);
			motorRight.rotate(rotDegree, true);
			adjustingMotors.set(false);

			// Wait until operation completes.
			LCD.drawString("Moving" + dist, 0, 4);
			waitUntilStopped(true);
			LCD.clear(4);
		}

		private void rotate(int angle) {
			// TODO: ASSERT stopped

			// Update position.
			Position currPos = getPosition();

			// Turn.
			int degreesToRotate = (angle * DEGREE_PER_360) / 360;
			int left = angle < 0 ? degreesToRotate : 0 - degreesToRotate;
			int right = angle > 0 ? degreesToRotate : 0 - degreesToRotate;

			while (adjustingMotors.getAndSet(true))
				Thread.yield();
			motorLeft.rotate(left, true);
			motorRight.rotate(right, true);
			adjustingMotors.set(false);

			// Wait until rotation completes.
			waitUntilStopped(false);

			// Calculate center position.
			int centerx = currPos.x - (int) (CENTER_TO_SENSOR * Math.cos(currHeading));
			int centery = currPos.y - (int) (CENTER_TO_SENSOR * Math.sin(currHeading));

			// Calculate heading.
			int newHeading = (angle + currHeading) % 360;
			
			// Calculate new sensor position.
			int x = centerx + (int) (CENTER_TO_SENSOR * Math.cos(newHeading));
			int y = centery + (int) (CENTER_TO_SENSOR * Math.sin(newHeading));
			
			
			// Update position
			// TODO Synchronize.
			while (acessinggWP.getAndSet(true)) Thread.yield();
			lastWP = getPosition();
			currHeading = newHeading;
			acessinggWP.set(false);
		}

		private void waitUntilStopped(boolean interruptOnStopFlag) {
			while (motorLeft.isMoving() || motorRight.isMoving()){
				// Return early, if necessary.
				if (interruptOnStopFlag && eStopped.get()) return;
				
				// Otherwise, wait.
				Thread.yield();
			}
		}
	}
}
 