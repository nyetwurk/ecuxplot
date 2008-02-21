package org.nyet.ecuxplot;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.JCheckBox;
import javax.swing.KeyStroke;

public final class FileMenu extends JMenu {
    public FileMenu(String id, ActionListener listener) {
	super(id);
	JMenuItem openitem = new JMenuItem("Open File");
	openitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_O, ActionEvent.CTRL_MASK));
	openitem.addActionListener(listener);
	this.add(openitem);

	this.add(new JSeparator());

	JMenuItem newitem = new JMenuItem("New Chart");
	newitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_N, ActionEvent.CTRL_MASK));
	newitem.addActionListener(listener);
	this.add(newitem);

	JMenuItem closeitem = new JMenuItem("Close Chart");
	closeitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	closeitem.addActionListener(listener);
	this.add(closeitem);

	this.add(new JSeparator());

	JMenuItem exportitem = new JMenuItem("Export Chart");
	exportitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_E, ActionEvent.CTRL_MASK));
	exportitem.addActionListener(listener);
	this.add(exportitem);

	this.add(new JSeparator());

	JMenuItem quititem = new JMenuItem("Quit");
	quititem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_F4, ActionEvent.ALT_MASK));
	quititem.addActionListener(listener);
	this.add(quititem);
    }
}
