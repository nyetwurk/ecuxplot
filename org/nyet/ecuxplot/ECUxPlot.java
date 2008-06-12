package org.nyet.ecuxplot;

import java.io.File;
import java.io.FileOutputStream;
import java.util.prefs.Preferences;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.apple.eawt.*;

import org.jfree.chart.JFreeChart;

import org.jfree.data.time.Month;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.util.WindowUtilities;
import org.nyet.util.WaitCursor;
import org.nyet.util.GenericFileFilter;
import org.nyet.util.SubActionListener;

import org.nyet.logfile.Dataset;

public class ECUxPlot extends ApplicationFrame implements SubActionListener {
    private ECUxDataset dataSet;
    private ECUxChartPanel chartPanel;

    // Menus
    private JMenuBar menuBar;
    private AxisMenu xAxis;
    private AxisMenu yAxis;
    private AxisMenu yAxis2;

    // Dialog boxes
    private JFileChooser fc;
    private FilterEditor fe;
    private ConstantsEditor ce;
    private PIDEditor pe;
    private FuelingEditor fle;

    // Preferences
    private Preferences prefs=null;

    private Env env;
    private Filter filter;

    public static boolean scatter(Preferences prefs) {
	return prefs.getBoolean("scatter", false);
    }

    private boolean scatter() {
	return this.scatter(this.prefs);
    }

    private Comparable xkey() {
	final String defaultXkey = "RPM";
	return this.prefs.get("xkey", defaultXkey);
    }

    private Comparable[] ykeys(int index) {
	final String[] defaultYkeys = {
	    "Calc WHP,Calc WTQ",
	    "BoostPressureDesired (PSI),BoostPressureActual (PSI)"
	};

	String k=this.prefs.get("ykeys"+index, defaultYkeys[index]);
	return k.split(",");
    }

    private void putYkeys(int axis) {
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(axis);
	this.prefs.put("ykeys"+axis,ECUxChartFactory.getDatasetYkeys(dataset));
    }

    private void setupAxisMenus(String[] headers) {

	if(this.xAxis!=null) this.menuBar.remove(this.xAxis);
	if(this.yAxis!=null) this.menuBar.remove(this.yAxis);
	if(this.yAxis2!=null) this.menuBar.remove(this.yAxis2);

	if(headers.length<=0) return;

	this.xAxis = new AxisMenu("X Axis", headers, this, true, this.xkey());
	this.yAxis = new AxisMenu("Y Axis", headers, this, false, this.ykeys(0));
	this.yAxis2 = new AxisMenu("Y Axis2", headers, this, false, this.ykeys(1));

	this.menuBar.add(xAxis);
	this.menuBar.add(yAxis);
	this.menuBar.add(yAxis2);
    }

    private void loadFile(File file) {
	try {
	    this.dataSet = new ECUxDataset(file.getAbsolutePath(), this.env, this.filter);

	    this.setTitle("ECUxPlot " + file.getName());

	    final JFreeChart chart = ECUxChartFactory.create2AxisChart(this.scatter());
	    this.chartPanel = new ECUxChartPanel(chart);

	    setContentPane(this.chartPanel);
	    rebuild();
	    addChartY(this.ykeys(0), 0);
	    addChartY(this.ykeys(1), 1);
	    setupAxisMenus(this.dataSet.getHeaders());
	} catch (Exception e) {
	    JOptionPane.showMessageDialog(this, e);
	    e.printStackTrace();
	    return;
	}
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
	    if(fc==null) {
		// current working dir
		// String dir  = System.getProperty("user.dir"));
		// home dir
		String dir = this.prefs.get("chooserDir", System.getProperty("user.home"));
		fc = new JFileChooser(dir);
		fc.setFileFilter(new GenericFileFilter("csv", "CSV File"));
	    }
	    int ret = fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		WaitCursor.startWaitCursor(this);
		loadFile(fc.getSelectedFile());
		WaitCursor.stopWaitCursor(this);
		this.prefs.put("chooserDir", fc.getCurrentDirectory().toString());
	    }
	} else if(source.getText().equals("Scatter plot")) {
	    boolean s = source.isSelected();
	    this.prefs.putBoolean("scatter", s);
	    if(this.chartPanel != null)
		ECUxChartFactory.setChartStyle(this.chartPanel.getChart(), !s, s);
	} else if(source.getText().equals("Filter data")) {
	    this.filter.enabled(source.isSelected());
	    rebuild();
	} else if(source.getText().equals("Configure filter...")) {
	    if(this.fe == null) this.fe = new FilterEditor(this.prefs);
	    this.fe.showDialog(this, "Filter", this.filter);
	} else if(source.getText().equals("Edit constants...")) {
	    if(this.ce == null) this.ce = new ConstantsEditor(this.prefs);
	    this.ce.showDialog(this, "Constants", this.env.c);
	} else if(source.getText().equals("Edit fueling...")) {
	    if(this.fle == null) this.fle = new FuelingEditor(this.prefs);
	    this.fle.showDialog(this, "Fueling", this.env.f);
	} else if(source.getText().equals("Edit PID...")) {
	    if(this.pe == null) this.pe = new PIDEditor();
	    this.pe.showDialog(this, "PID", this.env.pid);
	}
    }

    private void updateLabelTitle() {
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	String title = "";
	for(int axis=0; axis<plot.getDatasetCount(); axis++) {
	    final org.jfree.data.xy.XYDataset dataset = plot.getDataset(axis);
	    String seriesTitle = "", sprev=null;
	    String label="", lprev=null;
	    for(int i=0; dataset!=null && i<dataset.getSeriesCount(); i++) {
		Comparable key = dataset.getSeriesKey(i);
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
	    plot.getRangeAxis(axis).setLabel(label);
	    /* hide axis if this axis has no series */
	    plot.getRangeAxis(axis).setVisible(dataset.getSeriesCount()>0);
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
		ECUxChartFactory.addDataset(newdataset, this.dataSet, this.xkey(), ykey.getString());
	    }
	    plot.setDataset(i, newdataset);
	}
	if(this.dataSet.get(this.xkey())!=null) {
	    String units = this.dataSet.units(this.xkey());
	    plot.getDomainAxis().setLabel(this.xkey().toString() + " ("+units+")");
	} else {
	    plot.getDomainAxis().setLabel("");
	}
	WaitCursor.stopWaitCursor(this);
    }

    private void editChartY(Comparable ykey, int axis, boolean add) {
	if(add && !(this.dataSet.exists(ykey) && this.dataSet.exists(this.xkey())) )
	    return;
	final org.jfree.chart.plot.XYPlot plot = this.chartPanel.getChart().getXYPlot();
	DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(axis);
	if(add) ECUxChartFactory.addDataset(dataset, this.dataSet, this.xkey(), ykey);
	else ECUxChartFactory.removeDataset(dataset, ykey);
    }

    private void addChartY(Comparable[] ykey, int axis) {
	for(int i=0; i<ykey.length; i++)
	    editChartY(ykey[i], axis, true);
	updateLabelTitle();
    }

    public void actionPerformed(ActionEvent event, Comparable parentId) {
	AbstractButton source = (AbstractButton) (event.getSource());
	// System.out.println(source.getText() + ":" + parentId);
	if(parentId.equals("X Axis")) {
	    this.prefs.put("xkey",source.getText());
	    /* rebuild depends on the value of prefs */
	    rebuild();
	} else if(parentId.equals("Y Axis")) {
	    editChartY(source.getText(),0,source.isSelected());
	    /* putkeys depends on the stuff that edit chart does */
	    putYkeys(0);
	} else if(parentId.equals("Y Axis2")) {
	    editChartY(source.getText(),1,source.isSelected());
	    /* putkeys depends on the stuff that edit chart does */
	    putYkeys(1);
	}
	updateLabelTitle();
    }

    public ECUxPlot(final String title, String[] args) {
        super(title);
	WindowUtilities.setNativeLookAndFeel();
	this.menuBar = new JMenuBar();

	this.prefs = Preferences.userNodeForPackage(ECUxPlot.class);

	this.filter = new Filter(this.prefs);
	this.env = new Env(this.prefs);

	java.net.URL imageURL = getClass().getResource("icons/ECUxPlot2-64.png");
	if(imageURL==null) {
	    System.out.println("cant open icon");
	    System.exit(0);
	}
	this.setIconImage(new javax.swing.ImageIcon(imageURL).getImage());

	FileMenu filemenu = new FileMenu("File", this);
	menuBar.add(filemenu);

	OptionsMenu optmenu = new OptionsMenu("Options", this, this.prefs);
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
	    Application app = Application.getApplication();

	    if(app!=null) {
		app.addApplicationListener(new ApplicationAdapter() {
		    public void handleOpenFile(ApplicationEvent evt) {
			String file = evt.getFilename();
			plot.loadFile(new File(file));
		    }
		});
	    }

	    plot.pack();
	    RefineryUtilities.centerFrameOnScreen(plot);
	    plot.setVisible(true);
	} });
    }
}
