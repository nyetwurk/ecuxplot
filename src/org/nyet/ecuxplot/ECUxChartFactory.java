package org.nyet.ecuxplot;

import java.awt.Color;
import java.util.ArrayList;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.XYPlot;
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

        final Color colors[][] = {
            {
	    new Color(0xff, 0x00, 0x00),
	    new Color(0x00, 0x16, 0xff),
	    new Color(0x00, 0xe0, 0xff),
	    new Color(0xff, 0xc8, 0x00)
	    },
            {
	    new Color(0xb9, 0x00, 0xff),
	    new Color(0x00, 0xff, 0x48),
	    new Color(0xb2, 0xff, 0x00),
	    new Color(0xff, 0x70, 0x00)
	    }
        };

	axis = axis%colors.length;

	// find index of ykey in the ykeys list to get a unique base color
	final String[] list = getDatasetYkeys(d);
        int yki;
	for (yki=0;yki<list.length;yki++)
	    if (list[yki].equals(ykey.getString())) break;

	final Color color=colors[axis][yki%(colors[axis].length)];

	// make a variety of dark/light colors based on yki
	int i;
	Color c;
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

	for (final Integer serie : series)
	    renderer.setSeriesStroke(serie, strokes[index%strokes.length]);
    }

    public static Integer[] addDataset(DefaultXYDataset d, ECUxDataset data,
		    Comparable<?> xkey, Dataset.Key ykey, Filter filter) {
	final ArrayList<Integer> ret = new ArrayList<Integer>();
	final ArrayList<Dataset.Range> ranges = data.getRanges();
	// add empty data in case we turn off filter, or we get some error
	final double[][] empty = {{},{}};
	if(ranges.size()==0) {
	    filter.currentRange = 0;
	    final Dataset.Key key = data.new Key(ykey, data);
	    key.hideRange();
	    d.addSeries(key, empty);
	    ret.add(d.indexOf(key));
	    return ret.toArray(new Integer[0]);
	}

	if(filter.currentRange >= ranges.size()) {
	    filter.currentRange = data.getRanges().size() - 1;
	}
	final boolean showAllRanges = filter.showAllRanges();
	for(int	i = (showAllRanges ? 0 : filter.currentRange);
	        i < (showAllRanges ? ranges.size() : filter.currentRange + 1);
	        i++) {
	    final Dataset.Key key = data.new Key(ykey, i, data);
	    if(ranges.size()==1) key.hideRange();
	    else key.showRange();

	    final Dataset.Range r=ranges.get(i);
	    try {
		final double [][] s = new double [][]{
		    data.getData(xkey, r),
		    data.getData(ykey, r)};
		d.addSeries(key, s);
		ret.add(d.indexOf(key));
	    } catch (final Exception e){
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
    public static void removeDataset(DefaultXYDataset d, Comparable<?> ykey) {
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
	final ArrayList<String> ret = new ArrayList<String>();
	for(int i=0;i<d.getSeriesCount();i++) {
	    final Comparable<?> key = d.getSeriesKey(i);
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
