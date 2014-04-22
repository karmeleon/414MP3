public class FrameInfo {
	private long frameTime;
	private int frameSize;
	
	public FrameInfo(long time, int size) {
		frameTime = time;
		frameSize = size;
	}
	
	public long getFrameTime() {
		return frameTime;
	}
	
	public long getFrameSize() {
		return frameSize;
	}
}
