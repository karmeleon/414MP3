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

import org.gstreamer.Bin;
import org.gstreamer.Bus;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.elements.good.RTPBin;
import org.gstreamer.swing.VideoComponent;
import org.gstreamer.utils.GstDebugUtils;
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
		
		final int port = 45001;
		
		final Pipeline pipe = new Pipeline("pipeline");
		// in the real thing these'll just get sent over the control stream, but for now they're hardcoded
		
		
		// UDP
		
		// VIDEO
		Element udpVideoSrc = ElementFactory.make("udpsrc", "src1");
		udpVideoSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)2156703816, clock-base=(uint)1678649553, seqnum-base=(uint)31324" ));
		udpVideoSrc.set("uri", "udp://127.0.0.1:" + port);
		
		Element videoRtcpIn = ElementFactory.make("udpsrc", "src3");
		videoRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 1));
		
		Element videoRtcpOut = ElementFactory.make("udpsink", "snk1");
		videoRtcpOut.set("host", "127.0.0.1");
		videoRtcpOut.set("port", "" + (port + 5));
		videoRtcpOut.set("sync", "false");
		videoRtcpOut.set("async", "false");
		
		System.out.println("Video udp ports init'd");
		
		// AUDIO
		Element udpAudioSrc = ElementFactory.make("udpsrc", "src2");
		udpAudioSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
		udpAudioSrc.set("uri", "udp://127.0.0.1:" + (port + 2));
		
		Element audioRtcpIn = ElementFactory.make("udpsrc", "src4");
		audioRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 3));
		
		Element audioRtcpOut = ElementFactory.make("udpsink", "snk2");
		audioRtcpOut.set("host", "127.0.0.1");
		audioRtcpOut.set("port", "" + (port + 7));
		audioRtcpOut.set("sync", "false");
		audioRtcpOut.set("async", "false");
		
		System.out.println("Audio udp ports init'd");
		
		pipe.addMany(udpVideoSrc, udpAudioSrc, videoRtcpIn, audioRtcpIn, videoRtcpOut, audioRtcpOut);
		
		// VIDEO BIN
		
		final Bin videoBin = new Bin("videoBin");
		
		final Element videoDepay = ElementFactory.make("rtpjpegdepay", "depay");
		final Element videoDecode = ElementFactory.make("jpegdec", "decode");
		final Element videoColor = ElementFactory.make("ffmpegcolorspace", "color");
		
		videoBin.addMany(videoDepay, videoDecode, videoColor);
		Element.linkMany(videoDepay, videoDecode, videoColor);
		
		videoBin.addPad(new GhostPad("sink", videoDepay.getStaticPad("sink")));
		pipe.add(videoBin);
		
		System.out.println("VideoBin init'd");
		
		// AUDIO BIN
		
		final Bin audioBin = new Bin("audioBin");
		
		final Element audioDepay = ElementFactory.make("rtpL16depay", "auddepay");
		final Element audioSink = ElementFactory.make("autoaudiosink", "audsink");
		
		audioBin.addMany(audioDepay, audioSink);
		Element.linkMany(audioDepay, audioSink);
		
		audioBin.addPad(new GhostPad("sink", audioDepay.getStaticPad("sink")));
		pipe.add(audioBin);
		
		System.out.println("AudioBin init'd");
		
		// RTPBIN
		
		RTPBin rtp = new RTPBin("rtp");
		pipe.add(rtp);
		
		Element.linkPads(udpVideoSrc, "src", rtp, "recv_rtp_sink_0");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		Element.linkMany(rtp, videoBin);
		
		Element.linkPads(udpAudioSrc, "src", rtp, "recv_rtp_sink_1");
		Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
		Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
		Element.linkMany(rtp, audioBin);
		
		// BUS
		
		Bus bus = pipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
            	// GST_DEBUG_DUMP_DOT_DIR
                GstDebugUtils.gstDebugBinToDotFile(pipe, 1, "client");
                System.out.println("Error: code=" + code + " message=" + message);
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
                pipe.setState(State.NULL);
                System.out.println("EOS");
                System.exit(0);
            }
        });
		
		SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                VideoComponent videoComponent = new VideoComponent();
                Element videoSink = videoComponent.getElement();
                videoBin.add(videoSink);
                Element.linkMany(videoColor, videoSink);
                
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
