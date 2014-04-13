
public class CompareInfo {
	public long timeDiff;
	public long sizeSum;
	public CompareInfo(FrameInfo vf, FrameInfo af) {
		if (af != null) timeDiff = vf.getFrameTime() - af.getFrameTime();
		if (af != null) sizeSum = vf.getFrameSize() + af.getFrameSize();
		if (af == null) sizeSum = vf.getFrameSize();
	}
	
	public long getTimeDiff() {
		return timeDiff;
	}
}
