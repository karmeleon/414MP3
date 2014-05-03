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

public class ClientTarget {
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
	static int xint = 256;
	static int yint = 128;
	static int xpos = 0; // 8192
	static int ypos = 0;
	static Element mute = null;
	
	public static void handleRequest(VideoComponent vc, String settings, JTextArea log, String addr) throws UnknownHostException, IOException {
		textArea = log;
		
		String[] s = settings.split(" ");
		attribute = s[0];                // Passive/Active
		// clientbw = Integer.parseInt(s[1]); // Some amount
		request = s[2];
		
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
			else if(request.equalsIgnoreCase("up")) {
				JSONObject json_up = new JSONObject();
				json_up.put("command", "tilt");
				json_up.put("amount", -yint);
				out.println(json_up.toString());
			}
			else if(request.equalsIgnoreCase("down")) {
				JSONObject json_down = new JSONObject();
				json_down.put("command", "tilt");
				json_down.put("amount", yint);
				out.println(json_down.toString());			
			}
			else if(request.equalsIgnoreCase("left")) {
				if (xpos + xint > 4096) {
					
				}
				else {
					xpos += xint;
					JSONObject json_left = new JSONObject();
					json_left.put("command", "pan");
					json_left.put("amount", xint);
					out.println(json_left.toString());
				}
			}
			else if(request.equalsIgnoreCase("right")) {
				if (xpos - xint < -4096) {
					
				}
				else {
					xpos -= xint;
					JSONObject json_right = new JSONObject();
					json_right.put("command", "pan");
					json_right.put("amount", -xint);
					out.println(json_right.toString());
				}
			}
			else if(request.equalsIgnoreCase("reset")) {
				JSONObject json_up = new JSONObject();
				json_up.put("command", "reset");
				out.println(json_up.toString());
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
			udpAudioSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)8000, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
			udpAudioSrc.set("uri", "udp://" + clientLoc +":" + (port + 2));
			
			taud = ElementFactory.make("tee", "taud");
			Element qaud = ElementFactory.make("queue", "qaud");
			AppSink appAudioSink = (AppSink) ElementFactory.make("appsink", "appAudioSink");
			appAudioSink.set("emit-signals", true); 
			appAudioSink.setSync(false);
			audioQ = new LinkedList<FrameInfo>();
			appAudioSink.connect(new AppSink.NEW_BUFFER() { 
				public void newBuffer(AppSink sink) {
					Buffer b = sink.getLastBuffer();
					if (b != null) {
						audioQ.offer(new FrameInfo(System.currentTimeMillis(), b.getSize()));
					}
				} 
			});
			
			audioRtcpIn = ElementFactory.make("udpsrc", "src4");
			audioRtcpIn.set("uri", "udp://" + clientLoc +":" + (port + 3));
			
			audioRtcpOut = ElementFactory.make("udpsink", "snk2");
			audioRtcpOut.set("host", serverLoc);
			audioRtcpOut.set("port", "" + (port + 7));
			audioRtcpOut.set("sync", "false");
			audioRtcpOut.set("async", "false");
			
			clientPipe.addMany(taud, qaud, appAudioSink);
			clientPipe.addMany(udpAudioSrc, audioRtcpIn, audioRtcpOut);
			Element.linkMany(udpAudioSrc, taud, qaud, appAudioSink);
		}
		
		Element tvid = ElementFactory.make("tee", "tvid");
		Element qvid = ElementFactory.make("queue", "qvid");
		AppSink appVideoSink = (AppSink) ElementFactory.make("appsink", "appVideoSink");
		appVideoSink.set("emit-signals", true); 
		appVideoSink.setSync(false);
		videoQ = new LinkedList<FrameInfo>();
		appVideoSink.connect(new AppSink.NEW_BUFFER() { 
			public void newBuffer(AppSink sink) {
				Buffer b = sink.getLastBuffer();
				if (b != null) {
					videoQ.offer(new FrameInfo(System.currentTimeMillis(), b.getSize()));
				}
			} 
		});
		clientPipe.addMany(tvid, qvid, appVideoSink);
		clientPipe.addMany(udpVideoSrc, videoRtcpIn, videoRtcpOut);
		Element.linkMany(udpVideoSrc, tvid, qvid, appVideoSink);
		
		// VIDEO BIN
		
		videoBin = new Bin("videoBin");
		
		// src1
		Element videoDepay = ElementFactory.make("rtpjpegdepay", "depay");
		Element videoDecode = ElementFactory.make("jpegdec", "decode");
		Element videoRate = ElementFactory.make("videorate", "rate1");
		Element videoColor = ElementFactory.make("ffmpegcolorspace", "color");
		Element videoSrc1Caps = ElementFactory.make("capsfilter", "src1caps");
		videoSrc1Caps.setCaps(Caps.fromString("video/x-raw-yuv, framerate=30/1"));
		Element videoColor2 = ElementFactory.make("ffmpegcolorspace", "color2");
		
		
		videoBin.addMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoColor2);
		Element.linkMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoColor2);
		
		videoBin.addPad(new GhostPad("sink", videoDepay.getStaticPad("sink")));
		clientPipe.add(videoBin);
		
		final Bin audioBin = new Bin("audioBin");
		
		if(attribute.equalsIgnoreCase("active")) {
			// AUDIO BIN
			
			final Element audioDepay = ElementFactory.make("rtpL16depay", "auddepay");
			Element audioConvert = ElementFactory.make("audioconvert", "ChristianMissionary");
			mute = ElementFactory.make("volume", "vol");
			mute.set("mute", "true");
			final Element audioSink = ElementFactory.make("autoaudiosink", "audsink");
			
			audioBin.addMany(audioDepay, audioConvert, mute, audioSink);
			Element.linkMany(audioDepay, audioConvert, mute, audioSink);
			
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
				clientPipe.debugToDotFile(1, "clientsucc");
			}
		});
		
		Bus bus = clientPipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
                pushLog("> GSTREAMER ERROR: code=" + code + " message=" + message);
                clientPipe.debugToDotFile(1, "clienterr");
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
		jointQ = new LinkedList<CompareInfo>();
		appJointSink.connect(new AppSink.NEW_BUFFER() { 
			public void newBuffer(AppSink sink) {
				/*
				int vs = 0; int as = 0;
				while (videoQ != null) {
					vs++; videoQ.poll();
				}
				while (audioQ != null) {
					as++; audioQ.poll();
				}
				System.out.println("Compare: " + as + " : " + vs);
				*/
			} 
		});
		
		Element.linkMany(videoColor2, vc.getElement());
        
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
			String rscPath = "c_resource.txt";
			
			try {
				br = new BufferedReader(new FileReader(rscPath));
			} catch (FileNotFoundException e1) { e1.printStackTrace(); }
			try {
				bandwidth = Integer.parseInt(br.readLine());
				br.close();
			} catch (IOException e) { e.printStackTrace(); }
			
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
