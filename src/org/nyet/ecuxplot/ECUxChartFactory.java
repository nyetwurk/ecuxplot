package org.nyet.ecuxplot;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;
import java.util.ArrayList;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.nyet.logfile.Dataset;
import org.nyet.util.Strings;

public class ECUxChartFactory {
    /**
     * Calculate appropriate axis range with padding, ensuring zero is included
     * when dealing with negative values for better visual clarity.
     * @param min the minimum data value
     * @param max the maximum data value
     * @param paddingPercent percentage of range to add as padding (default 5%)
     * @return array with [minRange, maxRange]
     */
    private static double[] calculateAxisRange(double min, double max, double paddingPercent) {
        if (min == max) {
            // Handle case where all values are the same
            double center = min;
            double padding = Math.abs(center) * 0.1; // 10% padding
            return new double[]{center - padding, center + padding};
        }

        double range = max - min;
        double padding = range * paddingPercent;

        // If all values are negative, ensure zero is included with padding
        if (max <= 0) {
            double minRange = min - padding;
            double maxRange = padding; // Include zero with padding above
            return new double[]{minRange, maxRange};
        }

        // If all values are positive, use normal padding
        if (min >= 0) {
            return new double[]{min - padding, max + padding};
        }

        // Mixed positive and negative values - use normal padding
        return new double[]{min - padding, max + padding};
    }

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

    /**
     * Apply custom axis range calculation to ensure proper padding,
     * especially for negative values where zero should be included.
     * @param chart the chart to modify
     * @param axisIndex the axis index to modify (0 or 1)
     * @param dataset the dataset to analyze for range calculation
     */
    public static void applyCustomAxisRange(JFreeChart chart, int axisIndex, XYDataset dataset) {
        final XYPlot plot = chart.getXYPlot();
        final NumberAxis axis = (NumberAxis) plot.getRangeAxis(axisIndex);

        if (dataset == null || dataset.getSeriesCount() == 0) {
            return;
        }

        // Find min and max values across all series
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;
        boolean hasData = false;

        for (int series = 0; series < dataset.getSeriesCount(); series++) {
            for (int item = 0; item < dataset.getItemCount(series); item++) {
                double yValue = dataset.getYValue(series, item);
                if (!Double.isNaN(yValue)) {
                    minValue = Math.min(minValue, yValue);
                    maxValue = Math.max(maxValue, yValue);
                    hasData = true;
                }
            }
        }

        if (!hasData) {
            return;
        }

        // Check for invalid range (essentially zero range)
        if (Math.abs(maxValue - minValue) < 1e-10) {
            return;
        }

        // Calculate custom range with padding
        double[] range = calculateAxisRange(minValue, maxValue, 0.05); // 5% padding

        // Set the axis range
        axis.setRange(range[0], range[1]);

        // Only disable auto-range if it's currently enabled to avoid triggering events
        if (axis.isAutoRange()) {
            axis.setAutoRange(false);
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
            filter.setCurrentRange(0);
            final Dataset.Key key = data.new Key(ykey, data);
            key.hideRange();
            d.addSeries(key, empty);
            ret.add(d.indexOf(key));
            return ret.toArray(new Integer[0]);
        }

        if(filter.getCurrentRange() >= ranges.size()) {
            filter.setCurrentRange(data.getRanges().size() - 1);
        }
        final boolean showAllRanges = filter.showAllRanges();
        for(int i = (showAllRanges ? 0 : filter.getCurrentRange());
                i < (showAllRanges ? ranges.size() : filter.getCurrentRange() + 1);
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
        JFreeChart chart = ChartFactory.createBarChart3D (
            dataset.getTitle(), "", "",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false);

        // Configure the CategoryAxis to show more of the labels
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryAxis domainAxis = plot.getDomainAxis();

        // Allow labels to take up more space (default is usually 0.8)
        domainAxis.setMaximumCategoryLabelWidthRatio(1.2f);

        return chart;
    }

    /**
     * Custom XYLineAndShapeRenderer that elides legend labels for display.
     * Elides only the filename portion while preserving variable name and range.
     * This allows long filenames to be displayed compactly in the legend without
     * affecting the underlying series keys used for indexing and lookup.
     */
    private static class ElidedLegendRenderer extends XYLineAndShapeRenderer {
        private static final int MAX_LEGEND_LABEL_LENGTH = 15;

        @Override
        public LegendItem getLegendItem(int datasetIndex, int series) {
            LegendItem original = super.getLegendItem(datasetIndex, series);
            if (original != null) {
                String elidedLabel = elideLegendLabel(original.getLabel());
                if (!elidedLabel.equals(original.getLabel())) {
                    LegendItem item = new LegendItem(elidedLabel);
                    item.setDescription(original.getDescription());
                    item.setToolTipText(original.getToolTipText());
                    item.setURLText(original.getURLText());
                    item.setShape(original.getShape());
                    item.setFillPaint(original.getFillPaint());
                    item.setOutlineStroke(original.getOutlineStroke());
                    item.setOutlinePaint(original.getOutlinePaint());
                    item.setLine(original.getLine());
                    item.setLinePaint(original.getLinePaint());
                    item.setLineStroke(original.getLineStroke());
                    item.setSeriesKey(original.getSeriesKey());
                    item.setSeriesIndex(original.getSeriesIndex());
                    item.setDataset(original.getDataset());
                    item.setDatasetIndex(original.getDatasetIndex());
                    return item;
                }
            }
            return original;
        }

        private String elideLegendLabel(String label) {
            // Check if this is a Dataset.Key label (format: "filename:varname rangeNum")
            int colonIndex = label.indexOf(':');
            if (colonIndex > 0 && colonIndex < label.length() - 1) {
                String filename = label.substring(0, colonIndex);
                String rest = label.substring(colonIndex);
                // Use smart elision to preserve both version prefix and timestamp suffix
                String elidedFilename = Strings.elide(filename, MAX_LEGEND_LABEL_LENGTH);
                return elidedFilename + rest;
            }
            // Not a Dataset.Key format, use smart elision
            return Strings.elide(label, MAX_LEGEND_LABEL_LENGTH);
        }
    }

    /**
     * Apply elided legend labels to a chart.
     * Call this AFTER setAxisPaint and setAxisStroke to preserve customizations.
     * @param chart the JFreeChart to apply elided legend labels to
     */
    public static void applyElidedLegendLabels(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();

        // Wrap each axis renderer with elided legend functionality
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            XYItemRenderer existing = plot.getRenderer(i);
            if (!(existing instanceof ElidedLegendRenderer)) {
                ElidedLegendRenderer elided = new ElidedLegendRenderer();

                // Copy all settings from existing renderer
                if (existing instanceof XYLineAndShapeRenderer) {
                    XYLineAndShapeRenderer lr = (XYLineAndShapeRenderer) existing;
                    elided.setBaseLinesVisible(lr.getBaseLinesVisible());
                    elided.setBaseShapesVisible(lr.getBaseShapesVisible());
                }

                // Copy paint and stroke for all series
                for (int series = 0; series < 100; series++) {
                    Paint paint = existing.getSeriesPaint(series);
                    if (paint != null) {
                        elided.setSeriesPaint(series, paint);
                    }
                    Stroke stroke = existing.getSeriesStroke(series);
                    if (stroke != null) {
                        elided.setSeriesStroke(series, stroke);
                    }
                }

                plot.setRenderer(i, elided);
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
