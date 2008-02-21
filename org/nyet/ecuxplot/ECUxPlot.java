package org.nyet.ecuxplot;

import java.io.File;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.jfree.chart.JFreeChart;

import org.jfree.data.time.Month;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.util.WaitCursor;
import org.nyet.util.GenericFileFilter;
import org.nyet.util.SubActionListener;

import org.nyet.logfile.Dataset;

public class ECUxPlot extends ApplicationFrame implements SubActionListener {
    private ECUxDataset dataSet;
    private ECUxChartPanel chartPanel;
    private JMenuBar menuBar;
    private AxisMenu xAxis;
    private AxisMenu yAxis;
    private AxisMenu yAxis2;
    private Comparable xkey;
    private boolean scatter=false;
    private ECUxFilter defaultFilter;
    private Env defaultEnv;
    private FilterEditor fe;
    private ConstantEditor ce;

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

	if(this.xAxis!=null) this.menuBar.remove(this.xAxis);
	if(this.yAxis!=null) this.menuBar.remove(this.yAxis);
	if(this.yAxis2!=null) this.menuBar.remove(this.yAxis2);

	if(headers.length<=0) return;

	this.xAxis = new AxisMenu("X Axis", headers, this, true, this.initialXkey);
	this.yAxis = new AxisMenu("Y Axis", headers, this, false, this.initialYkey);
	this.yAxis2 = new AxisMenu("Y Axis2", headers, this, false, this.initialYkey2);

	this.menuBar.add(xAxis);
	this.menuBar.add(yAxis);
	this.menuBar.add(yAxis2);
    }

    private void loadFile(File file) {
	try {
	    this.dataSet = new ECUxDataset(file.getAbsolutePath());
	} catch (Exception e) {
	    JOptionPane.showMessageDialog(this, e);
	    e.printStackTrace();
	    return;
	}

	this.dataSet.setFilter(this.defaultFilter);
	this.dataSet.setEnv(this.defaultEnv);
	this.setTitle("ECUxPlot " + file.getName());

	final JFreeChart chart = ECUxChartFactory.create2AxisChart(this.scatter);
	this.chartPanel = new ECUxChartPanel(chart);

	setContentPane(this.chartPanel);
	rebuild();
	addChartY(this.initialYkey, 0);
	addChartY(this.initialYkey2, 1);
	setupAxisMenus(this.dataSet.headers);
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
		WaitCursor.startWaitCursor(this);
		loadFile(fc.getSelectedFile());
		WaitCursor.stopWaitCursor(this);
	    }
	} else if(source.getText().equals("Scatter plot")) {
	    this.scatter = source.isSelected();
	    ECUxChartFactory.setChartStyle(this.chartPanel.getChart(), !this.scatter, this.scatter);
	} else if(source.getText().equals("Filter data")) {
	    this.dataSet.getFilter().enabled=source.isSelected();
	    rebuild();
	} else if(source.getText().equals("Edit constants...")) {
	    if(this.ce == null) this.ce = new ConstantEditor();
	    this.ce.showDialog(this, "Constants", defaultEnv);
	} else if(source.getText().equals("Configure filter...")) {
	    if(this.fe == null) this.fe = new FilterEditor();
	    this.fe.showDialog(this, "Filter", defaultFilter);
	}
    }

    private void updateLabelTitle() {
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	String title = "";
	for(int i=0; i<plot.getDatasetCount(); i++) {
	    final org.jfree.data.xy.XYDataset dataset = plot.getDataset(i);
	    String seriesTitle = "", sprev=null;
	    String label="", lprev=null;
	    for(int j=0; dataset!=null && j<dataset.getSeriesCount(); j++) {
		Comparable key = dataset.getSeriesKey(j);
		if(key==null) continue;
		String s;

		if(key instanceof Dataset.Key) s = ((Dataset.Key)key).getString();
		else s = key.toString();

		String l = this.dataSet.units(key);

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
	this.chartPanel.getChart().setTitle(title);
    }

    public void rebuild() {
	if(this.chartPanel==null) return;
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	WaitCursor.startWaitCursor(this);
	for(int i=0;i<plot.getDatasetCount();i++) {
	    org.jfree.data.xy.XYDataset dataset = plot.getDataset(i);
	    final DefaultXYDataset newdataset = new DefaultXYDataset();
	    for(int j=0;j<dataset.getSeriesCount();j++) {
		Dataset.Key ykey = (Dataset.Key)dataset.getSeriesKey(j);
		ECUxChartFactory.addDataset(newdataset, this.dataSet, this.xkey, ykey.getString());
	    }
	    plot.setDataset(i, newdataset);
	}
	String units = this.dataSet.units(this.xkey);
	plot.getDomainAxis().setLabel(this.xkey.toString() + " ("+units+")");
	WaitCursor.stopWaitCursor(this);
    }

    private void editChartY(Comparable ykey, int series, boolean add) {
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(series);
	if(add) ECUxChartFactory.addDataset(dataset, this.dataSet, this.xkey, ykey);
	else ECUxChartFactory.removeDataset(dataset, ykey);
	updateLabelTitle();
	plot.getRangeAxis(series).setVisible(dataset.getSeriesCount()>0);
    }

    private void addChartY(Comparable[] ykey, int series) {
	for(int i=0; i<ykey.length; i++)
	    editChartY(ykey[i], series, true);
    }

    public void actionPerformed(ActionEvent event, Comparable parentId) {
	AbstractButton source = (AbstractButton) (event.getSource());
	// System.out.println(source.getText() + ":" + parentId);
	if(parentId.equals("X Axis")) {
	    this.xkey=source.getText();
	    rebuild();
	} else if(parentId.equals("Y Axis")) {
	    editChartY(source.getText(),0,source.isSelected());
	} else if(parentId.equals("Y Axis2")) {
	    editChartY(source.getText(),1,source.isSelected());
	}
    }

    public ECUxPlot(final String title, String[] args) {
        super(title);
	this.xkey = this.initialXkey[0];
	this.menuBar = new JMenuBar();
	this.defaultFilter = new ECUxFilter();
	this.defaultEnv = new Env();
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

    public static void main(final String[] args) {
	javax.swing.SwingUtilities.invokeLater(new Runnable() { public void run() {
	    final ECUxPlot plot = new ECUxPlot("ECUxPlot", args);
	    plot.pack();
	    RefineryUtilities.centerFrameOnScreen(plot);
	    plot.setVisible(true);
	} });
    }
}
