package org.nyet.ecuxplot;

import java.lang.reflect.*;

import java.util.prefs.Preferences;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class PreferencesEditor extends JPanel {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
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

    protected void processPairs(Object o, String [][] pairs) {
	processPairs(o, pairs, Integer.TYPE);
    }

    // set settings from the contents of the text fields
    protected void processPairs(Object o, String [][] pairs, Class<?> c) {
        for(int i=0;i<pairs.length; i++) {
	    try {
		// o."method"("class".valueOf(this."field".getText()));
		Method m = o.getClass().getMethod(pairs[i][1], c);
		Method convert = c.getMethod("valueOf", String.class);
		Field fld = this.getClass().getField(pairs[i][1]);
		JTextField f = (JTextField) fld.get(this);
		m.invoke(o, convert.invoke(null,f.getText()));
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    protected void addPairs(String [][] pairs, int [] fieldSizes) {
        for(int i=0;i<pairs.length; i++) {
            this.prefsPanel.add(new JLabel(pairs[i][0], JLabel.TRAILING));
            try {
		Field fld = this.getClass().getField(pairs[i][1]);
		Container tf;
		if(fieldSizes == null || fieldSizes.length<i+1)
		    tf = new JTextField(10);
		else if(fieldSizes[i]>0)
		    tf = new JTextField(fieldSizes[i]);
		else
		    tf = new JLabel("");
                fld.set(this, tf);
                this.prefsPanel.add(tf);
            } catch (Exception e) {
		e.printStackTrace();
            }
        }
    }

    public PreferencesEditor () { this(null); }
    public PreferencesEditor (Preferences prefs) { this(prefs, null); }
    public PreferencesEditor (Preferences prefs, String [][] pairs) {
	this(prefs, pairs, null);
    }
    public PreferencesEditor (Preferences prefs, String [][] pairs, int [] fieldSizes) {
	this.prefs = prefs;
	this.setLayout(new BorderLayout());

	this.prefsPanel = new JPanel();
	this.prefsPanel.setLayout(new SpringLayout());
	this.add(this.prefsPanel, BorderLayout.CENTER);

	if(pairs!=null) {
	    addPairs(pairs, fieldSizes);
	    org.nyet.util.SpringUtilities.makeCompactGrid(this.prefsPanel,
		pairs.length, 2, 6, 6, 6, 6);
	}

	JPanel panel = new JPanel();

	panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
	panel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

	this.jbtnOK = new JButton("OK");
	this.jbtnOK.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
		dialog.setVisible(false);
	    }
	});
	panel.add(this.jbtnOK);

	panel.add(Box.createHorizontalGlue());
	JButton jbtn = new JButton("Apply");
	jbtn.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		ok = true;
		Process(event);
	    }
	});
	panel.add(jbtn);

	if(prefs!=null) {
	    panel.add(Box.createHorizontalGlue());
	    jbtn = new JButton("Defaults");
	    jbtn.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    setDefaults();
		}
	    });
	    panel.add(jbtn);
	}

	panel.add(Box.createHorizontalGlue());
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

    // set the text fields according to what the current settings are
    protected void updateDialog() { }
    protected void updateDialog(Object o, String [][] pairs) {
        for(int i=0;i<pairs.length; i++) {
	    try {
		// o."field".setText("" + o."method"())
		Field fld = this.getClass().getField(pairs[i][1]);
		JTextField f = (JTextField) fld.get(this);
		Method m = o.getClass().getMethod(pairs[i][1]);
		f.setText("" + m.invoke(o));
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public boolean showDialog(Component parent, String title) {
	updateDialog();
	this.ok = false;
	Frame owner;

	if(parent instanceof Frame) owner = (Frame)parent;
	else owner = (Frame)SwingUtilities.
	    getAncestorOfClass(Frame.class, parent);

	if(this.dialog == null || this.dialog.getOwner() != owner) {
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
