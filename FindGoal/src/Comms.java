import java.util.ArrayList;
import java.util.Queue;

import javax.bluetooth.RemoteDevice;

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.comm.*;

/**
 * James Tanner <br>
 * COS 399 - Programming Autonomous Robots <p>
 * 
 * This class provides communications between two or more NXT bricks.
 */

/**
 * @author James Tanner
 * 
 */
public class Comms {

	// Connection objects:
	boolean connected;
	BTConnection btc;
	String partner;

	// In-stream objects:
	Flag.Lock queueLock = new Flag.Lock();
	Queue<Message> msgQueue = new Queue<Message>();

	// Message handlers:
	inboundHandler in;
	outboundHandler out;

	/**
	 * 
	 */
	public Comms() {
		LCD.clear(1);
		LCD.drawString("Comms: OFFLINE", 0, 1);
	}

	public static void openDebugging() {
		// Debugging
		RConsole.openAny(0);
		System.setErr(RConsole.getPrintStream());
	}

	private void debugMsg(String msg) {
		if (RConsole.isOpen())
			RConsole.println(msg);
	}

	public static byte[] intToBytes(int val) {
		// Convert to byte array:
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++)
			bytes[i] = (byte) (val >>> (i * 8));
		return bytes;
	}

	public static int bytesToInt(byte[] val) {
		int result = 0;
		for (int i = 0; i < 4; i++) {
			int temp = (int) val[i];
			result |= (temp << (i * 8));
		}
		return result;
	}

	public static class Message {
		static private final byte TYPE_KEEP_ALIVE = -128;
		static private final byte TYPE_HANDSHAKE = -127;
		static public final byte TYPE_BYTES = -126;
		static public final byte TYPE_STRING = -125;
		static public final byte TYPE_INT = -124;
		static public final byte TYPE_COMMAND = -123;
		static public final byte HIGHEST_TYPE = -123;

		private final byte[] msg;
		public final byte type;

		// Sending
		private byte[] pack() {
			int size = msg.length;
			byte[] envelope = new byte[size + 1];
			envelope[0] = type;
			System.arraycopy(msg, 0, envelope, 1, size);
			return envelope;
		}

		// Receiving:
		private static Message unpack(byte[] envelope) {
			byte type = envelope[0];
			int size = envelope.length - 1;
			byte[] msg = new byte[size];
			System.arraycopy(envelope, 1, msg, 0, size);
			return new Message(msg, type);
		}

		// Constructors:

		public Message(byte[] msg, byte type) {
			this.msg = msg;
			this.type = type;
		}

		public Message(byte[] message) {
			msg = message.clone();
			type = TYPE_BYTES;
		}

		public Message(String message) {
			msg = message.getBytes();
			type = TYPE_STRING;
		}

		public Message(int message) {
			msg = intToBytes(message);
			type = TYPE_INT;
		}

		public Message(Command command) {
			int size = command.value.length + 1;
			msg = new byte[size];
			msg[0] = command.type;
			if (size > 1)
				System.arraycopy(command.value, 0, msg, 1, size - 1);

			type = TYPE_COMMAND;
		}

		// Accessors:

		public String readAsString() {
			return new String(msg);
		}

		public int readAsInt() {
			return bytesToInt(msg);
		}

		public Command readAsCommand() {
			byte type = msg[0];
			int size = msg.length - 1;
			byte[] val = new byte[size];
			if (size > 0) {
				System.arraycopy(msg, 1, val, 0, size);
			}
			return new Command(type, val);
		}

		public byte[] readRaw() {
			return msg;
		}
	}

	public static class Command {

		public final static byte CMD_TERM = -128;
		public final static byte CMD_HALT = -127;
		public final static byte CMD_START = -126;
		public final static byte CMD_ROTATE = -125;
		public final static byte CMD_FIRE = -124;

		public final byte type;
		public final byte[] value;

		/**
		 * 
		 */
		public Command(byte type, byte[] value) {
			this.type = type;
			this.value = value;
		}

	}

	private void startHandlers() {
		in = new inboundHandler();
		out = new outboundHandler();
		in.start();
		out.start();
	}

	public boolean send(Message packet) {
		if (out == null)
			return false;
		out.pushMessage(packet);
		return true;
	}

	public Message receive() {
		if (in == null)
			return null;
		return in.popMessage();
	}

	public boolean isConnected() {
		if (btc == null)
			return false;
		return connected;
	}

	public void close() {
		connected = false;
		if (in != null) {
			in.interrupt();
			in = null;
		}
		if (out != null) {
			out.interrupt();
			out = null;
		}
		try{
		btc.close();
		} catch (Exception e){
			// Do nothing;
		}
		btc = null;
	}

	public boolean connect(String partner) {
		// TODO Fix to work by pre-coded name.
		// RemoteDevice btrd = selectPartner();
		RemoteDevice btrd = Bluetooth.getKnownDevice(partner);

		LCD.clear(1);
		LCD.clear(2);
		LCD.drawString("Comms: Connect", 0, 1);
		LCD.drawString("Outbound.", 0, 2);

		// Check partner.
		if (btrd == null) {
			debugMsg("Could not connect to device");
			LCD.clear(1);
			LCD.clear(2);
			LCD.drawString("Comms: ERR", 0, 1);
			LCD.drawString("Bad Partner", 0, 2);
			Button.waitForAnyPress();
			btrd = selectPartner();
		}

		// Get friendly name of partner.
		partner = btrd.getFriendlyName(false);
		debugMsg("Actually connecting to " + partner);

		// Attempt to connect to RemoteObject
		btc = Bluetooth.connect(partner, NXTConnection.PACKET);
		if (btc == null) {
			debugMsg("Could not connect to device");
			LCD.clear(1);
			LCD.clear(2);
			LCD.drawString("Comms: ERR", 0, 1);
			LCD.drawString("No Connection", 0, 2);
			Button.waitForAnyPress();
			return false;
		}

		debugMsg("Generating Handshake...");
		byte[] fname = Bluetooth.getFriendlyName().getBytes();
		int size = fname.length;
		byte[] envelope = new byte[size + 1];
		envelope[0] = Message.TYPE_HANDSHAKE;
		System.arraycopy(fname, 0, envelope, 1, size);

		debugMsg("Sending Handshake...");
		int wrote = btc.write(envelope, size + 1);

		if (wrote < 0) {
			debugMsg("Handshake Failed: " + wrote);
			LCD.clear(1);
			LCD.clear(2);
			LCD.drawString("Comms: ERR", 0, 1);
			LCD.drawString("Handshake Fail", 0, 2);
			return false;
		}

		// Return
		LCD.clear(1);
		LCD.clear(2);
		LCD.drawString("Comms: Online", 0, 1);
		debugMsg("Connected!");
		connected = true;
		startHandlers();

		// TODO REMOVE
		// Send test message.
		debugMsg("Sending test message...");
		Message msg = new Message("Test message");
		send(msg);

		return true;
	}

	public boolean listen() {
		if (isConnected())
			return false;

		debugMsg("Listening for connection...");
		btc = Bluetooth.waitForConnection();
		if (btc == null)
			return false;

		debugMsg("Found connection...");
		int size = btc.available();
		while (size < 1) {
			Thread.yield();
			size = btc.available();
		}

		debugMsg("Recieved message...");
		byte[] envelope = new byte[size];
		btc.read(envelope, size);
		Message msg = Message.unpack(envelope);

		if (msg.type != Message.TYPE_HANDSHAKE) {
			debugMsg("Message wasn't handshake.");
			btc.close();
			btc = null;
			return false;
		}

		debugMsg("Handshake accepted.");
		partner = msg.readAsString();
		debugMsg("Connected to " + partner);
		startHandlers();
		connected = true;
		LCD.clear(1);
		LCD.drawString("Comms: Online", 0, 1);
		return true;
	}

	private class inboundHandler extends Thread {
		Queue<Message> msgQueue = new Queue<Message>();
		Flag.Lock queueLock = new Flag.Lock();

		private Message popMessage() {
			// If no messages, return null.
			if (msgQueue.isEmpty())
				return null;

			// Otherwise, return next message.
			queueLock.acquire();
			Message msg = (Message) msgQueue.pop();
			queueLock.release();
			return msg;
		}

		public void run() {
			setDaemon(true);
			byte[] envelope;
			int size;

			debugMsg("Starting inbound message handler.");
			while (!interrupted()) {
				// Wait for there to be something to read.
				size = btc.available(2);
				if (size > 0) {
					envelope = new byte[size];
					debugMsg("Message incoming...");
					int read = btc.read(envelope, size);

					if (read < 0) {
						debugMsg("Read Error: " + read);
						continue;
					}

					if (envelope[0] == Message.TYPE_KEEP_ALIVE) {
						debugMsg("Keepalive recieved.");
						continue;
					}

					debugMsg("Message recieved. Unpacking...");
					Message msg = Message.unpack(envelope);

					if (msg.type == Message.TYPE_HANDSHAKE) {
						debugMsg("Message was handshake. Updating friendly name...");
						partner = msg.readAsString();
					} else {
						debugMsg("Message unpacked. Adding to list...");
						queueLock.acquire();
						msgQueue.push(msg);
						queueLock.release();
					}
					debugMsg("Done.");
				}

				Thread.yield();
			}
		}
	}

	private class outboundHandler extends Thread {
		Queue<Message> msgQueue = new Queue<Message>();
		Flag.Lock queueLock = new Flag.Lock();

		private void pushMessage(Message msg) {
			queueLock.acquire();
			msgQueue.push(msg);
			queueLock.release();
		}

		public void run() {
			setDaemon(true);

			debugMsg("Starting outbound message handler.");
			while (!interrupted()) {

				// If no messages, send keep-alive.
				if (!msgQueue.empty()) {
					debugMsg("\nMessage in queue. Sending...");

					// Pop message.
					queueLock.acquire();
					Message msg = (Message) msgQueue.pop();
					queueLock.release();

					// Write message to stream.
					debugMsg("Packing message...");
					byte[] envelope = msg.pack();
					int size = envelope.length;

					debugMsg("Sending message...");
					int wrote = btc.write(envelope, size);

					if (wrote != size) {
						debugMsg("Write Error: Wrote " + wrote + " of " + size
								+ " bytes.");
					} else if (wrote < 0) {
						debugMsg("Write Error: " + wrote);
					}
				}

				Thread.yield();
			}
		}
	}

	public static RemoteDevice selectPartner() {
		LCD.clear(4);
		LCD.clear(5);
		LCD.drawString("Connect To:", 0, 4);
		ArrayList<RemoteDevice> known = Bluetooth.getKnownDevicesList();
		if (known.size() == 0) {
			LCD.drawString("**NONE**", 0, 5);
			Button.ENTER.waitForPressAndRelease();
			return null;
		}

		int index = 0;
		while (true) {
			LCD.clear(5);
			LCD.drawString(known.get(index).getFriendlyName(false), 0, 5);
			int button = Button.waitForAnyPress();
			if (button == Button.ID_ENTER) {
				LCD.clear(4);
				LCD.clear(5);
				return known.get(index);
			}

			if (button == Button.ID_LEFT) {
				index--;
				if (index < 0)
					index = known.size() - 1;
			}

			else if (button == Button.ID_RIGHT) {
				index++;
				if (index >= known.size())
					index = 0;
			}
		}
	}
}