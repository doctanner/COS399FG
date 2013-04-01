import java.io.IOException;
import java.io.InputStream;

import lejos.nxt.Button;
import lejos.nxt.ColorSensor;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;
import lejos.robotics.RegulatedMotor;

public class RemoteBase {
	// Movement constants:
	private static final int DRIVE_SPEED = 900;
	private static final int DRIVE_ACCEL = 1000;
	private static final int ESTOP_ACCEL = 2700;
	private static final int ROTATE_SPEED = 300;

	// Light Sensor Constants:
	private static final SensorPort PORT_LIGHT = SensorPort.S3;
	private static final int COLOR_EDGE = ColorSensor.BLACK;
	private static final int COLOR_GOAL = ColorSensor.GREEN;
	private static final int COLOR_HOME = ColorSensor.BLUE;

	// Objects:
	private static final RegulatedMotor motorLeft = Motor.B;
	private static final RegulatedMotor motorRight = Motor.C;
	private static final ColorSensor sense = new ColorSensor(PORT_LIGHT);

	// Communications:
	static Comms comms;
	static String turretName = "Alien";
	static boolean hasTurret;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		motorLeft.setSpeed(DRIVE_SPEED);
		motorRight.setSpeed(DRIVE_SPEED);
		
		LCD.drawString("Waiting...", 0, 0);
		BTConnection btc = Bluetooth.waitForConnection();
		LCD.drawString("Connecting...", 0, 0);
		InputStream stream = btc.openInputStream();
		LCD.clear(0);
		LCD.drawString("Listening...", 0, 0);

		while (Button.ESCAPE.isUp()) {
			try {
				int key = stream.read();
				switch (key) {
				case 37: // left
					motorLeft.backward();
					motorRight.forward();
					break;
				case 38: // up
					motorLeft.backward();
					motorRight.backward();
					break;
				case 39: // right
					motorLeft.forward();
					motorRight.backward();
					break;
				case 40: // down
					motorLeft.forward();
					motorRight.forward();
					break;
				case 65:
					Sound.beepSequenceUp();
					Sound.beepSequenceUp();
					Sound.beepSequenceUp();
					break;
				case 0: // STOP
					motorLeft.setAcceleration(ESTOP_ACCEL);
					motorRight.setAcceleration(ESTOP_ACCEL);
					motorLeft.stop(true);
					motorRight.stop(true);
					motorLeft.setAcceleration(DRIVE_ACCEL);
					motorRight.setAcceleration(DRIVE_ACCEL);
				}

			} catch (IOException e) {
				System.exit(0);
			}
		}
	}

}
