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
	String rsrc;
	Socket bandwidthSKT = null;
	PrintWriter bandwidthWriter;
	
	Map<String, JButton> buttons;
	boolean playing = false;
	boolean connected = false;
	
	JPanel videoPanel;
	VideoComponent vc;
	VideoComponent pip;
	
	static ControlPanel pilotPanel;
	static ControlPanel targetPanel;
	
	static Timer pilotTimer;
	static Timer targetTimer;
	
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
		videoPanel.setBackground(Color.yellow);
		videoPanel.setLayout(null);
		mainContainer.add(videoPanel);
		
		pip = new VideoComponent();
		pip.setSize(new Dimension(300, 200));
		pip.setLocation(new Point(0,0));
		pip.setLayout(null);
		pip.setBackground(Color.green);
		
		vc = new VideoComponent();
		vc.setSize(new Dimension(1000, 640));
		vc.setLocation(new Point(0,0));
		vc.setLayout(null);
		vc.setBackground(Color.black);
		
		JLayeredPane lp = new JLayeredPane();
		lp.setPreferredSize(new Dimension(1000, 640));
		lp.setSize(new Dimension(1000, 640));
		lp.add(vc, new Integer(25));
		lp.add(pip, new Integer(50));
		videoPanel.add(lp);
		
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
				ClientPilot.updateResource();
				ClientTarget.updateResource();
			}
		});
		
		JPanel botPanel = new JPanel();
		botPanel.setPreferredSize(new Dimension(1200, 160));
		botPanel.setLayout(new BorderLayout());
		mainContainer.add(botPanel, BorderLayout.PAGE_END);
		
		pilotPanel = new ControlPanel("PILOT", 600, 160);
		botPanel.add(pilotPanel, BorderLayout.LINE_START);
		
		pilotPanel.playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: PILOT CONNECTING");
				pushLog("> SYS: REQUEST " + pilotPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				Thread clientThread = new Thread() {
					public void run() {
						String settings = "" + (pilotPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
								+ " " + bandwidth + " play" + " pilot";
						try {
							ClientPilot.handleRequest(pip, settings, textArea, pilotPanel.netAddress.getText());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				clientThread.start();
				startPilotMonitoring();
			}
		});
		pilotPanel.stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				clearLog();
				pushLog("> CTRL: PILOT CONNECTING");
				pushLog("> SYS: REQUEST " + pilotPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				
				String settings = "" + (pilotPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " stop" + " pilot";
				try {
					ClientPilot.handleRequest(pip, settings, textArea, pilotPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		targetPanel = new ControlPanel("TARGET", 600, 160);
		botPanel.add(targetPanel, BorderLayout.LINE_END);
		
		targetPanel.playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET CONNECTING");
				pushLog("> SYS: REQUEST " + targetPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				Thread clientThread = new Thread() {
					public void run() {
						String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
								+ " " + bandwidth + " play" + " target";
						try {
							ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
							
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				clientThread.start();
				startTargetMonitoring();
			}
		});
		targetPanel.stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				clearLog();
				pushLog("> CTRL: TARGET CONNECTING");
				pushLog("> SYS: REQUEST " + targetPanel.typeCombo.getSelectedItem() + " " + bandwidth);
				
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " stop" + " target";
				try {
					ClientTarget.handleRequest(vc, settings, textArea, targetPanel.netAddress.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		targetPanel.muteBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = pilotPanel.muteBox.isSelected();
				/*
				if(ClientTarget.mute != null)
					ClientTarget.mute.set("mute", selected ? "true" : "false");
				*/
			}
		});
		pilotPanel.muteBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = pilotPanel.muteBox.isSelected();
				/*
				if(ClientPilot.mute != null)
					ClientPilot.mute.set("mute", selected ? "true" : "false");
				*/
			}
		});
		
		targetPanel.upButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				pushLog("> CTRL: TARGET UP");
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " up" + " target";
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
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " down" + " target";
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
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " left" + " target";
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
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " right" + " target";
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
				String settings = "" + (targetPanel.typeCombo.getSelectedIndex() == 0 ? "Active" : "Passive")
						+ " " + bandwidth + " reset" + " target";
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
	
	private void scanResource() throws IOException {
		BufferedReader br = null;
		String rscPath = "resource.txt";
		try {
			br = new BufferedReader(new FileReader(rscPath));
		} catch (FileNotFoundException e1) {
			String somePath = ClientPilot.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
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
	
	public static void startPilotMonitoring() {
		pilotTimer = new Timer();
		pilotTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				double vsizesum = 0;
				double asizesum = 0;
				double vtimesum = 0;
				double atimesum = 0;
				double vcount = 0;
				double acount = 0;
				
				if (ClientPilot.videoQ != null && ClientPilot.audioQ != null && ClientPilot.videoQ.size() > 0 && ClientPilot.audioQ.size() > 0) {
					vcount = ClientPilot.videoQ.size();
					acount = ClientPilot.audioQ.size();
					
					while (ClientPilot.videoQ.peek() != null) {
						// mon1.setText("" + Client.videoQ.peek().getFrameSize());
						vsizesum+= ClientPilot.videoQ.peek().getFrameSize();
						vtimesum+= ClientPilot.videoQ.peek().getFrameTime();
						ClientPilot.videoQ.poll();
					}
					while (ClientPilot.audioQ.peek() != null) {
						// mon1.setText("" + Client.videoQ.peek().getFrameSize());
						asizesum+= ClientPilot.audioQ.peek().getFrameSize();
						atimesum+= ClientPilot.audioQ.peek().getFrameTime();
						ClientPilot.audioQ.poll();
					}
					
					int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
					int asizeAVG = (int) (asizesum / acount); double atimeAVG = atimesum / acount;
					
					pilotPanel.mon1.setText("" + ((vsizeAVG + asizeAVG) * 4));
					// DecimalFormat df = new DecimalFormat("0.00##");
					// String restime = df.format(vtimeAVG - atimeAVG);
					int skew = (int) (vtimeAVG - atimeAVG);
					if (skew > 10000) skew = 0;
					pilotPanel.mon2.setText("" + skew);
				}
				else {
					if (ClientPilot.videoQ != null && ClientPilot.videoQ.size() > 0) {
						vcount = ClientPilot.videoQ.size();
						while (ClientPilot.videoQ.peek() != null) {
							vsizesum+= ClientPilot.videoQ.peek().getFrameSize();
							vtimesum+= ClientPilot.videoQ.peek().getFrameTime();
							ClientPilot.videoQ.poll();
						}
						
						int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
						pilotPanel.mon1.setText("" + (vsizeAVG * 4));
						pilotPanel.mon2.setText("N/A");
					}
				}
			}
		}, 0, 250);
	}
		
	public static void startTargetMonitoring() {
		targetTimer = new Timer();
		targetTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				double vsizesum = 0;
				double asizesum = 0;
				double vtimesum = 0;
				double atimesum = 0;
				double vcount = 0;
				double acount = 0;
				
				if (ClientTarget.videoQ != null && ClientTarget.audioQ != null && ClientTarget.videoQ.size() > 0 && ClientTarget.audioQ.size() > 0) {
					vcount = ClientTarget.videoQ.size();
					acount = ClientTarget.audioQ.size();
					
					while (ClientTarget.videoQ.peek() != null) {
						// mon1.setText("" + Client.videoQ.peek().getFrameSize());
						vsizesum+= ClientTarget.videoQ.peek().getFrameSize();
						vtimesum+= ClientTarget.videoQ.peek().getFrameTime();
						ClientTarget.videoQ.poll();
					}
					while (ClientTarget.audioQ.peek() != null) {
						// mon1.setText("" + Client.videoQ.peek().getFrameSize());
						asizesum+= ClientTarget.audioQ.peek().getFrameSize();
						atimesum+= ClientTarget.audioQ.peek().getFrameTime();
						ClientTarget.audioQ.poll();
					}
					
					int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
					int asizeAVG = (int) (asizesum / acount); double atimeAVG = atimesum / acount;
					
					targetPanel.mon1.setText("" + ((vsizeAVG + asizeAVG) * 4));
					// DecimalFormat df = new DecimalFormat("0.00##");
					// String restime = df.format(vtimeAVG - atimeAVG);
					int skew = (int) (vtimeAVG - atimeAVG);
					if (skew > 10000) skew = 0;
					targetPanel.mon2.setText("" + skew);
				}
				else {
					if (ClientTarget.videoQ != null && ClientTarget.videoQ.size() > 0) {
						vcount = ClientTarget.videoQ.size();
						while (ClientTarget.videoQ.peek() != null) {
							vsizesum+= ClientTarget.videoQ.peek().getFrameSize();
							vtimesum+= ClientTarget.videoQ.peek().getFrameTime();
							ClientTarget.videoQ.poll();
						}
						
						int vsizeAVG = (int) (vsizesum / vcount); double vtimeAVG = vtimesum / vcount;
						targetPanel.mon1.setText("" + (vsizeAVG * 4));
						targetPanel.mon2.setText("N/A");
					}
				}
			}
		}, 0, 250);
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
