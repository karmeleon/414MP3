import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
	static int serverbw = 0;
	static String resolution = "";
	static Pipeline serverPipe = null;
	
	static String serverLoc;
	
	public static void startServer(JTextArea log, int opt) { // 0 - LAN ;; 1 - INET
		textArea = log;
		
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
		        ServerInstance thr = new ServerInstance(currentPort, clientLoc, serverLoc, skt, currThread);
		        thr.start();
		        
		        currentPort += 10;
		        currThread++;
		        
			} catch(Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	public static void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
}
