package shawntest;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.swing.VideoComponent;

public class TestServer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Starting testserver on port 45000...");
		try {
			//ServerSocket srvr = new ServerSocket(45000);
			//srvr.setReuseAddress(true);
	        //Socket skt = srvr.accept();
	        //System.out.println("Server has connected!");
	        //PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	        startStreaming();
	        Thread.sleep(50000);
	        //out.print(data);
	        //out.close();
	        //skt.close();
	        //srvr.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
/*
 * 
 * Pipeline pipe = new Pipeline("pipeline");
		Element videoSrc = ElementFactory.make("v4l2src", "source");
		Element videoFilter = ElementFactory.make("capsfilter", "filter");
		videoFilter.setCaps(Caps.fromString("video/x-raw-yuv,height=" + height
				+ ",width=" + width + ",framerate=" + framerate + "/1"));

		VideoComponent vc = new VideoComponent();
		Element videoSink = vc.getElement();
		vc.setPreferredSize(new Dimension(height, width));

		pipe.addMany(videoSrc, videoFilter, videoSink);
		Element.linkMany(videoSrc, videoFilter, videoSink);

		return new PreparedPipeline(pipe, vc);
		SERVER:
		$ gst-launch-0.10 -v filesrc location="beauty.mp4" 
		! decodebin2 
		! queue 
		! jpegenc 
		! rtpjpegpay 
		! udpsink host=127.0.0.1 port=5000 sync=true
 */
	
	
	private static void startStreaming() {
		// create the pipeline here
		Gst.init();
		Pipeline pipe = new Pipeline("pipeline");
		Element fileSrc = ElementFactory.make("filesrc", "source");
		fileSrc.set("location", "videos/beauty.mp4");
		Element decoder = ElementFactory.make("decodebin2", "decoder");
		Element queue = ElementFactory.make("queue", "queue");
		Element encoder = ElementFactory.make("jpegenc", "encoder");
		Element payloader = ElementFactory.make("rtpjpegpay", "payloader");
		Element sink = ElementFactory.make("udpsink", "sink");
		sink.set("host", "127.0.0.1");
		sink.set("port", "45001");
		sink.set("sync", "true");
		pipe.addMany(fileSrc, decoder, queue, encoder, payloader, sink);
		Element.linkMany(fileSrc, decoder, queue, encoder, payloader, sink);
		
		pipe.setState(State.PLAYING);
	}

}
