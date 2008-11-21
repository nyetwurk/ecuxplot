package org.nyet.ecuxplot;

import java.util.HashMap;
import java.awt.Point;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;

public class FATSChartFrame extends ChartFrame {
    private FATSDataset dataset;
    private ECUxPlot plotFrame;

    public static FATSChartFrame createFATSChartFrame(
	    HashMap<String, ECUxDataset> fileDatasets, ECUxPlot plotFrame) {
	FATSDataset dataset = new FATSDataset(fileDatasets);
	final JFreeChart chart =
	    ECUxChartFactory.createFATSChart(dataset);
	return new FATSChartFrame(chart, dataset, plotFrame);
    }

    public FATSChartFrame (JFreeChart chart, FATSDataset dataset,
	    ECUxPlot plotFrame) {
	super("FATS", chart);
	this.dataset=dataset;
	this.plotFrame=plotFrame;

	CategoryPlot plot = chart.getCategoryPlot();
	BarRenderer renderer = (BarRenderer)plot.getRenderer();

	renderer.setBaseItemLabelGenerator(
	    new org.jfree.chart.labels.StandardCategoryItemLabelGenerator(
		"{2}", new java.text.DecimalFormat("##.##")
	    )
	);
	renderer.setBaseItemLabelsVisible(true);

	this.setContentPane(new ECUxChartPanel(chart));
	this.setPreferredSize(windowSize());

	Point l = windowLocation();
	Point pl = plotFrame.getLocation();
	l.translate(pl.x, pl.y);
	super.setLocation(l);
    }

    public void setDatasets(HashMap<String, ECUxDataset> fileDatasets) {
	this.dataset.clear();
	java.util.Iterator itc = fileDatasets.values().iterator();
	while(itc.hasNext()) {
	    ECUxDataset data = (ECUxDataset) itc.next();
	    setDataset(data);
	}
    }

    public void setDataset(ECUxDataset data) {
	this.dataset.setValue(data);
    }
    public void clearDataset() {
	this.dataset.clear();
    }

    private java.awt.Dimension windowSize() {
	return new java.awt.Dimension(
	    this.plotFrame.getPreferences().getInt("FATSWindowWidth", 300),
	    this.plotFrame.getPreferences().getInt("FATSWindowHeight", 400));
    }

    private void putWindowSize() {
	this.plotFrame.getPreferences().putInt("FATSWindowWidth",
		this.getWidth());
	this.plotFrame.getPreferences().putInt("FATSWindowHeight",
		this.getHeight());
    }

    // relative to plot frame
    private java.awt.Point windowLocation() {
	return new java.awt.Point(
	    this.plotFrame.getPreferences().getInt("FATSWindowX",
		plotFrame.getWidth()),
	    this.plotFrame.getPreferences().getInt("FATSWindowY", 0));
    }

    private void putWindowLocation() {
	Point l = this.getLocation();
	Point plot = plotFrame.getLocation();
	l.translate(-plot.x, -plot.y);
	this.plotFrame.getPreferences().putInt("FATSWindowX", l.x);
	this.plotFrame.getPreferences().putInt("FATSWindowY", l.y);
    }

    // cleanup
    public void dispose() {
	putWindowSize();
	putWindowLocation();
	super.dispose();
    }
}
