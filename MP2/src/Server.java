import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.gstreamer.Bin;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.State;
import org.gstreamer.elements.PlayBin2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server {

	/**
	 * @param args
	 */
	static int clientbw = 0;
	static boolean closeResourceInfoStream = true;
	
	public static void startServer() {
		System.out.println("Starting testserver on port 45000...");
		try {
			ServerSocket srvr = new ServerSocket(45000);
			srvr.setReuseAddress(true);
	        Socket skt = srvr.accept();
	        System.out.println("Client has connected from " + skt.getRemoteSocketAddress().toString());
	        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        PrintWriter out = new PrintWriter(skt.getOutputStream(), true);

	        JSONObject response = new JSONObject(in.readLine());
	        String settings = response.getString("request");
	        System.out.println("> SYS: REQ " + settings);
	        
	        PlayBin2 pb = startStreaming(settings);   
	        
	       //  System.out.println("HERE1");
	        /*
	        ServerSocket bandwidthListener = new ServerSocket(45777);
			bandwidthListener.setReuseAddress(true);
	        Socket bandwidthSKT = bandwidthListener.accept();
	        final BufferedReader bandwidthReader = new BufferedReader(new InputStreamReader(bandwidthSKT.getInputStream()));
	        Thread bandwidthThread = new Thread() {
	        	public void run() {
	        		System.out.println("BW Thread Started");
	        		while(!closeResourceInfoStream) {
	        			try {
							JSONObject response = new JSONObject(bandwidthReader.readLine());
							String bwstring = response.getString("request");
							clientbw = Integer.parseInt(bwstring);
							System.out.println("> SYS: Bandwidth " + clientbw);
						} 
	        			catch (JSONException e) { e.printStackTrace(); } 
	        			catch (IOException e) { e.printStackTrace(); }
	        		}
	        	}
	        };
	        bandwidthThread.start();
	        */
	        // System.out.println("HERE");
	        
	        // listen for commands
	        while(pb.getState() != State.NULL) {
	        	System.out.println("Listening for more ctrl data...");
	        	String nextCommand = in.readLine();
	        	System.out.println("Received '" + nextCommand + "'.");
	        	response = new JSONObject(nextCommand);
	        	String bw = response.getString("bandwidth");
	        	clientbw = Integer.parseInt(bw);
	        	System.out.println("> RSRC: REQ " + clientbw);
	        	/*
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
	        	*/
	        }
	        
	        in.close();
	        out.close();
	        skt.close();
	        srvr.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private static PlayBin2 startStreaming(String settings) throws UnknownHostException, SocketException, InterruptedException {
		boolean closeResourceInfoStream = false;
		String[] s = settings.split(" ");
		String resolution = s[0];               // 240p/480p
		String attribute = s[1];                // Passive/Active
		clientbw = Integer.parseInt(s[2]); // Some amount
		
		final int port = 45001;
		// create the pipeline here
		Gst.init();
		final PlayBin2 playbin = new PlayBin2("VideoPlayer");
        playbin.setInputFile(new File("videos/" + resolution + ".mp4"));
        
        Bin vidBin = new Bin("vidbin");
        
        Element encoder = ElementFactory.make("jpegenc", "encoder");
		Element payloader = ElementFactory.make("rtpjpegpay", "payloader");
		Element sink = ElementFactory.make("udpsink", "netsink");
		sink.set("host", "127.0.0.1");
		sink.set("port", "" + port);
		sink.set("sync", "true");
		vidBin.addMany(encoder, payloader, sink);
		Element.linkMany(encoder, payloader, sink);
		vidBin.addPad(new GhostPad("sink", encoder.getStaticPad("sink")));
        
        playbin.setVideoSink(vidBin);
        
        if (attribute.equalsIgnoreCase("active")) {
        	Bin audBin = new Bin("audbin");
            // assuming raw audio unless told otherwise
            
            Element audConv = ElementFactory.make("audioconvert", "audioconv");
            Element audPayload = ElementFactory.make("rtpL16pay", "audpay");
            Element audSink = ElementFactory.make("udpsink", "aududpsink");
            audSink.set("host", "127.0.0.1");
            audSink.set("port", "" + (port + 1));
            audSink.set("sync", "true");
            audBin.addMany(audConv, audPayload, audSink);
            Element.linkMany(audConv, audPayload, audSink);
            audBin.addPad(new GhostPad("sink", audConv.getStaticPad("sink")));
            
            playbin.setAudioSink(audBin);
        }
        
        playbin.setState(State.PLAYING);
        // playbin.set
        return playbin;
	}

}
