import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.swing.VideoComponent;
import org.json.JSONArray;
import org.json.JSONObject;

public class Client {

	/**
	 * @param args
	 */
	
	static PrintWriter out;
	
	public static void startClient(Element videoSink, String settings) {
		// TODO Auto-generated method stub
		try {
			Socket skt = new Socket("localhost", 45000);
			skt.setReuseAddress(true);
	        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        out = new PrintWriter(skt.getOutputStream(), true);
	      
	        JSONObject response = new JSONObject();
	        response.put("request", settings);
	        out.println(response.toString());

	        startStreaming(videoSink, settings);
	        
	        // System.out.println("Listening for commands. Known commands include play, pause, and stop.");
	        String line;
	        Scanner s = new Scanner(System.in);
	        while(true) {
	        	line = s.nextLine();
	        	response = new JSONObject();
	        	response.put("command", line);
	        	out.println(response.toString());
	        	if(line.equals("stop"))
	        		break;
	        }
	        
	        in.close();
	        out.close();
	        skt.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private static void startStreaming(final Element videoSink, String settings) {
		String[] s = settings.split(" ");
		String resolution = s[0];               // 240p/480p
		String attribute = s[1];                // Passive/Active
		int bandwidth = Integer.parseInt(s[2]); // Some amount
		
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
		
		pipe.addMany(udpSrc, depay, decode, color, videoSink);
        Element.linkMany(udpSrc, depay, decode, color, videoSink);
		
        if (attribute.equalsIgnoreCase("active")) {
        	// audio caps string is application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704
    		Element udpAudSrc = ElementFactory.make("udpsrc", "src2");
    		udpAudSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
    		udpAudSrc.set("uri", "udp://127.0.0.1:45002");
    		Element audDepay = ElementFactory.make("rtpL16depay", "auddepay");
    		Element audSink = ElementFactory.make("autoaudiosink", "audsink");
    		pipe.addMany(udpAudSrc, audDepay, audSink);
    		Element.linkMany(udpAudSrc, audDepay, audSink);
        }

		Thread videoThread = new Thread() {
			public void run() {
	             pipe.setState(org.gstreamer.State.PLAYING);
			}
		};
		videoThread.start();
	}
	
	public static void updateResource() {
		int bandwidth = 0;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("resource.txt"));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			bandwidth = Integer.parseInt(br.readLine());
		} catch (NumberFormatException e) {
			// pushLog("> RSRC: BAD VALUE");
		} catch (IOException e) {
			// pushLog("> RSRC: BAD VALUE");
		}
		try {
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("RSRC: " + bandwidth);
		JSONObject response = new JSONObject();
        response.put("bandwidth", "" + bandwidth);
        out.println(response.toString());
	}

}
