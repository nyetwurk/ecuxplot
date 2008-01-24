import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;

import org.nyet.MapPack.*;

public class csvdump {

    public static void main(String[] args) throws Exception
    {
	showGraph(args[0]);
    }

    private static void showGraph(String fname) {
	Dataset data;
	try {
	  data = new Dataset(fname);
	} catch (Exception e) {
	  System.out.println(e.getMessage());
	  return;
  	}
	DefaultXYDataset xyDataset = new DefaultXYDataset();
	double[][] series = {data.asDoubles("TIME"), data.asDoubles("RPM")};
	xyDataset.addSeries("rpm vs time", series);

	JFreeChart chart = ChartFactory.createXYLineChart(
	    "createXYLineChart",
	    "TIME",
	    "RPM",
	    xyDataset, PlotOrientation.VERTICAL,
	    true, // show legend
	    false,
	    false);

	    BufferedImage image = chart.createBufferedImage(500,300);
	    JLabel lblChart = new JLabel();
	    lblChart.setIcon(new ImageIcon(image)
	);

	toJFrame(lblChart);
    }

    static void toJFrame(JLabel label) {
	WindowUtilities.setNativeLookAndFeel();
	JFrame f = new JFrame("new Jframe");
	f.setSize(800,600);
	Container content = f.getContentPane();
	content.setBackground(Color.white);
	content.setLayout(new FlowLayout());
	content.add(label);
	f.addWindowListener(new ExitListener());
	f.setVisible(true);
    }

    static void toJPG(JFreeChart chart, String filename) {
	try {
	  ChartUtilities.saveChartAsJPEG(new File(filename), chart, 500, 300);
	} catch (Exception e) {
	  System.out.println(e.getMessage());
	}
    }
}
