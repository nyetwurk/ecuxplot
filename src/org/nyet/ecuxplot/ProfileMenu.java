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
    private final ECUxPlot plotFrame;
    private final JMenu loadProfilesMenu;
    private final JMenu saveProfilesMenu;
    private final JMenu deleteProfilesMenu;

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
        this.deleteProfilesMenu = new JMenu("Delete Profile...");
        updateProfiles();
        this.add(this.loadProfilesMenu);
        this.add(this.saveProfilesMenu);
        this.add(this.deleteProfilesMenu);
    }

    private void updateProfiles() {
        File dir;
        try {
            // Add static profiles
            dir=new File(Locate.getClassDirectory(this),
                "profiles");

            // Try CWD if no profile dir in the Class directory (running in debugger)
            if (!dir.isDirectory()) {
                dir=new File("profiles");
            }
        } catch (final Exception e) {
            final ProfileMenu pm = ProfileMenu.this;
            JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
            e.printStackTrace();
            return;
        };

        if (dir.isDirectory()) {
            final LoadProfileAction lpa = new LoadProfileAction(dir);
            for(final File p : dir.listFiles()) {
                if(p.isDirectory() && !p.getName().startsWith(".")) {
                    // do not add static profiles to "save" menu
                    final JMenuItem jmi = new JMenuItem(p.getName());
                    jmi.addActionListener(lpa);
                    this.loadProfilesMenu.add(jmi);
                }
            }
        }

        // Add custom profiles
        dir=new File(Locate.getDataDirectory("ECUxPlot"), "profiles");

        // Issue #27 - create missing directories if needed
        if (!dir.exists()) {
            //final ProfileMenu pm = ProfileMenu.this;
            String err = null;
            try {
                if (!dir.mkdirs()) {
                    err = "Failed to create profile directory " + dir.getPath();
                }
            } catch (SecurityException se) {
                err = "Failed to create profile directory " + dir.getPath() + "\n" + se.toString();
            }
            if (err != null) System.out.println(err);
        }

        if (dir.isDirectory()) {
            final SaveProfileAction spa = new SaveProfileAction(dir);
            final DeleteProfileAction dpa = new DeleteProfileAction(dir);
            final File profiles[] = dir.listFiles();
            if(profiles!=null && profiles.length>0) {
                // add separator to load menu (divide static from custom)
                this.loadProfilesMenu.add(new JSeparator());
                final LoadProfileAction lpa = new LoadProfileAction(dir);
                for(final File p : profiles) {
                    if(p.isDirectory() && !p.getName().startsWith(".")) {
                        // add both static and custom items
                        JMenuItem jmi = new JMenuItem(p.getName());
                        jmi.addActionListener(lpa);
                        this.loadProfilesMenu.add(jmi);
                        jmi = new JMenuItem(p.getName());
                        jmi.addActionListener(spa);
                        this.saveProfilesMenu.add(jmi);
                        jmi = new JMenuItem(p.getName());
                        jmi.addActionListener(dpa);
                        this.deleteProfilesMenu.add(jmi);
                    }
                }
                // add separator to save menu (divide custom from new)
                this.saveProfilesMenu.add(new JSeparator());
            }
            final JMenuItem jmi = new JMenuItem("New Profile...");
            jmi.addActionListener(spa);
            this.saveProfilesMenu.add(jmi);
        }
    }

    private class LoadProfileAction implements ActionListener {
        private final File dir;
        public LoadProfileAction(File dir) {this.dir=dir;}
        @Override
        public void actionPerformed(ActionEvent event) {
            final ProfileMenu pm = ProfileMenu.this;
            try {
                final File pdir=new File(this.dir, event.getActionCommand());
                final File profiles[] = pdir.listFiles(new GenericFileFilter("xml"));
                for(final File p : profiles) {
                    ECUxPlot.getPreferences();
                    Preferences.importPreferences(new FileInputStream(p));
                }
                if(pm.plotFrame!=null) {
                    pm.plotFrame.rebuild();
                    pm.plotFrame.updateFATSVisibility();
                }
                // Update FATS window after profile load
                if(pm.plotFrame.fatsFrame != null) {
                    pm.plotFrame.fatsFrame.updateRpmPerMphFromConstants();
                }
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
                e.printStackTrace();
            };
        }
    }

    private class SaveProfileAction implements ActionListener {
        private final File dir;
        public SaveProfileAction(File dir) {this.dir=dir;}
        @Override
        public void actionPerformed(ActionEvent event) {
            String prof = event.getActionCommand();
            boolean make = false;
            final ProfileMenu pm = ProfileMenu.this;
            if(prof.equals("New Profile...")) {
                prof = ECUxPlot.showInputDialog("Enter profile name");
                if (prof==null) return;
                make = true;
            }
            try {
                final File pdir=new File(this.dir, prof);
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
                    for (final File f : pdir.listFiles()) f.delete();
                    pm.plotFrame.getEnv().c.get().exportNode(new
                        FileOutputStream(new File(pdir, "constants.xml")));
                    pm.plotFrame.getEnv().f.get().exportNode(new
                        FileOutputStream(new File(pdir, "fueling.xml")));
                }
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(pm.plotFrame, e.toString());
                e.printStackTrace();
            };
        }
    }

    private class DeleteProfileAction implements ActionListener {
        private final File dir;
        public DeleteProfileAction(File dir) {this.dir=dir;}
        @Override
        public void actionPerformed(ActionEvent event) {
            final String profileName = event.getActionCommand();
            final ProfileMenu pm = ProfileMenu.this;

            // Show confirmation dialog
            final int result = JOptionPane.showConfirmDialog(
                pm.plotFrame,
                "Are you sure you want to delete the profile '" + profileName + "'?\n\nThis action cannot be undone.",
                "Delete Profile",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (result != JOptionPane.YES_OPTION) {
                return; // User cancelled
            }

            try {
                final File profileDir = new File(this.dir, profileName);
                if (profileDir.isDirectory()) {
                    // Delete all files in the profile directory
                    final File[] files = profileDir.listFiles();
                    if (files != null) {
                        for (final File file : files) {
                            if (!file.delete()) {
                                JOptionPane.showMessageDialog(pm.plotFrame,
                                    "Failed to delete file: " + file.getName());
                                return;
                            }
                        }
                    }

                    // Delete the profile directory itself
                    if (!profileDir.delete()) {
                        JOptionPane.showMessageDialog(pm.plotFrame,
                            "Failed to delete profile directory");
                        return;
                    }

                    // Refresh the menus
                    pm.saveProfilesMenu.removeAll();
                    pm.loadProfilesMenu.removeAll();
                    pm.deleteProfilesMenu.removeAll();
                    pm.updateProfiles();

                    JOptionPane.showMessageDialog(pm.plotFrame,
                        "Profile '" + profileName + "' deleted successfully");
                } else {
                    JOptionPane.showMessageDialog(pm.plotFrame,
                        "Profile directory not found: " + profileDir.getPath());
                }
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(pm.plotFrame,
                    "Error deleting profile: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
