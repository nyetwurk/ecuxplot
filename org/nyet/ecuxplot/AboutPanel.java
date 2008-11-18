package org.nyet.ecuxplot;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.ImageIcon;

public class AboutPanel extends JPanel implements ActionListener {
    public AboutPanel() {
	this.setLayout(new BorderLayout());
	this.add(new JLabel((new org.nyet.util.Version()).toString()),
	    BorderLayout.EAST);

	JButton icon = new JButton(new ImageIcon(getClass().getResource(
	    "icons/ECUxPlot2-64.png")));
	icon.setBorderPainted(false);
	icon.setContentAreaFilled(false);
	icon.setDefaultCapable(false);
	this.add(icon, BorderLayout.CENTER);

	final String html =
	"<a href=\"http://nyet.org/cars/ECUxPlot\">ECUxPlot home page</a>";
	JButton url = new JButton("<html>" + html + "</html>");
	url.setActionCommand("Homepage");
	url.setBorderPainted(false);
	url.setContentAreaFilled(false);
	url.addActionListener(this);
	this.add(url, BorderLayout.SOUTH);
    }

    public void actionPerformed(ActionEvent event) {
	if("Homepage".equals(event.getActionCommand())) {
            boolean error = true;
            if (java.awt.Desktop.isDesktopSupported()) {
                try {
                    java.awt.Desktop.getDesktop().browse(
                            new java.net.URI("http://nyet.org/cars/ECUxPlot"));
                    error = false;
                } catch (Exception e) {
                }
            }
            if (error)
                JOptionPane.showMessageDialog(this,
            "Can't launch browser. Please download the latest JRE from Sun.");
	}
    }
}
