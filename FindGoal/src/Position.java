public class Position implements Comms.Sendable<Position>{

	/** X-coordinate. */
	final int x;

	/** y-coordinate. */
	final int y;

	Position(int x, int y) {
		this.x = x;
		this.y = y;
	}

	@Override
	public byte getType() {
		return 1;
	}

	@Override
	public byte[] pack() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Position unpack(byte[] msg) {
		// TODO Auto-generated method stub
		return null;
	}

}
