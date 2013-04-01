import java.io.*;
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

	private Flag.Lock connListLock = new Flag.Lock();
	private ArrayList<Connection> connList = new ArrayList<Connection>();

	/**
	 * 
	 */
	public Comms() {
		LCD.clear(1);
		LCD.drawString("Comms: OFFLINE", 0, 1);

		// Activate ConnectionListener
		new ConnectionListener().start();
	}

	public static void openDebugging() {
		// Debugging
		RConsole.openAny(0);
	}

	private void debugMsg(String msg) {
		if (RConsole.isOpen())
			RConsole.println(msg);
	}

	public Connection getConnection(String partner, boolean create) {
		// Search list for that connection.
		connListLock.acquire();
		Connection result = null;

		// Search for existing connection that matches.
		for (Connection conn : connList) {
			if (conn.partner.equalsIgnoreCase(partner)) {
				result = conn;
				break;
			}
		}

		// Release lock.
		connListLock.release();

		// Make a new connection if necessary.
		if (result == null && create) {
			// Make new connection.
			debugMsg("Creating new connection to " + partner);
			result = new Connection(partner);

			// Add it to the list, if it connected.
			if (result.isConnected()) {
				debugMsg("Connection made. Adding to list.");
				// Add this to list.
				connListLock.acquire();
				connList.add(result);
				connListLock.release();
			}

			else {
				debugMsg("Connection failed.");
				LCD.clear(1);
				LCD.clear(2);
				LCD.drawString("Comms: ERR", 0, 1);
				LCD.drawString("Connection Failed.", 0, 2);
			}
		}

		// Return whatever connection was found or created.
		return result;
	}

	public static class Message {
		static final byte TYPE_HANDSHAKE = -128;
		static final byte TYPE_BYTES = -127;
		static final byte TYPE_STRING = -126;
		static final byte TYPE_INT = -125;
		static final byte HIGHEST_TYPE = -125;

		private final byte[] msg;
		private final byte type;

		// Receiving:
		private Message(byte[] msg, byte type) {
			this.msg = msg;
			this.type = type;
		}

		// Packaging methods.
		public Message(byte[] message) {
			msg = message.clone();
			type = TYPE_BYTES;
		}

		public Message(String message) {
			msg = message.getBytes();
			type = TYPE_STRING;
		}

		public Message(int message) {
			// Convert to byte array:
			byte[] bytes = new byte[4];
			for (int i = 0; i < 4; i++)
				bytes[i] = (byte) (message >>> (i * 8));

			msg = bytes;
			type = TYPE_INT;
		}

		@SuppressWarnings("rawtypes")
		public Message(Sendable object) {
			type = object.getType();
			msg = object.pack();
		}

		// Unpackaging methods.

		public String readAsString() {
			return new String(msg);
		}

		public int readAsInt() {
			int result = 0;
			for (int i = 0; i < 4; i++) {
				int temp = (int) msg[0];
				result |= (temp << (i * 8));
			}
			return result;
		}

		@SuppressWarnings("rawtypes")
		public Object readObject(Sendable object) {
			// TODO Test object types.
			return object.unpack(msg);
		}
	}

	/**
	 * @author James Tanner
	 * 
	 */
	public interface Sendable<E> {

		/**
		 * All classes implementing the Sendable interface must have a type
		 * identifier. This must be unique among all types recognized by the
		 * system.
		 * <p>
		 * getType() should return the unique type ID for the implementing
		 * object type.
		 * 
		 * @return type constant.
		 */
		public byte getType();

		/**
		 * This method should pack the implementing object into a byte array to
		 * be messaged. It is used by the Message class and must be unpackable
		 * using {@link #unpack()};
		 * 
		 * @return byte array representing the object.
		 */
		public byte[] pack();

		/**
		 * This method should reverse the process from {@link #pack()} to return
		 * the original object.
		 * 
		 * @return Original object contained in a message.
		 */
		public E unpack(byte[] msg);

	}

	private class ConnectionListener extends Thread {
		public void run() {
			// Run as Daemon.
			this.setDaemon(true);

			LCD.clear(1);
			LCD.drawString("Comms: Online", 0, 1);

			// Listen for new connections forever.
			while (true) {
				// Wait for a connection
				BTConnection btc = Bluetooth.waitForConnection();
				Connection conn = new Connection(btc);

				// If connection made properly.
				if (conn.isConnected()) {
					try {
						// Get name from input stream.
						DataInputStream inStream = conn.btc.openDataInputStream();
						byte msgType = inStream.readByte();
						LCD.clear(1);
						LCD.clear(2);
						debugMsg("Incomming connection.");
						LCD.drawString("Comms: Connect", 0, 1);
						LCD.drawString("Incoming.", 0, 2);

						if (msgType != Message.TYPE_HANDSHAKE) {
							debugMsg("First message wasn't handshake.");
							conn.close();
							break;
							// TODO Handle non-handshake message more
							// gracefully.
						}
						byte nameSize = inStream.readByte();
						LCD.clear(2);
						LCD.drawString("Getting Name", 0, 2);
						byte[] nameArr = new byte[nameSize];
						LCD.clear(2);
						LCD.drawString("Adding", 0, 2);
						inStream.read(nameArr);
						conn.partner = new String(nameArr);
						debugMsg("Connection reported as coming from" + conn.partner);

						// Add this to list.
						connListLock.acquire();
						connList.add(conn);
						connListLock.release();

						// Start receiving on connection.
						LCD.clear(2);
						debugMsg("Starting message handler.");
						LCD.drawString("Starting", 0, 2);
						conn.start();

					} catch (IOException e) {
						LCD.clear(1);
						LCD.clear(2);
						LCD.drawString("Comms: ERR", 0, 1);
						LCD.drawString("ERR: ConListen", 0, 2);
						Button.waitForAnyPress();
						System.exit(0);
					}
				}

				LCD.clear(1);
				LCD.clear(2);
				LCD.drawString("Comms: Online", 0, 1);
			}
		}
	}

	public class Connection extends Thread {
		// Connection objects:
		boolean connected;
		BTConnection btc;
		String partner;

		// In-stream objects:
		Flag.Lock queueLock = new Flag.Lock();
		Queue<Message> msgQueue = new Queue<Message>();

		private Connection(String target) {
			this.setDaemon(true);

			// Attempt to connect.
			connected = connect();
		}

		private Connection(BTConnection btcIn) {
			this.setDaemon(true);

			// Create connection from incoming request.
			if (btcIn != null) {
				btc = btcIn;
				connected = true;
			}
		}

		public boolean send(Message packet) {

			debugMsg("\nsend():");

			// If connection isn't open, fail.
			if (!isConnected()) {
				LCD.clear(4);
				LCD.drawString("Not Connected.", 0, 4);
				return false;
			}
			// TODO Make send non-blocking!!

			// Try to send message.
			boolean success;
			try {
				debugMsg("Opening new stream.");
				DataOutputStream outStream = btc.openDataOutputStream();
				
				// Write message to stream.
				debugMsg("Sending requested message.");
				debugMsg("Sending type...");
				outStream.writeByte(packet.type);

				debugMsg("Sending length...");
				outStream.write(packet.msg.length);

				debugMsg("Sending message...");
				outStream.write(packet.msg);

				debugMsg("Flushing...");
				debugMsg("Bytes: " + outStream.size());
				outStream.flush();

				debugMsg("Sent!");
				success = true;
				
				debugMsg("Closing stream.");
				outStream.close();
			}

			// Failed to send message:
			catch (IOException e) {
				if (RConsole.isOpen()) {
					RConsole.println(e.toString());
					e.printStackTrace(RConsole.getPrintStream());
				}
				LCD.clear(4);
				LCD.drawString("IOException!", 0, 4);
				success = false;
				// TODO Handle send failure.
			}

			return success;
		}

		public Message receive() {
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
			
			DataInputStream inStream = btc.openDataInputStream();
			LCD.drawString("Con: " + partner, 0, 6);
			while (!this.isInterrupted()) {
				try {
					byte msgType = inStream.readByte();
					byte msgSize = inStream.readByte();
					byte[] msgArr = new byte[msgSize];
					inStream.read(msgArr);

					LCD.clear(6);
					LCD.drawString("Con: Message!", 0, 6);
					queueLock.acquire();
					msgQueue.push(new Message(msgArr, msgType));
					queueLock.release();

				} catch (IOException e) {
					// TODO Handle receive failure.
				}
			}
			
			// Attempt to close input stream.
			try {
				inStream.close();
			} catch (IOException e) {
				// Just continue.
			}
			LCD.clear(6);
		}

		public boolean isConnected() {
			if (btc == null)
				return false;
			return connected;
		}

		public void close() {
			this.interrupt();
			connected = false;
			btc.close();
			btc = null;
			connListLock.acquire();
			connList.remove(this);
			connListLock.release();
		}

		private boolean connect() {

			RemoteDevice btrd = selectPartner();

			LCD.clear(1);
			LCD.clear(2);
			LCD.drawString("Comms: Connect", 0, 1);
			LCD.drawString("Outgoing.", 0, 2);

			// Get RemoteObject, if paired.
			// RemoteDevice btrd = Bluetooth.getKnownDevice(partner);
			partner = btrd.getFriendlyName(false);
			debugMsg("Actually connecting to " + partner);
			// if (btrd == null) {
			// LCD.clear(1);
			// LCD.clear(2);
			// LCD.drawString("Comms: ERR", 0, 1);
			// LCD.drawString("Bad partner", 0, 2);
			// Button.waitForAnyPress();
			// return false;
			// }
			// Attempt to connect to RemoteObject
			btc = Bluetooth.connect(btrd);
			if (btc == null) {
				debugMsg("Could not connect to device");
				LCD.clear(1);
				LCD.clear(2);
				LCD.drawString("Comms: ERR", 0, 1);
				LCD.drawString("No Connection", 0, 2);
				Button.waitForAnyPress();
				return false;
			}

			// Open output stream.
			debugMsg("Opening output stream.");
			DataOutputStream outStream = btc.openDataOutputStream();

			debugMsg("Sending handshake.");
			LCD.drawString("Handshake...", 0, 2);

			// Send friendly name.
			try {
				String localName = Bluetooth.getFriendlyName();
				outStream.writeByte(Message.TYPE_HANDSHAKE);
				outStream.writeByte(localName.length());
				outStream.write(localName.getBytes());
				outStream.flush();
				debugMsg("Handshake complete.");
				
				debugMsg("Test sending second message.");
				String hi ="Handshake Test";
				outStream.write(Message.TYPE_STRING);
				outStream.write(hi.length());
				outStream.write(hi.getBytes());
				outStream.flush();
				debugMsg("Second message sent");
				
				debugMsg("Closing output stream.");
				outStream.close();
				
			} catch (IOException e) {
				debugMsg("Handshake failed");
				return false;
			}

			// Connection established. Listen.
			debugMsg("Starting inbound message handler.");
			this.start();

			// Return
			LCD.clear(1);
			LCD.clear(2);
			LCD.drawString("Comms: Online", 0, 1);
			return true;
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
