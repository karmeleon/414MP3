import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.text.DecimalFormat;
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
	static JTextField netAddress;
	
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
		mainContainer.setPreferredSize(new Dimension(1200, 800));
		mainContainer.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		mainContainer.setBackground(new Color(255,255,0));
		getContentPane().add(mainContainer);
		
		JPanel dataPanel = new JPanel();
		dataPanel.setPreferredSize(new Dimension(1200, 30));
		dataPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		dataPanel.setBackground(new Color(0,0,0));
		
		List<JLabel> datalabels = new ArrayList<JLabel>();
		
		JLabel lctime = new JLabel("Incoming Bandwidth: ");// "<html>Text color: <font color='red'>red</font></html>"
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
		videoPanel.setPreferredSize(new Dimension(1000, 640));
		videoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		videoPanel.setBackground(new Color(0,0,0));
		mainContainer.add(videoPanel);
		
		vc = new VideoComponent();
		vc.setPreferredSize(new Dimension(1200, 640));
		vc.setBackground(Color.green);
		videoPanel.add(vc);
		
		JPanel controlPanel = new JPanel();
		controlPanel.setBackground(Color.DARK_GRAY);
		controlPanel.setPreferredSize(new Dimension(200,640));
		mainContainer.add(controlPanel);
		
		JPanel spacer = new JPanel();
		spacer.setBackground(Color.darkGray);
		spacer.setPreferredSize(new Dimension(200,300));
		controlPanel.add(spacer);
		
		ImagePanel dpad = new ImagePanel("dpad.png");
		dpad.setPreferredSize(new Dimension(160,160));
		dpad.setBackground(Color.DARK_GRAY);
		controlPanel.add(dpad);
		
		JButton upButton = new JButton("A");
		upButton.setContentAreaFilled(false);
		upButton.setBorderPainted(false);
		upButton.setOpaque(false);
		upButton.setForeground(Color.white);
		dpad.add(upButton);
		
		JPanel a = new JPanel(); a.setPreferredSize(new Dimension(160,24)); dpad.add(a);
		a.setOpaque(false);
		
		JButton leftButton = new JButton("<");
		leftButton.setContentAreaFilled(false);
		leftButton.setBorderPainted(false);
		leftButton.setOpaque(false);
		leftButton.setForeground(Color.white);
		dpad.add(leftButton);
		
		JPanel b = new JPanel(); b.setPreferredSize(new Dimension(50,24)); dpad.add(b);
		b.setOpaque(false);
		
		JButton rightButton = new JButton(">");
		rightButton.setContentAreaFilled(false);
		rightButton.setBorderPainted(false);
		rightButton.setOpaque(false);
		rightButton.setForeground(Color.white);
		dpad.add(rightButton);
		
		JPanel c = new JPanel(); c.setPreferredSize(new Dimension(160,24)); dpad.add(c);
		c.setOpaque(false);
		
		JButton downButton = new JButton("V");
		downButton.setContentAreaFilled(false);
		downButton.setBorderPainted(false);
		downButton.setOpaque(false);
		downButton.setForeground(Color.white);
		dpad.add(downButton);
		
		ImagePanel launchPanel = new ImagePanel("launch.jpg");
		launchPanel.setPreferredSize(new Dimension(100,100));
		launchPanel.setBackground(Color.DARK_GRAY);
		controlPanel.add(launchPanel);
		
		
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
					Client.handleRequest(vc, settings, textArea, netAddress.getText()); // 0 - LAN ;; 1 - INET
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
					Client.handleRequest(vc, settings, textArea, netAddress.getText());
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
		ctrlPanel.add(netAddress);
		
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
		
		mainContainer.add(dataPanel);
		
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
			String settings = "" + resCombo.getSelectedItem()
					+ " " + actCombo.getSelectedItem()
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
	
	public class ImagePanel extends JPanel{

	    private BufferedImage image;

	    public ImagePanel(String name) {
	       try {                
	          image = ImageIO.read(new File(name));
	       } catch (IOException ex) {
	            // handle exception...
	       }
	    }

	    @Override
	    protected void paintComponent(Graphics g) {
	        super.paintComponent(g);
	        g.drawImage(image, 0, 0, null); // see javadoc for more info on the parameters            
	    }

	}
}
