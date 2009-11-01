package org.nyet.ecuxplot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.util.prefs.Preferences;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.JOptionPane;

import org.nyet.util.GenericFileFilter;
import org.nyet.util.Locate;

public final class ProfileMenu extends JMenu {
    private ECUxPlot plotFrame;
    private JMenu loadProfilesMenu;
    private JMenu saveProfilesMenu;

    public ProfileMenu(String id, ECUxPlot plotFrame) {
	super(id);
	this.plotFrame=plotFrame;
	JMenuItem jmi;

	jmi = new JMenuItem("Edit constants...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	jmi = new JMenuItem("Edit fueling...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	this.add(new JSeparator());

	this.loadProfilesMenu = new JMenu("Load Profile...");
	this.saveProfilesMenu = new JMenu("Save Profile...");
	updateProfiles();
	this.add(this.loadProfilesMenu);
	this.add(this.saveProfilesMenu);
    }

    private void updateProfiles() {
	try {
	    LoadProfileAction lpa = new LoadProfileAction();
	    SaveProfileAction spa = new SaveProfileAction();
	    File dir=new File(Locate.getClassDirectory(this.getClass()),
		"profiles");
	    File profiles[] = dir.listFiles();
	    for(File p : profiles) {
		if(p.isDirectory() && !p.getName().startsWith(".")) {
		    JMenuItem jmi = new JMenuItem(p.getName());
		    jmi.addActionListener(lpa);
		    this.loadProfilesMenu.add(jmi);
		    jmi = new JMenuItem(p.getName());
		    jmi.addActionListener(spa);
		    this.saveProfilesMenu.add(jmi);
		}
	    }
	    this.saveProfilesMenu.add(new JSeparator());
	    JMenuItem jmi = new JMenuItem("New Profile...");
	    jmi.addActionListener(spa);
	    this.saveProfilesMenu.add(jmi);
	} catch (Exception e) {
	    System.out.println(e);
	};
    }

    private class LoadProfileAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    ProfileMenu pm = ProfileMenu.this;
	    try {
		File dir=new File(Locate.getClassDirectory(this.getClass()),
		    "profiles/"+event.getActionCommand());
		File profiles[] = dir.listFiles(new GenericFileFilter("xml"));
		for(File p : profiles) {
		    pm.plotFrame.getPreferences().importPreferences(new
			FileInputStream(p));
		}
		if(pm.plotFrame!=null) pm.plotFrame.rebuild();
	    } catch (Exception e) {
		JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
	    };
	}
    }

    private class SaveProfileAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    String prof = event.getActionCommand();
	    boolean make = false;
	    ProfileMenu pm = ProfileMenu.this;
	    if(prof.equals("New Profile...")) {
		prof = JOptionPane.showInputDialog(pm, "Enter profile name");
		if(prof == null || prof.length() == 0) return;
		if(prof.startsWith(".") || prof.contains(":") ||
		    prof.contains("/") || prof.contains("\\")) {
		    JOptionPane.showMessageDialog(pm.plotFrame,
			"Invalid profile name");
		    return;
		}
		make = true;
	    }
	    try {
		File dir=new File(Locate.getClassDirectory(this.getClass()),
		    "profiles/"+prof);
		if(make) {
		    if(dir.isDirectory()) {
			JOptionPane.showMessageDialog(pm.plotFrame,
			    "Profile already exists");
			return;
		    }
		    dir.mkdir();
		    pm.saveProfilesMenu.removeAll();
		    pm.loadProfilesMenu.removeAll();
		    updateProfiles();
		}
		if(dir.isDirectory()) {
		    pm.plotFrame.getEnv().c.get().exportNode(new
			FileOutputStream(new File(dir, "constants.xml")));
		    pm.plotFrame.getEnv().f.get().exportNode(new
			FileOutputStream(new File(dir, "fueling.xml")));
		}
	    } catch (Exception e) {
		JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
	    };
	}
    }
}
