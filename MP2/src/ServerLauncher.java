import javax.swing.*;

public class ServerLauncher extends JFrame {
	private static final long serialVersionUID = -8107272587084526626L;
	
	String currpath;
	String logtext = "";
	JTextArea textArea;
	JScrollPane scrollPane;
	
	public ServerLauncher() {
		super("Server");
		currpath = System.getProperty("user.dir");
		setSize(500,800);
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		
		textArea = new JTextArea();
		scrollPane = new JScrollPane(textArea);
		getContentPane().add(scrollPane);
		
		push("Starting Server...");
	}
	
	private void push(String line) {
		textArea.setText(textArea.getText() + line + "\n");
	}
	
	public static void main(String[] args) {
		ServerLauncher s = new ServerLauncher();
	}
}
