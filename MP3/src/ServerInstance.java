import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.gstreamer.Bin;
import org.gstreamer.Bus;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Format;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pipeline;
import org.gstreamer.SeekFlags;
import org.gstreamer.SeekType;
import org.gstreamer.elements.good.RTPBin;
import org.json.JSONObject;

import au.edu.jcu.v4l4j.VideoDevice;


public class ServerInstance extends Thread {

	private String resolution;
	private String serverLoc, clientLoc;
	private int port;
	private Socket skt;
	private int clientBW;
	private boolean seeking;
	public Pipeline serverPipe;
	private int threadNum;
	private Element videorate;
	private boolean moveable;
	
	ServerInstance(int startPort, String clientIP, String serverIP, Socket skt, int num, boolean moveable) {
		serverLoc = serverIP;
		clientLoc = clientIP;
		port = startPort;
		this.skt = skt;
		threadNum = num;
		this.moveable = moveable;
	}
	
	public void run() {
		Server.pushLog("> (" + threadNum + ") NEW THREAD FOR CLIENT " + clientLoc);
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	        
	        JSONObject portNeg = new JSONObject();
	        portNeg.put("port", port);
	        out.println(portNeg.toString());
	        System.out.println("Server port: " + port);
	        
	        JSONObject json_settings = new JSONObject(in.readLine());
	        String settings = json_settings.getString("settings");
	        Server.pushLog("> (" + threadNum + ") SYS: REQ " + settings);
			
			Pipeline pb = startStreaming(settings);
			if (pb.getState() == org.gstreamer.State.PLAYING) System.out.println("Server side OK!");
	        
	        // listen for commands
	        JSONObject json_msg;
	        while(pb.getState() != org.gstreamer.State.READY && pb.getState() != org.gstreamer.State.NULL) {
	        	String nextMsg = in.readLine();
	        	if(nextMsg == null) {
	        		Server.pushLog("> (" + threadNum + ") CLIENT DISCONNECTED");
	        		pb.setState(org.gstreamer.State.PAUSED);
	        		pb.setState(org.gstreamer.State.NULL);
	        		break;
	        	}
	        	Server.pushLog("> (" + threadNum + ") SYS: GOT " + nextMsg);
	        	
	        	json_msg = new JSONObject(nextMsg);
	        	String command = json_msg.getString("command");
	        	int amount = 0;
	        	
	        	try {
	        		amount = json_msg.getInt("amount");
	        	} catch (Exception ex) {
	        		// this isn't a camera movement command, ignore it
	        	}
	        	
	        	
	        	try {
	        		clientBW = Integer.parseInt(command);
	        		Bin videoBin = (Bin) pb.getElementByName("VideoBin");
	        		negotiate();
	        	} catch(Exception ex) {
	        		// this isn't an fps command, ignore it here
	        	}
	        	
	        	switch(command) {
	        	case "play":
	        		if (seeking) {
	        			seeking = false;
	        			pb.seek(1.0, Format.TIME, SeekFlags.ACCURATE | SeekFlags.FLUSH, SeekType.SET, pb.queryPosition(Format.TIME), SeekType.NONE, 0);
	        		}
	        		pb.setState(org.gstreamer.State.PLAYING);
	        		break;
	        	case "pause":
	        		pb.setState(org.gstreamer.State.PAUSED);
	        		break;
	        	case "stop":
	        		pb.setState(org.gstreamer.State.PAUSED);
	        		pb.setState(org.gstreamer.State.NULL);
	        		break;
	        	case "ff":
	        		seeking = true;
	        		pb.seek(2.0, Format.TIME, SeekFlags.ACCURATE | SeekFlags.FLUSH, SeekType.SET, pb.queryPosition(Format.TIME), SeekType.NONE, 0);
	        		break;
	        	case "rw":
	        		seeking = true;
	        		pb.seek(-2.0, Format.TIME, SeekFlags.ACCURATE | SeekFlags.FLUSH, SeekType.SET, 0, SeekType.SET, pb.queryPosition(Format.TIME));
	        		break;
	        	case "reset":
	        		if(moveable) {
	        			VideoDevice vid = new VideoDevice("/dev/video0");
		        		vid.getControlList().getControl("Pan/tilt Reset").setValue(1);
		        		vid.releaseControlList();
		        		vid.release();
	        		}
	        		break;
	        	case "pan":
	        		if(moveable) {
	        			VideoDevice vd = new VideoDevice("/dev/video0");
		        		vd.getControlList().getControl("Pan (relative)").setValue(amount);
		        		vd.releaseControlList();
		        		vd.release();
	        		}
	        		break;
	        	case "tilt":
	        		if(moveable) {
	        			VideoDevice vde = new VideoDevice("/dev/video0");
		        		vde.getControlList().getControl("Tilt (relative)").setValue(amount);
		        		vde.releaseControlList();
		        		vde.release();
	        		}
	        		break;
	        	default:
	        		break;
	        	}
	        }
	        
	        in.close();
	        out.close();
	        skt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Pipeline startStreaming(String settings) throws UnknownHostException, SocketException, InterruptedException {
		String[] s = settings.split(" ");
		resolution = s[0];          // 240p/480p
		final String attribute = s[1];     // Passive/Active
		clientBW = Integer.parseInt(s[2]); // Some amount
		String[] args = {"--gst-plugin-path=/usr/local/lib/gstreamer-0.10"};
		Gst.init("server", args);
		
		serverPipe = new Pipeline();
		
		final Bin videoBin = new Bin("VideoBin");
		
		// camera input
		Element videoSrc = ElementFactory.make("v4l2src", "cam");
		Element videoColors = ElementFactory.make("ffmpegcolorspace", "vidcolors");
		Element videoScale = ElementFactory.make("videoscale", "vidscale");
		Element videoCaps = ElementFactory.make("capsfilter", "vidcaps");
		videoCaps.setCaps(Caps.fromString("video/x-raw-yuv,width=640,height=480"));
		Element videoColors4 = ElementFactory.make("ffmpegcolorspace", "vidcolors4");
		videoBin.addMany(videoSrc, videoColors, videoScale, videoCaps, videoColors4);
		Element.linkMany(videoSrc, videoColors);
		
		if(moveable) {
			Element motion = ElementFactory.make("motiondetector", "motion");
			motion.set("draw_motion", "true");
			motion.set("rate_limit", "500");
			motion.set("threshold", "128");
			Element videoColors5 = ElementFactory.make("ffmpegcolorspace", "vidcolors5");
			Element videoOverlay = ElementFactory.make("rsvgoverlay", "vidoverlay");
			videoOverlay.set("fit-to-frame", "true");
			videoOverlay.set("data", "<svg viewBox=\"0 0 640 480\"><image x=\"0\" y=\"0\" width=\"100%\" height=\"100%\" xlink:href=\"overlay1.png\" /></svg>");
			Element videoColors2 = ElementFactory.make("ffmpegcolorspace", "vidcolors2");
			Element balance = ElementFactory.make("videobalance", "balance");
			balance.set("saturation", "0.0");
			balance.set("contrast", "1.5");
			Element videoColors3 = ElementFactory.make("ffmpegcolorspace", "vidcolors3");
			videoBin.addMany(motion, videoColors5, videoOverlay, videoColors2, balance, videoColors3);
			Element.linkMany(videoColors, motion, videoColors5, videoOverlay, videoColors2, videoScale, videoCaps, videoColors3, balance, videoColors4);
		}
		else
			Element.linkMany(videoColors, videoScale, videoCaps, videoColors4);
		
		//Element.linkMany(videoSrc, videoColors, motion, videoColors5, videoOverlay, videoColors2, videoScale, videoCaps, videoColors3, balance, videoColors4);
		
		
		videorate = ElementFactory.make("videorate", "rate");
		
		/** asdf set fps here **/
		if(attribute.equalsIgnoreCase("passive")) {
			videorate.set("force-fps", "10");
		}
		else {
			negotiate();
		}
		Element videoenc = ElementFactory.make("jpegenc", "vencoder");
		videoenc.set("quality", "95");
		Element videopay = ElementFactory.make("rtpjpegpay", "vpayloader");
		
		videoBin.addMany(videorate, videoenc, videopay);
		Element.linkMany(videoColors4, videorate, videoenc, videopay);
		videoBin.addPad(new GhostPad("src", videopay.getStaticPad("src")));
		serverPipe.add(videoBin);
		
		final Bin audioBin = new Bin("AudioBin");
		
		if(attribute.equalsIgnoreCase("active")) {
			Element audSrc = ElementFactory.make("alsasrc", "audsrc");
			Element audRate = ElementFactory.make("audioresample", "audiorate");
			Element audCaps = ElementFactory.make("capsfilter", "audcaps");
			audCaps.setCaps(Caps.fromString("audio/x-raw-int, rate=8000"));
			Element audConv = ElementFactory.make("audioconvert", "audioconv");
	        Element audPayload = ElementFactory.make("rtpL16pay", "audpay");
	        
	        audioBin.addMany(audSrc, audRate, audCaps, audConv, audPayload);
	        Element.linkMany(audSrc, audRate, audCaps, audConv, audPayload);
	        audioBin.addPad(new GhostPad("src", audPayload.getStaticPad("src")));
	        serverPipe.add(audioBin);
		}
		
		RTPBin rtp = new RTPBin("rtp");
		serverPipe.add(rtp);
		
		// UDP ELEMENTS
		Element videoDataOut = ElementFactory.make("udpsink", "videodatout");
		videoDataOut.set("host", clientLoc);
		videoDataOut.set("port", "" + port);
		
		Element videoRtcpOut = ElementFactory.make("udpsink", "videortcpout");
		videoRtcpOut.set("host", clientLoc);
		videoRtcpOut.set("port", "" + (port + 1));
		videoRtcpOut.set("sync", "false");
		videoRtcpOut.set("async", "false");
		
		Element videoRtcpIn = ElementFactory.make("udpsrc", "videortcpin");
		videoRtcpIn.set("uri", "udp://" + serverLoc + ":" + (port + 5));
		
		serverPipe.addMany(videoDataOut, videoRtcpOut, videoRtcpIn);
		
		Element.linkPads(videoBin, "src", rtp, "send_rtp_sink_0");
		Element.linkPads(rtp, "send_rtp_src_0", videoDataOut, "sink");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		
		if(attribute.equalsIgnoreCase("active")) {
			Element audioDataOut = ElementFactory.make("udpsink", "audiodatout");
			audioDataOut.set("host", clientLoc);
			audioDataOut.set("port", "" + (port + 2));
			
			Element audioRtcpOut = ElementFactory.make("udpsink", "audiortcpout");
			audioRtcpOut.set("host", clientLoc);
			audioRtcpOut.set("port", "" + (port + 3));
			audioRtcpOut.set("sync", "false");
			audioRtcpOut.set("async", "false");
			
			Element audioRtcpIn = ElementFactory.make("udpsrc", "audiortcpin");
			audioRtcpIn.set("uri", "udp://" + serverLoc + ":" + (port + 7));
			serverPipe.addMany(audioDataOut, audioRtcpOut, audioRtcpIn);
			
			Element.linkPads(audioBin, "src", rtp, "send_rtp_sink_1");
			Element.linkPads(rtp, "send_rtp_src_1", audioDataOut, "sink");
			Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
			Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
		}
		
		Bus bus = serverPipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
                Server.pushLog("> (" + threadNum + ") GSTREAMER ERROR: code=" + code + " message=" + message);
                serverPipe.debugToDotFile(1, "servererr");
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
            	serverPipe.setState(org.gstreamer.State.NULL);
            	Server.pushLog("> (" + threadNum + ") EOS, TERMINATING");
            	Thread.currentThread().interrupt();
            }

        });
        
        serverPipe.play();
        //Gst.main();
        return serverPipe;
	}
	
	public void negotiate() {
		// kilobits per second
		// 720 - 1000 kbps -> 30fps
		// 480 - 750 kbps -> 30fps
		// 360 - 500 kbps -> 30fps
		// 240 -  250 kbps -> 30fps
		double cap = 30;
		int res = Integer.parseInt(resolution.substring(0, 3));
		int setfps = 0;
		if (res == 720)
			setfps = Math.min((int) cap, Math.min((int) (Server.serverBW * cap / 1000.0), (int) (clientBW * cap / 1000.0)));
		if (res == 480)
			setfps = Math.min((int) cap, Math.min((int) (Server.serverBW * cap / 1000.0), (int) (clientBW * cap / 750.0)));
		if (res == 360)
			setfps = Math.min((int) cap, Math.min((int) (Server.serverBW * cap / 1000.0), (int) (clientBW * cap / 500.0)));
		if (res == 240)
			setfps = Math.min((int) cap, Math.min((int) (Server.serverBW * cap / 1000.0), (int) (clientBW * cap / 250.0)));
		if (serverPipe != null) {
			videorate.set("force-fps", "" + setfps);
			if (Server.textArea != null)
				Server.pushLog("> (" + threadNum + ") SYS: NEGT FPS " + setfps);
		}
	}
}
