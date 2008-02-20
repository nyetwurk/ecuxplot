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
	    new DefaultXYDataset(), PlotOrientation.VERTICAL, true, true, false);

	final XYPlot plot = chart.getXYPlot();
	((NumberAxis) plot.getRangeAxis(0)).setAutoRangeIncludesZero(false);

	addAxis(plot, "", new DefaultXYDataset(), 1, true, false);

	return chart;
    }

    public static JFreeChart create2AxisScatterPlot () {
	final JFreeChart chart = ChartFactory.createScatterPlot(
	    "", "", "",
	    new DefaultXYDataset(), PlotOrientation.VERTICAL, true, true, false);

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
	    final XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer)plot.getRenderer(i);
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

    public static void setSeriesPaint(JFreeChart chart, int series, int subseries, java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(subseries, paint);
    }

    private static void addDataset(DefaultXYDataset d, ECUxDataset data, Comparable xkey, Comparable ykey) {
	data.filter.enabled = !xkey.equals("TIME");
	ArrayList<Dataset.Range> ranges = data.getRanges();
	if(ranges.size()==0) {
	    // add empty data in case we turn off filter
	    double[][] s = {{},{}};
	    d.addSeries(data.new Key(ykey.toString(),0), s);
	    return;
	}
	for(int i=0;i<ranges.size();i++) {
	    Dataset.Range r=ranges.get(i);
	    double[][] s = {data.getData(xkey, r), data.getData(ykey,r)};
	    d.addSeries(data.new Key(ykey.toString(),i), s);
	}
    }
    private static void removeDataset(DefaultXYDataset d, Comparable ykey) {
	if(ykey instanceof Dataset.Key) {
	    ykey = ((Dataset.Key)ykey).getString();
	}
	// ykey might be a string, so we have to walk series ourselves
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

    public static void setChartX(JFreeChart chart, ECUxDataset data,
	Comparable xkey) {
	final XYPlot plot = chart.getXYPlot();
	for(int i=0;i<plot.getDatasetCount();i++) {
	    XYDataset dataset = plot.getDataset(i);
	    final DefaultXYDataset newdataset = new DefaultXYDataset();
	    for(int j=0;j<dataset.getSeriesCount();j++) {
		Dataset.Key ykey = (Dataset.Key)dataset.getSeriesKey(j);
		addDataset(newdataset, data, xkey, ykey.getString());
	    }
	    plot.setDataset(i, newdataset);
	}
	String units = data.units(xkey);
	plot.getDomainAxis().setLabel(xkey.toString() + " ("+units+")");
    }
    public static void setChartX(ChartPanel chartPanel, ECUxDataset data,
	Comparable xkey) {
	setChartX(chartPanel.getChart(), data, xkey);
    }

    private static void updateLabelTitle(JFreeChart chart, ECUxDataset data) {
	final XYPlot plot = chart.getXYPlot();
	String title = "";
	for(int i=0; i<plot.getDatasetCount(); i++) {
	    final XYDataset dataset = plot.getDataset(i);
	    String seriesTitle = "", sprev=null;
	    String label="", lprev=null;
	    for(int j=0; dataset!=null && j<dataset.getSeriesCount(); j++) {
		Comparable key = dataset.getSeriesKey(j);
		if(key==null) continue;
		String s;

		if(key instanceof Dataset.Key) s = ((Dataset.Key)key).getString();
		else s = key.toString();

		String l = data.units(key);

		if(sprev==null || !s.equals(sprev)) {
		    if(!seriesTitle.equals("")) seriesTitle += ", ";
		    seriesTitle += s;
		}
		sprev=s;

		if(l==null) continue;
		if(lprev==null || !l.equals(lprev)) {
		    if(!label.equals("")) label += ", ";
		    label += l;
		}
		lprev=l;
	    }
	    if(!seriesTitle.equals("")) {
		if(!title.equals("")) title += " and ";
		title += seriesTitle;
	    }
	    plot.getRangeAxis(i).setLabel(label);
	}
	chart.setTitle(title);
    }

    public static void editChartY(JFreeChart chart, ECUxDataset data,
	Comparable xkey, Comparable ykey, int series, boolean add) {

	final XYPlot plot = chart.getXYPlot();
	DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(series);
	if(add) addDataset(dataset, data, xkey, ykey);
	else removeDataset(dataset, ykey);
	updateLabelTitle(chart, data);
	plot.getRangeAxis(series).setVisible(dataset.getSeriesCount()>0);
    }
    public static void editChartY(ChartPanel chartPanel, ECUxDataset data,
	Comparable xkey, Comparable ykey, int series, boolean add) {
	editChartY(chartPanel.getChart(), data, xkey, ykey, series, add);
    }
    public static void addChartY(JFreeChart chart, ECUxDataset data,
	Comparable xkey, Comparable ykey, int series) {
	editChartY(chart, data, xkey, ykey, series, true);
    }
    public static void addChartY(JFreeChart chart, ECUxDataset data,
	Comparable xkey, Comparable[] ykey, int series) {
	for(int i=0; i<ykey.length; i++)
	    editChartY(chart, data, xkey, ykey[i], series, true);
    }
    public static void removeChartY(JFreeChart chart, ECUxDataset data,
	Comparable xkey, Comparable ykey, int series) {
	editChartY(chart, data, xkey, ykey, series, false);
    }
}
