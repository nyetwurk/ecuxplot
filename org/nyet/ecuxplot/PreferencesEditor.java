package org.nyet.ecuxplot;

import java.util.prefs.Preferences;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.beans.EventHandler;

public class PreferencesEditor extends JPanel {
    private JDialog dialog;
    private JButton jbtnOK;
    private boolean ok;
    private ECUxPlot eplot;

    private JPanel prefsPanel;

    private Preferences prefs;

    protected void Process(ActionEvent event) {
	if(eplot!=null) eplot.rebuild();
    }

    private void setDefaults() {
	if(this.prefs!=null) {
	    try { this.prefs.clear(); }
	    catch (Exception e) { }
	    if(eplot!=null) eplot.rebuild();
	    updateDialog();
	}
    }

    public PreferencesEditor (Preferences prefs) {
	this.prefs = prefs;
	this.setLayout(new BorderLayout());

	this.prefsPanel = new JPanel();
	this.prefsPanel.setLayout(new GridLayout(0,2));
	this.add(this.prefsPanel, BorderLayout.CENTER);

	JPanel panel = new JPanel();
	panel.setLayout(new GridLayout(1,0));
	this.jbtnOK = new JButton("OK");
	this.jbtnOK.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
		dialog.setVisible(false);
	    }
	});
	panel.add(this.jbtnOK);

	JButton jbtn = new JButton("Apply");
	jbtn.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
	    }
	});
	panel.add(jbtn);

	if(prefs!=null) {
	    jbtn = new JButton("Defaults");
	    jbtn.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    setDefaults();
		}
	    });
	    panel.add(jbtn);
	}

	jbtn = new JButton("Cancel");
	jbtn.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = false;
		dialog.setVisible(false);
	    }
	});
	panel.add(jbtn);

	this.add(panel, BorderLayout.SOUTH);
    }
    public PreferencesEditor () { this(null); }

    public void updateDialog() { }

    public boolean showDialog(Component parent, String title) {
	updateDialog();
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
