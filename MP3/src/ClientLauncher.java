import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.Timer;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.gstreamer.Gst;
import org.gstreamer.swing.VideoComponent;


public class ClientLauncher extends JFrame{
	private static final long serialVersionUID = 5429778737562008920L;
	
	String currpath;
	int bandwidth = 0;
	String logtext = "";
	JTextArea textArea;
	JPanel videoPanel;
	String rsrc;
	JComboBox<String> actCombo;
	Socket bandwidthSKT = null;
	PrintWriter bandwidthWriter;
	
	Map<String, JButton> buttons;
	boolean playing = false;
	boolean connected = false;
	VideoComponent vc;
	JLabel mon1; JLabel mon2; JLabel mon3; JLabel mon4;
	Timer monitor;
	static JTextField netAddress;
	
	public ClientLauncher() throws IOException {
		super("Client");
		System.out.println("RUNNING CLIENT");
		Gst.init();
		currpath = System.getProperty("user.dir");
		setSize(1200,800);
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		buttons = new HashMap<String, JButton>();
		
		JPanel mainContainer = new JPanel();
		mainContainer.setPreferredSize(new Dimension(1200, 800));
		mainContainer.setLayout(new BorderLayout());
		// mainContainer.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		mainContainer.setBackground(new Color(255,255,0));
		getContentPane().add(mainContainer);
		
		videoPanel = new JPanel();
		videoPanel.setPreferredSize(new Dimension(1000, 640));
		videoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		videoPanel.setBackground(new Color(0,0,0));
		mainContainer.add(videoPanel, BorderLayout.LINE_START);
		
		vc = new VideoComponent();
		vc.setPreferredSize(new Dimension(1000, 640));
		vc.setBackground(Color.green);
		videoPanel.add(vc);
		
		JPanel debugPanel = new JPanel();
		debugPanel.setPreferredSize(new Dimension(200, 640));
		debugPanel.setBackground(Color.red);
		debugPanel.setLayout(new BorderLayout());
		mainContainer.add(debugPanel, BorderLayout.LINE_END);
		
		textArea = new JTextArea();
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(200, 610));
		debugPanel.add(scrollPane, BorderLayout.PAGE_START);
		
		scanResource();
		
		makeCtrlButton("Refresh");
		debugPanel.add(buttons.get("Refresh"), BorderLayout.PAGE_END);
		buttons.get("Refresh").setPreferredSize(new Dimension(100, 30));
		buttons.get("Refresh").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pushLog("> RSRC: UPDATED");
				Client.updateResource();
			}
		});
		
		JPanel botPanel = new JPanel();
		botPanel.setPreferredSize(new Dimension(1200, 160));
		botPanel.setLayout(new BorderLayout());
		mainContainer.add(botPanel, BorderLayout.PAGE_END);
		
		ControlPanel pilotPanel = new ControlPanel("PILOT", 600, 160);
		botPanel.add(pilotPanel, BorderLayout.LINE_START);
		
		ControlPanel targetPanel = new ControlPanel("TARGET", 600, 160);
		botPanel.add(targetPanel, BorderLayout.LINE_END);
		
		/*
		makeCtrlButton("Play");
		buttons.get("Play").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean status = play();
				buttons.get("Play").setText(status ? "Pause" : "Play");
			}
		});
		// ctrlPanel.add(buttons.get("Play"));
		
		makeCtrlButton("Stop");
		// ctrlPanel.add(buttons.get("Stop"));
		buttons.get("Stop").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearLog();
				pushLog("> CTRL: STOP");
				String settings = "" + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " stop";
				try {
					Client.handleRequest(vc, settings, textArea, netAddress.getText());
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				playing = false;
				buttons.get("Play").setText("Play");
			}
		});
		
		netAddress = new JTextField();
		netAddress.setPreferredSize(new Dimension(200, 30));
		// ctrlPanel.add(netAddress);
		
		JPanel optPanel = new JPanel();
		optPanel.setPreferredSize(new Dimension(200,30));
		// mainContainer.add(optPanel);
		
		String[] actSettings = {"Passive", "Active"};
		actCombo = new JComboBox<String>(actSettings);
		actCombo.setPreferredSize(new Dimension(95,30));
		// optPanel.add(actCombo);
		*/
		pushLog("Starting Client...");
		setVisible(true);
		
		// Path path = FileSystems.getDefault().getPath(System.getProperty("user.dir"));
		pushLog("> SYS: PATH " + currpath);
		
	}
	
	private boolean play() {
		if (!playing) {
			pushLog("> SYS: REQUEST " + actCombo.getSelectedItem() + " " + bandwidth);
			Thread clientThread = new Thread() {
				public void run() {
					String settings = "" + (actCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
							+ " " + bandwidth + " play";
					try {
						Client.handleRequest(vc, settings, textArea, netAddress.getText());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			clientThread.start();
			monitor = new Timer();
			monitor.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					double vsizesum = 0;
					double asizesum = 0;
					double vtimesum = 0;
					double atimesum = 0;
					double vcount = 0;
					double acount = 0;
					
					if (Client.videoQ != null && Client.audioQ != null && Client.videoQ.size() > 0 && Client.audioQ.size() > 0) {
						vcount = Client.videoQ.size();
						acount = Client.audioQ.size();
						
						while (Client.videoQ.peek() != null) {
							// mon1.setText("" + Client.videoQ.peek().getFrameSize());
							vsizesum+= Client.videoQ.peek().getFrameSize();
							vtimesum+= Client.videoQ.peek().getFrameTime();
							Client.videoQ.poll();
						}
						while (Client.audioQ.peek() != null) {
							// mon1.setText("" + Client.videoQ.peek().getFrameSize());
							asizesum+= Client.audioQ.peek().getFrameSize();
							atimesum+= Client.audioQ.peek().getFrameTime();
							Client.audioQ.poll();
						}
						
						int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
						int asizeAVG = (int) (asizesum / acount); double atimeAVG = atimesum / acount;
						
						mon1.setText("" + ((vsizeAVG + asizeAVG) * 4));
						// DecimalFormat df = new DecimalFormat("0.00##");
						// String restime = df.format(vtimeAVG - atimeAVG);
						int skew = (int) (vtimeAVG - atimeAVG);
						if (skew > 10000) skew = 0;
						mon2.setText("" + skew);
					}
					else {
						if (Client.videoQ != null && Client.videoQ.size() > 0) {
							vcount = Client.videoQ.size();
							while (Client.videoQ.peek() != null) {
								vsizesum+= Client.videoQ.peek().getFrameSize();
								vtimesum+= Client.videoQ.peek().getFrameTime();
								Client.videoQ.poll();
							}
							
							int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
							mon1.setText("" + (vsizeAVG * 4));
							mon2.setText("N/A");
						}
					}
				}
			}, 0, 250);
			
			return (playing = true);
		}
		else { // if (playing)
			String settings = "" + actCombo.getSelectedItem()
					+ " " + bandwidth + " pause";
			try {
				Client.handleRequest(vc, settings, textArea, netAddress.getText());
			} catch (IOException e) {
				e.printStackTrace();
			}
			monitor.cancel();
			return (playing = false);
		}
		
	}
	
	private void scanResource() throws IOException {
		BufferedReader br = null;
		String rscPath = "resource.txt";
		try {
			br = new BufferedReader(new FileReader(rscPath));
		} catch (FileNotFoundException e1) {
			String somePath = Client.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
			rscPath = somePath.substring(0, somePath.indexOf("client")) + "c_resource.txt"; // find the local directory
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
