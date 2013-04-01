import lejos.nxt.Button;
import lejos.nxt.LCD;

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

		// TODO Remove
		Comms.openDebugging();

		LCD.drawString("Press Enter.", 0, 0);
		Button.ENTER.waitForPressAndRelease();
		LCD.drawString("Connecting...", 0, 0);
		// Connect to base.
		base = comms.getConnection(baseName, true);
		LCD.clear(0);
		LCD.drawString("Connected!", 0, 0);

		while (base.isConnected()) {

			int i = 1;
			int pressed;
			Comms.Message msg;
			do {
				LCD.drawString("Send Message " + i + "?", 0, 4);
				pressed = Button.waitForAnyPress();

				if (pressed == Button.ID_ENTER) {
					LCD.clear(4);
					LCD.drawString("Sending...", 0, 4);
					msg = new Comms.Message("Hello #" + i++);
					base.send(msg);
					LCD.clear(4);
					LCD.drawString("Sent!", 0, 4);
					Button.ENTER.waitForPressAndRelease();
				}

			} while (pressed != Button.ID_ESCAPE);

			i = 1;
			do {
				LCD.drawString("Check Message " + i + "?", 0, 4);
				pressed = Button.waitForAnyPress();

				if (pressed == Button.ID_ENTER) {
					LCD.clear(4);
					LCD.drawString("Checking...", 0, 4);

					msg = base.receive();
					if (msg != null) {
						LCD.clear(4);
						LCD.drawString(msg.readAsString(), 0, 4);
						i++;
						Button.ENTER.waitForPressAndRelease();
					} else {
						LCD.clear(4);
						LCD.drawString("Nothing.", 0, 4);
						Button.ENTER.waitForPressAndRelease();
					}
				}

			} while (pressed != Button.ID_ESCAPE);
		}
	}

}
