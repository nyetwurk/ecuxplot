package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class FuelingEditor extends PreferencesEditor {
    private Fueling fueling;

    private JTextField MAF;
    private JTextField injector;
    private JTextField MAF_offset;

    protected void Process(ActionEvent event) {
	if(this.fueling==null) return;
	this.fueling.MAF = Double.valueOf(this.MAF.getText());
	this.fueling.injector = Double.valueOf(this.injector.getText());
	this.fueling.MAF_offset = Double.valueOf(this.MAF_offset.getText());
	super.Process(event);
    }

    public FuelingEditor () {
	JPanel pp = this.getPrefsPanel();
	pp.setLayout(new GridLayout(3,2,4,4));

	pp.add(new JLabel("MAF diameter (mm):"));
	this.MAF = new JTextField(10);
	pp.add(this.MAF);

	pp.add(new JLabel("Injector size (cc/min):"));
	this.injector = new JTextField(10);
	pp.add(this.injector);

	pp.add(new JLabel("MAF offset (g/sec):"));
	this.MAF_offset = new JTextField(10);
	pp.add(this.MAF_offset);
    }

    public boolean showDialog(Component parent, String title, Fueling fueling) {
	this.fueling = fueling;
	this.MAF.setText("" + fueling.MAF);
	this.injector.setText("" + fueling.injector);
	this.MAF_offset.setText("" + fueling.MAF_offset);
	return super.showDialog(parent, title);
    }
}
