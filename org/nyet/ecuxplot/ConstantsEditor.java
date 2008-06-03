package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ConstantsEditor extends PreferencesEditor {
    private Constants c;

    private JTextField mass;
    private JTextField rpm_per_mph;
    private JTextField Cd;
    private JTextField FA;
    private JTextField driveline_loss;

    protected void Process(ActionEvent event) {
	if(this.c==null) return;
	this.c.mass(Double.valueOf(this.mass.getText()));
	this.c.rpm_per_mph(Double.valueOf(this.rpm_per_mph.getText()));
	this.c.Cd(Double.valueOf(this.Cd.getText()));
	this.c.FA(Double.valueOf(this.FA.getText()));
	this.c.driveline_loss(Double.valueOf(this.driveline_loss.getText())/100);
	super.Process(event);
    }

    public ConstantsEditor (Preferences prefs) {
	super(prefs.node(Constants.PREFS_TAG));

	JPanel pp = this.getPrefsPanel();

	pp.add(new JLabel(" Mass (kg):"));
	this.mass = new JTextField(10);
	pp.add(this.mass);

	pp.add(new JLabel(" RPM per mph:"));
	this.rpm_per_mph = new JTextField(10);
	pp.add(this.rpm_per_mph);

	pp.add(new JLabel(" Coefficient of drag:"));
	this.Cd = new JTextField(10);
	pp.add(this.Cd);

	pp.add(new JLabel(" Frontal area (m^2):"));
	this.FA = new JTextField(10);
	pp.add(this.FA);

	pp.add(new JLabel(" Driveline loss (%):"));
	this.driveline_loss = new JTextField(10);
	pp.add(this.driveline_loss);
    }

    public void updateDialog() {
	this.mass.setText("" + this.c.mass());
	this.rpm_per_mph.setText("" + this.c.rpm_per_mph());
	this.Cd.setText("" + this.c.Cd());
	this.FA.setText("" + this.c.FA());
	this.driveline_loss.setText("" + this.c.driveline_loss()*100);
    }

    public boolean showDialog(Component parent, String title, Constants c) {
	this.c = c;
	return super.showDialog(parent, title);
    }
}
