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
	JMenuItem item = new JMenuItem("Open File");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_O, ActionEvent.CTRL_MASK));
	item.addActionListener(listener);
	this.add(item);

	item = new JMenuItem("Add File");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_A, ActionEvent.CTRL_MASK));
	item.addActionListener(listener);
	this.add(item);

	this.add(new JSeparator());

	item = new JMenuItem("New Chart");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_N, ActionEvent.CTRL_MASK));
	item.addActionListener(listener);
	this.add(item);

	item = new JMenuItem("Clear Chart");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_C, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	item.addActionListener(listener);
	this.add(item);

	item = new JMenuItem("Close Chart");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	item.addActionListener(listener);
	this.add(item);

	this.add(new JSeparator());

	item = new JMenuItem("Export Chart");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_E, ActionEvent.CTRL_MASK));
	item.addActionListener(listener);
	this.add(item);

	this.add(new JSeparator());

	item = new JMenuItem("Quit");
	item.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_F4, ActionEvent.ALT_MASK));
	item.addActionListener(listener);
	this.add(item);
    }
}
