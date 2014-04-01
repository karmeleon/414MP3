package shawntest;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
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

public class TestClient {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			Socket skt = new Socket("localhost", 45000);
			skt.setReuseAddress(true);
	        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	
	        JSONObject response = new JSONObject(in.readLine());
	        System.out.println("Successfully connected to server at 127.0.0.1:45000. Available files:");
	        JSONArray files = response.getJSONArray("files");
	        for(int i = 0; i < files.length(); i++)
	        	System.out.println(i + ": " + files.getString(i));
	        System.out.println("Which file would you like to play?");
	        Scanner s = new Scanner(System.in);
	        response = new JSONObject();
	        response.put("request", files.getString(s.nextInt()));
	        out.println(response.toString());

	        startStreaming();
	        
	        //send commands
	        System.out.println("Listening for commands. Known commands include play, pause, and stop.");
	        String line;
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
		
		// audio caps string is application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704
		Element udpAudSrc = ElementFactory.make("udpsrc", "src2");
		udpAudSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
		udpAudSrc.set("uri", "udp://127.0.0.1:45002");
		Element audDepay = ElementFactory.make("rtpL16depay", "auddepay");
		Element audSink = ElementFactory.make("autoaudiosink", "audsink");
		
		pipe.addMany(udpAudSrc, audDepay, audSink);
		Element.linkMany(udpAudSrc, audDepay, audSink);
		
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
