package shawntest;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.swing.VideoComponent;

public class TestClient {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			//Socket skt = new Socket("localhost", 45000);
			//skt.setReuseAddress(true);
	        //BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        //System.out.print("Received string: '");
	
	        //while (!in.ready()) {}
	        //System.out.println(in.readLine()); // Read one line and output it
	
	        //System.out.print("'\n");
	        //in.close();
			startStreaming();
			//dumpUDP();
			Thread.sleep(50000);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private static void dumpUDP() {
		try {
		//BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		DatagramSocket clientSocket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName("127.0.0.1");
		//byte[] sendData = new byte[1024];
		byte[] receiveData = new byte[1024];
		//String sentence = inFromUser.readLine();
		//sendData = sentence.getBytes();
		//DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
		//clientSocket.send(sendPacket);
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length, IPAddress, 45001);
		clientSocket.receive(receivePacket);
		String modifiedSentence = new String(receivePacket.getData());
		System.out.println("FROM SERVER:" + modifiedSentence);
		clientSocket.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	/*
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
	 * CLIENT:
$ gst-launch-0.10 -v udpsrc uri="udp://127.0.0.1:5000" 
! "application/x-rtp, 
	media=(string)video, 
	clock-rate=(int)90000, 
	encoding-name=(string)JPEG, 
	payload=(int)96, 
	ssrc=(uint)2156703816, c
	lock-base=(uint)1678649553, 
	seqnum-base=(uint)31324" 
! rtpjpegdepay 
! jpegdec 
! ffmpegcolorspace 
! autovideosink
	 */
	private static void startStreaming() {
		Gst.init();
		final Pipeline pipe = new Pipeline("pipeline");
		final Element udpSrc = ElementFactory.make("udpsrc", "src");
		// in the real thing these'll just get sent over the control stream, but for now they're hardcoded
		udpSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)2156703816, clock-base=(uint)1678649553, seqnum-base=(uint)31324" ));
		udpSrc.set("uri", "udp://127.0.0.1:45001");
		final Element depay = ElementFactory.make("rtpjpegdepay", "depay");
		final Element decode = ElementFactory.make("jpegdec", "decode");
		final Element color = ElementFactory.make("ffmpegcolorspace", "color");
		//final Element sink = ElementFactory.make("autovideosink", "sink");
		
		SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                VideoComponent videoComponent = new VideoComponent();
                Element videosink = videoComponent.getElement();
                pipe.addMany(udpSrc, depay, decode, color, videosink);
                Element.linkMany(udpSrc, depay, decode, color, videosink);
                
                // Now create a JFrame to display the video output
                JFrame frame = new JFrame("Swing Video Test");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(videoComponent, BorderLayout.CENTER);
                videoComponent.setPreferredSize(new Dimension(720, 576));
                frame.pack();
                frame.setVisible(true);
                
                // Start the pipeline processing
                pipe.setState(State.PLAYING);
            }
        });
	}

}
