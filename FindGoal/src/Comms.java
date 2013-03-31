import java.io.*;
import java.util.ArrayList;
import java.util.Queue;

import javax.bluetooth.RemoteDevice;

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
		// Activate ConnectionListener
		new ConnectionListener().start();
	}

	public Connection getConnection(String partner, boolean create) {
		// Search list for that connection.
		connListLock.acquire();
		Connection result = null;

		// Search for existing connection that matches.
		for (Connection conn : connList) {
			if (conn.partner.equals(partner)) {
				result = conn;
				break;
			}
		}

		// Release lock.
		connListLock.release();

		// Make a new connection if necessary.
		if (result == null && create) {
			// Make new connection.
			result = new Connection(partner);

			// Add it to the list, if it connected.
			if (result.isConnected()) {
				// Add this to list.
				connListLock.acquire();
				connList.add(result);
				connListLock.release();
			}

			else
				result.close();
		}

		// Return whatever connection was found or created.
		return result;
	}

	public class Message {
		static final byte TYPE_HANDSHAKE = -128;
		static final byte TYPE_BYTES = -127;
		static final byte TYPE_STRING = -126;
		static final byte TYPE_INT = -125;
		static final byte HIGHEST_TYPE = -125;

		private final byte[] msg;
		private final byte type;

		// Recieving:
		private Message(byte[] msg, byte type){
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
		
		public Message (Sendable object){
			type = object.getType();
			msg = object.pack();
		}

		// Unpackaging methods.

		public String readAsString() {
			return String.valueOf(msg);
		}

		public int readAsInt() {
			int result = 0;
			for (int i = 0; i < 4; i++) {
				int temp = (int) msg[0];
				result |= (temp << (i * 8));
			}
			return result;
		}
		
		public Object readObject(Sendable object){
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

			// Listen for new connections forever.
			while (true) {
				// Wait for a connection
				BTConnection btc = Bluetooth.waitForConnection();
				Connection conn = new Connection(btc);

				// If connection made properly.
				if (conn.isConnected()) {
					try {
						// Get name from input stream.
						byte msgType = conn.inStream.readByte();
						if (msgType != Message.TYPE_HANDSHAKE){
							conn.close();
							break;
							// TODO Handle non-handshake message more gracefully.
						}
						byte nameSize = conn.inStream.readByte();
						byte[] nameArr = new byte[nameSize];
						conn.inStream.read(nameArr);
						conn.partner = String.valueOf(nameArr);

						// Add this to list.
						connListLock.acquire();
						connList.add(conn);
						connListLock.release();

					} catch (IOException e) {
						conn.close();
					}
				}
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
		DataInputStream inStream;

		// Out-stream objects:
		final Flag.Lock outLock = new Flag.Lock();
		DataOutputStream outStream;

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
				openStreams();
				connected = true;
			}
		}

		public boolean send(Message packet) {
			// If connection isn't open, fail.
			if (isConnected())
				return false;

			// Get lock on the connection.
			outLock.acquire();
			// TODO Make send non-blocking!!

			// Try to send message.
			boolean success;
			try {
				// Write message to stream.
				outStream.writeByte(packet.type);
				outStream.writeByte(packet.msg.length);
				outStream.write(packet.msg);
				outStream.flush();
				success = true;
			}

			// Failed to send message:
			catch (IOException e) {
				success = false;
			}

			outLock.release();
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
			while (!this.isInterrupted()) {
				try {
					byte msgType = inStream.readByte();
					byte msgSize = inStream.readByte();
					byte[] msgArr = new byte[msgSize];
					inStream.read(msgArr);

					queueLock.acquire();
					msgQueue.push(new Message(msgArr, msgType));
					queueLock.release();

				} catch (IOException e) {
					// TODO Handle receive failure.
				}
			}
		}

		public boolean isConnected() {
			if (btc == null)
				return false;
			return connected;
		}

		private boolean connect() {
			// Get RemoteObject, if paired.
			RemoteDevice btrd = Bluetooth.getKnownDevice(partner);
			if (btrd == null)
				return false;

			// Attempt to connect to RemoteObject
			btc = Bluetooth.connect(btrd);
			if (btc == null)
				return false;
			
			// Open io streams.
			openStreams();

			// Send friendly name.
			try {
				String localName = Bluetooth.getFriendlyName();
				outStream.writeByte(Message.TYPE_HANDSHAKE);
				outStream.writeByte(localName.length());
				outStream.write(localName.getBytes());
				outStream.flush();
			} catch (IOException e) {
				return false;
			}
			
			// Connection established. Listen.
			this.start();
			return true;
		}

		private void close() {
			this.interrupt();
			connected = false;
			btc.close();
			inStream = null;
			outStream = null;
			btc = null;
		}

		private void openStreams() {
			inStream = btc.openDataInputStream();
			outStream = btc.openDataOutputStream();
		}
	}
}
