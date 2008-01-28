import java.io.File;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.*;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JCheckBox;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;

import org.jfree.data.time.Month;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.logfile.Dataset;
import org.nyet.util.WaitCursor;
import org.nyet.util.EChartFactory;

public class ECUxPlot extends ApplicationFrame implements ActionListener {
    private Dataset dataSet;
    private ChartPanel chart;
    private JMenu xAxisMenu;
    private JMenu[] yAxisMenu;

    private void setupAxisMenus(String[] headers) {
	ButtonGroup bg = new ButtonGroup();
	xAxisMenu.removeAll();
	yAxisMenu[0].removeAll();
	yAxisMenu[1].removeAll();
	if(headers.length<=0) return;
	for(int i=0;i<headers.length;i++) {
	    if(headers[i].equals("TIME") || headers[i].equals("RPM")) {
		JRadioButtonMenuItem jrbmt = new JRadioButtonMenuItem(headers[i], headers[i].equals("TIME"));
		bg.add(jrbmt);
		xAxisMenu.add(jrbmt);
		jrbmt.addActionListener(this);
	    }
	    JCheckBox cb = new JCheckBox(headers[i], headers[i].equals("RPM"));
	    yAxisMenu[0].add(cb);
	    cb.addActionListener(this);
	    cb = new JCheckBox(headers[i],headers[i].equals("EngineLoad"));
	    yAxisMenu[1].add(cb);
	    cb.addActionListener(this);
	}
	xAxisMenu.setEnabled(true);
	yAxisMenu[0].setEnabled(true);
	yAxisMenu[1].setEnabled(true);
    }

    private void setXAxis(String id) {
	System.out.println("xaxis " + id);
    }
    private void setYAxis(String id, boolean on) {
	System.out.println("yaxis " + id);
    }
    private void setYAxis2(String id, boolean on) {
	System.out.println("yaxis2 " + id);
    }
    public void actionPerformed(ActionEvent event) {
	AbstractButton source = (AbstractButton) (event.getSource());
	if(source.getText().equals("Quit")) {
	    System.exit(0);
	} else if(source.getText().equals("Close Chart")) {
	    this.dispose();
	} else if(source.getText().equals("New Chart")) {
	    final ECUxPlot plot = new ECUxPlot("ECUxPlot");
	    plot.pack();
	    Point where = this.getLocation();
	    where.translate(20,20);
	    plot.setLocation(where);
	    plot.setVisible(true);
	} else if(source.getText().equals("Open File")) {
	    final JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
	    int ret = fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		File file = fc.getSelectedFile();
		WaitCursor.startWaitCursor(this);
		try {
		    dataSet = new Dataset(file.getAbsolutePath());
		    chart = CreateChartPanel(dataSet);
		    setContentPane(chart);
		    this.setTitle("ECUxPlot " + file.getName());
		    setupAxisMenus(dataSet.headers);
		} catch (Exception e) {
		    JOptionPane.showMessageDialog(this, e.getMessage());
		}
		WaitCursor.stopWaitCursor(this);
	    }	
	} else if (source.getParent() instanceof JPopupMenu) {
	    JMenu parent = (JMenu)((JPopupMenu)source.getParent()).getInvoker();
	    if(parent.getText().equals("X Axis")) {
		EChartFactory.setChartX(chart, dataSet, source.getText());
	    } else if(parent.getText().equals("Y Axis")) {
		EChartFactory.editChartY(chart, dataSet, source.getText(),0,source.isSelected());
	    } else if(parent.getText().equals("Y Axis2")) {
		EChartFactory.editChartY(chart, dataSet, source.getText(),1,source.isSelected());
	    }
	}
    }

    private final void PopulateFileMenu(JMenu filemenu) {
	JMenuItem openitem = new JMenuItem("Open File");
	openitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_O, ActionEvent.CTRL_MASK));
	openitem.addActionListener(this);
	filemenu.add(openitem);

	filemenu.add(new JSeparator());

	JMenuItem newitem = new JMenuItem("New Chart");
	newitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_N, ActionEvent.CTRL_MASK));
	newitem.addActionListener(this);
	filemenu.add(newitem);

	JMenuItem closeitem = new JMenuItem("Close Chart");
	closeitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	closeitem.addActionListener(this);
	filemenu.add(closeitem);

	filemenu.add(new JSeparator());

	JMenuItem quititem = new JMenuItem("Quit");
	quititem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_F4, ActionEvent.ALT_MASK));
	quititem.addActionListener(this);
	filemenu.add(quititem);
    }

    public ECUxPlot(final String title) {
        super(title);

	JMenuBar menuBar = new JMenuBar();

	/* File Menu */
	JMenu filemenu = new JMenu("File");
	PopulateFileMenu(filemenu);
	menuBar.add(filemenu);

	/* X axis Menu */
	xAxisMenu = new JMenu("X Axis");
	xAxisMenu.setEnabled(false);
	menuBar.add(xAxisMenu);
	yAxisMenu = new JMenu[2];

	/* Y axis Menu */
	yAxisMenu[0] = new JMenu("Y Axis");
	yAxisMenu[0].setEnabled(false);
	menuBar.add(yAxisMenu[0]);

	/* Y axis2 Menu */
	yAxisMenu[1] = new JMenu("Y Axis2");
	yAxisMenu[1].setEnabled(false);
	menuBar.add(yAxisMenu[1]);


	setJMenuBar(menuBar);

	setPreferredSize(new java.awt.Dimension(800,600));
    }

    private static ChartPanel CreateChartPanel(Dataset data) throws Exception {

	final String[] what = {"TIME", "RPM", "EngineLoad"};
	final String xAxisLegend = "TIME";

        final XYDataset dataset1 = EChartFactory.createDataset(data, what[0], what[1]);
	final String yAxisLegend = "RPM";

        final XYDataset dataset2 = EChartFactory.createDataset(data, what[0], what[2]);
	final String y2AxisLegend = "%";

        final JFreeChart chart = EChartFactory.create2AxisXYLineChart(
            what[0] + " and " + what[1],
	    xAxisLegend, yAxisLegend, y2AxisLegend,
            dataset1, dataset2,
	    PlotOrientation.VERTICAL,
            true,	// show legend
            true,	// show tooltips
            false	// show urls
        );
	//EChartFactory.setSeriesPaint(chart, 0, Color.red);
	//EChartFactory.setSeriesPaint(chart, 1, Color.blue);

        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
	return chartPanel;
    }

    public static void main(final String[] args) {
	final ECUxPlot plot = new ECUxPlot("ECUxPlot");
	plot.pack();
	RefineryUtilities.centerFrameOnScreen(plot);
	plot.setVisible(true);
    }
}
