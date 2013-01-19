package org.nyet.ecuxplot;

import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

public final class HelpMenu extends JMenu {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public HelpMenu(String id, ActionListener listener) {
	super(id);
	/*
	JMenuItem item = new JMenuItem("Help");
	item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1,0));
	item.addActionListener(listener);
	this.add(item);
	*/
	JMenuItem item = new JMenuItem("About...");
	item.addActionListener(listener);
	this.add(item);
    }
}
