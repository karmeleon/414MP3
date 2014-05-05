import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.*;

import java.util.List;


public class ControlPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	String title;
	public JLabel mon1; public JLabel mon2; public JLabel mon3; public JLabel mon4;
	
	public JComboBox typeCombo;
	public JTextField netAddress;
	public JButton playButton;
	public JButton stopButton;
	public JCheckBox muteBox;
	JButton upButton;
	JButton downButton;
	JButton leftButton;
	JButton rightButton;
	JButton okButton;
	
	public ControlPanel(String t, int w, int h) {
		super();
		super.setPreferredSize(new Dimension(w, h));
		super.setBackground(Color.darkGray);
		
		title = t;
		super.setLayout(new BorderLayout());
		JLabel titleLabel = new JLabel(title, JLabel.CENTER);
		titleLabel.setForeground(Color.white);
		super.add(titleLabel, BorderLayout.PAGE_START);
		
		int ph = 110;
		JPanel leftPanel = new JPanel();
		leftPanel.setPreferredSize(new Dimension(300, ph));
		leftPanel.setLayout(new FlowLayout());
		super.add(leftPanel, BorderLayout.LINE_START);
		
		netAddress = new JTextField();
		netAddress.setPreferredSize(new Dimension(200, 30));
		leftPanel.add(netAddress);
		
		String[] types = {"Active", "Passive"};
		typeCombo = new JComboBox(types);
		leftPanel.add(typeCombo);
		
		playButton = new JButton("Connect");
		leftPanel.add(playButton);
		
		stopButton = new JButton("Release");
		leftPanel.add(stopButton);
		
		JLabel muteLabel = new JLabel("mute", JLabel.RIGHT);
		muteLabel.setPreferredSize(new Dimension(70,20));
		leftPanel.add(muteLabel);
		
		muteBox = new JCheckBox();
		muteBox.setSelected(true);
		leftPanel.add(muteBox);
		
		JPanel rightPanel = new JPanel();
		rightPanel.setPreferredSize(new Dimension(300, ph));
		rightPanel.setLayout(new FlowLayout());
		super.add(rightPanel, BorderLayout.LINE_END);
		
		if (title.equalsIgnoreCase("target")) {
			ImagePanel dpad = new ImagePanel("dpad.png");
			dpad.setPreferredSize(new Dimension(110,110));
			dpad.setLayout(new BorderLayout());
			rightPanel.add(dpad);
			
			upButton = new JButton();
			upButton.setBackground(Color.yellow);
			upButton.setPreferredSize(new Dimension(0, 36));
			dpad.add(upButton, BorderLayout.PAGE_START);
			upButton.setOpaque(false);
			upButton.setContentAreaFilled(false);
			upButton.setBorderPainted(false);
			
			okButton = new JButton();
			okButton.setBackground(Color.black);
			dpad.add(okButton, BorderLayout.CENTER);
			okButton.setOpaque(false);
			okButton.setContentAreaFilled(false);
			okButton.setBorderPainted(false);
			
			leftButton = new JButton();
			leftButton.setBackground(Color.red);
			leftButton.setPreferredSize(new Dimension(36, 0));
			dpad.add(leftButton, BorderLayout.LINE_START);
			leftButton.setOpaque(false);
			leftButton.setContentAreaFilled(false);
			leftButton.setBorderPainted(false);
			
			rightButton = new JButton();
			rightButton.setBackground(Color.green);
			rightButton.setPreferredSize(new Dimension(36, 0));
			dpad.add(rightButton, BorderLayout.LINE_END);
			rightButton.setOpaque(false);
			rightButton.setContentAreaFilled(false);
			rightButton.setBorderPainted(false);
			
			downButton = new JButton();
			downButton.setBackground(Color.gray);
			downButton.setPreferredSize(new Dimension(0,36));
			dpad.add(downButton, BorderLayout.PAGE_END);
			downButton.setOpaque(false);
			downButton.setContentAreaFilled(false);
			downButton.setBorderPainted(false);
		}
		
		JPanel dataPanel = new JPanel();
		dataPanel.setPreferredSize(new Dimension(600, 30));
		dataPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0,0));
		dataPanel.setBackground(new Color(0,0,0));

		List<JLabel> datalabels = new ArrayList<JLabel>();

		JLabel lctime = new JLabel("Inc. BandW: ");// "<html>Text color: <font color='red'>red</font></html>"
		datalabels.add(lctime);
		JLabel ldtime = new JLabel("Sync Skew: ");
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
			datalabels.get(i).setPreferredSize(new Dimension(100, 30));
			datalabels.get(i).setForeground(Color.white);
		}

		for (int i = 0; i < 4; i++) {
			datatexts.get(i).setHorizontalAlignment(SwingConstants.LEFT);
			datatexts.get(i).setPreferredSize(new Dimension(50, 30));
			datatexts.get(i).setForeground(Color.white);
		}

		for (int i = 0; i < 4; i++) {
			dataPanel.add(datalabels.get(i));
			dataPanel.add(datatexts.get(i));
		}
		
		super.add(dataPanel, BorderLayout.PAGE_END);
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
