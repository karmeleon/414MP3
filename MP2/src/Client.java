import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import javax.swing.JTextArea;

import org.gstreamer.Bin;
import org.gstreamer.Buffer;
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
import org.gstreamer.elements.AppSink;
import org.gstreamer.elements.good.RTPBin;
import org.gstreamer.swing.VideoComponent;
import org.json.JSONObject;

public class Client {
	static PrintWriter out;
	static JTextArea textArea;
	static Pipeline clientPipe;
	static Bin videoBin;
	
	static String resolution = "";
	static String attribute = "";
	static String request = "";
	static Queue<FrameInfo> videoQ;
	static Queue<FrameInfo> audioQ;
	static Queue<CompareInfo> jointQ;
	
	static String clientLoc;
	static String serverLoc = "localhost";
	
	public static void handleRequest(VideoComponent vc, String settings, JTextArea log, String addr) throws UnknownHostException, IOException {
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
				connectAndPlay(vc, settings, addr);
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
			}
		}
		else {
			// Play -> if playing then nothing || if paused then resume 
			// Pause -> if playing then pause || if  paused then nothing
			// Stop -> Signal kill and purge pipes
			if (request.equalsIgnoreCase("play")) {
				commandResume();
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
			}
			else if (request.equalsIgnoreCase("pause")) {
				commandPause();
			}
			else if (request.equalsIgnoreCase("rewind")) {
				commandRewind();
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
			}
			else if (request.equalsIgnoreCase("forward" )) {
				commandForward();
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
			}
			else { // request = stop
				commandStop(vc);
			}
		}
		vc.setPreferredSize(new Dimension(1200, 640));
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

	private static void connectAndPlay(VideoComponent vc, String settings, String addr) throws UnknownHostException, IOException {
		// find this ip
		Socket skt = null;
		if (addr.length() > 0) {
			Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
			while(e.hasMoreElements())
			{
				NetworkInterface n = (NetworkInterface) e.nextElement();
				Enumeration<InetAddress> ee = n.getInetAddresses();
				while (ee.hasMoreElements())
				{
					InetAddress i = (InetAddress) ee.nextElement();
					if(!i.isLinkLocalAddress() && !i.isLoopbackAddress()) {
						pushLog("CLNT START IP:" + i.getHostAddress());
					    clientLoc = i.getHostAddress();
					}
				}
			}
			serverLoc = addr;
			skt = new Socket(serverLoc, 45000);
		}
		else if (addr.length() == 0) {
			clientLoc = "127.0.0.1";
			serverLoc = "127.0.0.1";
			skt = new Socket("localhost", 45000);
		}
		
		skt.setReuseAddress(true);
        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
        out = new PrintWriter(skt.getOutputStream(), true);

        JSONObject portNeg = new JSONObject(in.readLine());
        int port = portNeg.getInt("port");
        System.out.println("Client port: " + port);
        
        JSONObject json_settings = new JSONObject();
        json_settings.put("settings", settings);
        out.println(json_settings.toString());
        
        pushLog("> SYS: CNCT SUCCESS");
        startStreaming(vc, settings, port);
        
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
	
	private static void startStreaming(final VideoComponent vc, String settings, int port) {
		Gst.init();
		clientPipe = new Pipeline("pipeline");
		pushLog("> CTRL: " + "PLAY");
		pushLog("> SYS: " + " INIT STREAM");

		System.out.println("Starting with: C=" + clientLoc + ", S=" + serverLoc);
		
		// VIDEO
		Element udpVideoSrc = ElementFactory.make("udpsrc", "src1");
		udpVideoSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)2156703816, clock-base=(uint)1678649553, seqnum-base=(uint)31324" ));
		udpVideoSrc.set("uri", "udp://" + clientLoc +":" + port);
		
		Element videoRtcpIn = ElementFactory.make("udpsrc", "src3");
		videoRtcpIn.set("uri", "udp://" + clientLoc +":" + (port + 1));
		
		Element videoRtcpOut = ElementFactory.make("udpsink", "snk1");
		videoRtcpOut.set("host", serverLoc);
		videoRtcpOut.set("port", "" + (port + 5));
		videoRtcpOut.set("sync", "false");
		videoRtcpOut.set("async", "false");
		
		Element udpAudioSrc = null, audioRtcpIn = null, audioRtcpOut = null, taud = null;
		
		if(attribute.equalsIgnoreCase("active")) {
			// AUDIO
			udpAudioSrc = ElementFactory.make("udpsrc", "src2");
			udpAudioSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)44100, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
			udpAudioSrc.set("uri", "udp://" + clientLoc +":" + (port + 2));
			
			taud = ElementFactory.make("tee", "taud");
			Element qaud = ElementFactory.make("queue", "qaud");
			AppSink appAudioSink = (AppSink) ElementFactory.make("appsink", "appAudioSink");
			appAudioSink.set("emit-signals", true); 
			appAudioSink.setSync(false);
			appAudioSink.connect(new AppSink.NEW_BUFFER() { 
				public void newBuffer(AppSink sink) {
					//System.out.println("Audio Frame");
				} 
			});
			clientPipe.addMany(taud, qaud, appAudioSink);
			clientPipe.addMany(udpAudioSrc, audioRtcpIn, audioRtcpOut);
			Element.linkMany(udpAudioSrc, taud, qaud, appAudioSink);
			
			audioRtcpIn = ElementFactory.make("udpsrc", "src4");
			audioRtcpIn.set("uri", "udp://" + clientLoc +":" + (port + 3));
			
			audioRtcpOut = ElementFactory.make("udpsink", "snk2");
			audioRtcpOut.set("host", serverLoc);
			audioRtcpOut.set("port", "" + (port + 7));
			audioRtcpOut.set("sync", "false");
			audioRtcpOut.set("async", "false");
			
		}
		
		Element tvid = ElementFactory.make("tee", "tvid");
		Element qvid = ElementFactory.make("queue", "qvid");
		AppSink appVideoSink = (AppSink) ElementFactory.make("appsink", "appVideoSink");
		appVideoSink.set("emit-signals", true); 
		appVideoSink.setSync(false);
		appVideoSink.connect(new AppSink.NEW_BUFFER() { 
			public void newBuffer(AppSink sink) {
				Buffer b = sink.getLastBuffer();
				if (b != null) {
					// NullPointerException on this line
					// videoQ.offer(new FrameInfo(System.currentTimeMillis(), b.getSize()));
				}
			} 
		});
		clientPipe.addMany(tvid, qvid, appVideoSink);
		clientPipe.addMany(udpVideoSrc, videoRtcpIn, videoRtcpOut);
		Element.linkMany(udpVideoSrc, tvid, qvid, appVideoSink);
		
		// VIDEO BIN
		
		videoBin = new Bin("videoBin");
		
		final Element videoDepay = ElementFactory.make("rtpjpegdepay", "depay");
		final Element videoDecode = ElementFactory.make("jpegdec", "decode");
		final Element videoColor = ElementFactory.make("ffmpegcolorspace", "color");
		
		videoBin.addMany(videoDepay, videoDecode, videoColor);
		Element.linkMany(videoDepay, videoDecode, videoColor);
		
		videoBin.addPad(new GhostPad("sink", videoDepay.getStaticPad("sink")));
		clientPipe.add(videoBin);
		
		final Bin audioBin = new Bin("audioBin");
		
		if(attribute.equalsIgnoreCase("active")) {
			// AUDIO BIN
			
			final Element audioDepay = ElementFactory.make("rtpL16depay", "auddepay");
			final Element audioSink = ElementFactory.make("autoaudiosink", "audsink");
			
			audioBin.addMany(audioDepay, audioSink);
			Element.linkMany(audioDepay, audioSink);
			
			audioBin.addPad(new GhostPad("sink", audioDepay.getStaticPad("sink")));
			clientPipe.add(audioBin);
		}

		// RTPBIN
		
		final RTPBin rtp = new RTPBin("rtp");
		clientPipe.add(rtp);
		
		Element.linkPads(tvid, "src1", rtp, "recv_rtp_sink_0");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		
		if(attribute.equalsIgnoreCase("active")) {
			Element.linkPads(taud, "src1", rtp, "recv_rtp_sink_1");
			Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
			Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
		}
		
		// BUS
		
		rtp.connect(new Element.PAD_ADDED() {
			@Override
			public void padAdded(Element arg0, Pad arg1) {
				if(arg1.getName().startsWith("recv_rtp_src_0")) {
	                arg1.link(videoBin.getStaticPad("sink"));
				} else if(arg1.getName().startsWith("recv_rtp_src_1") && attribute.equalsIgnoreCase("active")) {
					arg1.link(audioBin.getStaticPad("sink"));
				}
			}
		});
		
		Bus bus = clientPipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
                pushLog("> GSTREAMER ERROR: code=" + code + " message=" + message);
                clientPipe.debugToDotFile(1, "client");
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
            	clientPipe.setState(State.NULL);
                System.out.println("EOS");
            }
        });
		
        videoBin.add(vc.getElement());
        
        AppSink appJointSink = (AppSink) ElementFactory.make("appsink", "appJointSink");
		appJointSink.set("emit-signals", true); 
		appJointSink.setSync(false);
		appJointSink.connect(new AppSink.NEW_BUFFER() { 
			public void newBuffer(AppSink sink) {
			} 
		});
		
		Element.linkMany(videoColor, vc.getElement());
        
        Thread videoThread = new Thread() {
        	public void run() {
        		clientPipe.setState(org.gstreamer.State.PLAYING);
        	}
        };
        videoThread.start();
        clientPipe.debugToDotFile(0, "appsink");
	}
	
	public static void updateResource() {
		if (clientPipe != null) {
			int bandwidth = 0;
			BufferedReader br = null;
			try {
				String somePath = Client.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
				String appPath = "";
				if (somePath.indexOf('!') != -1) { // for jar files
					appPath = somePath.substring(0, somePath.indexOf('!')); // find the local directory
				}
				else { // running on eclipse
					appPath = ""; // just use the local resource.txt
				}
				br = new BufferedReader(new FileReader(appPath + "resource.txt"));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
			try {
				bandwidth = Integer.parseInt(br.readLine());
			} catch (NumberFormatException e) {
			} catch (IOException e) {
			}
			try {
				br.close();
			} catch (IOException e) {
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
