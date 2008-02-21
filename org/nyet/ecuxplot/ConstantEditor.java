package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ConstantEditor extends PreferencesEditor {
    private Env env;

    private JTextField mass;
    private JTextField rpm_per_mph;
    private JTextField Cd;
    private JTextField FA;
    private JTextField driveline_loss;

    protected void Process(ActionEvent event) {
	if(this.env==null) return;
	this.env.mass = Double.valueOf(this.mass.getText());
	this.env.rpm_per_mph = Double.valueOf(this.rpm_per_mph.getText());
	this.env.Cd = Double.valueOf(this.Cd.getText());
	this.env.FA = Double.valueOf(this.FA.getText());
	this.env.driveline_loss = Double.valueOf(this.driveline_loss.getText())/100;
	super.Process(event);
    }

    public ConstantEditor () {
	JPanel pp = this.getPrefsPanel();
	pp.setLayout(new GridLayout(5,2,4,4));

	pp.add(new JLabel("Mass (kg):"));
	this.mass = new JTextField(10);
	pp.add(this.mass);

	pp.add(new JLabel("RPM per mph:"));
	this.rpm_per_mph = new JTextField(10);
	pp.add(this.rpm_per_mph);

	pp.add(new JLabel("Coefficient of drag:"));
	this.Cd = new JTextField(10);
	pp.add(this.Cd);

	pp.add(new JLabel("Frontal area (m^3):"));
	this.FA = new JTextField(10);
	pp.add(this.FA);

	pp.add(new JLabel("Driveline loss (%):"));
	this.driveline_loss = new JTextField(10);
	pp.add(this.driveline_loss);
    }

    public boolean showDialog(Component parent, String title, Env env) {
	this.env = env;
	this.mass.setText("" + env.mass);
	this.rpm_per_mph.setText("" + env.rpm_per_mph);
	this.Cd.setText("" + env.Cd);
	this.FA.setText("" + env.FA);
	this.driveline_loss.setText("" + env.driveline_loss*100);
	return super.showDialog(parent, title);
    }
}
