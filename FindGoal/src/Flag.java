public class Flag {

	public Flag() {
		// TODO Auto-generated constructor stub
	}

	public static class BoolFlag {
		boolean value;

		public BoolFlag(boolean value) {
			this.value = value;
		}

		public synchronized boolean get() {
			return value;
		}
		
		public synchronized void set(boolean value) {
			this.value = value;
		}

		public synchronized boolean getAndSet(boolean newValue) {
			boolean oldValue = value;
			value = newValue;
			return oldValue;
		}
	}

}
