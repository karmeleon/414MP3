import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import javax.swing.JTextArea;
import org.gstreamer.Bin;
import org.gstreamer.Bus;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pad;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.elements.good.RTPBin;
import org.gstreamer.swing.VideoComponent;
import org.gstreamer.utils.GstDebugUtils;
import org.json.JSONObject;

public class Client {
	static PrintWriter out;
	static JTextArea textArea;
	static Pipeline clientPipe;
	static Bin videoBin;
	
	static String resolution = "";
	static String attribute = "";
	static String request = "";
	// static boolean seeking = false;
	
	public static void handleRequest(VideoComponent vc, String settings, JTextArea log) throws UnknownHostException, IOException {
		textArea = log;
		
		String[] s = settings.split(" ");
		resolution = s[0];               // 240p/480p
		attribute = s[1];                // Passive/Active
		// clientbw = Integer.parseInt(s[2]); // Some amount
		request = s[3];
		
		if (clientPipe == null) {
			// Play -> connect and play
			// Pause -> nothing
			// Stop -> Nothing
			if (request.equalsIgnoreCase("play")) {
				connectAndPlay(vc, settings);
			}
		}
		else {
			// Play -> if playing then nothing || if paused then resume 
			// Pause -> if playing then pause || if  paused then nothing
			// Stop -> Signal kill and purge pipes
			if (request.equalsIgnoreCase("play")) {
				commandResume();
			}
			else if (request.equalsIgnoreCase("pause")) {
				commandPause();
			}
			else if (request.equalsIgnoreCase("rewind")) {
				commandRewind();
			}
			else if (request.equalsIgnoreCase("forward" )) {
				commandForward();
			}
			else { // request = stop
				commandStop(vc);
				System.out.println("HERE");
			}
		}
	}
	
	public static void commandForward() {
		JSONObject json_pause = new JSONObject();
		json_pause.put("command", "ff");
		out.println(json_pause.toString());
	}
	
	public static void commandRewind() {
		JSONObject json_pause = new JSONObject();
		json_pause.put("command", "rw");
		out.println(json_pause.toString());
	}
	
	public static void commandStop(VideoComponent vc) {
		commandPause();
		
		clientPipe.setState(State.PAUSED);
		videoBin.setState(State.PAUSED);
		videoBin.unlink(vc.getElement());
		videoBin.remove(vc.getElement());

		clientPipe = null;
		JSONObject json_pause = new JSONObject();
		json_pause.put("command", "stop");
		out.println(json_pause.toString());
	}
	
	public static void commandResume() {
		JSONObject json_pause = new JSONObject();
		json_pause.put("command", "play");
		out.println(json_pause.toString());
	}
	
	public static void commandPause() {
		JSONObject json_pause = new JSONObject();
		json_pause.put("command", "pause");
		out.println(json_pause.toString());
	}

	private static void connectAndPlay(VideoComponent vc, String settings) throws UnknownHostException, IOException {
		Socket skt = new Socket("localhost", 45000);
		skt.setReuseAddress(true);
        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
        out = new PrintWriter(skt.getOutputStream(), true);

        JSONObject json_settings = new JSONObject();
        json_settings.put("settings", settings);
        out.println(json_settings.toString());
        
        pushLog("> SYS: CNCT SUCCESS");
        startStreaming(vc, settings);
        
        pushLog("> CTRL: LISTENING FOR COMMANDS");
        Scanner s = new Scanner(System.in);
        String line;
        JSONObject json_command;
        while(true) {
        	line = s.nextLine();
        	json_command = new JSONObject();
        	json_command.put("command", line);
        	// json_command.put("bandwidth", "");
        	out.println(json_command.toString());
        	
        	if(line.equals("stop"))
        		break;
        }
        
        in.close();
        out.close();
        skt.close();
	}
	
	private static void startStreaming(final VideoComponent vc, String settings) {
		Gst.init();
		final int port = 45001;
		clientPipe = new Pipeline("pipeline");
		pushLog("> CTRL: " + "PLAY");
		pushLog("> SYS: " + " INIT STREAM");
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
		
		clientPipe.addMany(udpVideoSrc, udpAudioSrc, videoRtcpIn, audioRtcpIn, videoRtcpOut, audioRtcpOut);
		
		// VIDEO BIN
		
		videoBin = new Bin("videoBin");
		
		final Element videoDepay = ElementFactory.make("rtpjpegdepay", "depay");
		final Element videoDecode = ElementFactory.make("jpegdec", "decode");
		final Element videoColor = ElementFactory.make("ffmpegcolorspace", "color");
		
		videoBin.addMany(videoDepay, videoDecode, videoColor);
		Element.linkMany(videoDepay, videoDecode, videoColor);
		
		videoBin.addPad(new GhostPad("sink", videoDepay.getStaticPad("sink")));
		clientPipe.add(videoBin);
		
		System.out.println("VideoBin init'd");
		
		// AUDIO BIN
		
		final Bin audioBin = new Bin("audioBin");
		
		final Element audioDepay = ElementFactory.make("rtpL16depay", "auddepay");
		final Element audioSink = ElementFactory.make("autoaudiosink", "audsink");
		
		audioBin.addMany(audioDepay, audioSink);
		Element.linkMany(audioDepay, audioSink);
		
		audioBin.addPad(new GhostPad("sink", audioDepay.getStaticPad("sink")));
		clientPipe.add(audioBin);
		
		System.out.println("AudioBin init'd");
		
		// RTPBIN
		
		final RTPBin rtp = new RTPBin("rtp");
		clientPipe.add(rtp);
		
		Element.linkPads(udpVideoSrc, "src", rtp, "recv_rtp_sink_0");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		
		Element.linkPads(udpAudioSrc, "src", rtp, "recv_rtp_sink_1");
		Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
		Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
		
		// BUS
		
		rtp.connect(new Element.PAD_ADDED() {
			@Override
			public void padAdded(Element arg0, Pad arg1) {
				if(arg1.getName().startsWith("recv_rtp_src_0")) {
					System.out.println("found video pad, trying to link to pad " + arg1.getName());
	                System.out.println(arg1.link(videoBin.getStaticPad("sink")));
				} else if(arg1.getName().startsWith("recv_rtp_src_1")) {
					System.out.println("found audio pad, trying to link to pad " + arg1.getName());
					System.out.println(arg1.link(audioBin.getStaticPad("sink")));
				}
			}
		});
		
		Bus bus = clientPipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
                System.out.println("Error: code=" + code + " message=" + message);
             // GST_DEBUG_DUMP_DOT_DIR
                GstDebugUtils.gstDebugBinToDotFile(clientPipe, 1, "client");
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
            	clientPipe.setState(State.NULL);
                System.out.println("EOS");
                System.exit(0);
            }
        });
		
        videoBin.add(vc.getElement());
		Element.linkMany(videoColor, vc.getElement());
        
        Thread videoThread = new Thread() {
        	public void run() {
        		clientPipe.setState(org.gstreamer.State.PLAYING);
        	}
        };
        videoThread.start();
	}
	
	public static void updateResource() {
		if (clientPipe != null) {
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
			
			JSONObject json_bandwidth = new JSONObject();
	        json_bandwidth.put("command", "" + bandwidth);
	        out.println(json_bandwidth.toString());
	        pushLog("> RSRC: REQ BW " + bandwidth);
		}
	}
	public static long getUnsignedInt(int x) {
	    return x & 0x00000000ffffffffL;
	}
	private static void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
}
