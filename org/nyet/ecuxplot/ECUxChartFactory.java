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

    public static JFreeChart create2AxisXYLineChart () {
	final JFreeChart chart = ChartFactory.createXYLineChart(
	    "", "", "",
	    new DefaultXYDataset(), PlotOrientation.VERTICAL,
	    true, true, false);

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);

	addAxis(plot, "", new DefaultXYDataset(), 1, true, false);

	return chart;
    }

    public static JFreeChart create2AxisScatterPlot () {
	final JFreeChart chart = ChartFactory.createScatterPlot(
	    "", "", "",
	    new DefaultXYDataset(), PlotOrientation.VERTICAL,
	    true, true, false);

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);

	addAxis(plot, "", new DefaultXYDataset(), 1, false, true);

	return chart;
    }

    public static JFreeChart create2AxisChart (boolean scatter) {
	if(scatter) {
	    return ECUxChartFactory.create2AxisScatterPlot();
	} else {
	    return ECUxChartFactory.create2AxisXYLineChart();
	}
    }

    public static void setChartStyle(JFreeChart chart, boolean lines,
	boolean shapes) {

	final XYPlot plot = chart.getXYPlot();
	for(int i=0; i<plot.getDatasetCount(); i++) {
	    final XYLineAndShapeRenderer renderer =
		(XYLineAndShapeRenderer)plot.getRenderer(i);

	    renderer.setBaseLinesVisible(lines);
	    renderer.setBaseShapesVisible(shapes);
	    /* not needed, until we play with custom shapes/lines */
	    /*
	    XYDataset dataset = plot.getDataset(i);
	    for(int j=0; j<dataset.getSeriesCount(); j++) {
		renderer.setSeriesLinesVisible(j, lines);
		renderer.setSeriesShapesVisible(j, shapes);
	    }
	    */
	}
    }

    public static void setSeriesPaint(JFreeChart chart, int series,
	java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(0, paint);
    }

    public static void setSeriesPaint(JFreeChart chart, int series,
	int subseries, java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(subseries, paint);
    }

    public static void addDataset(DefaultXYDataset d, ECUxDataset data,
		    Comparable xkey, Dataset.Key ykey) {
	ArrayList<Dataset.Range> ranges = data.getRanges();
	// add empty data in case we turn off filter, or we get some error
	double[][] empty = {{},{}};
	if(ranges.size()==0) {
	    Dataset.Key key = data.new Key(ykey);
	    key.hideFilename();
	    key.hideSeries();
	    d.addSeries(key, empty);
	    return;
	}
	for(int i=0;i<ranges.size();i++) {
	    Dataset.Key key = data.new Key(ykey, i);
	    if(ranges.size()==1) key.hideSeries();

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
	    Comparable k = d.getSeriesKey(0);
	    d.removeSeries(k);
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
	    Comparable k = d.getSeriesKey(i);
	    if(k.equals(ykey)) {
		d.removeSeries(k);
		// stay here, we just removed this index, the next one in
		// the list will appear at i again.
		i--;
	    }
	}
    }

    public static String getDatasetYkeys(DefaultXYDataset d) {
	String ret = "";
	boolean first = true;
	for(int i=0;i<d.getSeriesCount();i++) {
	    Comparable key = d.getSeriesKey(i);
	    String s;
	    if(key instanceof Dataset.Key) s = ((Dataset.Key)key).getString();
	    else s = key.toString();
	    if(first) {
		first = false;
		ret += s;
	    } else {
		ret += "," + s;
	    }
	}
	return ret;
    }
}
