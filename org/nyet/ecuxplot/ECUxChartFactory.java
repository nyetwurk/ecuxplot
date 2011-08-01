package org.nyet.ecuxplot;

import java.util.ArrayList;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.category.DefaultCategoryDataset;


import org.nyet.logfile.Dataset;

public class ECUxChartFactory {
    private static void addAxis(XYPlot plot, String label, XYDataset dataset,
	int series, boolean lines, boolean shapes) {
	final NumberAxis axis = new NumberAxis(label);
	axis.setAutoRangeIncludesZero(false);
	plot.setRangeAxis(1, axis);
	plot.setDataset(1, dataset);
	plot.mapDatasetToRangeAxis(1, 1);
	plot.setRenderer(1, new XYLineAndShapeRenderer(lines, shapes));
    }

    private static JFreeChart create2AxisXYLineChart () {
	final JFreeChart chart = ChartFactory.createXYLineChart(
	    "", "", "",
	    new DefaultXYDataset(), PlotOrientation.VERTICAL,
	    true, true, false);

	final XYPlot plot = chart.getXYPlot();
	addAxis(plot, "", new DefaultXYDataset(), 1, true, false);

	return chart;
    }

    private static JFreeChart create2AxisScatterPlot () {
	final JFreeChart chart = ChartFactory.createScatterPlot(
	    "", "", "",
	    new DefaultXYDataset(), PlotOrientation.VERTICAL,
	    true, true, false);

	final XYPlot plot = chart.getXYPlot();
	addAxis(plot, "", new DefaultXYDataset(), 1, false, true);

	return chart;
    }

    public static JFreeChart create2AxisChart (boolean scatter) {
	JFreeChart chart;
	if(scatter) {
	    chart = ECUxChartFactory.create2AxisScatterPlot();
	} else {
	    chart = ECUxChartFactory.create2AxisXYLineChart();
	}

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);
	plot.getRangeAxis(1).setLabelFont(
		plot.getRangeAxis(0).getLabelFont());

	return chart;
    }

    public static void setChartStyle(JFreeChart chart, boolean lines,
	boolean shapes) {

	final XYPlot plot = chart.getXYPlot();
	for(int i=0; i<plot.getDatasetCount(); i++) {
	    final XYLineAndShapeRenderer renderer =
		(XYLineAndShapeRenderer)plot.getRenderer(i);

	    renderer.setBaseLinesVisible(lines);
	    renderer.setBaseShapesVisible(shapes);
	}
    }

    // set both axis and all series the same paint
    public static void setPaint(JFreeChart chart,
	java.awt.Paint paint) {
	setAxisPaint(chart, 0, paint);
	setAxisPaint(chart, 1, paint);
    }

    // set all series in an axis the same paint
    public static void setAxisPaint(JFreeChart chart, int axis,
	java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(axis);
	// renderer.setBasePaint(paint);
	for(int i=0; i<plot.getDataset(axis).getSeriesCount(); i++)
	    renderer.setSeriesPaint(i, paint);
    }

    // set both axis for a given series the same paint
    public static void setSeriesPaint(JFreeChart chart, int series,
	    java.awt.Paint paint) {
	setSeriesPaint(chart, 0, series, paint);
	setSeriesPaint(chart, 1, series, paint);
    }
    public static void setSeriesPaint(JFreeChart chart, int axis,
	    int series, java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(axis);
	renderer.setSeriesPaint(series, paint);
    }

    // set both axis and all series the same stroke
    public static void setStroke(JFreeChart chart,
	    java.awt.Stroke stroke) {
	setSeriesStroke(chart, 0, stroke);
	setSeriesStroke(chart, 1, stroke);
    }

    // set all series on an axis the same stroke
    public static void setAxisStroke(JFreeChart chart, int axis,
	    java.awt.Stroke stroke) {
	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(axis);
	// renderer.setBaseStroke(stroke);
	for(int i=0; i<plot.getDataset(axis).getSeriesCount(); i++)
	    renderer.setSeriesStroke(i, stroke);
    }

    // set both axis for a given series the same stroke
    public static void setSeriesStroke(JFreeChart chart, int series,
	    java.awt.Stroke stroke) {
	setSeriesStroke(chart, 0, series, stroke);
	setSeriesStroke(chart, 1, series, stroke);
    }
    public static void setSeriesStroke(JFreeChart chart, int axis,
	int series, java.awt.Stroke stroke) {
	final XYPlot plot = chart.getXYPlot();
	plot.getRenderer(axis).setSeriesStroke(series, stroke);
    }

    public static void addDataset(DefaultXYDataset d, ECUxDataset data,
		    Comparable xkey, Dataset.Key ykey) {
	ArrayList<Dataset.Range> ranges = data.getRanges();
	// add empty data in case we turn off filter, or we get some error
	double[][] empty = {{},{}};
	if(ranges.size()==0) {
	    Dataset.Key key = data.new Key(ykey);
	    key.hideRange();
	    d.addSeries(key, empty);
	    return;
	}
	for(int i=0;i<ranges.size();i++) {
	    Dataset.Key key = data.new Key(ykey, i);
	    if(ranges.size()==1) key.hideRange();
	    else key.showRange();

	    Dataset.Range r=ranges.get(i);
	    try {
		double [][] s = new double [][]{
		    data.getData(xkey, r),
		    data.getData(ykey, r)};
		d.addSeries(key, s);
	    } catch (Exception e){
		d.addSeries(key, empty);
	    }
	}
    }

    // remove ALL series from the dataset
    public static void removeDataset(DefaultXYDataset d) {
	while(d.getSeriesCount()>0) {
	    d.removeSeries(d.getSeriesKey(0));
	}
    }

    // remove ALL series that match the data column tag
    public static void removeDataset(DefaultXYDataset d, Comparable ykey) {
	if(ykey instanceof Dataset.Key) {
	    // pull out ONLY the data column tag, and ykey is now a String.
	    ykey = ((Dataset.Key)ykey).getString();
	}
	// ykey is now a string, so we have to walk series ourselves
	// and call "equals" for String, which only compares the data tag
	for(int i=0;i<d.getSeriesCount();i++) {
	    // When we delete things, the next key falls into our
	    // position, so stay there and clean.
	    while(i<d.getSeriesCount() && d.getSeriesKey(i).equals(ykey)) {
		d.removeSeries(d.getSeriesKey(i));
	    }
	}
    }

    public static String [] getDatasetYkeys(DefaultXYDataset d) {
	ArrayList<String> ret = new ArrayList<String>();
	for(int i=0;i<d.getSeriesCount();i++) {
	    Comparable key = d.getSeriesKey(i);
	    if(key instanceof Dataset.Key)
		ret.add(((Dataset.Key)key).getString());
	    else
		ret.add(key.toString());
	}
	return ret.toArray(new String[0]);
    }

    public static JFreeChart createFATSChart (FATSDataset dataset) {
	return ChartFactory.createBarChart3D (
	    dataset.getTitle(), "", "",
	    dataset,
	    PlotOrientation.VERTICAL,
	    true, true, false);
    }
}
