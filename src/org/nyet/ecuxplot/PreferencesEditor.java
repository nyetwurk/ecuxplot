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
    protected ECUxPlot eplot;

    private final JPanel prefsPanel;

    private final Preferences prefs;

    protected void Process(ActionEvent event) {
        if(this.eplot!=null) this.eplot.rebuild();
    }

    /**
     * Returns an array of preference keys that should be excluded from reset
     * when "Defaults" is clicked. Subclasses can override this to preserve
     * certain preference values.
     * @return Array of preference keys to exclude from reset
     */
    protected String[] getExcludedKeysFromDefaults() {
        return new String[0];
    }

    private void setDefaults() {
        if(this.prefs!=null) {
            // Get keys to exclude from reset
            final String[] excludedKeys = getExcludedKeysFromDefaults();

            // Save excluded preference values (as strings - Preferences stores everything as strings)
            final java.util.Map<String, String> savedValues = new java.util.HashMap<>();
            for (final String key : excludedKeys) {
                final String value = this.prefs.get(key, null);
                if (value != null) {
                    savedValues.put(key, value);
                }
            }

            // Clear all preferences
            try { this.prefs.clear(); }
            catch (final Exception e) { }

            // Restore excluded preference values
            for (final java.util.Map.Entry<String, String> entry : savedValues.entrySet()) {
                try {
                    this.prefs.put(entry.getKey(), entry.getValue());
                } catch (final Exception e) {
                    // Ignore restore errors
                }
            }

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
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);

        for(int i=0; i<pairs.length; i++) {
            // Add label
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.anchor = GridBagConstraints.EAST;
            JLabel label = new JLabel(pairs[i][0], SwingConstants.TRAILING);
            this.prefsPanel.add(label, gbc);

            // Add field
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

                gbc.gridx = 1;
                gbc.gridy = i;
                gbc.anchor = GridBagConstraints.WEST;
                this.prefsPanel.add(tf, gbc);
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
        this.prefsPanel.setLayout(new GridBagLayout());
        this.add(this.prefsPanel, BorderLayout.CENTER);

        if(pairs!=null) {
            addPairs(pairs, fieldSizes);
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

        // Always set eplot reference if owner is ECUxPlot (even if dialog is reused)
        if(owner instanceof ECUxPlot) {
            this.eplot = (ECUxPlot)owner;
        }

        if(this.dialog == null || this.dialog.getOwner() != owner) {
            this.dialog = new JDialog(owner);
            this.dialog.add(this);
            this.dialog.getRootPane().setDefaultButton(this.jbtnOK);
            this.dialog.setResizable(false);
            this.dialog.pack();
            final Point where = owner.getLocation();
            where.translate(20,20);
            this.dialog.setLocation(where);
        }
        this.dialog.setTitle(title);
        this.dialog.pack();
        this.dialog.setVisible(true);
        return this.ok;
    }

    public JPanel getPrefsPanel() { return this.prefsPanel; }
}

// vim: set sw=4 ts=8 expandtab:
