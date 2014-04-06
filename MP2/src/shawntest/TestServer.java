package shawntest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
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
import org.gstreamer.Structure;
import org.gstreamer.elements.DecodeBin2;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.elements.good.RTPBin;
import org.gstreamer.utils.GstDebugUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class TestServer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Starting testserver on port 45000...");
		try {
			ServerSocket srvr = new ServerSocket(45000);
			srvr.setReuseAddress(true);
	        Socket skt = srvr.accept();
	        System.out.println("Client has connected from " + skt.getRemoteSocketAddress().toString());
	        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
	        // using JSON because it's easy
	        JSONObject response = new JSONObject();
	        
	        File folder = new File(System.getProperty("user.dir") + "/videos/");
	        for (final File fileEntry : folder.listFiles()) {
	            if (fileEntry.isFile())
	                response.append("files", fileEntry.getName());
	        }
	        out.println(response.toString());
	        System.out.println("File list sent, awaiting response.");
	        
	        response = new JSONObject(in.readLine());
	        String toStream = response.getString("request");
	        System.out.println("Client requested " + toStream);
	        
	        Pipeline pb = startStreaming(toStream);
	        
	        // listen for commands
	        while(pb.getState() != State.READY && pb.getState() != State.NULL) {
	        	String nextCommand = in.readLine();
	        	System.out.println("Received '" + nextCommand + "'.");
	        	response = new JSONObject(nextCommand);
	        	String command = response.getString("command");
	        	switch(command) {
	        	case "play":
	        		pb.setState(State.PLAYING);
	        		break;
	        	case "pause":
	        		pb.setState(State.PAUSED);
	        		break;
	        	case "stop":
	        		pb.setState(State.NULL);
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
	
	@SuppressWarnings("deprecation")
	private static Pipeline startStreaming(String toStream) throws UnknownHostException, SocketException, InterruptedException {
		final int port = 45001;
		// create the pipeline here
		Gst.init();
		
		final Pipeline pipe = new Pipeline();
		
		Element filesrc = ElementFactory.make("filesrc", "src");
		filesrc.set("location", "videos/" + toStream);
		
		DecodeBin2 decode = new DecodeBin2("decode");
		
		final Bin videoBin = new Bin("VideoBin");
		
		Element videoenc = ElementFactory.make("jpegenc", "vencoder");
		Element videopay = ElementFactory.make("rtpjpegpay", "vpayloader");
		/*
		Element videosink = ElementFactory.make("udpsink", "vnetsink");
		videosink.set("host", "127.0.0.1");
		videosink.set("port", "" + port);
		videosink.set("sync", "true");
		*/
		
		videoBin.addMany(videoenc, videopay);
		Element.linkMany(videoenc, videopay);
		videoBin.addPad(new GhostPad("sink", videoenc.getStaticPad("sink")));
		videoBin.addPad(new GhostPad("src", videopay.getStaticPad("src")));
		pipe.add(videoBin);
		
		final Bin audioBin = new Bin("AudioBin");
		
		Element audConv = ElementFactory.make("audioconvert", "audioconv");
        Element audPayload = ElementFactory.make("rtpL16pay", "audpay");
        /*
        Element audSink = ElementFactory.make("udpsink", "aududpsink");
        audSink.set("host", "127.0.0.1");
        audSink.set("port", "" + (port + 1));
        audSink.set("sync", "true");
        */
        
        audioBin.addMany(audConv, audPayload);
        Element.linkMany(audConv, audPayload);
        audioBin.addPad(new GhostPad("sink", audConv.getStaticPad("sink")));
        audioBin.addPad(new GhostPad("src", audPayload.getStaticPad("src")));
        pipe.add(audioBin);
        
        decode.connect(new DecodeBin2.NEW_DECODED_PAD() {
			
			@Override
			public void newDecodedPad(DecodeBin2 elem, Pad pad, boolean last) {
				// TODO Auto-generated method stub
				if(pad.isLinked())
					return;
				
				Caps caps = pad.getCaps();
				Structure struct = caps.getStructure(0);
				if(struct.getName().startsWith("audio/")) {
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
		//videoRtcpIn.set("port", "" + (port + 5));
		videoRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 5));
		
		Element audioDataOut = ElementFactory.make("udpsink", "audiodatout");
		audioDataOut.set("host", "127.0.0.1");
		audioDataOut.set("port", "" + (port + 2));
		
		Element audioRtcpOut = ElementFactory.make("udpsink", "audiortcpout");
		audioRtcpOut.set("host", "127.0.0.1");
		audioRtcpOut.set("port", "" + (port + 3));
		audioRtcpOut.set("sync", "false");
		audioRtcpOut.set("async", "false");
		
		Element audioRtcpIn = ElementFactory.make("udpsrc", "audiortcpin");
		//audioRtcpIn.set("port", "" + (port + 7));
		audioRtcpIn.set("uri", "udp://127.0.0.1:" + (port + 7));
		
		pipe.addMany(videoDataOut, videoRtcpOut, videoRtcpIn, audioDataOut, audioRtcpOut, audioRtcpIn);
		
		Element.linkPads(videoBin, "src", rtp, "send_rtp_sink_0");
		Element.linkPads(rtp, "send_rtp_src_0", videoDataOut, "sink");
		Element.linkPads(rtp, "send_rtcp_src_0", videoRtcpOut, "sink");
		Element.linkPads(videoRtcpIn, "src", rtp, "recv_rtcp_sink_0");
		
		Element.linkPads(audioBin, "src", rtp, "send_rtp_sink_1");
		Element.linkPads(rtp, "send_rtp_src_1", audioDataOut, "sink");
		Element.linkPads(rtp, "send_rtcp_src_1", audioRtcpOut, "sink");
		Element.linkPads(audioRtcpIn, "src", rtp, "recv_rtcp_sink_1");
		
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
		
        GstDebugUtils.gstDebugBinToDotFile(pipe, 0, "server");
        
		pipe.play();
        Gst.main();
        return pipe;
	}

}
