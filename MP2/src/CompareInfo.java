
public class CompareInfo {
	public long timeDiff;
	public CompareInfo(FrameInfo vf, FrameInfo af) {
		timeDiff = vf.getFrameTime() - af.getFrameTime();
	}
	
	public long getTimeDiff() {
		return timeDiff;
	}
}
