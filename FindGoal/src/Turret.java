/**
 * James Tanner <br>
 * COS 399 - Programming Autonomous Robots <p>
 * 
 * This is the turret code used in Find The Goal, MKII.
 */

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.robotics.RegulatedMotor;

/**
 * @author James Tanner
 * 
 */
public class Turret {
	// Movement constants:
	private static final int TURN_SPEED = 200;
	private static final int TURN_ACCEL = 600;
	private static final int SHOOT_SPEED = 900;
	private static final int DEGREE_PER_360 = 845;

	// Objects:
	protected static final RegulatedMotor motorLeft = Motor.A;
	protected static final RegulatedMotor motorRight = Motor.B;
	protected static final RegulatedMotor motorWeapon = Motor.C;

	// Communications:
	static Comms comms = new Comms();
	static String baseName = "Predator";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		// Set up motors:
		motorLeft.setSpeed(TURN_SPEED);
		motorRight.setSpeed(TURN_SPEED);
		motorLeft.setAcceleration(TURN_ACCEL);
		motorRight.setAcceleration(TURN_ACCEL);
		motorWeapon.setSpeed(SHOOT_SPEED);

		// TODO Remove
		// Comms.openDebugging();

		LCD.drawString("Press Enter.", 0, 0);
		Button.ENTER.waitForPressAndRelease();
		LCD.drawString("Connecting...", 0, 0);
		// Connect to base.
		if (comms.connect()) {
			LCD.clear(0);
			LCD.drawString("Connected!", 0, 0);
		}
		
		while(Button.ESCAPE.isUp()){
			// Get the next message, if there is one.
			Comms.Message msg = comms.receive();
			if (msg == null){
				Thread.yield();
				continue;
			}
			
			// Handle the message type.
			switch (msg.type){
			case Comms.Message.TYPE_COMMAND:
				// Handle the command.
				handleCommand(msg.readAsCommand());
				break;
			
			case Comms.Message.TYPE_STRING:
				LCD.clear(4);
				LCD.drawString(msg.readAsString(), 0, 4);
			}
		}
	}
	
	private static void handleCommand(Comms.Command command){
		switch (command.type){
		case Comms.Command.CMD_TERM:
			System.exit(1);
		case Comms.Command.CMD_ROTATE:
			rotateTo(Comms.bytesToInt(command.value));
		}
		
	}

	private static void rotateTo(int angle) {
		int motorAngle = (angle * DEGREE_PER_360) / 360;
		motorLeft.rotateTo(motorAngle, true);
		motorLeft.rotateTo(0 - motorAngle, true);
	}

}
