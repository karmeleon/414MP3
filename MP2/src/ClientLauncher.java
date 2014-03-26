import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;


public class ClientLauncher extends JFrame{
	private static final long serialVersionUID = 5429778737562008920L;
	
	String currpath;
	String logtext = "";
	JTextArea textArea;
	JPanel videoPanel;
	String rsrc;
	
	Map<String, JButton> buttons;
	boolean playing = false;
	
	public ClientLauncher() throws IOException {
		super("Client");
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
		
		videoPanel = new JPanel();
		videoPanel.setPreferredSize(new Dimension(1200, 640));
		videoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		videoPanel.setBackground(new Color(0,0,0));
		mainContainer.add(videoPanel);
		
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
				try {
					scanResource();
				} 
				catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		
		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setPreferredSize(new Dimension(800, 30));
		mainContainer.add(ctrlPanel);	

		makeCtrlButton("FF");
		ctrlPanel.add(buttons.get("FF"));
		makeCtrlButton("RW");
		ctrlPanel.add(buttons.get("RW"));
		
		makeCtrlButton("Play");
		buttons.get("Play").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean status = playback();
				buttons.get("Play").setText(status ? "Pause" : "Play");
				pushLog("> CTRL: " + (status ? "PLAY" : "PAUSE"));
			}
		});
		ctrlPanel.add(buttons.get("Play"));
		
		makeCtrlButton("Stop");
		ctrlPanel.add(buttons.get("Stop"));
		buttons.get("Stop").addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearLog();
				pushLog("> CTRL: STOP");
			}
		});
		
		JPanel optPanel = new JPanel();
		optPanel.setPreferredSize(new Dimension(200,30));
		mainContainer.add(optPanel);
		
		String[] resSettings = {"480p", "240p"};
		JComboBox resCombo = new JComboBox(resSettings);
		resCombo.setPreferredSize(new Dimension(95,30));
		optPanel.add(resCombo);
		
		String[] actSettings = {"Active", "Passive"};
		JComboBox actCombo = new JComboBox(actSettings);
		actCombo.setPreferredSize(new Dimension(95,30));
		optPanel.add(actCombo);
		
		textArea = new JTextArea();
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(1200, 100));
		mainContainer.add(scrollPane);
		
		JPanel dataPanel = new JPanel();
		dataPanel.setPreferredSize(new Dimension(1200, 30));
		dataPanel.setBackground(new Color(0,0,0));
		mainContainer.add(dataPanel);
		
		pushLog("Starting Client...");
		setVisible(true);
		
		Path path = FileSystems.getDefault().getPath(System.getProperty("user.dir"));
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
		return playing;
	}
	
	private void scanResource() throws IOException {
		BufferedReader br = new BufferedReader(new FileReader("resource.txt"));
		String currline = "";
		while ((currline = br.readLine()) != null) {
			rsrc += currline + "\n";
		}
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