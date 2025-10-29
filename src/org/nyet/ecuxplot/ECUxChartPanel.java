package org.nyet.ecuxplot;

import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.ui.ExtensionFileFilter;

public class ECUxChartPanel extends ChartPanel {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ECUxChartPanel(JFreeChart chart) {
        super(chart);
        setMouseWheelEnabled(true);
        setMouseZoomable(true);
    }

    public void doSaveAs(String fname) throws IOException {
        // Get last save directory from preferences
        final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(org.nyet.ecuxplot.ECUxPlot.class);
        final String lastDir = prefs.get("chooserDirSave",
            System.getProperty("user.home"));

        final JFileChooser fileChooser = new JFileChooser(lastDir);
        fileChooser.setSelectedFile(new File(fname + ".png"));
        final ExtensionFileFilter filter = new ExtensionFileFilter(
               localizationResources.getString("PNG_Image_Files"), ".png");
        fileChooser.addChoosableFileFilter(filter);

        final int option = fileChooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
           // Save last save directory to preferences
           prefs.put("chooserDirSave", fileChooser.getCurrentDirectory().toString());

           String filename = fileChooser.getSelectedFile().getPath();
           if (isEnforceFileExtensions()) {
               if (!filename.endsWith(".png")) {
                   filename = filename + ".png";
               }
           }
           saveChartAsPNG(new File(filename));
        }
    }

    public void saveChartAsPNG(File f) throws IOException {
           ChartUtilities.saveChartAsPNG(f, this.getChart(), this.getWidth(),
                   this.getHeight());
    }

    public void saveChartAsPNG(String filename) throws IOException {
           this.saveChartAsPNG(new File(filename));
    }
}

// vim: set sw=4 ts=8 expandtab:
