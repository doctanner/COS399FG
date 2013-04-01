import java.io.IOException;
import java.io.InputStream;

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;
import lejos.robotics.RegulatedMotor;


public class RemoteTurret {

	// Movement constants:
	private static final int TURN_SPEED = 350;
	private static final int SHOOT_SPEED = 900;

	// Motors:
	protected static final RegulatedMotor motorLeft = Motor.A;
	protected static final RegulatedMotor motorRight = Motor.B;
	protected static final RegulatedMotor motorWeapon = Motor.C;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		motorLeft.setStallThreshold(1000, 1000);
		motorRight.setStallThreshold(1000, 1000);
		motorLeft.setSpeed(TURN_SPEED);
		motorRight.setSpeed(TURN_SPEED);
		motorWeapon.setSpeed(SHOOT_SPEED);
		
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
				case 83: // s
					motorLeft.backward();
					motorRight.forward();
					break;
				case 68: // d
					motorLeft.forward();
					motorRight.backward();
					break;
				case 70: // f
					motorWeapon.rotate(360);
				case 0: // STOP
					motorLeft.stop();
					motorRight.stop(true);

				}

			} catch (IOException e) {
				System.exit(0);
			}
		}
	}

}
