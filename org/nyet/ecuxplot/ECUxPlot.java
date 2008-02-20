package org.nyet.ecuxplot;

import java.io.File;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.*;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JCheckBox;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.jfree.chart.JFreeChart;

import org.jfree.data.time.Month;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.util.WaitCursor;
import org.nyet.util.GenericFileFilter;
import org.nyet.util.SubActionListener;

public class ECUxPlot extends ApplicationFrame implements SubActionListener {
    private ECUxDataset dataSet;
    private ECUxChartPanel chartPanel;
    private JMenuBar menuBar;
    private AxisMenu xAxis;
    private AxisMenu yAxis;
    private AxisMenu yAxis2;
    private Comparable xkey;
    private boolean scatter=false;

    private static final Comparable[] initialXkey = { "RPM" };
    private static final Comparable[] initialYkey = {
	"Calc WHP",
	"Calc WTQ"
    };
    private static final Comparable[] initialYkey2 = {
	"BoostPressureDesired",
	"BoostPressureActual"
    };

    private void setupAxisMenus(String[] headers) {

	if(xAxis!=null) this.menuBar.remove(xAxis);
	if(yAxis!=null) this.menuBar.remove(yAxis);
	if(yAxis2!=null) this.menuBar.remove(yAxis2);

	if(headers.length<=0) return;

	xAxis = new AxisMenu("X Axis", headers, this, true, this.initialXkey);
	yAxis = new AxisMenu("Y Axis", headers, this, false, this.initialYkey);
	yAxis2 = new AxisMenu("Y Axis2", headers, this, false, this.initialYkey2);

	this.menuBar.add(xAxis);
	this.menuBar.add(yAxis);
	this.menuBar.add(yAxis2);
    }

    private void loadFile(File file) {
	WaitCursor.startWaitCursor(this);
	try {
	    this.dataSet = new ECUxDataset(file.getAbsolutePath());
	    this.chartPanel = CreateChartPanel(this.dataSet, this.xkey, this.scatter);
	    setContentPane(this.chartPanel);
	    this.setTitle("ECUxPlot " + file.getName());
	    setupAxisMenus(this.dataSet.headers);
	} catch (Exception e) {
	    JOptionPane.showMessageDialog(this, e);
	    e.printStackTrace();
	}
	WaitCursor.stopWaitCursor(this);
    }

    public void actionPerformed(ActionEvent event) {
	AbstractButton source = (AbstractButton) (event.getSource());
	if(source.getText().equals("Quit")) {
	    System.exit(0);
	} else if(source.getText().equals("Export Chart")) {
	    if(this.chartPanel == null) {
		JOptionPane.showMessageDialog(this, "Open a CSV first");
	    } else {
		try {
		    String [] a=this.dataSet.getFilename().split("\\.");
		    String stem="";
		    for(int i=0; i<a.length-1; i++) stem+=a[i];
		    this.chartPanel.doSaveAs(stem);
		} catch (Exception e) {
		    JOptionPane.showMessageDialog(this, e);
		    e.printStackTrace();
		}
	    }
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
	    // current working dir 
	    //final JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
	    // home dir
	    final JFileChooser fc = new JFileChooser();
	    fc.setFileFilter(new GenericFileFilter("csv", "CSV File"));
	    int ret = fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		loadFile(fc.getSelectedFile());
	    }
	} else if(source.getText().equals("Scatter plot")) {
	    this.scatter = source.isSelected();
	    ECUxChartFactory.setChartStyle(this.chartPanel.getChart(), !this.scatter, this.scatter);
	} else if(source.getText().equals("Filter data")) {
	    this.dataSet.getFilter().enabled=source.isSelected();
	    ECUxChartFactory.setChartX(this.chartPanel, this.dataSet, this.xkey);
	}
    }

    public void actionPerformed(ActionEvent event, Comparable parentId) {
	AbstractButton source = (AbstractButton) (event.getSource());
	// System.out.println(source.getText() + ":" + parentId);
	if(parentId.equals("X Axis")) {
	    this.xkey=source.getText();
	    ECUxChartFactory.setChartX(this.chartPanel, this.dataSet, this.xkey);
	} else if(parentId.equals("Y Axis")) {
	    ECUxChartFactory.editChartY(this.chartPanel, this.dataSet, this.xkey, source.getText(),0,source.isSelected());
	} else if(parentId.equals("Y Axis2")) {
	    ECUxChartFactory.editChartY(this.chartPanel, this.dataSet, this.xkey, source.getText(),1,source.isSelected());
	}
    }

    private final class FileMenu extends JMenu {
	public FileMenu(String id, ActionListener listener) {
	    super(id);
	    JMenuItem openitem = new JMenuItem("Open File");
	    openitem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_O, ActionEvent.CTRL_MASK));
	    openitem.addActionListener(listener);
	    this.add(openitem);

	    this.add(new JSeparator());

	    JMenuItem newitem = new JMenuItem("New Chart");
	    newitem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_N, ActionEvent.CTRL_MASK));
	    newitem.addActionListener(listener);
	    this.add(newitem);

	    JMenuItem closeitem = new JMenuItem("Close Chart");
	    closeitem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	    closeitem.addActionListener(listener);
	    this.add(closeitem);

	    this.add(new JSeparator());

	    JMenuItem exportitem = new JMenuItem("Export Chart");
	    exportitem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_E, ActionEvent.CTRL_MASK));
	    exportitem.addActionListener(listener);
	    this.add(exportitem);

	    this.add(new JSeparator());

	    JMenuItem quititem = new JMenuItem("Quit");
	    quititem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_F4, ActionEvent.ALT_MASK));
	    quititem.addActionListener(listener);
	    this.add(quititem);
	}
    }

    private final class OptionsMenu extends JMenu {
	public OptionsMenu(String id, ActionListener listener) {
	    super(id);
	    JCheckBox scatter = new JCheckBox("Scatter plot");
	    scatter.addActionListener(listener);
	    this.add(scatter);
	    JCheckBox filter = new JCheckBox("Filter data", true);
	    filter.addActionListener(listener);
	    this.add(filter);
	}
    }

    public ECUxPlot(final String title, String[] args) {
        super(title);
	this.xkey = this.initialXkey[0];
	this.menuBar = new JMenuBar();
	java.net.URL imageURL = getClass().getResource("icons/ECUxPlot2-64.png");
	if(imageURL==null) {
	    System.out.println("cant open icon");
	    System.exit(0);
	}
	this.setIconImage(new javax.swing.ImageIcon(imageURL).getImage());

	FileMenu filemenu = new FileMenu("File", this);
	menuBar.add(filemenu);


	OptionsMenu optmenu = new OptionsMenu("Options", this);
	menuBar.add(optmenu);

	setJMenuBar(menuBar);

	setPreferredSize(new java.awt.Dimension(800,600));

	if(args!=null && args.length>0 && args[0].length()>0)
	    loadFile(new File(args[0]));
    }

    public ECUxPlot(final String title) {
	this(title, null);
    }

    private static ECUxChartPanel CreateChartPanel(ECUxDataset data, Comparable xkey, boolean scatter) throws Exception {
        final JFreeChart chart = ECUxChartFactory.create2AxisChart(scatter);

	ECUxChartFactory.setChartX(chart, data, xkey);
	ECUxChartFactory.addChartY(chart, data, xkey, initialYkey, 0);
	ECUxChartFactory.addChartY(chart, data, xkey, initialYkey2, 1);

        return new ECUxChartPanel(chart);
    }

    public static void main(final String[] args) {
	javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
	    final ECUxPlot plot = new ECUxPlot("ECUxPlot", args);
	    plot.pack();
	    RefineryUtilities.centerFrameOnScreen(plot);
	    plot.setVisible(true);
	} });
    }
}
