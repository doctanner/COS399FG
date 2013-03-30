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
	Flag.Lock listLock = new Flag.Lock();
	ArrayList<Connection> connections = new ArrayList<Connection>();

	/**
	 * 
	 */
	public Comms() {
		// Activate ConnectionListener
		new ConnectionListener().start();
	}

	public class Message {
		final byte[] msg;

		private Message(byte[] newMessage) {
			msg = newMessage;
		}
	}

	public class MsgAgent extends Thread {
		Connection conn;
		Flag.Lock queueLock = new Flag.Lock();
		Queue<Message> msgQueue = new Queue<Message>();

		private MsgAgent(Connection conn) {
			if (conn.hasMsgAgentBoolFlag.getAndSet(true))
				throw new RuntimeException("Message agent already attached");
			
			this.conn = conn;
			this.setDaemon(true);
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

		public boolean send(byte[] msg) {
			// If connection isn't open, fail.
			if (!conn.isConnected())
				return false;

			// Get lock on the connection.
			conn.lock.acquire();
			// TODO Make send non-blocking!!

			// Try to send message.
			try {
				// Write message to stream.
				conn.outStream.writeByte(msg.length);
				conn.outStream.write(msg);
				conn.outStream.flush();

				// Release lock and report success.
				conn.lock.release();
				return true;
			}

			// Failed to send message:
			catch (IOException e) {
				// Release lock and report fail.
				conn.lock.release();
				return false;
			}
		}

		public void run() {
			while (true) {
				try {
					byte msgSize = conn.inStream.readByte();
					byte[] msgArr = new byte [msgSize];
					conn.inStream.read(msgArr);
					
					queueLock.acquire();
					msgQueue.push(new Message(msgArr));
					queueLock.release();
					
				} catch (IOException e) {
					// TODO Close message handler.
				}
			}
		}
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
						byte nameSize = conn.inStream.readByte();
						byte[] nameArr = new byte[nameSize];
						conn.inStream.read(nameArr);
						conn.partner = String.valueOf(nameArr);

						// Add the connection to the connections list.
						listLock.acquire();
						connections.add(conn);
						listLock.release();

					} catch (IOException e) {
						conn.close();
					}
				}
			}
		}
	}

	private class Connection {
		boolean connected;
		final Flag.Lock lock = new Flag.Lock();
		final Flag.BoolFlag hasMsgAgentBoolFlag = new Flag.BoolFlag(false);
		BTConnection btc;
		String partner;
		DataInputStream inStream;
		DataOutputStream outStream;

		private Connection(String target) {
			// Attempt to connect.
			if (connect()) {

				// Open io streams.
				openStreams();

				// Send friendly name.
				try {
					String localName = Bluetooth.getFriendlyName();
					outStream.writeByte(localName.length());
					outStream.write(localName.getBytes());
					outStream.flush();
				} catch (IOException e) {
					connected = false;
					return;
				}

				// Connection successful.
				connected = true;
			}

			// Connection failed
			else
				connected = false;
		}

		private Connection(BTConnection btcIn) {
			// Create connection from incoming request.
			if (btcIn != null) {
				btc = btcIn;
				openStreams();
				connected = true;
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

			// Connection established.
			return true;
		}

		private void close() {
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
