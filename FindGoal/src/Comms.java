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

	public static class Message {
		static final byte TYPE_KEEP_ALIVE = -128;
		static final byte TYPE_HANDSHAKE = -127;
		static final byte TYPE_BYTES = -126;
		static final byte TYPE_STRING = -125;
		static final byte TYPE_INT = -124;
		static final byte HIGHEST_TYPE = -124;

		private final byte[] msg;
		private final byte type;

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
		btc.close();
		btc = null;
	}

	public boolean connect() {
		// TODO Fix to work by pre-coded name.
		RemoteDevice btrd = selectPartner();

		LCD.clear(1);
		LCD.clear(2);
		LCD.drawString("Comms: Connect", 0, 1);
		LCD.drawString("Outbound.", 0, 2);

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
	
	public boolean listen(){
		if (isConnected()) return false;
		
		debugMsg("Listening for connection...");
		btc = Bluetooth.waitForConnection();
		if (btc == null) return false;

		debugMsg("Found connection...");
		int size = btc.available();
		while (size < 1){
			Thread.yield();
			size = btc.available();
		}

		debugMsg("Recieved message...");
		byte[] envelope = new byte[size];
		btc.read(envelope, size);
		Message msg = Message.unpack(envelope);
		
		if (msg.type != Message.TYPE_HANDSHAKE){
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
			byte[] keepAlive = { Message.TYPE_HANDSHAKE };

			debugMsg("Starting outbound message handler.");
			while (!interrupted()) {

				// If no messages, send keep-alive.
				if (msgQueue.empty()) {
					if (btc.write(keepAlive, 1) < 0) {
						debugMsg("Failed to send keepAlive.");
						close();
					}
				}

				// Otherwise, send the message.
				else {
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
