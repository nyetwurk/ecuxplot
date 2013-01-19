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
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
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
	File dir;
	try {
	    // Add static profiles
	    dir=new File(Locate.getClassDirectory(this),
		"profiles");
	} catch (Exception e) {
	    ProfileMenu pm = ProfileMenu.this;
	    JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
	    e.printStackTrace();
	    return;
	};

	LoadProfileAction lpa = new LoadProfileAction(dir);
	for(File p : dir.listFiles()) {
	    if(p.isDirectory() && !p.getName().startsWith(".")) {
		// do not add static profiles to "save" menu
		JMenuItem jmi = new JMenuItem(p.getName());
		jmi.addActionListener(lpa);
		this.loadProfilesMenu.add(jmi);
	    }
	}
	// Add custom profiles
	dir=new File(Locate.getDataDirectory("ECUxPlot"),
	    "profiles");
	SaveProfileAction spa = new SaveProfileAction(dir);
	File profiles[] = dir.listFiles();
	if(profiles!=null && profiles.length>0) {
	    // add separator to load menu (divide static from custom)
	    this.loadProfilesMenu.add(new JSeparator());
	    lpa = new LoadProfileAction(dir);
	    for(File p : profiles) {
		if(p.isDirectory() && !p.getName().startsWith(".")) {
		    // add both static and custom items
		    JMenuItem jmi = new JMenuItem(p.getName());
		    jmi.addActionListener(lpa);
		    this.loadProfilesMenu.add(jmi);
		    jmi = new JMenuItem(p.getName());
		    jmi.addActionListener(spa);
		    this.saveProfilesMenu.add(jmi);
		}
	    }
	    // add separator to save menu (divide custom from new)
	    this.saveProfilesMenu.add(new JSeparator());
	}
	JMenuItem jmi = new JMenuItem("New Profile...");
	jmi.addActionListener(spa);
	this.saveProfilesMenu.add(jmi);
    }

    private class LoadProfileAction implements ActionListener {
	private File dir;
	public LoadProfileAction(File dir) {this.dir=dir;}
	public void actionPerformed(ActionEvent event) {
	    ProfileMenu pm = ProfileMenu.this;
	    try {
		File pdir=new File(this.dir, event.getActionCommand());
		File profiles[] = pdir.listFiles(new GenericFileFilter("xml"));
		for(File p : profiles) {
		    ECUxPlot.getPreferences();
		    Preferences.importPreferences(new FileInputStream(p));
		}
		if(pm.plotFrame!=null) pm.plotFrame.rebuild();
	    } catch (Exception e) {
		JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
		e.printStackTrace();
	    };
	}
    }

    private class SaveProfileAction implements ActionListener {
	private File dir;
	public SaveProfileAction(File dir) {this.dir=dir;}
	public void actionPerformed(ActionEvent event) {
	    String prof = event.getActionCommand();
	    boolean make = false;
	    ProfileMenu pm = ProfileMenu.this;
	    if(prof.equals("New Profile...")) {
		prof = ECUxPlot.showInputDialog("Enter profile name");
		if (prof==null) return;
		make = true;
	    }
	    try {
		File pdir=new File(this.dir, prof);
		if(make) {
		    if(pdir.isDirectory()) {
			JOptionPane.showMessageDialog(pm.plotFrame,
			    "Profile already exists");
			return;
		    }
		    if(!pdir.mkdirs()) {
			JOptionPane.showMessageDialog(pm.plotFrame,
			    "Failed to create profile");
			return;
		    }
		    pm.saveProfilesMenu.removeAll();
		    pm.loadProfilesMenu.removeAll();
		    updateProfiles();
		}
		if(pdir.isDirectory()) {
		    for (File f : pdir.listFiles()) f.delete();
		    pm.plotFrame.getEnv().c.get().exportNode(new
			FileOutputStream(new File(pdir, "constants.xml")));
		    pm.plotFrame.getEnv().f.get().exportNode(new
			FileOutputStream(new File(pdir, "fueling.xml")));
		}
	    } catch (Exception e) {
		JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
		e.printStackTrace();
	    };
	}
    }
}
