import java.io.*;
import java.util.ArrayList;

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

	public class MsgAgent extends Thread {
		Flag.Lock inLock = new Flag.Lock();
		Flag.Lock outLock = new Flag.Lock();

		private MsgAgent(Connection conn) {

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
						conn.ready.set(true);

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
		private boolean connected;
		private Flag.BoolFlag ready = new Flag.BoolFlag(false);
		private BTConnection btc;
		private String partner;
		private DataInputStream inStream;
		private DataOutputStream outStream;

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
				ready.set(true);
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
