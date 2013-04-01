/**
 * James Tanner <br>
 * COS 399 - Programming Autonomous Robots <p>
 * 
 * This is the turret code used in Find The Goal, MKII.
 */

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.UltrasonicSensor;
import lejos.robotics.RegulatedMotor;

/**
 * @author James Tanner
 * 
 */
public class Turret {
	// Movement constants:
	private static final int TURN_SPEED = 900;
	private static final int TURN_ACCEL = 600;
	private static final int SHOOT_SPEED = 900;
	private static final int DEGREE_PER_1440 = 3380;
	private static final int ALERT_THRESH = 18;
	private static final int STALL_THRESH_DEGREE = 200;
	private static final int STALL_THRESH_TIME = 10000;	

	// Motors:
	protected static final RegulatedMotor motorLeft = Motor.A;
	protected static final RegulatedMotor motorRight = Motor.B;
	protected static final RegulatedMotor motorWeapon = Motor.C;

	// Sensor:
	protected static final SensorPort SONAR_PORT = SensorPort.S1;
	protected static final UltrasonicSensor sonar = new UltrasonicSensor(
			SONAR_PORT);

	// Communications:
	private static Comms comms = new Comms();
	private static String baseName = "Predator";

	// State:
	private static boolean alert = false;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// Set up motors:
		motorLeft.setSpeed(TURN_SPEED);
		motorRight.setSpeed(TURN_SPEED);
		motorLeft.setAcceleration(TURN_ACCEL);
		motorRight.setAcceleration(TURN_ACCEL);
		motorLeft.setStallThreshold(STALL_THRESH_DEGREE, STALL_THRESH_TIME);
		motorRight.setStallThreshold(STALL_THRESH_DEGREE, STALL_THRESH_TIME);
		motorWeapon.setSpeed(SHOOT_SPEED);

		// Set up sonar.
		sonar.continuous();
		// TODO Use capture() to look for other sonars.

		// TODO Remove
		//Comms.openDebugging();

		LCD.drawString("Press Enter.", 0, 0);
		Button.ENTER.waitForPressAndRelease();
		LCD.drawString("Connecting...", 0, 0);
		// Connect to base.
		if (comms.connect(baseName)) {
			LCD.clear(0);
			LCD.drawString("Connected!", 0, 0);
		}

		LCD.clear(4);
		Sound.beepSequenceUp();
		LCD.drawString("Ready?", 0, 4);
		Button.ENTER.waitForPressAndRelease();
		LCD.clear(4);
		sendStart();

		while (Button.ESCAPE.isUp()) {
			// Get the next message, if there is one.
			Comms.Message msg = comms.receive();
			if (msg != null) {
				// Handle the message type.
				switch (msg.type) {
				case Comms.Message.TYPE_COMMAND:
					// Handle the command.
					handleCommand(msg.readAsCommand());
					break;

				case Comms.Message.TYPE_STRING:
					LCD.clear(4);
					LCD.drawString(msg.readAsString(), 0, 4);
				}
			}

			// Get the current distance.
			int dist = sonar.getDistance();
			if (dist < ALERT_THRESH && alert) {
					sendHalt();
					alert = false;
			}

			LCD.clear(3);
			LCD.drawString("Dist: " + dist + "cm", 0, 3);

		}
	}

	private static void sendHalt() {
		comms.send(new Comms.Message(new Comms.Command(Comms.Command.CMD_HALT,
				new byte[0])));
	}

	private static void sendStart() {
		comms.send(new Comms.Message(new Comms.Command(Comms.Command.CMD_START,
				new byte[0])));
	}

	private static void handleCommand(Comms.Command command) {
		switch (command.type) {
		case Comms.Command.CMD_TERM:
			comms.close();
			System.exit(1);
		case Comms.Command.CMD_ROTATE:
			rotateTo(Comms.bytesToInt(command.value));
			break;
		case Comms.Command.CMD_START:
			alert = true;
			break;
		case Comms.Command.CMD_HALT:
			alert = false;
			break;
		case Comms.Command.CMD_FIRE:
			int rounds = Comms.bytesToInt(command.value);
			for (int i = 0; i < rounds; i++)
				fire();
			sendStart();
			break;
		}

	}

	private static void rotateTo(int angle) {
		int motorAngle = (angle *  DEGREE_PER_1440) / 1440;
		motorLeft.rotate(motorAngle, true);
		motorLeft.rotate(0 - motorAngle, true);
	}
	
	private static void fire(){
		motorWeapon.rotate(360);
	}

}
