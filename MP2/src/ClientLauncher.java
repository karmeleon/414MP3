import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Timer;

import javax.swing.*;

import org.gstreamer.Element;
import org.gstreamer.Gst;
import org.gstreamer.swing.VideoComponent;
import org.json.JSONObject;


public class ClientLauncher extends JFrame{
	private static final long serialVersionUID = 5429778737562008920L;
	
	String currpath;
	int bandwidth = 0;
	String logtext = "";
	JTextArea textArea;
	JPanel videoPanel;
	String rsrc;
	JComboBox<String> resCombo;
	JComboBox<String> actCombo;
	Socket bandwidthSKT = null;
	PrintWriter bandwidthWriter;
	
	Map<String, JButton> buttons;
	boolean playing = false;
	boolean connected = false;
	VideoComponent vc;
	JLabel mon1; JLabel mon2; JLabel mon3; JLabel mon4;
	Timer monitor;
	
	public ClientLauncher() throws IOException {
		super("Client");
		Gst.init();
		currpath = System.getProperty("user.dir");
		setSize(1200,800);
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		buttons = new HashMap<String, JButton>();
		
		JPanel mainContainer = new JPanel();
		mainContainer.setPreferredSize(new Dimension(120, 800));
		mainContainer.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		// mainContainer.setBackground(new Color(255,255,0));
		getContentPane().add(mainContainer);
		
		JPanel dataPanel = new JPanel();
		dataPanel.setPreferredSize(new Dimension(1200, 30));
		dataPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		dataPanel.setBackground(new Color(0,0,0));
		mainContainer.add(dataPanel);
		
		List<JLabel> datalabels = new ArrayList<JLabel>();
		
		JLabel lctime = new JLabel("Acceptance Rate: ");// "<html>Text color: <font color='red'>red</font></html>"
		datalabels.add(lctime);
		JLabel ldtime = new JLabel("Synchronization Skew: ");
		datalabels.add(ldtime);
		JLabel lfsize = new JLabel("Media Jitter: ");
		datalabels.add(lfsize);
		JLabel lcratio = new JLabel("Failure Rate: ");
		datalabels.add(lcratio);
		
		List<JLabel> datatexts = new ArrayList<JLabel>();
		mon1 = new JLabel("0");// "<html>Text color: <font color='red'>red</font></html>"
		datatexts.add(mon1);
		mon2 = new JLabel("0");
		datatexts.add(mon2);
		mon3 = new JLabel("0");
		datatexts.add(mon3);
		mon4 = new JLabel("0");
		datatexts.add(mon4);
		
		for (int i = 0; i < 4; i++) {
			datalabels.get(i).setHorizontalAlignment(SwingConstants.RIGHT);
			datalabels.get(i).setPreferredSize(new Dimension(200, 30));
			datalabels.get(i).setForeground(Color.white);
		}
		
		for (int i = 0; i < 4; i++) {
			datatexts.get(i).setHorizontalAlignment(SwingConstants.LEFT);
			datatexts.get(i).setPreferredSize(new Dimension(100, 30));
			datatexts.get(i).setForeground(Color.white);
		}
		
		for (int i = 0; i < 4; i++) {
			dataPanel.add(datalabels.get(i));
			dataPanel.add(datatexts.get(i));
		}
		
		videoPanel = new JPanel();
		videoPanel.setPreferredSize(new Dimension(1200, 640));
		videoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		videoPanel.setBackground(new Color(0,0,0));
		mainContainer.add(videoPanel);
		
		vc = new VideoComponent();
		vc.setPreferredSize(new Dimension(1200, 640));
		vc.setBackground(Color.green);
		videoPanel.add(vc);
		
		JPanel resPanel = new JPanel();
		resPanel.setPreferredSize(new Dimension(200, 30));
		resPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0,0));
		mainContainer.add(resPanel);
		
		scanResource();
		
		makeCtrlButton("Refresh");
		resPanel.add(buttons.get("Refresh"));
		buttons.get("Refresh").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pushLog("> RSRC: UPDATED");
				Client.updateResource();
			}
		});
		
		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setPreferredSize(new Dimension(800, 30));
		mainContainer.add(ctrlPanel);	

		makeCtrlButton("FF");
		ctrlPanel.add(buttons.get("FF"));
		buttons.get("FF").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String settings = "" + resCombo.getSelectedItem()
						+ " " + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " forward";
				try {
					Client.handleRequest(vc, settings, textArea);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		
		makeCtrlButton("RW");
		ctrlPanel.add(buttons.get("RW"));
		buttons.get("RW").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String settings = "" + resCombo.getSelectedItem()
						+ " " + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " rewind";
				try {
					Client.handleRequest(vc, settings, textArea);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		
		makeCtrlButton("Play");
		buttons.get("Play").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean status = playback();
				buttons.get("Play").setText(status ? "Pause" : "Play");
			}
		});
		ctrlPanel.add(buttons.get("Play"));
		
		makeCtrlButton("Stop");
		ctrlPanel.add(buttons.get("Stop"));
		buttons.get("Stop").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearLog();
				pushLog("> CTRL: STOP");
				String settings = "" + resCombo.getSelectedItem()
						+ " " + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " stop";
				try {
					Client.handleRequest(vc, settings, textArea);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				playing = false;
				buttons.get("Play").setText("Play");
			}
		});
		
		JPanel optPanel = new JPanel();
		optPanel.setPreferredSize(new Dimension(200,30));
		mainContainer.add(optPanel);
		
		String[] resSettings = {"240p", "360p", "480p", "720p"};
		resCombo = new JComboBox<String>(resSettings);
		resCombo.setPreferredSize(new Dimension(95,30));
		optPanel.add(resCombo);
		
		String[] actSettings = {"Passive", "Active"};
		actCombo = new JComboBox<String>(actSettings);
		actCombo.setPreferredSize(new Dimension(95,30));
		optPanel.add(actCombo);
		
		textArea = new JTextArea();
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(1200, 100));
		mainContainer.add(scrollPane);
		
		pushLog("Starting Client...");
		setVisible(true);
		
		// Path path = FileSystems.getDefault().getPath(System.getProperty("user.dir"));
		pushLog("> SYS: PATH " + currpath);
	}
	
	/**
	 * Tries to change the current playback state of the media.
	 * When clicked:
	 * 	Does nothing when no video is playing.
	 * 	Repeats video when video had finished playing.
	 * 	Recovers normal playback speed if FF or RW was active
	 * 	Reports back to play toggle button
	 * @return state of player, true = playing, false = remain paused
	 */
	private boolean playback() {
		if (!playing) {
			pushLog("> SYS: REQUEST " + actCombo.getSelectedItem() + " " + resCombo.getSelectedItem() + " " + bandwidth);
			Thread clientThread = new Thread() {
				public void run() {
					String settings = "" + resCombo.getSelectedItem()
							+ " " + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
							+ " " + bandwidth + " play";
					try {
						Client.handleRequest(vc, settings, textArea);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			clientThread.start();
			/*
			rectimer = new Timer();
			rectimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					// List<Long> data = new ArrayList<Long>();
					while(pipe.getQueue1().peek() != null && pipe.getQueue2().peek() != null) {
						tctime.setText((""+ (pipe.getQueue2().peek().getFrameTime() - pipe.getQueue1().peek().getFrameTime())));
						tfsize.setText("" + pipe.getQueue2().peek().getFrameSize());
						tcratio.setText(""+ pipe.getQueue2().peek().getFrameSize() / (double) pipe.getQueue1().peek().getFrameSize());
						pipe.getQueue1().poll();
						pipe.getQueue2().poll();
					}
				}
			}, 0, 25);
			*/
			return (playing = true);
		}
		else { // if (playing)
			String settings = "" + resCombo.getSelectedItem()
					+ " " + actCombo.getSelectedItem()
					+ " " + bandwidth + " pause";
			try {
				Client.handleRequest(vc, settings, textArea);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			monitor.cancel();
			return (playing = false);
		}
		
	}
	
	private void scanResource() throws IOException {
		String somePath = Client.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
		String appPath = "";
		if (somePath.indexOf('!') != -1) { // for jar files
			appPath = somePath.substring(0, somePath.indexOf('!')); // find the local directory
		}
		else { // running on eclipse
			appPath = ""; // just use the local resource.txt
		}
		BufferedReader br = new BufferedReader(new FileReader(appPath + "resource.txt"));
		try {
			bandwidth = Integer.parseInt(br.readLine());
		} catch (NumberFormatException e) {
			pushLog("> RSRC: BAD VALUE");
		} catch (IOException e) {
			pushLog("> RSRC: BAD VALUE");
		}
		br.close();
	}
	
	private void makeCtrlButton(String name) {
		JButton b = new JButton(name);
		b.setMargin(new Insets(0,0,0,0));
		buttons.put(name, b);
	}
	
	private void clearLog() {
		textArea.setText("");
	}
	
	private void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
	
	public static void main(String[] args) throws IOException {
		ClientLauncher s = new ClientLauncher();
	}
}
