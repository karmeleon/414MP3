package shawntest;

import java.util.Queue;

import org.gstreamer.Pipeline;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.swing.VideoComponent;

public class PreparedPipeline {
	// The pipeline object associated with this class.
	public Pipeline pipe;
	// The VideoComponent associated with this class.
	private VideoComponent videoOutput;
	public PlayBin2 playbin;
	
	/**
	 * Default constructor.
	 */
	public PreparedPipeline() {
		
	}
	
	/**
	 * Constructor.
	 * @param p The Pipeline to be attached to this object.
	 * @param v The VideoComponent to be attached to this object.
	 */
	public PreparedPipeline(Pipeline p, VideoComponent v) {
		setPipe(p);
		setVideoOutput(v);
	}
	
	public PreparedPipeline(Pipeline p, VideoComponent v, PlayBin2 b) {
		setPipe(p);
		setVideoOutput(v);
		playbin = b;
	}

	public Pipeline getPipe() {
		return pipe;
	}

	public void setPipe(Pipeline pipe) {
		this.pipe = pipe;
	}

	public VideoComponent getVideoOutput() {
		return videoOutput;
	}

	public void setVideoOutput(VideoComponent videoOutput) {
		this.videoOutput = videoOutput;
	}
}
