import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.swing.JTextArea;

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
import org.gstreamer.State;
import org.gstreamer.Structure;
import org.gstreamer.elements.DecodeBin2;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.elements.good.RTPBin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server {

	/**
	 * @param args
	 */
	static PrintWriter out;
	static int clientbw = 0;
	static boolean closeResourceInfoStream = true;
	static JTextArea textArea = null;
	static boolean seeking = false;
	
	public static void startServer(JTextArea log) {
		textArea = log;
		// System.out.println("Starting testserver on port 45000...");
		while(true) {
			try {
				log.setText("");
				pushLog("> CTRL: Starting Server ...");
				ServerSocket srvr = new ServerSocket(45000);
				srvr.setReuseAddress(true);
		        Socket skt = srvr.accept();
		        pushLog("> SYS: CNCT FROM " + skt.getRemoteSocketAddress().toString());
		        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
		        out = new PrintWriter(skt.getOutputStream(), true);
		        
		        
		        JSONObject json_settings = new JSONObject(in.readLine());
		        String settings = json_settings.getString("settings");
		        pushLog("> SYS: REQ " + settings);
		        
		        Pipeline pb = startStreaming(settings);
		        
		        // listen for commands
		        JSONObject json_msg;
		        while(pb.getState() != State.READY && pb.getState() != State.NULL) {
		        	//pb.debugToDotFile(0, "server");
		        	String nextMsg = in.readLine();
		        	pushLog("> SYS: GOT " + nextMsg);
		        	
		        	json_msg = new JSONObject(nextMsg);
		        	String command = json_msg.getString("command");
		        	
		        	try {
		        		// int fps = json_msg.getInt("fps");
		        		int fps = Integer.parseInt(command);
		        		Bin videoBin = (Bin) pb.getElementByName("VideoBin");
		        		videoBin.getElementByName("rate").set("force-fps", "" + fps);
		        		pushLog("> SYS: SET BW " + command);
		        	} catch(Exception e) {
		        		// this isn't an fps command, ignore it here
		        	}
		        	
		        	
		        	switch(command) {
		        	case "play":
		        		if (seeking) {
		        			seeking = false;
		        			pb.seek(1.0, Format.TIME, SeekFlags.ACCURATE | SeekFlags.FLUSH, SeekType.SET, pb.queryPosition(Format.TIME), SeekType.NONE, 0);
		        		}
		        		pb.setState(State.PLAYING);
		        		break;
		        	case "pause":
		        		pb.setState(State.PAUSED);
		        		break;
		        	case "stop":
		        		pb.setState(State.PAUSED);
		        		pb.setState(State.NULL);
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
		        srvr.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	private static Pipeline startStreaming(String settings) throws UnknownHostException, SocketException, InterruptedException {
		// boolean closeResourceInfoStream = false;
		String[] s = settings.split(" ");
		String resolution = s[0];               // 240p/480p
		final String attribute = s[1];                // Passive/Active
		clientbw = Integer.parseInt(s[2]); // Some amount
		
		final int port = 45001;
		// create the pipeline here
		Gst.init();
		
		final Pipeline pipe = new Pipeline();
		
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
		
		Element videoenc = ElementFactory.make("jpegenc", "vencoder");
		Element videopay = ElementFactory.make("rtpjpegpay", "vpayloader");
		
		videoBin.addMany(videorate, videoenc, videopay);
		Element.linkMany(videorate, videoenc, videopay);
		videoBin.addPad(new GhostPad("sink", videorate.getStaticPad("sink")));
		videoBin.addPad(new GhostPad("src", videopay.getStaticPad("src")));
		pipe.add(videoBin);
		
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
	        pipe.add(audioBin);
		}
        decode.connect(new DecodeBin2.NEW_DECODED_PAD() {
			
			@Override
			public void newDecodedPad(DecodeBin2 elem, Pad pad, boolean last) {
				// TODO Auto-generated method stub
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
		
		pipe.addMany(filesrc, decode);
		Element.linkMany(filesrc, decode);
		
		// https://github.com/ClementNotin/gstreamer-rtp-experiments/blob/master/src/main/java/RoomSender.java
		RTPBin rtp = new RTPBin("rtp");
		pipe.add(rtp);
		
		// UDP ELEMENTS
		Element videoDataOut = ElementFactory.make("udpsink", "videodatout");
		videoDataOut.set("host", "127.0.0.1");
		videoDataOut.set("port", "" + port);
		
		Element videoRtcpOut = ElementFactory.make("udpsink", "videortcpout");
		videoRtcpOut.set("host", "127.0.0.1");
		videoRtcpOut.set("port", "" + (port + 1));
		videoRtcpOut.set("sync", "false");
		videoRtcpOut.set("async", "false");
		
		Element videoRtcpIn = ElementFactory.make("udpsrc", "videortcpin");
		videoRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 5));
		
		pipe.addMany(videoDataOut, videoRtcpOut, videoRtcpIn);
		
		Element.linkPads(videoBin, "src", rtp, "send_rtp_sink_0");
		Element.linkPads(rtp, "send_rtp_src_0", videoDataOut, "sink");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		
		if(attribute.equalsIgnoreCase("active")) {
			Element audioDataOut = ElementFactory.make("udpsink", "audiodatout");
			audioDataOut.set("host", "127.0.0.1");
			audioDataOut.set("port", "" + (port + 2));
			
			Element audioRtcpOut = ElementFactory.make("udpsink", "audiortcpout");
			audioRtcpOut.set("host", "127.0.0.1");
			audioRtcpOut.set("port", "" + (port + 3));
			audioRtcpOut.set("sync", "false");
			audioRtcpOut.set("async", "false");
			
			Element audioRtcpIn = ElementFactory.make("udpsrc", "audiortcpin");
			audioRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 7));
			pipe.addMany(audioDataOut, audioRtcpOut, audioRtcpIn);
			
			Element.linkPads(audioBin, "src", rtp, "send_rtp_sink_1");
			Element.linkPads(rtp, "send_rtp_src_1", audioDataOut, "sink");
			Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
			Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
		}
		
		Bus bus = pipe.getBus();
        
        bus.connect(new Bus.ERROR() {
            public void errorMessage(GstObject source, int code, String message) {
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
        
		pipe.play();
        //Gst.main();
        return pipe;
	}
	
	public static void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
}
