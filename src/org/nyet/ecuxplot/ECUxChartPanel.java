package org.nyet.ecuxplot;

import java.awt.Cursor;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.AxisEntity;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.ui.ExtensionFileFilter;

public class ECUxChartPanel extends ChartPanel {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private ECUxPlot plotFrame;

    public ECUxChartPanel(JFreeChart chart, ECUxPlot plotFrame) {
        super(chart);
        this.plotFrame = plotFrame;
        setMouseWheelEnabled(true);
        setMouseZoomable(true);

        // Add axis click listener
        addChartMouseListener(new AxisClickListener());
    }

    private class AxisClickListener implements ChartMouseListener {
        @Override
        public void chartMouseClicked(ChartMouseEvent event) {
            ChartEntity entity = event.getEntity();
            if (entity instanceof AxisEntity) {
                AxisEntity axisEntity = (AxisEntity) entity;
                handleAxisClick(axisEntity);
            }
        }

        @Override
        public void chartMouseMoved(ChartMouseEvent event) {
            ChartEntity entity = event.getEntity();
            if (entity instanceof AxisEntity) {
                // UX Enhancement: Change cursor to hand pointer when hovering over clickable axes
                // This provides visual feedback that the axis area is interactive
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                // Reset to default cursor when not hovering over axes
                setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    private void handleAxisClick(AxisEntity axisEntity) {
        if (plotFrame == null) return;

        Axis axis = axisEntity.getAxis();
        XYPlot plot = (XYPlot) getChart().getPlot();

        // Get the mouse click location from the chart mouse event
        Point clickPoint = getMousePosition();
        if (clickPoint == null) {
            // Fallback to center if mouse position not available
            clickPoint = new Point(getWidth() / 2, getHeight() / 2);
        }

        // Determine which axis was clicked
        if (axis == plot.getDomainAxis()) {
            // X-axis clicked
            plotFrame.showXAxisDialog(clickPoint);
        } else if (axis == plot.getRangeAxis(0)) {
            // Y-axis (left) clicked
            plotFrame.showYAxisDialog(clickPoint);
        } else if (axis == plot.getRangeAxis(1)) {
            // Y2-axis (right) clicked
            plotFrame.showY2AxisDialog(clickPoint);
        }
    }

    public void doSaveAs(String fname) throws IOException {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fname + ".png"));
        final ExtensionFileFilter filter = new ExtensionFileFilter(
               localizationResources.getString("PNG_Image_Files"), ".png");
        fileChooser.addChoosableFileFilter(filter);

        final int option = fileChooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
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
