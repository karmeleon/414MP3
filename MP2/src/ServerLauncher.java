import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

public class ServerLauncher extends JFrame {
	private static final long serialVersionUID = -8107272587084526626L;
	
	String currpath;
	String logtext = "";
	JTextArea textArea;
	JScrollPane scrollPane;
	JButton startButton;
	ServerLauncher serverGUI;
	
	public ServerLauncher() {
		super("Server");
		currpath = System.getProperty("user.dir");
		setSize(500,800);
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		JPanel mainContainer = new JPanel();
		mainContainer.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		getContentPane().add(mainContainer);
		
		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setPreferredSize(new Dimension(500, 30));
		mainContainer.add(ctrlPanel);
		
		startButton = new JButton("Start");
		startButton.setMargin(new Insets(0,0,0,0));
		
		startButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Thread serverThread = new Thread() {
					public void run() {
						Server.startServer(textArea);
					}
				};
				serverThread.start();
			}
		});
		ctrlPanel.add(startButton);
		
		textArea = new JTextArea();
		scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(500, 740));
		mainContainer.add(scrollPane);
		
		JPanel dataPanel = new JPanel();
		dataPanel.setPreferredSize(new Dimension(500, 30));
		dataPanel.setBackground(new Color(0,0,0));
		mainContainer.add(dataPanel);
		
		pushLog("Starting Server...");
		setVisible(true);
	}
	
	public void pushLog(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
	
	public static void main(String[] args) {
		ServerLauncher s = new ServerLauncher();
	}
}
