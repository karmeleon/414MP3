package shawntest;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.gstreamer.Bin;
import org.gstreamer.Buffer;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.elements.AppSink;
import org.gstreamer.elements.PlayBin2;

public class TestServer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Starting testserver on port 45001...");
		try {
			//ServerSocket srvr = new ServerSocket(45000);
			//srvr.setReuseAddress(true);
	        //Socket skt = srvr.accept();
	        //System.out.println("Server has connected!");
	        //PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	        startStreaming();
	        //out.print(data);
	        //out.close();
	        //skt.close();
	        //srvr.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void startStreaming() throws UnknownHostException, SocketException {
		final int port = 45001;
		// create the pipeline here
		Gst.init();
		final PlayBin2 playbin = new PlayBin2("VideoPlayer");
        playbin.setInputFile(new File("videos/beauty.mp4"));
        
        Bin bin = new Bin();
        
        Element encoder = ElementFactory.make("jpegenc", "encoder");
		Element payloader = ElementFactory.make("rtpjpegpay", "payloader");
		Element sink = ElementFactory.make("udpsink", "netsink");
		sink.set("host", "127.0.0.1");
		sink.set("port", "" + port);
		sink.set("sync", "true");
		bin.addMany(encoder, payloader, sink);
		Element.linkMany(encoder, payloader, sink);
		bin.addPad(new GhostPad("sink", encoder.getStaticPad("sink")));
        
        playbin.setVideoSink(bin);
        playbin.setState(State.PLAYING);
        Gst.main();
        playbin.setState(State.NULL);

	}

}
