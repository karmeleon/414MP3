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
	static JTextArea textArea;
	static String clientLoc;
	static int connectionType;
	
	static PrintWriter targetOut;
	static PrintWriter pilotOut;
	
	static Pipeline targetClientPipe;
	static Pipeline pilotClientPipe;
	static Bin targetVideoBin;
	static Bin pilotVideoBin;
	
	static String attribute = "";
	static String request = "";
	
	static String targetServerLoc = "localhost";
	static String pilotServerLoc = "localhost";
	
	static Queue<FrameInfo> videoQ;
	static Queue<FrameInfo> audioQ;
	static Queue<CompareInfo> jointQ;
	
	public static void handleRequest(VideoComponent vc, String settings, JTextArea log, String addr) throws UnknownHostException, IOException {
		textArea = log;
	
		String[] s = settings.split(" ");
		connectionType = s[3].equalsIgnoreCase("pilot") ? 0 : 1; // pilot = 0 / target = 1
		attribute = s[0]; // Passive/Active
		request = s[2];   // Play/Stop
		
		if (pilotClientPipe == null && connectionType == 0) {
			// Play -> connect and play
			// Pause -> nothing
			// Stop -> Nothing
			if (request.equalsIgnoreCase("play")) {
				pilotServerLoc = addr;
				connectAndPlay(connectionType, vc, settings, addr);
				/*
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
				*/
			}
		}
		else if (targetClientPipe == null && connectionType == 1) {
			if (request.equalsIgnoreCase("play")) {
				targetServerLoc = addr;
				connectAndPlay(connectionType, vc, settings, addr);
				/*
				videoQ = new LinkedList<FrameInfo>();
				audioQ = new LinkedList<FrameInfo>();
				jointQ = new LinkedList<CompareInfo>();
				*/
			}
		}
		else {
			if (request.equalsIgnoreCase("stop")) {
				commandStop(connectionType, vc);
			}
		}
		vc.setPreferredSize(new Dimension(1200, 640));
	}
	
	public static void commandStop(int t, VideoComponent vc) {
		// commandPause();
		if (connectionType == 0) {
			pilotClientPipe.setState(State.PAUSED);
			pilotVideoBin.setState(State.PAUSED);
			pilotVideoBin.unlink(vc.getElement());
			pilotVideoBin.remove(vc.getElement());

			pilotClientPipe = null;
			JSONObject json_pause = new JSONObject();
			json_pause.put("command", "pause");
			pilotOut.println(json_pause.toString());
			
			JSONObject json_stop = new JSONObject();
			json_stop.put("command", "stop");
			pilotOut.println(json_stop.toString());
		}
		else if (connectionType == 1) {
			targetClientPipe.setState(State.PAUSED);
			targetVideoBin.setState(State.PAUSED);
			targetVideoBin.unlink(vc.getElement());
			targetVideoBin.remove(vc.getElement());

			targetClientPipe = null;
			JSONObject json_pause = new JSONObject();
			json_pause.put("command", "pause");
			targetOut.println(json_pause.toString());
			
			JSONObject json_stop = new JSONObject();
			json_stop.put("command", "stop");
			targetOut.println(json_stop.toString());
		}
	}

	private static void connectAndPlay(int t, VideoComponent vc, String settings, String addr) throws UnknownHostException, IOException {
		if (t == 0) pilotServerLoc = addr;
		if (t == 1) targetServerLoc = addr;
		
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
			
			if (t == 0) skt = new Socket(pilotServerLoc, 45000);
			if (t == 1) skt = new Socket(targetServerLoc, 45000);
		}
		else if (addr.length() == 0) {
			clientLoc = "127.0.0.1";
			if (t == 0) pilotServerLoc = "127.0.0.1";
			if (t == 1) targetServerLoc = "127.0.0.1";
			skt = new Socket("localhost", 45000);
		}
		
		skt.setReuseAddress(true);
        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
        if (t == 0) pilotOut = out;
        if (t == 1) targetOut = out;

        JSONObject portNeg = new JSONObject(in.readLine());
        int port = portNeg.getInt("port");
        System.out.println("Client port: " + port);
        
        JSONObject json_settings = new JSONObject();
        json_settings.put("settings", settings);
        out.println(json_settings.toString());
        
        pushLog("> SYS: CNCT SUCCESS");
        startStreaming(t, vc, settings, port);
        
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
	
	private static void startStreaming(final int t, final VideoComponent vc, String settings, int port) {
		Gst.init();
		Pipeline clientPipe = null;
		if (t == 0)
			clientPipe = pilotClientPipe;
		else
			clientPipe = targetClientPipe;
		
		clientPipe = new Pipeline("pipeline");
		pushLog("> CTRL: " + "PLAY");
		pushLog("> SYS: " + " INIT STREAM");
		
		if (t == 0) System.out.println("Starting with: C=" + clientLoc + ", S=" + pilotServerLoc);
		if (t == 1) System.out.println("Starting with: C=" + clientLoc + ", S=" + targetServerLoc);
		
		// TARGET
		Element udpVideoSrc = ElementFactory.make("udpsrc", "src1");
		udpVideoSrc.setCaps(Caps.fromString("application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)2156703816, clock-base=(uint)1678649553, seqnum-base=(uint)31324"));
		udpVideoSrc.set("uri", "udp://" + clientLoc + ":" + port);
		
		Element videoRtcpIn = ElementFactory.make("udpsrc", "src3");
		videoRtcpIn.set("uri", "udp://" + clientLoc + ":" + (port + 1));
		
		Element videoRtcpOut = ElementFactory.make("udpsink", "snk1");
		if (t == 0) videoRtcpOut.set("host", pilotServerLoc);
		if (t == 1) videoRtcpOut.set("host", targetServerLoc);
		videoRtcpOut.set("port", "" + (port + 5));
		videoRtcpOut.set("sync", "false");
		videoRtcpOut.set("async", "false");
		
		// AUDIO1
		Element udpAudioSrc = null, audioRtcpIn = null, audioRtcpOut = null, taud = null;
		
		// PILOT
		Element udpVideoSrc2 = ElementFactory.make("udpsrc", "src2.1");
		udpVideoSrc2.setCaps(Caps.fromString("application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)2156703816, clock-base=(uint)1678649553, seqnum-base=(uint)31324"));
		udpVideoSrc2.set("uri", "udp://" + targetServerLoc + ":" + port);	// CHANGE THIS PORT
		
		Element videoRtcpIn2 = ElementFactory.make("udpsrc", "src2.3");
		videoRtcpIn2.set("uri", "udp://" + targetServerLoc + ":" + (port + 1));	// AND THIS ONE
		
		Element videoRtcpOut2 = ElementFactory.make("udpsink", "sink2.1");
		videoRtcpOut2.set("host", targetServerLoc);
		videoRtcpOut2.set("port", "" + (port + 5));
		videoRtcpOut2.set("sync", "false");
		videoRtcpOut2.set("async", "false");
		
		// AUDIO2
		Element udpAudioSrc2 = null, audioRtcpIn2 = null, audioRtcpOut2 = null,  taud2 = null;
		
		if(attribute.equalsIgnoreCase("active")) {
			// AUDIO1
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
			clientPipe.addMany(taud, qaud, appAudioSink);
			clientPipe.addMany(udpAudioSrc, audioRtcpIn, audioRtcpOut);
			Element.linkMany(udpAudioSrc, taud, qaud, appAudioSink);
			
			audioRtcpIn = ElementFactory.make("udpsrc", "src4");
			audioRtcpIn.set("uri", "udp://" + clientLoc +":" + (port + 3));
			
			audioRtcpOut = ElementFactory.make("udpsink", "snk2");
			if (t == 0) audioRtcpOut.set("host", pilotServerLoc);
			if (t == 1) audioRtcpOut.set("host", targetServerLoc);
			audioRtcpOut.set("port", "" + (port + 7));
			audioRtcpOut.set("sync", "false");
			audioRtcpOut.set("async", "false");
		}
		
		if(attribute.equalsIgnoreCase("active")) {	// NEED TO KNOW WHICH SERVERS ARE ACTIVE AND WHICH ARE PASSIVE INDIVIDUALLY
			// AUDIO2
			udpAudioSrc2 = ElementFactory.make("udpsrc", "src2.2");
			udpAudioSrc2.setCaps(Caps.fromString("application/x-rtp, media=(string)audio, clock-rate=(int)8000, encoding-name=(string)L16, encoding-params=(string)2, channels=(int)2, payload=(int)96, ssrc=(uint)3489550614, clock-base=(uint)2613725642, seqnum-base=(uint)1704"));
			udpAudioSrc2.set("uri", "udp://" + targetServerLoc + ":" + (port + 2));	// THIS ONE TOO
			
			taud2 = ElementFactory.make("tee", "taud2");
			Element qaud2 = ElementFactory.make("queue", "qaud2");
			AppSink appAudioSink2 = (AppSink) ElementFactory.make("appsink", "appAudioSink2");
			appAudioSink2.set("emit-signals", true);
			appAudioSink2.setSync(false);
			// ADD A QUEUE HERE
			appAudioSink2.connect(new AppSink.NEW_BUFFER() {
				public void newBuffer(AppSink sink) {
					// do something here
				}
			});
			clientPipe.addMany(taud2, qaud2, appAudioSink2);
			clientPipe.addMany(udpAudioSrc2, audioRtcpIn2, audioRtcpOut2);
			
			audioRtcpIn2 = ElementFactory.make("udpsrc", "src2.4");
			audioRtcpIn2.set("uri", "udp://" + targetServerLoc + ":" + (port + 3));	// CHANGEAROO
			
			audioRtcpOut2 = ElementFactory.make("udpsink", "sink2.2");
			audioRtcpOut2.set("host", targetServerLoc);
			audioRtcpOut2.set("port", "" + (port + 7));		// YOU KNOW THE DRILL
			audioRtcpOut2.set("sync", "false");
			audioRtcpOut2.set("async", "false");
		}
		// VIDEO1
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
		
		// VIDEO2
		Element tvid2 = ElementFactory.make("tee", "tvid2");
		Element qvid2 = ElementFactory.make("queue", "qvid2");
		AppSink appVideoSink2 = (AppSink) ElementFactory.make("appsink", "appVideoSink2");
		appVideoSink2.set("emit-signals", true);
		appVideoSink2.setSync(false);
		// QUEUE HERE
		appVideoSink2.connect(new AppSink.NEW_BUFFER() {
			public void newBuffer(AppSink sink) {
				// do someting here
			}
		});
		clientPipe.addMany(tvid2, qvid2, appVideoSink2);
		clientPipe.addMany(udpVideoSrc2, videoRtcpIn2, videoRtcpOut2);
		Element.linkMany(udpVideoSrc2, tvid2, qvid2, appVideoSink2);
		// VIDEO BIN
		
		if (t == 0) pilotVideoBin = new Bin("videoBin");
		if (t == 1) targetVideoBin = new Bin("videoBin");
		
		// src1
		Element videoDepay = ElementFactory.make("rtpjpegdepay", "depay");
		Element videoDecode = ElementFactory.make("jpegdec", "decode");
		Element videoRate = ElementFactory.make("videorate", "rate1");
		Element videoSrc1Caps = ElementFactory.make("capsfilter", "src1caps");
		videoSrc1Caps.setCaps(Caps.fromString("video/x-raw-yuv, framerate=30/1"));
		Element videoColor = ElementFactory.make("ffmpegcolorspace", "color");
		// src2
		/*
		Element videoSrc2 = ElementFactory.make("videotestsrc", "vidsrc2");
		Element videoSrc2Caps = ElementFactory.make("capsfilter", "src2caps");
		videoSrc2Caps.setCaps(Caps.fromString("video/x-raw-yuv, framerate=30/1, width=200, height=150"));
		Element videoColor2 = ElementFactory.make("ffmpegcolorspace", "color2");
		*/
		Element videoDepay2 = ElementFactory.make("rtpjpegdepay", "depay2");
		Element videoDecode2 = ElementFactory.make("jpegdec", "decode2");
		Element videoRate2 = ElementFactory.make("videorate", "rate2");
		Element videoSrc2Caps = ElementFactory.make("capsfilter", "src2caps");
		videoSrc2Caps.setCaps(Caps.fromString("video/x-raw-yuv, framerate=30/1"));
		Element videoColor2 = ElementFactory.make("ffmpegcolorspace", "color2");
		// mixer
		Element videoMix = ElementFactory.make("videomixer", "vidmix");
		Element mixedColor = ElementFactory.make("ffmpegcolorspace", "mixcolor");
		
		if (t == 0) {
			pilotVideoBin.addMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoMix, mixedColor);
			pilotVideoBin.addMany(videoDepay2, videoDecode2, videoRate2, videoColor2, videoSrc2Caps);
			// videoBin.addMany(videoSrc2, videoSrc2Caps, videoColor2);
			Element.linkMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoMix, mixedColor); 
			Element.linkMany(videoDepay2, videoDecode2, videoRate2, videoColor2, videoSrc2Caps, videoMix);
			//Element.linkMany(videoSrc2, videoSrc2Caps, videoColor2, videoMix);
			
			pilotVideoBin.addPad(new GhostPad("sink_1", videoDepay.getStaticPad("sink")));
			pilotVideoBin.addPad(new GhostPad("sink_2", videoDepay2.getStaticPad("sink")));
			clientPipe.add(pilotVideoBin);
		}
		else if (t == 1) {
			targetVideoBin.addMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoMix, mixedColor);
			targetVideoBin.addMany(videoDepay2, videoDecode2, videoRate2, videoColor2, videoSrc2Caps);
			// videoBin.addMany(videoSrc2, videoSrc2Caps, videoColor2);
			Element.linkMany(videoDepay, videoDecode, videoRate, videoColor, videoSrc1Caps, videoMix, mixedColor); 
			Element.linkMany(videoDepay2, videoDecode2, videoRate2, videoColor2, videoSrc2Caps, videoMix);
			//Element.linkMany(videoSrc2, videoSrc2Caps, videoColor2, videoMix);
			
			targetVideoBin.addPad(new GhostPad("sink_1", videoDepay.getStaticPad("sink")));
			targetVideoBin.addPad(new GhostPad("sink_2", videoDepay2.getStaticPad("sink")));
			clientPipe.add(targetVideoBin);
		}
		
		
		final Bin audioBin = new Bin("audioBin");
		
		if(attribute.equalsIgnoreCase("active")) {
			// AUDIO BIN
			
			// src1
			Element audioDepay = ElementFactory.make("rtpL16depay", "auddepay");
			
			// src2
			Element audioDepay2 = ElementFactory.make("rtpL16depay", "auddepay2");
			
			// mixer
			Element audioAdder = ElementFactory.make("adder", "audmix");
			Element audioSink = ElementFactory.make("autoaudiosink", "audsink");
			
			audioBin.addMany(audioDepay, audioDepay2, audioAdder, audioSink);
			Element.linkMany(audioDepay, audioAdder, audioSink);
			Element.linkMany(audioDepay2, audioAdder);
			
			audioBin.addPad(new GhostPad("sink_1", audioDepay.getStaticPad("sink")));
			audioBin.addPad(new GhostPad("sink_2", audioDepay2.getStaticPad("sink")));
			clientPipe.add(audioBin);
		}

		// RTPBIN1
		
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
		
		// RTPBIN2
		
		final RTPBin rtp2 = new RTPBin("rtp2");
		clientPipe.add(rtp2);
		
		Element.linkPads(tvid2, "src1", rtp2, "recv_rtp_sink_0");
		Element.linkPads(videoRtcpIn2, "src", rtp2, "recv_rtcp_sink_0");
		Element.linkPads(rtp2, "send_rtcp_src_0", audioRtcpOut2, "sink");
		
		if(attribute.equalsIgnoreCase("active")) {	// NEED TO DIFFERENTIATE DIFFERENT ACTIVE CLIENTS
			Element.linkPads(taud2, "src1", rtp2, "recv_rtp_sink_1");
			Element.linkPads(audioRtcpIn2, "src", rtp2, "recv_rtcp_sink_1");
			Element.linkPads(rtp2, "send_rtcp_src_1", audioRtcpOut2, "sink");
		}
		
		// BUS
		
		rtp.connect(new Element.PAD_ADDED() {
			@Override
			public void padAdded(Element arg0, Pad arg1) {
				if(arg1.getName().startsWith("recv_rtp_src_0")) {
	                if (t == 0) arg1.link(pilotVideoBin.getStaticPad("sink_1"));
	                if (t == 1) arg1.link(targetVideoBin.getStaticPad("sink_1"));
				} else if(arg1.getName().startsWith("recv_rtp_src_1") && attribute.equalsIgnoreCase("active")) {
					arg1.link(audioBin.getStaticPad("sink_1"));
				}
				if (t == 0) pilotClientPipe.debugToDotFile(1, "clientsucc1");
				if (t == 1) targetClientPipe.debugToDotFile(1, "clientsucc1");
			}
		});
		
		rtp2.connect(new Element.PAD_ADDED() {
			@Override
			public void padAdded(Element arg0, Pad arg1) {
				if(arg1.getName().startsWith("recv_rtp_src_0")) {
	                if (t == 0) arg1.link(pilotVideoBin.getStaticPad("sink_2"));
	                if (t == 1) arg1.link(targetVideoBin.getStaticPad("sink_2"));
				} else if(arg1.getName().startsWith("recv_rtp_src_1") && attribute.equalsIgnoreCase("active")) {
					arg1.link(audioBin.getStaticPad("sink_2"));
				}
				if (t == 0) pilotClientPipe.debugToDotFile(1, "clientsucc2");
				if (t == 0) targetClientPipe.debugToDotFile(1, "clientsucc2");
			}
		});
		
		Bus bus = clientPipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
                pushLog("> GSTREAMER ERROR: code=" + code + " message=" + message);
                if (t == 0) pilotClientPipe.debugToDotFile(1, "clienterr");
                if (t == 1) targetClientPipe.debugToDotFile(1, "clienterr");
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
            	if (t == 0) pilotClientPipe.setState(State.NULL);
            	if (t == 1) targetClientPipe.setState(State.NULL);
                System.out.println("EOS");
            }
        });
		
        if (t == 0) pilotVideoBin.add(vc.getElement());
        if (t == 1) targetVideoBin.add(vc.getElement());
        
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
		
		Element.linkMany(mixedColor, vc.getElement());
        
        Thread videoThread = new Thread() {
        	public void run() {
        		if (t == 0) pilotClientPipe.setState(org.gstreamer.State.PLAYING);
        		if (t == 1) targetClientPipe.setState(org.gstreamer.State.PLAYING);
        	}
        };
        videoThread.start();
        clientPipe.debugToDotFile(0, "appsink");
	}
	
	public static void updateResource() {
		int bandwidth = 0;
		BufferedReader br = null;
		String rscPath = "c_resource.txt";
		try {
			br = new BufferedReader(new FileReader(rscPath));
		} catch (FileNotFoundException e1) {
			String somePath = Client.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
			rscPath = somePath.substring(0, somePath.indexOf("client")) + "c_resource.txt"; // find the local directory
			try {
				br = new BufferedReader(new FileReader(rscPath));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
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
        if (pilotClientPipe != null) pilotOut.println(json_bandwidth.toString());
        if (targetClientPipe != null) targetOut.println(json_bandwidth.toString());
        pushLog("> RSRC: REQ BW " + bandwidth);
	}
	public static long getUnsignedInt(int x) {
	    return x & 0x00000000ffffffffL;
	}
	private static void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
}
