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
		// Connect to base.
		base = comms.getConnection(baseName, true);
		
		while (base.isConnected()){
			Comms.Message msg;
			msg = base.receive();
			if (msg != null){
				LCD.clear();
				LCD.drawString(msg.readAsString(), 0, 0);
			}
		}
	}

	
	
}
