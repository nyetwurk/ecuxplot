package org.nyet.ecuxplot;

import java.io.File;
import java.io.IOException;

import java.util.*;
import java.util.prefs.Preferences;

import java.awt.Point;
import java.awt.event.ActionEvent;

import javax.swing.*;

import java.awt.desktop.*;
import java.awt.Desktop;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import org.jfree.data.xy.DefaultXYDataset;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.util.*;

import org.nyet.logfile.Dataset;
import org.nyet.logfile.Dataset.DatasetId;

public class ECUxPlot extends ApplicationFrame implements SubActionListener, FileDropHost,
    OpenFilesHandler, QuitHandler
    /* AboutHandler, PreferencesHandler */ {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    // each file loaded has an associated dataset
    private TreeMap<String, ECUxDataset> fileDatasets = new TreeMap<String, ECUxDataset>();
    private ArrayList<String> files = new ArrayList<String>();

    private ECUxChartPanel chartPanel;
    private FATSChartFrame fatsFrame;

    // Menus
    private final JMenuBar menuBar;
    private AxisMenu xAxis;
    private AxisMenu yAxis[] = new AxisMenu[2];

    // Dialog boxes
    private JFileChooser fc;
    private FilterEditor fe;
    private ConstantsEditor ce;
    private PIDEditor pe;
    private FuelingEditor fle;
    private SAEEditor sae;

    // Preferences
    private final Preferences prefs = getPreferences();

    private final Env env;
    private final Filter filter;

    private int verbose = 0;
    private boolean exitOnClose = true;

    // List of open plots
    private ArrayList<ECUxPlot> plotlist = null;

    // Constructor
    public ECUxPlot(final String title, java.awt.Dimension size, boolean exitOnClose, int verbose) { this(title, size, null, exitOnClose, verbose); }
    public ECUxPlot(final String title, ArrayList<ECUxPlot> plotlist) { this(title, null, plotlist, false, 0); }
    public ECUxPlot(final String title, java.awt.Dimension size, ArrayList<ECUxPlot> plotlist, boolean exitOnClose, int verbose) {
        super(title);
	ToolTipManager.sharedInstance().setInitialDelay(0);
	ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

	this.plotlist = (plotlist!=null)?plotlist:new ArrayList<ECUxPlot>();
	this.plotlist.add(this);

	this.exitOnClose=exitOnClose;
	this.verbose=verbose;
	WindowUtilities.setNativeLookAndFeel();
	this.menuBar = new JMenuBar();

	this.filter = new Filter(this.prefs);
	this.env = new Env(this.prefs);

	final java.net.URL imageURL =
	    getClass().getResource("icons/ECUxPlot2-64.png");

	this.setIconImage(new javax.swing.ImageIcon(imageURL).getImage());

	this.menuBar.add(new FileMenu("File", this));
	this.menuBar.add(new ProfileMenu("Vehicle Profiles", this));
	this.menuBar.add(new OptionsMenu("Options", this));

	this.menuBar.add(Box.createHorizontalGlue());
	final HelpMenu helpMenu = new HelpMenu("Help", this);
	this.menuBar.add(helpMenu);

	setJMenuBar(this.menuBar);

	setPreferredSize(size!=null?size:this.windowSize());
	new FileDropListener(this, this);
    }

    public static boolean scatter(Preferences prefs) {
	return prefs.getBoolean("scatter", false);
    }

    private boolean scatter() {
	return ECUxPlot.scatter(this.prefs);
    }

    private boolean showFATS() {
      return this.prefs.getBoolean("showfats", false);
    }

    private Comparable<?> xkey() {
	final Comparable<?> defaultXkey = new ECUxPreset("Power").xkey();
	return this.prefs.get("xkey", defaultXkey.toString());
    }

    private Comparable<?>[] ykeys(int index) {
	final Comparable<?>[] ykeys = new ECUxPreset("Power").ykeys(0);
	final Comparable<?>[] ykeys2 = new ECUxPreset("Power").ykeys(1);
	final String[] defaultYkeys = { Strings.join(",", ykeys), Strings.join(",", ykeys2) };

	final String k=this.prefs.get("ykeys"+index, defaultYkeys[index]);
	return k.split(",");
    }

    private java.awt.Dimension windowSize() {
	return new java.awt.Dimension(
	    this.prefs.getInt("windowWidth", 800),
	    this.prefs.getInt("windowHeight", 600));
    }

    private void prefsPutWindowSize() {
	this.prefs.putInt("windowWidth", this.getWidth());
	this.prefs.putInt("windowHeight", this.getHeight());
    }

    private void prefsPutXkey(Comparable<?> xkey) {
	this.prefs.put("xkey", xkey.toString());
    }
    private void prefsPutYkeys(int axis, Comparable<?>[] ykeys) {
	this.prefs.put("ykeys"+axis,Strings.join(",",ykeys));
    }
    private void prefsPutYkeys(int axis) {
	final XYPlot plot = this.chartPanel.getChart().getXYPlot();
	final DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(axis);
	this.prefsPutYkeys(axis, ECUxChartFactory.getDatasetYkeys(dataset));
    }

    private void addChartYFromPrefs() {
	this.addChartYFromPrefs(0);
	this.addChartYFromPrefs(1);
	updatePlotTitleAndYAxisLabels();
    }
    private void addChartYFromPrefs(int axis) {
	this.addChartY(this.ykeys(axis), axis);
    }

    private void fileDatasetsChanged() {
	// set title
	this.setTitle("ECUxPlot " + Strings.join(", ", this.fileDatasets.keySet()));

	// xaxis label depends on units found in files
	updateXAxisLabel();

	// Add all the data we just finished loading fom the files
	addChartYFromPrefs();

	// merge ids using a TreeSet - only add new headers
	// note that TreeSet keeps us sorted!
	final TreeSet<DatasetId> hset = new TreeSet<DatasetId>();
	for(final ECUxDataset d : this.fileDatasets.values()) {
	    for(final DatasetId s : d.getIds())
		if(s!=null) hset.add(s);
	}
	final DatasetId [] ids = hset.toArray(new DatasetId[0]);
	if(ids.length<=0) return;

	// rebuild the axis menus
	if(this.xAxis!=null) this.menuBar.remove(this.xAxis);
	if(this.yAxis[0]!=null) this.menuBar.remove(this.yAxis[0]);
	if(this.yAxis[1]!=null) this.menuBar.remove(this.yAxis[1]);

	this.xAxis = new AxisMenu("X Axis", ids, this, true, this.xkey());
	this.menuBar.add(this.xAxis, 3);

	this.yAxis[0] = new AxisMenu("Y Axis", ids, this, false,
	    this.ykeys(0));
	this.menuBar.add(this.yAxis[0], 4);

	this.yAxis[1] = new AxisMenu("Y Axis2", ids, this, false,
	    this.ykeys(1));
	this.menuBar.add(this.yAxis[1], 5);

	// hide/unhide filenames in the legend
	final XYPlot plot = this.chartPanel.getChart().getXYPlot();
	for(int axis=0;axis<plot.getDatasetCount();axis++) {
	    final org.jfree.data.xy.XYDataset pds = plot.getDataset(axis);
	    for(int series=0;series<pds.getSeriesCount();series++) {
		final Dataset.Key ykey = (Dataset.Key)pds.getSeriesKey(series);
		if(this.fileDatasets.size()==1) ykey.hideFilename();
		else ykey.showFilename();
	    }
	}

	/* we can't do this on construct, because fatsFrame does its own
	   restoreLocation() based on our location, and if we do it during
	   our constructor, our location is 0,0
	 */
	if(this.fatsFrame==null) {
	    this.fatsFrame =
		FATSChartFrame.createFATSChartFrame(this.fileDatasets, this);
	    this.fatsFrame.pack();

	    final java.net.URL imageURL =
		getClass().getResource("icons/ECUxPlot2-64.png");

	    this.fatsFrame.setIconImage(new
		    javax.swing.ImageIcon(imageURL).getImage());
	} else {
	    this.fatsFrame.setDatasets(this.fileDatasets);
	}

	// grab title from prefs, or just use what current title is
	this.chartTitle(this.prefs.get("title", this.chartTitle()));
    }

    public void loadFiles(ArrayList<String> files) {
	WaitCursor.startWaitCursor(this);
	for(final String s : files) {
	    if(s.length()>0) _loadFile(new File(s), false);
	}
	fileDatasetsChanged();
	WaitCursor.stopWaitCursor(this);
    }

    @Override
    public void loadFiles(List<File> files) {
	WaitCursor.startWaitCursor(this);
	for(final File f : files) {
	    _loadFile(f, false);
	}
	fileDatasetsChanged();
	WaitCursor.stopWaitCursor(this);
    }

    @Override
    public void loadFile(File file) { loadFile(file, false); }
    private void loadFile(File file, boolean replace) {
	WaitCursor.startWaitCursor(this);
	_loadFile(file, replace);
	fileDatasetsChanged();
	WaitCursor.stopWaitCursor(this);
    }
    private void _loadFile(File file, boolean replace) {
	try {
	    // replacing, nuke all the currently loaded datasets
	    if(replace) this.nuke();

	    if(this.chartPanel == null) {
		final JFreeChart chart =
		    ECUxChartFactory.create2AxisChart(this.scatter());
		this.chartPanel = new ECUxChartPanel(chart);
		setContentPane(this.chartPanel);
	    }

	    final ECUxDataset data = new ECUxDataset(file.getAbsolutePath(),
		    this.env, this.filter, this.verbose);

	    this.fileDatasets.put(file.getName(), data);
	    this.files.add(file.getAbsolutePath());
	} catch (final Exception e) {
	    JOptionPane.showMessageDialog(this, e);
	    e.printStackTrace();
	    return;
	}
    }

    public void setMyVisible(boolean b) {
	super.setVisible(b);
	if(this.fatsFrame==null) return;
	if(!this.filter.enabled()) b=false;
	if(b!=this.fatsFrame.isShowing() && this.showFATS())
	    this.fatsFrame.setVisible(b);
    }

    // nuke datasets
    private void nuke() {
	this.fileDatasets = new TreeMap<String, ECUxDataset>();
	this.files = new ArrayList<String>();
	this.setTitle("ECUxPlot");
	if(this.chartPanel!=null) {
	    this.chartPanel.setChart(null);
	    this.chartPanel.removeAll();
	    this.chartPanel=null;
	}
	if(this.fatsFrame!=null)
	    this.fatsFrame.clearDataset();
    }

    private String getExportStem() {
	String stem=null;
	for(final ECUxDataset d : this.fileDatasets.values()) {
	    final String fname=d.getFileId();
	    if(stem == null) {
		stem = Files.stem(fname);
	    } else {
		stem += "_vs_" + Files.stem(Files.filename(fname));
	    }
	}

	if (this.chartPanel == null) return stem;
	return stem;
    }

    public String exportChartStem(String dir)
    {
	String ret = null;
	if (this.chartPanel != null) {
	    final String stem = getExportStem();
	    ret = dir + File.separator + stem;
	}
	return ret;
    }

    private Point newChart() { return this.newChart(null, null); }
    private Point newChart(String preset, Point where) {
	// do not exit if this child plot is closed
	final ECUxPlot plot = new ECUxPlot("ECUxPlot", this.plotlist);
	plot.pack();

	if (where==null) where = this.getLocation();
	where.translate(20,20);

	plot.setLocation(where);

	if (this.files!=null) plot.loadFiles(this.files);

	if (preset!=null) plot.loadPreset(preset);
	else {
	    plot.removeAllY();
	    plot.updatePlotTitleAndYAxisLabels();
	}

	plot.setMyVisible(true);
	return where;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
	final AbstractButton source = (AbstractButton) (event.getSource());
	if(source.getText().equals("Quit")) {
	    exitApp();
	} else if(source.getText().equals("Export Chart")) {
	    if(this.chartPanel == null) {
		JOptionPane.showMessageDialog(this, "Open a CSV first");
	    } else {
		try {
		    this.chartPanel.doSaveAs(getExportStem());
		} catch (final Exception e) {
		    JOptionPane.showMessageDialog(this, e);
		    e.printStackTrace();
		}
	    }
	} else if(source.getText().equals("Export All Charts")) {
	    if (this.plotlist == null || this.plotlist.isEmpty()) {
		JOptionPane.showMessageDialog(this, "No charts to export");
		return;
	    }
	    boolean ok = false;
	    for (final ECUxPlot p: this.plotlist) {
		if(p.chartPanel != null) {
		    ok = true;
		    break;
		}
	    }

	    if (!ok) {
		JOptionPane.showMessageDialog(this, "No charts to export");
		return;
	    }

	    String dir = this.prefs.get("exportDir",
		System.getProperty("user.home"));
	    final JFileChooser fileChooser = new JFileChooser(dir);
	    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	    final int option = fileChooser.showSaveDialog(this);
	    final ArrayList<String> seen = new ArrayList<String>();
	    if (option == JFileChooser.APPROVE_OPTION) {
		dir = fileChooser.getSelectedFile().getPath();
		for (final ECUxPlot p: this.plotlist) {
		    if (p==null) continue;
		    final String stem = p.exportChartStem(dir);
		    if (stem==null) continue;
		    String fname = stem + "-" + p.chartTitle() + ".png";
		    for(int i=1; seen.contains(fname); i++)
			fname = stem + "-" + p.chartTitle() + "_" + i + ".png";
		    try {
			p.chartPanel.saveChartAsPNG(fname);
			seen.add(fname);
		    } catch (final Exception e) {
			JOptionPane.showMessageDialog(this, e);
			e.printStackTrace();
		    }

		    if((p.fatsFrame != null) && p.fatsFrame.isShowing()) {
			fname = stem + "-FATS.png";
			for(int i=1; seen.contains(fname); i++)
			    fname = stem + "-FATS_" + i + ".png";
			try {
			    p.fatsFrame.saveChartAsPNG(fname);
			    seen.add(fname);
			} catch (final Exception e) {
			    JOptionPane.showMessageDialog(this, e);
			    e.printStackTrace();
			}
		    }
		}
		if (seen.isEmpty()) {
		    JOptionPane.showMessageDialog(this, "Nothing exported");
		} else {
		    JOptionPane.showMessageDialog(this, "Exported:\n" +
			Strings.join("\n", seen));
		}
		this.prefs.put("exportDir", dir);
	    }
	} else if(source.getText().equals("Clear Chart")) {
	    // nuke axis menus
	    if(this.menuBar!=null) {
		if(this.xAxis!=null)
		    this.menuBar.remove(this.xAxis);
		if(this.yAxis!=null) {
		    if(this.yAxis[0]!=null)
			this.menuBar.remove(this.yAxis[0]);
		    if(this.yAxis[1]!=null)
			this.menuBar.remove(this.yAxis[1]);
		}
	    }
	    this.xAxis = null;
	    this.yAxis = new AxisMenu[2];
	    this.nuke();
	} else if(source.getText().equals("Close Chart")) {
	    this.plotlist.remove(this);
	    this.dispose();
	} else if(source.getText().equals("New Chart")) {
	    this.newChart();
	} else if(source.getText().equals("Open File") ||
		  source.getText().equals("Add File") ) {
	    if(this.fc==null) {
		// current working dir
		// String dir  = System.getProperty("user.dir"));
		// home dir
		final String dir = this.prefs.get("chooserDir",
		    System.getProperty("user.home"));
		this.fc = new JFileChooser(dir);
		this.fc.setFileFilter(new GenericFileFilter("csv", "CSV File"));
	    }
	    final int ret = this.fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		final boolean replace =
		    source.getText().equals("Open File")?true:false;

		WaitCursor.startWaitCursor(this);
		loadFile(this.fc.getSelectedFile(), replace);
		// if somebody hid the fats frame, lets unhide it for them.
		setMyVisible(true);
		WaitCursor.stopWaitCursor(this);
		this.prefs.put("chooserDir",
		    this.fc.getCurrentDirectory().toString());
	    }
	} else if(source.getText().equals("Use alternate column names")) {
	    final boolean s = source.isSelected();
	    this.prefs.putBoolean("altnames", s);
	    // rebuild title and labels
	    this.updateXAxisLabel();
	    this.updatePlotTitleAndYAxisLabels();
	} else if(source.getText().equals("Scatter plot")) {
	    final boolean s = source.isSelected();
	    this.prefs.putBoolean("scatter", s);
	    if(this.chartPanel != null)
		ECUxChartFactory.setChartStyle(this.chartPanel.getChart(),
		    !s, s);
	} else if(source.getText().equals("Filter data")) {
	    this.filter.enabled(source.isSelected());
	    rebuild();
	} else if(source.getText().equals("Show all ranges")) {
	    this.filter.showAllRanges(source.isSelected());
	    rebuild();
	} else if(source.getText().equals("Next range...")) {
	    this.filter.currentRange++;
	    rebuild();
	} else if(source.getText().equals("Previous range...")) {
	    if(this.filter.currentRange > 0) {
	        this.filter.currentRange--;
	    }
	    rebuild();
	} else if(source.getText().equals("Configure filter...")) {
	    if(this.fe == null) this.fe =
		new FilterEditor(this.prefs, this.filter);
	    this.fe.showDialog(this, "Filter");
	} else if(source.getText().equals("Edit constants...")) {
	    if(this.ce == null) this.ce =
		new ConstantsEditor(this.prefs, this.env.c);
	    this.ce.showDialog(this, "Constants");
	} else if(source.getText().equals("Edit fueling...")) {
	    if(this.fle == null) this.fle =
		new FuelingEditor(this.prefs, this.env.f);
	    this.fle.showDialog(this, "Fueling");
	} else if(source.getText().equals("Edit PID...")) {
	    if(this.pe == null) this.pe = new PIDEditor(this.env.pid);
	    this.pe.showDialog(this, "PID");
	} else if(source.getText().equals("Apply SAE")) {
	    this.env.sae.enabled(source.isSelected());
	    rebuild();
	    updatePlotTitleAndYAxisLabels();
	} else if(source.getText().equals("Edit SAE constants...")) {
	    if(this.sae == null) this.sae = new SAEEditor(this.prefs, this.env.sae);
	    this.sae.showDialog(this, "SAE");
	} else if(source.getText().equals("About...")) {
	    JOptionPane.showMessageDialog(this, new AboutPanel(),
		    "About ECUxPlot", JOptionPane.PLAIN_MESSAGE);
	} else if(source.getText().equals("Show FATS window...")) {
	    final boolean s = source.isSelected();
	    this.prefs.putBoolean("showfats", s);
	    if(!s && this.fatsFrame.isShowing()) {
		this.fatsFrame.setVisible(s);
	    }
	    rebuild();
	} else {
	    JOptionPane.showMessageDialog(this,
		"unhandled getText=" + source.getText() +
		", actionCommand=" + event.getActionCommand());
	}
    }

    private String findUnits(Comparable<?> key) {
	final ArrayList<String> units = new ArrayList<String>();
	for(final ECUxDataset d : this.fileDatasets.values()) {
	    final String u = d.units(key);
	    if(u==null || u.length()==0) continue;
	    if(!units.contains(u)) units.add(u);
	}
	return Strings.join(",", units);
    }

    private void chartTitle(String title) {
	this.chartPanel.getChart().setTitle(title);
    }
    private String chartTitle() {
	final String ret = this.chartPanel.getChart().getTitle().getText();
	return ret;
    }
    private void updatePlotTitleAndYAxisLabels() {
	if(this.chartPanel!=null)
	    updatePlotTitleAndYAxisLabels(
		    this.chartPanel.getChart().getXYPlot());
    }
    private void updatePlotTitleAndYAxisLabels(XYPlot plot) {
	final ArrayList<String> title = new ArrayList<String>();
	for(int axis=0; axis<plot.getDatasetCount(); axis++) {
	    final ArrayList<String> seriesTitle = new ArrayList<String>();
	    final ArrayList<String> label= new ArrayList<String>();
	    final org.jfree.data.xy.XYDataset dataset = plot.getDataset(axis);
	    if(dataset!=null) {
		for(int series=0; series<dataset.getSeriesCount(); series++) {
		    final Comparable<?> key = dataset.getSeriesKey(series);
		    if(key==null) continue;
		    String s;

		    if(key instanceof Dataset.Key) {
			final Dataset.Key k = (Dataset.Key)key;
			s = this.prefs.getBoolean("altnames", false)?k.getId2():k.getString();
		    } else
			s = key.toString();

		    // construct title array
		    if(!seriesTitle.contains(s)) seriesTitle.add(s);

		    // construct y axis label array
		    final String l = findUnits(key);
		    if(l==null || l.length()==0) continue;
		    if(!label.contains(l)) label.add(l);
		}
	    }

	    if(seriesTitle.size()>0)
		title.add(Strings.join(", ", seriesTitle));

	    plot.getRangeAxis(axis).setLabel(Strings.join(",",label));
	    // hide axis if this axis has no series
	    plot.getRangeAxis(axis).setVisible(dataset.getSeriesCount()>0);
	}
	this.chartTitle(Strings.join(" and ", title));
    }

    private void updateXAxisLabel() {
	if(this.chartPanel!=null)
	    updateXAxisLabel(this.chartPanel.getChart().getXYPlot());
    }

    private void updateXAxisLabel(XYPlot plot) {
	// find x axis label. just pick first one that has units we can use
	String label = "";
	for (final ECUxDataset data : this.fileDatasets.values()) {
	    if(data.get(this.xkey())!=null) {
		final String units = data.units(this.xkey());
		if(units != null) {
		    label =
			data.getLabel(this.xkey(), this.prefs.getBoolean("altnames", false));
		    if(label.indexOf(units)==-1)
			label += " ("+units+")";
		    break;
		}
	    }
	}
	plot.getDomainAxis().setLabel(label);
    }

    private void addDataset(int axis, DefaultXYDataset d,
	    Dataset.Key ykey) {
	// ugh. need an index for axis stroke, so we cant just do a get.
	// walk the filenames and get it, and the index for it
	ECUxDataset data=null;
	int stroke=0;
	for(final Map.Entry<String, ECUxDataset> e : this.fileDatasets.entrySet()) {
	    if(e.getKey().equals(ykey.getFilename())) {
		data=e.getValue();
		break;
	    }
	    stroke++;
	}
	if (data==null) return;

	/* returns the series indicies of the dataset we just added */
	final Integer[] series =
	    ECUxChartFactory.addDataset(d, data, this.xkey(), ykey, this.filter);

	/* set the color for those series */
	ECUxChartFactory.setAxisPaint(this.chartPanel.getChart(), axis,
	    d, ykey, series);

	/* set the stroke for those series */
	ECUxChartFactory.setAxisStroke(this.chartPanel.getChart(), axis,
	    d, ykey, series, stroke);
    }

    public void rebuild() {
	if(this.chartPanel==null) return;

	WaitCursor.startWaitCursor(this);

	for(final ECUxDataset data : this.fileDatasets.values())
	    data.buildRanges();

	if(this.fatsFrame!=null)
	    this.fatsFrame.setDatasets(this.fileDatasets);

	final XYPlot plot = this.chartPanel.getChart().getXYPlot();
	for(int axis=0;axis<plot.getDatasetCount();axis++) {
	    final org.jfree.data.xy.XYDataset pds = plot.getDataset(axis);
	    final DefaultXYDataset newdataset = new DefaultXYDataset();
	    for(int series=0;series<pds.getSeriesCount();series++) {
		final Dataset.Key ykey = (Dataset.Key)pds.getSeriesKey(series);
		addDataset(axis, newdataset, ykey);
	    }
	    plot.setDataset(axis, newdataset);
	}
	updateXAxisLabel(plot);

	WaitCursor.stopWaitCursor(this);
	this.setMyVisible(true);
    }

    private void removeAllY() { this.removeAllY(0); this.removeAllY(1); }
    private void removeAllY(int axis) {
	final XYPlot plot = this.chartPanel.getChart().getXYPlot();
	ECUxChartFactory.removeDataset((DefaultXYDataset)plot.getDataset(axis));
	this.yAxis[axis].uncheckAll();
    }

    private void editChartY(Comparable<?> ykey, int axis, boolean add) {
	for(final ECUxDataset data : this.fileDatasets.values())
	    editChartY(data, ykey, axis, add);
    }

    private void editChartY(ECUxDataset data, Comparable<?> ykey, int axis,
	boolean add) {
	if(add && !(data.exists(ykey)) )
	    return;
	final XYPlot plot = this.chartPanel.getChart().getXYPlot();
	final DefaultXYDataset pds = (DefaultXYDataset)plot.getDataset(axis);
	if(add) {
	    final Dataset.Key key = data.new Key(ykey.toString(), data);
	    if(this.fileDatasets.size()==1) key.hideFilename();
	    addDataset(axis, pds, key);
	} else {
	    ECUxChartFactory.removeDataset(pds, ykey);
	}
    }

    private void addChartY(Comparable<?>[] ykey, int axis) {
	for(final Comparable<?> k : ykey)
	    editChartY(k, axis, true);
    }

    public void saveUndoPreset() {
	if(this.chartPanel==null) return;
	final ECUxPreset p = new ECUxPreset("Undo", this.xkey(),
	    this.ykeys(0), this.ykeys(1), this.scatter());
	p.tag(this.chartTitle());
    }

    public void savePreset(Comparable<?> name) {
	if(this.chartPanel==null) return;
	new ECUxPreset(name, this.xkey(), this.ykeys(0), this.ykeys(1), this.scatter());
	this.chartTitle((String)name);
	this.prefs.put("title", (String)name);
    }

    public void loadPresets(List<String> presets) {
	boolean first = true;
	Point where = null;
	for (final String s : presets) {
	    if (first) {
		loadPreset(s);
		first = false;
		continue;
	    }
	    where = this.newChart(s, where);
	}
    }

    public void loadPreset(Comparable<String> name) {
	final ECUxPreset p = new ECUxPreset(name);
	if(p!=null) loadPreset(p);
    }
    private void loadPreset(ECUxPreset p) {
	if(this.chartPanel==null) return;

	// get rid of everything
	removeAllY();

	prefsPutXkey(p.xkey());
	// updateXAxisLabel depends on xkey prefs
	updateXAxisLabel();

	prefsPutYkeys(0,p.ykeys(0));
	prefsPutYkeys(1,p.ykeys(1));

	// addChart depends on the xkey,ykeys put in prefs
	addChartYFromPrefs();

	// set up scatter depending on preset
	final boolean s = p.scatter();
	ECUxChartFactory.setChartStyle(this.chartPanel.getChart(), !s, s);
	this.prefs.putBoolean("scatter", s);
	this.chartTitle(p.tag());
	this.prefs.put("title", p.tag());

	// update AxisMenu selections
	this.xAxis.setSelected(p.xkey());
	this.yAxis[0].setOnlySelected(p.ykeys(0));
	this.yAxis[1].setOnlySelected(p.ykeys(1));
    }

    @Override
    public void actionPerformed(ActionEvent event, Comparable<?> parentId) {
	final AbstractButton source = (AbstractButton) (event.getSource());
	// System.out.println(source.getText() + ":" + parentId);
	if(parentId.equals("X Axis")) {
	    prefsPutXkey(source.getText());
	    // rebuild depends on the value of prefs
	    rebuild();
	} else if(parentId.equals("Y Axis")) {
	    if(source.getText().equals("Remove all")) removeAllY(0);
	    else editChartY(source.getText(),0,source.isSelected());
	    // prefsPutYkeys depends on the stuff that edit chart does
	    prefsPutYkeys(0);
	} else if(parentId.equals("Y Axis2")) {
	    if(source.getText().equals("Remove all")) removeAllY(1);
	    else editChartY(source.getText(),1,source.isSelected());
	    // putkeys depends on the stuff that edit chart does
	    prefsPutYkeys(1);
	}
	updatePlotTitleAndYAxisLabels();
	try {
	    // System.out.println("removing title pref");
	    this.prefs.remove("title");
	} catch (final Exception e) {}
    }

    @Override
    public void windowClosing(java.awt.event.WindowEvent we) {
	if(this.exitOnClose) exitApp();
    }

    private void exitApp() {
	this.prefsPutWindowSize();
	if(this.fatsFrame!=null)
	    this.fatsFrame.dispose();
	System.exit(0);
    }

    public static final Preferences getPreferences() {
	return Preferences.userNodeForPackage(ECUxPlot.class);
    }

    public static final String showInputDialog(String message) {
	final String ret = JOptionPane.showInputDialog(message);
	if(ret == null || ret.length() == 0) return null;
	if(ret.startsWith(".") || ret.contains(":") ||
	    ret.contains(File.separator) ||
	    ret.contains("/") || ret.contains("\\")) {
	    JOptionPane.showMessageDialog(null, "Invalid name");
	    return null;
	}
	return ret;
    }

    private static class Options {
	public String preset = null;
	public File output = null;
	public java.awt.Dimension size = null;
	public ArrayList<String> files = new ArrayList<String>();
	public int verbose = 0;

	public Options(String[] args) {
	    int width = -1, height = -1;
	    for(int i=0; i<args.length; i++) {
		if(args[i].charAt(0) == '-') {
		    if(args[i].equals("-v"))
			this.verbose++;
		    else if(i<args.length-1) {
			if(args[i].equals("-p"))
			    this.preset = args[i+1];
			else if(args[i].equals("-o"))
			    this.output = new File(args[i+1]);
			else if(args[i].equals("-w"))
			    width = Integer.valueOf(args[i+1]);
			else if(args[i].equals("-h"))
			    height = Integer.valueOf(args[i+1]);
			i++;	// all above take an arg
		    }
		    if(args[i].equals("-l")) {
			try {
			    for(final String s : ECUxPreset.getPresets())
				System.out.println(s);
			} catch (final Exception e) {}
			System.exit(0);
		    }
		    if(args[i].equals("-?")) {
			System.out.println(
			    "usage: ECUxPlot [-v] [-p Preset] [-o OutputFile] " +
			    "[-w width] [-h height] [LogFiles ... ]");
			System.out.println("       ECUxPlot -l (list presets)");
			System.out.println("       ECUxPlot -? (show usage)");
			System.exit(0);
		    }
		} else this.files.add(args[i]);
	    }
	    if(width>0 && height>0)
		this.size = new java.awt.Dimension(width, height);
	}
    }

    // java.awt.Desktop stuff
    /*
    // we implement AboutHandler
    public void handleAbout(AboutEvent e)
    {
	JOptionPane.showMessageDialog(this, new AboutPanel(), "About ECUxPlot", JOptionPane.PLAIN_MESSAGE);
    }

    // we don't implement PreferencesHandler
    public void handlePreferences(PreferencesEvent e)
    {
    }
    */

    // we implement OpenFilesHandler
    public void openFiles(OpenFilesEvent e)
    {
	this.loadFiles(e.getFiles());
    }

    // we implement QuitHandler
    public void handleQuitRequestWith(QuitEvent e, QuitResponse r)
    {
	this.exitApp();
    }

    public static void main(final String[] args) {
	javax.swing.SwingUtilities.invokeLater(new Runnable() {
	    @Override
	    public void run() {
		final Options o = new Options(args);

		// exit on close
		final ECUxPlot plot = new ECUxPlot("ECUxPlot", o.size, true, o.verbose);

		// java.awt.Desktop stuff
		final Desktop dt = Desktop.getDesktop();

		if(dt!=null) {
		    /*
		    if (dt.isSupported(Desktop.Action.APP_ABOUT)) {
			dt.setAboutHandler(plot);
		    }
		    if (dt.isSupported(Desktop.Action.APP_PREFERENCES)) {
			dt.setPreferencesHandler(plot);
		    }
		    */
		    if (dt.isSupported(Desktop.Action.APP_OPEN_FILE)) {
			dt.setOpenFileHandler(plot);
		    }
		    if (dt.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
			dt.setQuitHandler(plot);
		    }
		}

		plot.pack();
		RefineryUtilities.centerFrameOnScreen(plot);
		plot.loadFiles(o.files);

		if(o.preset!=null)
		    plot.loadPreset(o.preset);

		if(o.output!=null) {
		    try {
			plot.pack();
			plot.chartPanel.saveChartAsPNG(o.output);
			System.exit(0);
		    } catch (final IOException e) {
			e.printStackTrace();
		    }
		}

		plot.setMyVisible(true);
	    }
	});
    }

    public Env getEnv() { return this.env; }
}
