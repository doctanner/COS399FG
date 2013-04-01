import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.comm.Bluetooth;

/**
 * James Tanner <br>
 * COS 399 - Programming Autonomous Robots <p>
 * 
 * This is the turret code used in Find The Goal, MKII.
 */

/**
 * @author James Tanner
 * 
 */
public class Turret {

	// Communications:
	static Comms comms = new Comms();
	static Comms.Connection base;
	static String baseName = "Predator";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
				
		LCD.drawString("Press Enter.", 0, 0);
		Button.ENTER.waitForPressAndRelease();
		LCD.drawString("Connecting...", 0, 0);
		// Connect to base.
		base = comms.getConnection(baseName, true);
		LCD.clear(0);
		LCD.drawString("Connected!", 0, 0);

		while (base.isConnected()) {
			
			LCD.clear(4);
			LCD.drawString("Check Message?", 0, 4);
			Button.ENTER.waitForPressAndRelease();
			Comms.Message msg;
			msg = base.receive();
			if (msg != null) {
				LCD.clear(4);
				LCD.drawString(msg.readAsString(), 0, 4);
				Button.ENTER.waitForPressAndRelease();
			}
		}
	}

}
