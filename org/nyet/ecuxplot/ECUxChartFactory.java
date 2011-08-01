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

    // set all series of a given ykey different shades of a base paint
    public static void setAxisPaint(JFreeChart chart, int axis,
	DefaultXYDataset d, Dataset.Key ykey, Integer[] series) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(axis);

        final java.awt.Color colors[][] = {
            { java.awt.Color.red, java.awt.Color.blue, java.awt.Color.green},
            { java.awt.Color.yellow, java.awt.Color.cyan, java.awt.Color.gray}
        };

	axis = axis%colors.length;

	// find index of ykey in the ykeys list to get a unique base color
	String[] list = getDatasetYkeys(d);
        int yki;
	for (yki=0;yki<list.length;yki++)
	    if (list[yki].equals(ykey.getString())) break;

	java.awt.Color color=colors[axis][yki%(colors[axis].length)];

	// make a variety of dark/light colors based on yki
	int i;
	java.awt.Color c;
	for(i=series.length/2, c=color; i>=0; i--, c=c.darker())
	    renderer.setSeriesPaint(series[i], c);

	for(i=(series.length/2+1), c=color; i<series.length; i++, c=c.brighter())
	    renderer.setSeriesPaint(series[i], c);
    }

    // set all series for a given filename to the same stroke
    public static void setAxisStroke(JFreeChart chart, int axis,
	DefaultXYDataset d, Dataset.Key ykey, Integer[] series, int index) {
	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(axis);

        final java.awt.Stroke strokes[] = {
	    new java.awt.BasicStroke(1.0f),
	    new java.awt.BasicStroke(
		1.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND,
		1.0f, new float[] {3.0f, 3.0f}, 0.0f
	    ),
	    new java.awt.BasicStroke(
		1.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND,
		1.0f, new float[] {6.0f, 6.0f}, 0.0f
	    ),
	    new java.awt.BasicStroke(
		1.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND,
		1.0f, new float[] {6.0f, 3.0f, 1.0f, 3.0f}, 0.0f
	    )
        };

	for(int i=0; i<series.length; i++)
	    renderer.setSeriesStroke(series[i], strokes[index%strokes.length]);
    }

    public static Integer[] addDataset(DefaultXYDataset d, ECUxDataset data,
		    Comparable xkey, Dataset.Key ykey) {
	ArrayList<Integer> ret = new ArrayList<Integer>();
	ArrayList<Dataset.Range> ranges = data.getRanges();
	// add empty data in case we turn off filter, or we get some error
	double[][] empty = {{},{}};
	if(ranges.size()==0) {
	    Dataset.Key key = data.new Key(ykey);
	    key.hideRange();
	    d.addSeries(key, empty);
	    ret.add(d.indexOf(key));
	    return ret.toArray(new Integer[0]);
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
		ret.add(d.indexOf(key));
	    } catch (Exception e){
		d.addSeries(key, empty);
		ret.add(d.indexOf(key));
	    }
	}
	return ret.toArray(new Integer[0]);
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
	    String s;
	    if(key instanceof Dataset.Key)
		s=((Dataset.Key)key).getString();
	    else
		s=key.toString();

	    if (!ret.contains(s)) ret.add(s);
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
