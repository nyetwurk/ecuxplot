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

	/* create axis 2 and renderer */
	final NumberAxis axis2 = new NumberAxis(y2AxisLabel);
	axis2.setAutoRangeIncludesZero(false);
	plot.setRangeAxis(1, axis2);
	plot.setDataset(1, dataset2);
	plot.mapDatasetToRangeAxis(1, 1);
	final StandardXYItemRenderer renderer2 = new StandardXYItemRenderer();
	plot.setRenderer(1, renderer2);

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

    public static void setChartX(ChartPanel chartPanel, Dataset data, Comparable xkey) {
	setChartX(chartPanel.getChart(), data, xkey);
    }

    public static void setChartX(JFreeChart chart, Dataset data, Comparable xkey) {
	final XYPlot plot = chart.getXYPlot();
	for(int i=0;i<plot.getDatasetCount();i++) {
	    XYDataset dataset = plot.getDataset(i);
	    final DefaultXYDataset newdataset = new DefaultXYDataset();
	    for(int j=0;j<dataset.getSeriesCount();j++) {
		Comparable ykey = dataset.getSeriesKey(j);
		double[][] s = {data.asDoubles(xkey.toString()), data.asDoubles(ykey.toString())};
		newdataset.addSeries(ykey, s);
	    }
	    plot.setDataset(i, newdataset);
	}
	plot.getDomainAxis().setLabel(xkey.toString());
    }
}
