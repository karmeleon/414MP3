import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
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
	Socket bandwidthSKT = null;
	PrintWriter bandwidthWriter;
	
	Map<String, JButton> buttons;
	boolean playing = false;
	boolean connected = false;
	VideoComponent vc;
	
	ControlPanel pilotPanel;
	ControlPanel targetPanel;
	
	
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
		vc.setBackground(Color.black);
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
				ClientTarget.updateResource();
				ClientPilot.updateResource();
			}
		});
		
		JPanel botPanel = new JPanel();
		botPanel.setPreferredSize(new Dimension(1200, 160));
		botPanel.setLayout(new BorderLayout());
		mainContainer.add(botPanel, BorderLayout.PAGE_END);
		
		pilotPanel = new ControlPanel("PILOT", 600, 160);
		botPanel.add(pilotPanel, BorderLayout.LINE_START);
		
		targetPanel = new ControlPanel("TARGET", 600, 160);
		botPanel.add(targetPanel, BorderLayout.LINE_END);
		
		targetPanel.playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET CONNECTING");
				pushLog("> SYS: REQUEST " + targetPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				Thread clientThread = new Thread() {
					public void run() {
						String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
								+ " " + bandwidth + " play";
						try {
							ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				clientThread.start();
			}
		});
		targetPanel.stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				clearLog();
				pushLog("> CTRL: TARGET CONNECTING");
				pushLog("> SYS: REQUEST " + targetPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " play";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		targetPanel.upButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET UP");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " up";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		targetPanel.downButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET DOWN");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " down";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		targetPanel.leftButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET LEFT");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " left";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		targetPanel.rightButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET RIGHT");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " right";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		targetPanel.okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET RESET");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Passive" : "Active")
						+ " " + bandwidth + " reset";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		pushLog("> SYS: Starting Client...");
		pushLog("> SYS: PATH " + currpath);
		setVisible(true);
	}
	

	/*
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
	*/
	
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
