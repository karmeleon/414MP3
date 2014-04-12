import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import org.gstreamer.Pad;
import org.gstreamer.Pipeline;
import org.gstreamer.SeekFlags;
import org.gstreamer.SeekType;
import org.gstreamer.Structure;
import org.gstreamer.elements.DecodeBin2;
import org.gstreamer.elements.good.RTPBin;
import org.json.JSONObject;


public class ServerInstance extends Thread {

	private String resolution;
	String serverLoc, clientLoc;
	int port;
	Socket skt;
	private int clientbw;
	private int serverbw;
	private boolean seeking;
	private Pipeline serverPipe;
	
	ServerInstance(int startPort, String clientIP, String serverIP, Socket skt) {
		serverLoc = serverIP;
		clientLoc = clientIP;
		port = startPort;
		this.skt = skt;
		System.out.println("server: " + serverLoc + " client: " + clientLoc);
	}
	
	public void run() {
		Server.pushLog("> NEW THREAD FOR CLIENT " + clientLoc);
		try {
			updateResource();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	        
	        JSONObject portNeg = new JSONObject();
	        portNeg.put("port", port);
	        out.println(portNeg.toString());
	        
	        JSONObject json_settings = new JSONObject(in.readLine());
	        String settings = json_settings.getString("settings");
	        Server.pushLog("> SYS: REQ " + settings);
			
			Pipeline pb = startStreaming(settings);
	        
	        // listen for commands
	        JSONObject json_msg;
	        while(pb.getState() != org.gstreamer.State.READY && pb.getState() != org.gstreamer.State.NULL) {
	        	String nextMsg = in.readLine();
	        	Server.pushLog("> SYS: GOT " + nextMsg);
	        	
	        	json_msg = new JSONObject(nextMsg);
	        	String command = json_msg.getString("command");
	        	
	        	try {
	        		clientbw = Integer.parseInt(command);
	        		Bin videoBin = (Bin) pb.getElementByName("VideoBin");
	        		negotiate(videoBin.getElementByName("rate"), clientbw, serverbw);
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
	        	default :
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

	@SuppressWarnings("deprecation")
	private Pipeline startStreaming(String settings) throws UnknownHostException, SocketException, InterruptedException {
		String[] s = settings.split(" ");
		resolution = s[0];          // 240p/480p
		final String attribute = s[1];     // Passive/Active
		clientbw = Integer.parseInt(s[2]); // Some amount
		Gst.init();
		
		serverPipe = new Pipeline();
		
		Element filesrc = ElementFactory.make("filesrc", "src");
		filesrc.set("location", "videos/" + resolution + ".mp4");
		
		DecodeBin2 decode = new DecodeBin2("decode");
		
		final Bin videoBin = new Bin("VideoBin");
		
		Element videorate = ElementFactory.make("videorate", "rate");
		
		/** asdf set fps here **/
		videorate.set("force-fps", "" + clientbw);
		if(attribute.equalsIgnoreCase("passive")) {
			videorate.set("force-fps", "10");
		}
		else {
			negotiate(videorate, clientbw, serverbw);
		}
		
		Element videoenc = ElementFactory.make("jpegenc", "vencoder");
		Element videopay = ElementFactory.make("rtpjpegpay", "vpayloader");
		
		videoBin.addMany(videorate, videoenc, videopay);
		Element.linkMany(videorate, videoenc, videopay);
		videoBin.addPad(new GhostPad("sink", videorate.getStaticPad("sink")));
		videoBin.addPad(new GhostPad("src", videopay.getStaticPad("src")));
		serverPipe.add(videoBin);
		
		final Bin audioBin = new Bin("AudioBin");
		
		if(attribute.equalsIgnoreCase("active")) {
			Element audRate = ElementFactory.make("audioresample", "audiorate");
			audRate.setCaps(Caps.fromString("audio/x-raw-int, rate=8000"));
			Element audConv = ElementFactory.make("audioconvert", "audioconv");
	        Element audPayload = ElementFactory.make("rtpL16pay", "audpay");
	        
	        audioBin.addMany(audRate, audConv, audPayload);
	        Element.linkMany(audRate, audConv, audPayload);
	        audioBin.addPad(new GhostPad("sink", audRate.getStaticPad("sink")));
	        audioBin.addPad(new GhostPad("src", audPayload.getStaticPad("src")));
	        serverPipe.add(audioBin);
		}
        decode.connect(new DecodeBin2.NEW_DECODED_PAD() {
			
			@Override
			public void newDecodedPad(DecodeBin2 elem, Pad pad, boolean last) {
				if(pad.isLinked())
					return;
				
				Caps caps = pad.getCaps();
				Structure struct = caps.getStructure(0);
				if(struct.getName().startsWith("audio/") && attribute.equalsIgnoreCase("active")) {
					System.out.println("Linking audio pad: " + struct.getName());
					pad.link(audioBin.getStaticPad("sink"));
				} else if(struct.getName().startsWith("video/")) {
					System.out.println("Linking video pad: " + struct.getName());
					pad.link(videoBin.getStaticPad("sink"));
				} else {
					System.out.println("Unknown pad [" + struct.getName() + "]");
				}
			}
		});
		
        serverPipe.addMany(filesrc, decode);
		Element.linkMany(filesrc, decode);
		
		// https://github.com/ClementNotin/gstreamer-rtp-experiments/blob/master/src/main/java/RoomSender.java
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
                System.out.println("Error: code=" + code + " message=" + message);
            }
        });
        bus.connect(new Bus.EOS() {

            public void endOfStream(GstObject source) {
            	serverPipe.setState(org.gstreamer.State.NULL);
                System.out.println("EOS");
            }

        });
        
        serverPipe.play();
        //Gst.main();
        return serverPipe;
	}
	
	public void negotiate(Element videorate,  int cbw, int sbw) {
		// kilobits per second
		// 720 - 1000 kbps -> 30fps
		// 480 - 750 kbps -> 30fps
		// 360 - 500 kbps -> 30fps
		// 240 -  250 kbps -> 30fps
		double cap = 30;
		int res = Integer.parseInt(resolution.substring(0, 3));
		int setfps = 0;
		if (res == 720)
			setfps = Math.min((int) cap, Math.min((int) (serverbw * cap / 1000.0), (int) (clientbw * cap / 1000.0)));
		if (res == 480)
			setfps = Math.min((int) cap, Math.min((int) (serverbw * cap / 1000.0), (int) (clientbw * cap / 750.0)));
		if (res == 360)
			setfps = Math.min((int) cap, Math.min((int) (serverbw * cap / 1000.0), (int) (clientbw * cap / 500.0)));
		if (res == 240)
			setfps = Math.min((int) cap, Math.min((int) (serverbw * cap / 1000.0), (int) (clientbw * cap / 250.0)));
		videorate.set("force-fps", "" + setfps);
		if (Server.textArea != null)
			Server.pushLog("> SYS: NEGT FPS " + setfps);
	}
	
	public void updateResource() {
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
		
		serverbw = bandwidth;
        if (Server.textArea != null)
        	Server.pushLog("> RSRC: SET BW " + bandwidth);
        
        if (serverPipe != null) {
        	if (serverPipe.getState() != org.gstreamer.State.NULL) {
        		Bin videoBin = (Bin) serverPipe.getElementByName("VideoBin");
        		negotiate(videoBin.getElementByName("rate"), clientbw, serverbw);
        	}
        }
	}
}
