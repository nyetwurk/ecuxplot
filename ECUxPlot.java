import java.awt.Color;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.Month;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

public class ECUxPlot extends ApplicationFrame {

    public static JFreeChart Create2AxisXYLineChart(
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

    public static void SetSeriesPaint(JFreeChart chart, int series,
	java.awt.Paint paint) {

	final XYPlot plot = chart.getXYPlot();
	final XYItemRenderer renderer = plot.getRenderer(series);
	renderer.setSeriesPaint(0, paint);
    }

    public ECUxPlot(final String fname, final String title, final String chartTitle) {
        super(title);
	Dataset data;
	try {
	    data = new Dataset(fname);
	} catch (Exception e) {
	    System.out.println(e.getMessage());
	    return;
	}

	final String xAxisLegend = "X Axis";

        final XYDataset dataset1 = createDataset(data, "RPM");
	final String yAxisLegend = "RPM";

        final XYDataset dataset2 = createDataset(data, "EngineLoad");
	final String y2AxisLegend = "%";

        final JFreeChart chart = Create2AxisXYLineChart(
            chartTitle, xAxisLegend, 
            yAxisLegend, y2AxisLegend,
            dataset1, dataset2, 
	    PlotOrientation.VERTICAL,
            true, 	// show legend
            true, 	// show tooltips
            false	// show urls
        );
	SetSeriesPaint(chart, 0, Color.red);
	SetSeriesPaint(chart, 1, Color.blue);

        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private XYDataset createDataset(Dataset data, Comparable key) {
        final DefaultXYDataset dataset = new DefaultXYDataset();
	double[][] s = {data.asDoubles("TIME"), data.asDoubles(key.toString())};
        dataset.addSeries(key, s);

        return dataset;
    }

    public static void main(final String[] args) {

        final ECUxPlot plot = new ECUxPlot(args[0], "ECUxPlot", "Chart Title");
        plot.pack();
        RefineryUtilities.centerFrameOnScreen(plot);
        plot.setVisible(true);
    }
}

