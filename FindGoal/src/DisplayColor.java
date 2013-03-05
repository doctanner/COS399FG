import lejos.nxt.Button;
import lejos.nxt.ColorSensor;
import lejos.nxt.ColorSensor.Color;
import lejos.nxt.LCD;
import lejos.nxt.SensorPort;

public class DisplayColor {

	private static final SensorPort SENSE_PORT = SensorPort.S3;
	private static final int FLOOD_COLOR = ColorSensor.BLUE;
	private static final ColorSensor sense = new ColorSensor(SENSE_PORT);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LCD.drawString("Color Code: ", 0, 0);
		LCD.drawString("R:", 0, 1);
		LCD.drawString("G:", 0, 2);
		LCD.drawString("B:", 0, 3);
		LCD.drawString("W:", 0, 4);

		while (Button.ESCAPE.isUp()) {
			Color colorVal = sense.getColor();
			LCD.drawInt(colorVal.getColor(), 2, 11, 0);
			LCD.drawInt(colorVal.getRed(), 3, 2, 1);
			LCD.drawInt(colorVal.getGreen(), 3, 2, 2);
			LCD.drawInt(colorVal.getBlue(), 3, 2, 3);
			LCD.drawInt(colorVal.getBackground(), 3, 2, 4);
		}
		
		int temp = Color.RED;

	}

}
