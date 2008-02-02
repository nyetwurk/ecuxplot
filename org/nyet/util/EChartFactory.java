package org.nyet.util;

import org.nyet.logfile.Dataset;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.DefaultXYDataset;


public class EChartFactory {
    private static void addAxis(XYPlot plot, String label, XYDataset dataset, int series, boolean lines, boolean shapes) {
	final NumberAxis axis = new NumberAxis(label);
	axis.setAutoRangeIncludesZero(false);
	plot.setRangeAxis(1, axis);
	plot.setDataset(1, dataset);
	plot.mapDatasetToRangeAxis(1, 1);
	plot.setRenderer(1, new XYLineAndShapeRenderer(lines, shapes));
    }

    public static JFreeChart create2AxisXYLineChart (
	String title, String xAxisLabel,
	String yAxisLabel, String y2AxisLabel,
	XYDataset dataset1, XYDataset dataset2,
	PlotOrientation orientation,
	boolean legend, boolean tooltips, boolean urls) {

	final JFreeChart chart = ChartFactory.createXYLineChart(
	    title, xAxisLabel, yAxisLabel,
	    dataset1, orientation, legend, tooltips, urls);

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);

	addAxis(plot, y2AxisLabel, dataset2, 1, true, false);

	return chart;
    }

    public static JFreeChart create2AxisScatterPlot (
	String title, String xAxisLabel,
	String yAxisLabel, String y2AxisLabel,
	XYDataset dataset1, XYDataset dataset2,
	PlotOrientation orientation,
	boolean legend, boolean tooltips, boolean urls) {

	final JFreeChart chart = ChartFactory.createScatterPlot(
	    title, xAxisLabel, yAxisLabel,
	    dataset1, orientation, legend, tooltips, urls);

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);

	addAxis(plot, y2AxisLabel, dataset2, 1, false, true);

	return chart;
    }

    public static void setSeriesPaint(JFreeChart chart, int series,
	java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(0, paint);
    }

    public static void setSeriesPaint(JFreeChart chart, int series, int subseries, java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(subseries, paint);
    }

    public static XYDataset createDataset(Dataset data, Comparable xkey, Comparable ykey) throws Exception {
        final DefaultXYDataset dataset = new DefaultXYDataset();
	double[][] s = {data.asDoubles(xkey.toString()), data.asDoubles(ykey.toString())};
        dataset.addSeries(ykey, s);

        return dataset;
    }

    public static void setChartX(JFreeChart chart, Dataset data,
    	Comparable xkey) {
	final XYPlot plot = chart.getXYPlot();
	for(int i=0;i<plot.getDatasetCount();i++) {
	    XYDataset dataset = plot.getDataset(i);
	    final DefaultXYDataset newdataset = new DefaultXYDataset();
	    for(int j=0;j<dataset.getSeriesCount();j++) {
		Comparable ykey = dataset.getSeriesKey(j);
		data.filter.enabled = !xkey.equals("TIME");
		double[][] s = {data.asDoubles(xkey.toString()), data.asDoubles(ykey.toString())};
		newdataset.addSeries(ykey, s);
	    }
	    plot.setDataset(i, newdataset);
	}
	String units = data.units(xkey);
	plot.getDomainAxis().setLabel(xkey.toString() + " ("+units+")");
    }
    public static void setChartX(ChartPanel chartPanel, Dataset data,
	Comparable xkey) {
	setChartX(chartPanel.getChart(), data, xkey);
    }

    private static void updateLabelTitle(JFreeChart chart, Dataset data) {
	final XYPlot plot = chart.getXYPlot();
	String title = "";
	for(int i=0; i<plot.getDatasetCount(); i++) {
	    final XYDataset dataset = plot.getDataset(i);
	    String seriesTitle = "";
	    String label=null, prev=null;
	    for(int j=0; dataset!=null && j<dataset.getSeriesCount(); j++) {
		Comparable key = dataset.getSeriesKey(j);
		String l = data.units(key);
		if(l!=null) {
		    if(label==null) label=l;
		    if(prev!=null && !l.equals(prev)) {
			label="";
		    }
		    if(!seriesTitle.equals("")) seriesTitle += ", ";
		    seriesTitle += key.toString();
		    prev=l;
		}
	    }
	    if(!seriesTitle.equals("")) {
		if(!title.equals("")) title += " and ";
		title += seriesTitle;
	    }
	    if(label==null) label="";
	    plot.getRangeAxis(i).setLabel(label);
	}
	chart.setTitle(title);
    }

    public static void editChartY(JFreeChart chart, Dataset data,
	Comparable xkey, Comparable ykey, int series, boolean add) {

	final XYPlot plot = chart.getXYPlot();
	DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(series);
	if(add) {
	    data.filter.enabled = !xkey.equals("TIME");
	    double[][] s = {data.asDoubles(xkey.toString()), data.asDoubles(ykey.toString())};
	    dataset.addSeries(ykey, s);
	} else {
	    dataset.removeSeries(ykey);
	}
	updateLabelTitle(chart, data);
    }
    public static void editChartY(ChartPanel chartPanel, Dataset data,
	Comparable xkey, Comparable ykey, int series, boolean add) {
	editChartY(chartPanel.getChart(), data, xkey, ykey, series, add);
    }
    public static void addChartY(JFreeChart chart, Dataset data,
	Comparable xkey, Comparable ykey, int series) {
	editChartY(chart, data, xkey, ykey, series, true);
    }
    public static void removeChartY(JFreeChart chart, Dataset data,
    	Comparable xkey, Comparable ykey, int series) {
	editChartY(chart, data, xkey, ykey, series, false);
    }
}
