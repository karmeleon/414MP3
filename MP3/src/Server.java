import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import javax.swing.JTextArea;

import org.gstreamer.Pipeline;

public class Server {

	/**
	 * @param args
	 */
	static int clientbw = 0;
	static boolean closeResourceInfoStream = true;
	static JTextArea textArea = null;
	static boolean seeking = false;
	static int serverBW = 0;
	static String resolution = "";
	static Pipeline serverPipe = null;
	
	static String serverLoc;
	static ArrayList<ServerInstance> instances;
	
	public static void startServer(JTextArea log, int opt, int cam) { // 0 - LAN ;; 1 - INET
		textArea = log;
		
		instances = new ArrayList<ServerInstance>();
		updateResource();
		
		log.setText("");
		pushLog("> CTRL: Starting Server ...");
		InetAddress inet = null;
		// find this ip
		if (opt == 1) {
			Enumeration<NetworkInterface> e = null;
			try {
				e = NetworkInterface.getNetworkInterfaces();
			} catch (SocketException e3) {
				e3.printStackTrace();
			}
			while(e.hasMoreElements())
			{
				NetworkInterface n = (NetworkInterface) e.nextElement();
				Enumeration<InetAddress> ee = n.getInetAddresses();
				while (ee.hasMoreElements())
				{
					InetAddress i = (InetAddress) ee.nextElement();
					if(!i.isLinkLocalAddress() && !i.isLoopbackAddress()) {
						pushLog("SRVR START IP:" + i.getHostAddress());
					    serverLoc = i.getHostAddress();
					    inet = i;
					}
				}
			}
		}
		else if (opt == 0) {
			serverLoc = "127.0.0.1";
		}
		
		ServerSocket srvr = null;
		try {
			if (opt == 1) srvr = new ServerSocket(45000, 1, inet);
			else if (opt == 0) srvr = new ServerSocket(45000);
			srvr.setReuseAddress(true);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		
		int currentPort = 45010;	// the server uses port to port+7
		int currThread = 0;
		
		while(true) { // y = 2.4 * x + 240
			try {
				// System.out.println("Waiting to recieve ...");
		        Socket skt = srvr.accept();
		        // System.out.println("Connected! ...");
		        String clientLoc = "";
		        if (opt == 1) {
		        	pushLog("> SYS: CNCT FROM " + skt.getRemoteSocketAddress().toString());
		        	clientLoc = skt.getRemoteSocketAddress().toString();
			        clientLoc = clientLoc.substring(1, clientLoc.indexOf(":"));
		        }
		        else if (opt == 0) {
		        	clientLoc = "127.0.0.1";
		        }
		        ServerInstance thr = new ServerInstance(currentPort, clientLoc, serverLoc, skt, currThread, (cam == 1));
		        instances.add(thr);
		        thr.start();
		        
		        currentPort += 10;
		        currThread++;
		        
			} catch(Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	public static void updateResource() {
		int bandwidth = 0;
		BufferedReader br = null;
		String rscPath = "s_resource.txt";
		try {
			br = new BufferedReader(new FileReader(rscPath));
		} catch (FileNotFoundException e1) {
			String somePath = ClientLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
			rscPath = somePath.substring(0, somePath.indexOf("server")) + "s_resource.txt"; // find the local directory
			try {
				br = new BufferedReader(new FileReader(rscPath));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
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
		
		serverBW = bandwidth;
        negotiateAll();
	}
	
	public static void negotiateAll() {
		for (int i = 0; i < instances.size(); i++) {
			ServerInstance si = instances.get(i);
			if (si != null && si.serverPipe != null && si.serverPipe.getState() != org.gstreamer.State.NULL) {
				si.negotiate();
			}
		}
	}
	
	public static void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
}
