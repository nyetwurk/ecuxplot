package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.beans.EventHandler;

public class PreferencesEditor extends JPanel {
    private JDialog dialog;
    private JButton jbtnOK;
    private JButton jbtnApply;
    private boolean ok;
    private ECUxPlot eplot;

    private JPanel prefsPanel;
    protected void Process(ActionEvent event) {
	if(eplot!=null) eplot.rebuild();
    }

    public PreferencesEditor () {
	this.setLayout(new BorderLayout());

	this.prefsPanel = new JPanel();
	this.prefsPanel.setLayout(new GridLayout(0,2));
	this.add(this.prefsPanel, BorderLayout.CENTER);

	JPanel panel = new JPanel();
	panel.setLayout(new GridLayout(1,3));
	jbtnOK = new JButton("OK");
	jbtnOK.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
		dialog.setVisible(false);
	    }
	});
	panel.add(jbtnOK);

	jbtnApply = new JButton("Apply");
	jbtnApply.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
	    }
	});
	panel.add(jbtnApply);

	JButton jbtnCancel = new JButton("Cancel");
	jbtnCancel.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = false;
		dialog.setVisible(false);
	    }
	});
	panel.add(jbtnCancel);

	this.add(panel, BorderLayout.SOUTH);
    }

    public boolean showDialog(Component parent, String title) {
	this.ok = false;
	Frame owner = null;
	if(parent instanceof Frame) owner = (Frame)parent;
	else owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
	if(this.dialog == null || this.dialog.getOwner() != owner)
	{
	    if(owner instanceof ECUxPlot) eplot = (ECUxPlot)owner;

	    this.dialog = new JDialog(owner);
	    this.dialog.add(this);
	    this.dialog.getRootPane().setDefaultButton(this.jbtnOK);
	    this.dialog.pack();
	    Point where = owner.getLocation();
	    where.translate(20,20);
	    this.dialog.setLocation(where);
	    this.dialog.setResizable(false);
	}
	this.dialog.setTitle(title);
	this.dialog.setVisible(true);
	return ok;
    }

    public JPanel getPrefsPanel() { return this.prefsPanel; }
}
