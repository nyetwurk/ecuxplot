package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class FilterEditor extends PreferencesEditor {
    private Filter filter;

    private JTextField gear;
    private JTextField minRPM;
    private JTextField maxRPM;
    private JTextField minPedal;
    private JTextField minPoints;

    protected void Process(ActionEvent event) {
	if(this.filter==null) return;
	this.filter.gear = Integer.valueOf(this.gear.getText());
	this.filter.minRPM = Integer.valueOf(this.minRPM.getText());
	this.filter.maxRPM = Integer.valueOf(this.maxRPM.getText());
	this.filter.minPedal = Integer.valueOf(this.minPedal.getText());
	this.filter.minPoints = Integer.valueOf(this.minPoints.getText());
	super.Process(event);
    }

    public FilterEditor () {
	JPanel pp = this.getPrefsPanel();

	pp.add(new JLabel(" Gear:"));
	this.gear = new JTextField(10);
	pp.add(this.gear);

	pp.add(new JLabel(" Minimum RPM:"));
	this.minRPM = new JTextField(10);
	pp.add(this.minRPM);

	pp.add(new JLabel(" Maximum RPM:"));
	this.maxRPM = new JTextField(10);
	pp.add(this.maxRPM);

	pp.add(new JLabel(" Minimum Pedal:"));
	this.minPedal = new JTextField(10);
	pp.add(this.minPedal);

	pp.add(new JLabel(" Minimum Points:"));
	this.minPoints = new JTextField(10);
	pp.add(this.minPoints);
    }

    public boolean showDialog(Component parent, String title, Filter filter) {
	this.filter = filter;
	this.gear.setText("" + filter.gear);
	this.minRPM.setText("" + filter.minRPM);
	this.maxRPM.setText("" + filter.maxRPM);
	this.minPedal.setText("" + filter.minPedal);
	this.minPoints.setText("" + filter.minPoints);
	return super.showDialog(parent, title);
    }
}
