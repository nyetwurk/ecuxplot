package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class PIDEditor extends PreferencesEditor {
    private PID pid;

    private JTextField time_constant;
    private JTextField P_deadband;
    private JTextField I_limit;
    private JTextField P;
    private JTextField I;
    private JTextField D0; // 0
    private JTextField D1; // 300
    private JTextField D2; // 500
    private JTextField D3; // 700

    protected void Process(ActionEvent event) {
	if(this.pid==null) return;
	this.pid.time_constant = Double.valueOf(this.time_constant.getText());
	this.pid.P_deadband = Double.valueOf(this.P_deadband.getText());
	this.pid.I_limit = Double.valueOf(this.I_limit.getText());
	this.pid.P = Double.valueOf(this.P.getText());
	this.pid.I = Double.valueOf(this.I.getText());
	this.pid.D[0] = Double.valueOf(this.D0.getText());
	this.pid.D[1] = Double.valueOf(this.D1.getText());
	this.pid.D[2] = Double.valueOf(this.D2.getText());
	this.pid.D[3] = Double.valueOf(this.D3.getText());
	super.Process(event);
    }

    public PIDEditor () {
	JPanel pp = this.getPrefsPanel();

	pp.add(new JLabel(" Time constant (s):"));
	this.time_constant = new JTextField(10);
	pp.add(this.time_constant);

	pp.add(new JLabel(" P deadband (mBar):"));
	this.P_deadband = new JTextField(10);
	pp.add(this.P_deadband);

	pp.add(new JLabel(" I limiter (%):"));
	this.I_limit = new JTextField(10);
	pp.add(this.I_limit);

	pp.add(new JLabel(" P (%/100mBar):"));
	this.P = new JTextField(10);
	pp.add(this.P);
	pp.add(new JLabel(" I (%/100mBar):"));
	this.I = new JTextField(10);
	pp.add(this.I);
	pp.add(new JLabel(" D (%/100mBar):"));

	JPanel pd = new JPanel();
	pd.setLayout(new FlowLayout(FlowLayout.CENTER,0,0));
	pp.add(pd);

	this.D0 = new JTextField(4);
	pd.add(this.D0);
	this.D1 = new JTextField(4);
	pd.add(this.D1);
	this.D2 = new JTextField(4);
	pd.add(this.D2);
	this.D3 = new JTextField(4);
	pd.add(this.D3);
    }

    public void updateDialog() {
	this.time_constant.setText("" + this.pid.time_constant);
	this.P_deadband.setText("" + this.pid.P_deadband);
	this.I_limit.setText("" + this.pid.I_limit);
	this.P.setText("" + this.pid.P);
	this.I.setText("" + this.pid.I);
	this.D0.setText("" + this.pid.D[0]);
	this.D1.setText("" + this.pid.D[1]);
	this.D2.setText("" + this.pid.D[2]);
	this.D3.setText("" + this.pid.D[3]);
    }

    public boolean showDialog(Component parent, String title, PID pid) {
	this.pid = pid;
	return super.showDialog(parent, title);
    }
}
