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
    private final JButton jbtnOK;
    private boolean ok;
    private ECUxPlot eplot;

    private final JPanel prefsPanel;

    private final Preferences prefs;

    protected void Process(ActionEvent event) {
	if(this.eplot!=null) this.eplot.rebuild();
    }

    private void setDefaults() {
	if(this.prefs!=null) {
	    try { this.prefs.clear(); }
	    catch (final Exception e) { }
	    if(this.eplot!=null) this.eplot.rebuild();
	    updateDialog();
	}
    }

    protected void processPairs(Object o, String [][] pairs) {
	processPairs(o, pairs, Integer.TYPE);
    }

    // set settings from the contents of the text fields
    protected void processPairs(Object o, String [][] pairs, Class<?> c) {
        for (final String[] pair : pairs) {
	    try {
		// o."method"("class".valueOf(this."field".getText()));
		final Method m = o.getClass().getMethod(pair[1], c);
		final Method convert = c.getMethod("valueOf", String.class);
		final Field fld = this.getClass().getField(pair[1]);
		final JTextField f = (JTextField) fld.get(this);
		m.invoke(o, convert.invoke(null,f.getText()));
	    } catch (final Exception e) {
		e.printStackTrace();
	    }
	}
    }

    protected void addPairs(String [][] pairs, int [] fieldSizes) {
        for(int i=0;i<pairs.length; i++) {
            this.prefsPanel.add(new JLabel(pairs[i][0], SwingConstants.TRAILING));
            try {
		final Field fld = this.getClass().getField(pairs[i][1]);
		Container tf;
		if(fieldSizes == null || fieldSizes.length<i+1)
		    tf = new JTextField(10);
		else if(fieldSizes[i]>0)
		    tf = new JTextField(fieldSizes[i]);
		else
		    tf = new JLabel("");
                fld.set(this, tf);
                this.prefsPanel.add(tf);
            } catch (final Exception e) {
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

	final JPanel panel = new JPanel();

	panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
	panel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

	this.jbtnOK = new JButton("OK");
	this.jbtnOK.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent event) {
		PreferencesEditor.this.ok = true;
		Process(event);
		PreferencesEditor.this.dialog.setVisible(false);
	    }
	});
	panel.add(this.jbtnOK);

	panel.add(Box.createHorizontalGlue());
	JButton jbtn = new JButton("Apply");
	jbtn.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent event) {
		PreferencesEditor.this.ok = true;
		Process(event);
	    }
	});
	panel.add(jbtn);

	if(prefs!=null) {
	    panel.add(Box.createHorizontalGlue());
	    jbtn = new JButton("Defaults");
	    jbtn.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event) {
		    setDefaults();
		}
	    });
	    panel.add(jbtn);
	}

	panel.add(Box.createHorizontalGlue());
	jbtn = new JButton("Cancel");
	jbtn.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent event) {
		PreferencesEditor.this.ok = false;
		PreferencesEditor.this.dialog.setVisible(false);
	    }
	});
	panel.add(jbtn);

	this.add(panel, BorderLayout.SOUTH);
    }

    // set the text fields according to what the current settings are
    protected void updateDialog() { }
    protected void updateDialog(Object o, String [][] pairs) {
        for (final String[] pair : pairs) {
	    try {
		// o."field".setText("" + o."method"())
		final Field fld = this.getClass().getField(pair[1]);
		final JTextField f = (JTextField) fld.get(this);
		final Method m = o.getClass().getMethod(pair[1]);
		f.setText("" + m.invoke(o));
	    } catch (final Exception e) {
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
	    if(owner instanceof ECUxPlot) this.eplot = (ECUxPlot)owner;

	    this.dialog = new JDialog(owner);
	    this.dialog.add(this);
	    this.dialog.getRootPane().setDefaultButton(this.jbtnOK);
	    this.dialog.pack();
	    final Point where = owner.getLocation();
	    where.translate(20,20);
	    this.dialog.setLocation(where);
	    this.dialog.setResizable(false);
	}
	this.dialog.setTitle(title);
	this.dialog.setVisible(true);
	return this.ok;
    }

    public JPanel getPrefsPanel() { return this.prefsPanel; }
}
