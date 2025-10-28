package org.nyet.ecuxplot;

import java.awt.Desktop;
import java.awt.Point;
import java.awt.desktop.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.prefs.Preferences;

import javax.swing.*;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;

import org.nyet.util.*;
import org.nyet.logfile.Dataset;
import org.nyet.logfile.Dataset.DatasetId;

public class ECUxPlot extends ApplicationFrame implements SubActionListener, FileDropHost,
    OpenFilesHandler, QuitHandler
    /* AboutHandler, PreferencesHandler */ {
    private static final Logger logger = LoggerFactory.getLogger(ECUxPlot.class);

    /**
     *
     */
    private TreeMap<String, ECUxDataset> fileDatasets = new TreeMap<String, ECUxDataset>();

    // Track series metadata for fast visibility updates
    // Map: (axis, seriesIndex) -> (filename, rangeIndex)
    private Map<String, SeriesInfo> seriesInfoMap = new HashMap<>();

    private static class SeriesInfo {
        final String filename;
        final Integer range;
        final int axis;
        final int seriesIndex;

        SeriesInfo(String filename, Integer range, int axis, int seriesIndex) {
            this.filename = filename;
            this.range = range;
            this.axis = axis;
            this.seriesIndex = seriesIndex;
        }

        String getKey() {
            return axis + ":" + seriesIndex;
        }
    }

    private static final long serialVersionUID = 1L;
    // each file loaded has an associated dataset
    private ArrayList<String> files = new ArrayList<String>();

    FATSChartFrame fatsFrame;
    FATSDataset fatsDataset;
    private ECUxChartPanel chartPanel;
    private EventWindow eventWindow;
    private FilterWindow filterWindow;
    private RangeSelectorWindow rangeSelectorWindow;

    // Menus
    private final JMenuBar menuBar;
    private AxisMenu xAxis;
    private AxisMenu yAxis[] = new AxisMenu[2];
    OptionsMenu optionsMenu;
    AxisPresetsMenu axisPresetsMenu;

    // Dialog boxes
    private JFileChooser fc;
    private ConstantsEditor ce;
    private PIDEditor pe;
    private FuelingEditor fle;
    private SAEEditor sae;

    // Preferences
    final Preferences prefs = getPreferences();

    final Env env;
    final Filter filter;
    final FATS fats;

    private Options options = new Options();
    private boolean exitOnClose = true;

    // List of open plots
    private ArrayList<ECUxPlot> plotlist = null;

    // Constructor
    public ECUxPlot(final String title, final Options o, boolean exitOnClose) { this(title, o, null, exitOnClose); }
    public ECUxPlot(final String title, ArrayList<ECUxPlot> plotlist) { this(title, new Options(), plotlist, false); }
    public ECUxPlot(final String title, final Options o, ArrayList<ECUxPlot> plotlist, boolean exitOnClose) {
        super(title);

        // Initialize event window BEFORE any logging to capture events from app start
        this.eventWindow = new EventWindow();

        logger.info("Initializing ECUxPlot: {}", title);
        this.options=o;
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        this.plotlist = (plotlist!=null)?plotlist:new ArrayList<ECUxPlot>();
        this.plotlist.add(this);

        this.exitOnClose=exitOnClose;
        WindowUtilities.setNativeLookAndFeel();
        this.menuBar = new JMenuBar();

        this.filter = new Filter(this.prefs);
        this.env = new Env(this.prefs);
        this.fats = new FATS(this.prefs);

        final java.net.URL imageURL =
            getClass().getResource("icons/ECUxPlot2-64.png");

        this.setIconImage(new javax.swing.ImageIcon(imageURL).getImage());

        this.menuBar.add(new FileMenu("File", this));
        this.menuBar.add(new ProfileMenu("Vehicle Profiles", this));
        this.optionsMenu = new OptionsMenu("Options", this);
        this.menuBar.add(this.optionsMenu);

        // Axis presets menu before axis menus
        this.axisPresetsMenu = new AxisPresetsMenu("Axis Presets", this);
        this.menuBar.add(this.axisPresetsMenu);

        // Right-align Help menu
        this.menuBar.add(Box.createHorizontalGlue());

        final HelpMenu helpMenu = new HelpMenu("Help", this);
        this.menuBar.add(helpMenu);

        setJMenuBar(this.menuBar);

        setPreferredSize(o.size!=null?o.size:this.windowSize());
        new FileDropListener(this, this);
    }

    public static boolean scatter(Preferences prefs) {
        return prefs.getBoolean("scatter", false);
    }

    private boolean scatter() {
        return ECUxPlot.scatter(this.prefs);
    }

    private Comparable<?> xkey() {
        // Use a hardcoded default instead of creating ECUxPreset to avoid infinite recursion
        final String defaultXkey = "RPM";
        final String key = this.prefs.get("xkey", defaultXkey);
        return org.nyet.logfile.Dataset.isPlaceholderKey(key) ? defaultXkey : key;
    }

    private Comparable<?>[] ykeys(int index) {
        // Use hardcoded defaults instead of creating ECUxPreset to avoid infinite recursion
        final String[] defaultYkeys0 = {"WHP","WTQ","HP","TQ"};
        final String[] defaultYkeys1 = {"BoostPressureDesired (PSI)","BoostPressureActual (PSI)"};
        final String[] defaultYkeys = { Strings.join(",", defaultYkeys0), Strings.join(",", defaultYkeys1) };

        // Check if preference was explicitly cleared (set to empty string)
        final String k = this.prefs.get("ykeys"+index, defaultYkeys[index]);
        if(k.isEmpty()) return new String[0];

        final String[] keys = k.split(",");
        final ArrayList<String> filtered = new ArrayList<String>();
        for(String key : keys) {
            key = key.trim();
            if(key.length() > 0 && !org.nyet.logfile.Dataset.isPlaceholderKey(key)) filtered.add(key);
        }

        // If filtered empty and original was empty string, return empty; otherwise use defaults
        return filtered.size() > 0 ? filtered.toArray(new String[0]) : defaultYkeys[index].split(",");
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
        // set title with elided filenames to prevent extremely long title bars
        String title = buildElidedTitle();
        this.setTitle(title);

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

        // Remove existing axis menus from menu bar before recreating them
        if (this.xAxis != null) {
            this.menuBar.remove(this.xAxis);
        }
        if (this.yAxis[0] != null) {
            this.menuBar.remove(this.yAxis[0]);
        }
        if (this.yAxis[1] != null) {
            this.menuBar.remove(this.yAxis[1]);
        }

        // rebuild the axis menus
        // ODDITY: Create axis menus for popup functionality (not added to menu bar)
        // These menus are used for axis click popups but are no longer displayed in the menu bar
        this.xAxis = new AxisMenu("X Axis", ids, this, true, this.xkey());
        this.yAxis[0] = new AxisMenu("Y Axis", ids, this, false, this.ykeys(0));
        this.yAxis[1] = new AxisMenu("Y Axis2", ids, this, false, this.ykeys(1));

        // grab title from prefs, or just use what current title is
        this.chartTitle(this.prefs.get("title", this.chartTitle()));

        // Update FATS availability after files are loaded
        if (this.optionsMenu != null) {
            this.optionsMenu.updateFATSAvailability();
        }

        // Update axis menu visibility based on user preference
        updateAxisMenuVisibility();

        // Initialize filter with "show all" for each file that doesn't have selections yet
        // DO THIS FIRST so the chart update has correct filter selections
        logger.debug("Initializing filter selections for {} files", fileDatasets.size());
        for (Map.Entry<String, ECUxDataset> entry : this.fileDatasets.entrySet()) {
            String filename = entry.getValue().getFileId();

            // Check if this file already has selections
            Set<Integer> existingSelections = this.filter.getSelectedRanges(filename);
            if (existingSelections == null || existingSelections.isEmpty()) {
                // File doesn't have selections yet, initialize it
                List<Dataset.Range> ranges = entry.getValue().getRanges();
                if (ranges != null && !ranges.isEmpty()) {
                    Set<Integer> allRanges = new HashSet<>();
                    for (int i = 0; i < ranges.size(); i++) {
                        allRanges.add(i);
                    }
                    logger.debug("  Setting 'show all' for {}: {} ranges", filename, ranges.size());
                    this.filter.setSelectedRanges(filename, allRanges);
                } else {
                    logger.warn("  No ranges found for {}, ranges={}", filename, ranges);
                }
            } else {
                logger.debug("  {} already has {} selections, skipping", filename, existingSelections.size());
            }
        }

        // For initial file load, do synchronous update to avoid callback complexity
        // Ranges are already built during dataset construction, so we can proceed directly
        updateChartForNewFiles();

        // Hide/unhide filenames in the legend
        final XYPlot plot = this.chartPanel.getChart().getXYPlot();
        for(int axis=0;axis<plot.getDatasetCount();axis++) {
            final org.jfree.data.xy.XYDataset pds = plot.getDataset(axis);
            for(int series=0;series<pds.getSeriesCount();series++) {
                final Object seriesKey = pds.getSeriesKey(series);
                // ODDITY: Type safety check needed because placeholder data uses String keys
                // while real data uses Dataset.Key objects. This prevents ClassCastException.
                if(seriesKey instanceof Dataset.Key) {
                    final Dataset.Key ykey = (Dataset.Key)seriesKey;
                    if(this.fileDatasets.size()==1) ykey.hideFilename();
                    else ykey.showFilename();
                }
                // Skip placeholder series (String keys like Dataset.PLACEHOLDER_KEY)
            }
        }
    }

    /**
     * Synchronous chart update for initial file loads.
     * Creates FATS dataset and updates chart with data from loaded files.
     * Used for initial file loading to avoid async complexity.
     */
    private void updateChartForNewFiles() {
        if(this.chartPanel==null) return;

        logger.debug("updateChartForNewFiles: Updating chart with {} files", fileDatasets.size());

        // Clear series tracking when updating chart
        seriesInfoMap.clear();

        WaitCursor.startWaitCursor(this);
        try {
            // Create FATSDataset if it doesn't exist
            if (this.fatsDataset == null && !this.fileDatasets.isEmpty()) {
                logger.debug("Creating new FATSDataset");
                this.fatsDataset = new FATSDataset(this.fileDatasets, this.fats, this.filter);

                // Update Range Selector window if it's open
                if(this.rangeSelectorWindow != null) {
                    this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
                }
            } else if (this.fatsDataset != null && !this.fileDatasets.isEmpty()) {
                // Rebuild existing FATS dataset when new files are loaded
                logger.debug("Rebuilding existing FATSDataset");
                this.fatsDataset.rebuild();
            }

            final XYPlot plot = this.chartPanel.getChart().getXYPlot();
            logger.debug("Updating {} axes with current data", plot.getDatasetCount());

            // Rebuild each axis by re-adding all Y-keys from preferences
            for(int axis=0;axis<plot.getDatasetCount();axis++) {
                final DefaultXYDataset newdataset = new DefaultXYDataset();

                // Get all Y-keys configured for this axis
                final Comparable<?>[] ykeys = this.ykeys(axis);

                // Re-add each Y-key with current file data
                for (final Comparable<?> ykeyName : ykeys) {
                    final String yvar = ykeyName.toString();

                    // Add data for each loaded file
                    for (final Map.Entry<String, ECUxDataset> entry : this.fileDatasets.entrySet()) {
                        final String filename = entry.getKey();
                        final ECUxDataset data = entry.getValue();

                        // Create base key for this Y-variable
                        final Dataset.Key baseKey = data.new Key(yvar, data);
                        final Comparable<?> xkey = "RPM";

                        // addDataset will read the filter and add only selected ranges
                        ECUxChartFactory.addDataset(newdataset, data, xkey, baseKey, this.filter, filename);
                    }
                }

                plot.setDataset(axis, newdataset);
                logger.debug("  Axis {}: {} series added", axis, newdataset.getSeriesCount());

                // Track series metadata for this dataset
                for(int series=0; series<newdataset.getSeriesCount(); series++) {
                    final Object seriesKey = newdataset.getSeriesKey(series);
                    if(seriesKey instanceof Dataset.Key) {
                        final Dataset.Key key = (Dataset.Key)seriesKey;
                        final SeriesInfo info = new SeriesInfo(key.getFilename(), key.getRange(), axis, series);
                        seriesInfoMap.put(info.getKey(), info);
                    }
                }

                // Apply custom axis range calculation for better padding with negative values
                ECUxChartFactory.applyCustomAxisRange(chartPanel.getChart(), axis, newdataset);
            }
            updateXAxisLabel(plot);

            logger.debug("Chart update complete, updating windows");
            // Update all open windows to show new file data
            updateOpenWindows();

        } finally {
            WaitCursor.stopWaitCursor(this);
        }
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
        if (files == null || files.isEmpty()) {
            return;
        }

        // Track starting dataset count
        int startingCount = this.fileDatasets.size();

        for (final File f : files) {
            if (f == null) continue;
            _loadFile(f, false);
        }

        // Check if any files were actually loaded
        int filesAdded = this.fileDatasets.size() - startingCount;

        // Only update the UI if at least one file was loaded successfully
        // Note: fileDatasetsChanged() will start the wait cursor and rebuild() handles stopping it
        if (filesAdded > 0) {
            fileDatasetsChanged();
        }
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
                this.chartPanel = new ECUxChartPanel(chart, this);
                setContentPane(this.chartPanel);

                // Add placeholder data to empty axes for clean startup appearance
                addPlaceholderDataToEmptyAxes();
            }

            final ECUxDataset data = new ECUxDataset(file.getAbsolutePath(),
                    this.env, this.filter, this.options.verbose);

            this.fileDatasets.put(file.getName(), data);
            this.files.add(file.getAbsolutePath());
        } catch (final Exception e) {
            // Provide user-friendly error messages instead of raw stack traces
            String errorMessage;
            if (e instanceof FileNotFoundException) {
                errorMessage = "File not found: " + file.getName() + "\n\nPlease check that the file exists and the path is correct.";
            } else if (e instanceof SecurityException) {
                errorMessage = "Cannot read file: " + file.getName() + "\n\nPlease check file permissions.";
            } else if (e instanceof IOException && e.getMessage().contains("empty")) {
                errorMessage = "File is empty: " + file.getName() + "\n\nPlease select a file with data.";
            } else {
                errorMessage = "Error loading file: " + file.getName() + "\n\n" + e.getMessage();
            }

            MessageDialog.showMessageDialog(this, errorMessage, "File Loading Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }

    /**
     * Load multiple files with error handling.
     * @param files array of files to load
     * @param replace whether to replace existing datasets
     */
    private void loadFilesWithProgress(File[] files, boolean replace) {
        if (files == null || files.length == 0) {
            return;
        }

        int successCount = 0;
        int errorCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (int i = 0; i < files.length; i++) {
            File file = files[i];

            try {
                // Load the file
                _loadFile(file, replace && i == 0); // Only replace on first file
                successCount++;

            } catch (Exception e) {
                errorCount++;
                String errorMsg = String.format("Failed to load %s: %s",
                    file.getName(), e.getMessage());
                errorMessages.append(errorMsg).append("\n");
                logger.error("Error loading file: {}", file.getAbsolutePath(), e);
            }
        }

        // Show summary message (only in GUI mode)
        if (files.length > 1 && !this.options.nogui) {
            String summary;
            if (errorCount == 0) {
                summary = String.format("Successfully loaded %d file(s)", successCount);
                MessageDialog.showMessageDialog(this, summary, "File Loading Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } else if (successCount == 0) {
                summary = String.format("Failed to load all %d file(s)", errorCount);
                MessageDialog.showMessageDialog(this, summary + "\n\n" + errorMessages.toString(),
                    "File Loading Failed", JOptionPane.ERROR_MESSAGE);
            } else {
                summary = String.format("Loaded %d of %d file(s) successfully", successCount, files.length);
                MessageDialog.showMessageDialog(this, summary + "\n\nErrors:\n" + errorMessages.toString(),
                    "File Loading Partial Success", JOptionPane.WARNING_MESSAGE);
            }
        } else if (this.options.nogui && errorCount > 0) {
            // In no-gui mode, log errors instead of showing dialogs
            logger.error("File loading errors: {}", errorMessages.toString());
        }

        // Update the display if any files were loaded successfully
        if (successCount > 0) {
            fileDatasetsChanged();
        }
    }

    public void setMyVisible(boolean b) {
        super.setVisible(b);
        // Don't hide FATS window when filter is disabled - let user adjust settings
        // if(!this.filter.enabled()) b=false;
    }

    /**
     * Update FATS window visibility - no longer needed since we create on-demand
     */
    public void updateFATSVisibility() {
        // No-op: FATS windows are now created on-demand
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
        if(this.fatsFrame != null)
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
        if(source.getText().equals("Exit")) {
            exitApp();
        } else if(source.getText().equals("Export Chart")) {
            if(this.chartPanel == null) {
                MessageDialog.showMessageDialog(this, "Open a CSV first");
            } else {
                try {
                    this.chartPanel.doSaveAs(getExportStem());
                } catch (final Exception e) {
                    MessageDialog.showMessageDialog(this, e);
                    e.printStackTrace();
                }
            }
        } else if(source.getText().equals("Export All Charts")) {
            if (this.plotlist == null || this.plotlist.isEmpty()) {
                MessageDialog.showMessageDialog(this, "No charts to export");
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
                MessageDialog.showMessageDialog(this, "No charts to export");
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

                    if((this.fatsFrame != null) && this.fatsFrame.isShowing()) {
                        fname = stem + "-FATS.png";
                        for(int i=1; seen.contains(fname); i++)
                            fname = stem + "-FATS_" + i + ".png";
                        try {
                            this.fatsFrame.saveChartAsPNG(fname);
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
                this.fc.setDialogTitle("Select CSV Log File");
                this.fc.setApproveButtonText("Load File");
                this.fc.setApproveButtonToolTipText("Load the selected CSV file");
                this.fc.setMultiSelectionEnabled(true);
                // Enhanced file dialog with better error handling, progress indicators, and user feedback
            }
            final int ret = this.fc.showOpenDialog(this);
            if(ret == JFileChooser.APPROVE_OPTION) {
                final boolean replace =
                    source.getText().equals("Open File")?true:false;

                // Handle multiple file selection
                File[] selectedFiles = this.fc.getSelectedFiles();
                if (selectedFiles.length == 0) {
                    selectedFiles = new File[]{this.fc.getSelectedFile()};
                }

                loadFilesWithProgress(selectedFiles, replace);
                // if somebody hid the fats frame, lets unhide it for them.
                setMyVisible(true);
                this.prefs.put("chooserDir",
                    this.fc.getCurrentDirectory().toString());
            }
        } else if(source.getText().equals("Alt column names")) {
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
        } else if(source.getText().equals("Enable filter")) {
            this.filter.enabled(source.isSelected());
            rebuild();
            // FATS needs to be recalculated when filter is enabled (not when disabled)
            if (source.isSelected()) {
                rebuildFATS();
            }
            if (this.optionsMenu != null) {
                this.optionsMenu.updateFATSAvailability();
            }
        } else if(source.getText().equals("Filter...")) {
            // Old range selectors removed - now using Range Selector for per-file range selection
            if(this.filterWindow == null) this.filterWindow =
                new FilterWindow(this.filter, this);
            // Set all datasets for multi-file support
            this.filterWindow.setFileDatasets(this.fileDatasets);
            this.filterWindow.setVisible(true);
        } else if(source.getText().equals("Ranges...")) {
            // Ensure FATS dataset is created if files are loaded
            if(this.fatsDataset == null && !this.fileDatasets.isEmpty()) {
                this.fatsDataset = new FATSDataset(this.fileDatasets, this.fats, this.filter);
            }

            if(this.rangeSelectorWindow == null) this.rangeSelectorWindow =
                new RangeSelectorWindow(this.filter, this);

            // Set FATS dataset BEFORE file datasets to ensure it's available for award calculation
            if(this.fatsDataset != null) {
                this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
            }
            // Set all datasets for multi-file support (this triggers updateList which needs FATS dataset)
            this.rangeSelectorWindow.setFileDatasets(this.fileDatasets);
            this.rangeSelectorWindow.setVisible(true);
        } else if(source.getText().equals("Constants...")) {
            if(this.ce == null) this.ce =
                new ConstantsEditor(this.prefs, this.env.c);
            boolean changesMade = this.ce.showDialog(this, "Constants");
            // Update FATS window if constants were changed and window is open
            if (changesMade && this.fatsFrame != null) {
                this.fatsFrame.updateRpmFieldsFromConstants();
            }
        } else if(source.getText().equals("Fueling...")) {
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
        } else if(source.getText().equals("Hide axis menus")) {
            // UX Enhancement: Allow users to choose between new axis click functionality
            // and traditional menu bar dropdowns for axis configuration
            this.prefs.putBoolean("hideaxismenus", source.isSelected());
            updateAxisMenuVisibility();
        } else if(source.getText().equals("SAE constants...")) {
            if(this.sae == null) this.sae = new SAEEditor(this.prefs, this.env.sae);
            this.sae.showDialog(this, "SAE");
        } else if(source.getText().equals("About...")) {
            JOptionPane.showMessageDialog(this, new AboutPanel(),
                    "About ECUxPlot", JOptionPane.PLAIN_MESSAGE);
        } else if(source.getText().equals("Show FATS")) {
            // Create FATS dataset if it doesn't exist yet
            if (this.fatsDataset == null && !this.fileDatasets.isEmpty()) {
                this.fatsDataset = new FATSDataset(this.fileDatasets, this.fats, this.filter);
            }

            // Create window if it doesn't exist
            if (this.fatsFrame == null) {
                this.fatsFrame = FATSChartFrame.createFATSChartFrame(this.fatsDataset, this);
                this.fatsFrame.pack();

                // Set window icon
                final java.net.URL imageURL =
                    getClass().getResource("icons/ECUxPlot2-64.png");
                if (imageURL != null) {
                    this.fatsFrame.setIconImage(new javax.swing.ImageIcon(imageURL).getImage());
                }
            }

            this.fatsFrame.setVisible(true);
        } else if(source.getText().equals("Events")) {
            if(this.eventWindow == null) {
                this.eventWindow = new EventWindow();
            }
            this.eventWindow.showWindow();
        } else if(source.getText().equals("Show Filter Window")) {
            if(this.filterWindow == null) this.filterWindow =
                new FilterWindow(this.filter, this);
            // Set all datasets for multi-file support
            this.filterWindow.setFileDatasets(this.fileDatasets);
            this.filterWindow.setVisible(true);
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

    /**
     * Build window title with elided filenames if too long
     * Limits individual filename display to prevent extremely long titles
     */
    private String buildElidedTitle() {
        final int MAX_FILENAME_LENGTH = 40;
        List<String> elidedFilenames = new ArrayList<>();

        for (String filename : this.fileDatasets.keySet()) {
            elidedFilenames.add(Strings.elide(filename, MAX_FILENAME_LENGTH));
        }

        return "ECUxPlot " + Strings.join(", ", elidedFilenames);
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

                    // Skip placeholder series - they should not appear in chart titles
                    if(org.nyet.logfile.Dataset.isPlaceholderKey(key)) continue;

                    String s;

                    if(key instanceof Dataset.Key) {
                        final Dataset.Key k = (Dataset.Key)key;
                        s = this.prefs.getBoolean("altnames", false)?k.getId2():k.getString();
                    } else
                        s = key.toString();

                    // construct title array
                    if(!seriesTitle.contains(s)) seriesTitle.add(s);

                    // construct y axis label array
                    // Skip findUnits for non-Dataset.Key objects to avoid ConcurrentModificationException
                    // Only Dataset.Key objects have valid data in the fileDatasets
                    String l = null;
                    if(key instanceof Dataset.Key) {
                        try {
                            l = findUnits(key);
                        } catch (final Exception e) {
                            // Ignore exceptions when looking up units to avoid crashes
                            logger.debug("Error finding units for key {}: {}", key, e.getMessage());
                        }
                    }
                    if(l==null || l.length()==0) continue;
                    if(!label.contains(l)) label.add(l);
                }
            }

            if(seriesTitle.size()>0)
                title.add(Strings.join(", ", seriesTitle));

            plot.getRangeAxis(axis).setLabel(Strings.join(",",label));
            // ODDITY: Always keep axes visible so they remain clickable even when empty
            // This enables axis click functionality for configuration even when no data is displayed
            plot.getRangeAxis(axis).setVisible(true);
        }
        this.chartTitle(Strings.join(" and ", title));
    }

    private void updateXAxisLabel() {
        if(this.chartPanel!=null)
            updateXAxisLabel(this.chartPanel.getChart().getXYPlot());
    }

    /**
     * Update axis menu visibility based on user preference.
     * If "Hide axis menus" is enabled, removes axis menus from menu bar.
     * If disabled, adds them to menu bar (but keeps them for popup functionality).
     */
    private void updateAxisMenuVisibility() {
        boolean hideAxisMenus = this.prefs.getBoolean("hideaxismenus", false);
        boolean showInMenuBar = !hideAxisMenus; // Invert the logic

        // ODDITY: Axis menus may not be created yet if no files are loaded
        // This method is called from the constructor and from preference changes
        if (this.xAxis == null || this.yAxis[0] == null || this.yAxis[1] == null) {
            return; // Skip if axis menus haven't been created yet
        }

        if (showInMenuBar) {
            // Add axis menus after Axis Presets and before the Glue
            if (this.xAxis != null && !this.menuBarContains(this.xAxis)) {
                this.menuBar.add(this.xAxis, 4); // Insert after Axis Presets (position 3)
            }
            if (this.yAxis[0] != null && !this.menuBarContains(this.yAxis[0])) {
                this.menuBar.add(this.yAxis[0], 5); // Insert after X Axis
            }
            if (this.yAxis[1] != null && !this.menuBarContains(this.yAxis[1])) {
                this.menuBar.add(this.yAxis[1], 6); // Insert after Y Axis
            }
        } else {
            // Remove axis menus from menu bar if present
            if (this.xAxis != null) {
                this.menuBar.remove(this.xAxis);
            }
            if (this.yAxis[0] != null) {
                this.menuBar.remove(this.yAxis[0]);
            }
            if (this.yAxis[1] != null) {
                this.menuBar.remove(this.yAxis[1]);
            }
        }

        // Refresh the menu bar
        this.menuBar.revalidate();
        this.menuBar.repaint();
    }

    /**
     * Helper method to check if a menu is already in the menu bar.
     */
    private boolean menuBarContains(JMenu menu) {
        for (int i = 0; i < this.menuBar.getMenuCount(); i++) {
            if (this.menuBar.getMenu(i) == menu) {
                return true;
            }
        }
        return false;
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
        if (series.length > 0) {
            ECUxChartFactory.setAxisPaint(this.chartPanel.getChart(), axis,
                d, ykey, series);

            /* set the stroke for those series */
            ECUxChartFactory.setAxisStroke(this.chartPanel.getChart(), axis,
                d, ykey, series, stroke);

            // Apply elided legend labels after paint/stroke settings
            ECUxChartFactory.applyElidedLegendLabels(this.chartPanel.getChart());
        }
    }

    public void rebuild() {
        rebuild(null);
    }

    /**
     * Update chart visibility based on current filter selections
     * Does NOT rebuild ranges or FATS - only toggles visibility of existing series
     */
    public void updateChartVisibility() {
        if(this.chartPanel==null || seriesInfoMap.isEmpty()) return;

        final XYPlot plot = this.chartPanel.getChart().getXYPlot();

        // Use pre-computed series info for fast updates
        for(SeriesInfo info : seriesInfoMap.values()) {
            // Check if this range should be visible
            Set<Integer> selectedRanges = this.filter.getSelectedRanges(info.filename);
            boolean shouldBeVisible;

            if(info.range != null && info.range >= 0) {
                // Multiple range file - check if specific range is selected
                shouldBeVisible = selectedRanges.contains(info.range);
            } else {
                // Single range file - visible if any range for this file is selected
                shouldBeVisible = !selectedRanges.isEmpty();
            }

            // Toggle visibility
            final XYItemRenderer renderer = plot.getRenderer(info.axis);
            renderer.setSeriesVisible(info.seriesIndex, shouldBeVisible);
        }

        // Update axis ranges after visibility changes
        for(int axis=0; axis<plot.getDatasetCount(); axis++) {
            final DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(axis);
            if(dataset != null) {
                ECUxChartFactory.applyCustomAxisRange(chartPanel.getChart(), axis, dataset);
            }
        }

        updateXAxisLabel(plot);

        // Notify FATS window if open (it may need to refresh if it shows per-range data)
        if(this.fatsFrame != null) {
            // FATS chart shows aggregate data, but refresh it to ensure consistency
            this.fatsFrame.refreshFromFATS();
        }
    }

    public void rebuild(Runnable callback, JFrame... additionalWindows) {
        if(this.chartPanel==null) return;

        // Start wait cursor on main window and any additional windows
        WaitCursor.startWaitCursor(this);
        for (JFrame window : additionalWindows) {
            if (window != null) {
                WaitCursor.startWaitCursor(window);
            }
        }

        // Clear series tracking when rebuilding
        seriesInfoMap.clear();

        // Move heavy work to background thread to keep UI responsive
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                for(final ECUxDataset data : fileDatasets.values()) {
                    data.buildRanges();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    // Check for exceptions during background work
                    get(); // This will throw any exception that occurred in doInBackground()
                } catch (final Exception e) {
                    logger.error("Error building ranges: {}", e.getMessage(), e);
                    // Continue - partial data is better than nothing
                }

                try {
                    // Create FATSDataset if it doesn't exist
                    if (ECUxPlot.this.fatsDataset == null && !ECUxPlot.this.fileDatasets.isEmpty()) {
                        ECUxPlot.this.fatsDataset = new FATSDataset(ECUxPlot.this.fileDatasets, ECUxPlot.this.fats, ECUxPlot.this.filter);

                        // Update Range Selector window if it's open
                        if(ECUxPlot.this.rangeSelectorWindow != null) {
                            ECUxPlot.this.rangeSelectorWindow.setFATSDataset(ECUxPlot.this.fatsDataset);
                        }
                    } else if (ECUxPlot.this.fatsDataset != null && !ECUxPlot.this.fileDatasets.isEmpty()) {
                        // Rebuild existing FATS dataset when new files are loaded
                        ECUxPlot.this.fatsDataset.rebuild();
                    }

                    // FATS window will automatically show updated data since it uses the same FATSDataset instance

                    final XYPlot plot = ECUxPlot.this.chartPanel.getChart().getXYPlot();

                    // Rebuild each axis by re-adding all Y-keys from preferences
                    // This ensures removed series are added back when their ranges are selected
                    for(int axis=0;axis<plot.getDatasetCount();axis++) {
                        final DefaultXYDataset newdataset = new DefaultXYDataset();

                        // Get all Y-keys configured for this axis
                        final Comparable<?>[] ykeys = ECUxPlot.this.ykeys(axis);

                        // Re-add each Y-key with current file data
                        for (final Comparable<?> ykeyName : ykeys) {
                            final String yvar = ykeyName.toString();

                            // Add data for each loaded file
                            for (final Map.Entry<String, ECUxDataset> entry : ECUxPlot.this.fileDatasets.entrySet()) {
                                final String filename = entry.getKey();
                                final ECUxDataset data = entry.getValue();

                                // Create base key for this Y-variable
                                final Dataset.Key baseKey = data.new Key(yvar, data);
                                final Comparable<?> xkey = "RPM";

                                // addDataset will read the filter and add only selected ranges
                                ECUxChartFactory.addDataset(newdataset, data, xkey, baseKey, ECUxPlot.this.filter, filename);
                            }
                        }

                        plot.setDataset(axis, newdataset);

                        // Add placeholder data if axis is empty (to keep it clickable)
                        if(newdataset.getSeriesCount() == 0) {
                            newdataset.addSeries(org.nyet.logfile.Dataset.PLACEHOLDER_KEY, new double[][]{{Double.NaN, Double.NaN}, {0, 0}});
                            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis(axis);
                            rangeAxis.setRange(0, 100);
                            rangeAxis.setAutoRange(false);
                            rangeAxis.setLabel("");
                        }

                        // Track series metadata for this dataset
                        for(int series=0; series<newdataset.getSeriesCount(); series++) {
                            final Object seriesKey = newdataset.getSeriesKey(series);
                            if(seriesKey instanceof Dataset.Key) {
                                final Dataset.Key key = (Dataset.Key)seriesKey;
                                final SeriesInfo info = new SeriesInfo(key.getFilename(), key.getRange(), axis, series);
                                seriesInfoMap.put(info.getKey(), info);
                            }
                        }

                        // Apply custom axis range calculation for better padding with negative values
                        ECUxChartFactory.applyCustomAxisRange(chartPanel.getChart(), axis, newdataset);
                    }
                    updateXAxisLabel(plot);

                    // Don't call updateOpenWindows() here - window updates are handled by callbacks
                    // This prevents Range Selector tree from being rebuilt when user changes selections
                    //updateOpenWindows();
                } catch (final Exception e) {
                    logger.error("Error updating chart: {}", e.getMessage(), e);
                    // Continue - WaitCursor will be stopped in finally block
                } finally {
                    // Stop wait cursor on all windows
                    WaitCursor.stopWaitCursor(ECUxPlot.this);
                    for (JFrame window : additionalWindows) {
                        if (window != null) {
                            WaitCursor.stopWaitCursor(window);
                        }
                    }
                    // Execute callback after rebuild is complete
                    if (callback != null) {
                        callback.run();
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Add placeholder data to empty Y-axes for clean startup appearance.
     * This prevents JFreeChart from showing garbage ranges like "0 to 1.05" when no data is loaded.
     *
     * ODDITY: Uses Double.NaN for X-axis values to prevent interference with domain axis autoscaling.
     * JFreeChart ignores NaN values when calculating axis ranges, so the X-axis can still autoscale
     * to real data when it's loaded.
     */
    private void addPlaceholderDataToEmptyAxes() {
        final XYPlot plot = this.chartPanel.getChart().getXYPlot();

        // Add placeholder to both Y axes
        for(int axis = 0; axis < plot.getDatasetCount(); axis++) {
            final DefaultXYDataset dataset = (DefaultXYDataset)plot.getDataset(axis);
            if(dataset.getSeriesCount() == 0) {
                // ODDITY: X-axis values are NaN to avoid interfering with domain axis autoscaling
                dataset.addSeries(org.nyet.logfile.Dataset.PLACEHOLDER_KEY, new double[][]{{Double.NaN, Double.NaN}, {0, 0}});

                // Set clean range for empty axis
                final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis(axis);
                rangeAxis.setRange(0, 100);
                rangeAxis.setAutoRange(false);
                rangeAxis.setLabel("");
            }
        }

        // Ensure X-axis remains in auto-range mode AFTER adding placeholder data
        final NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        domainAxis.setAutoRange(true);
    }

    private void removeAllY() { this.removeAllY(0); this.removeAllY(1); }

    /**
     * Remove all data from a Y-axis and add clean placeholder data.
     * This ensures the axis remains clickable and shows a professional appearance.
     *
     * ODDITY: After removing all data, we immediately add placeholder data to prevent
     * the axis from becoming invisible/unclickable. This maintains the axis click functionality
     * for configuration even when empty.
     */
    private void removeAllY(int axis) {
        final XYPlot plot = this.chartPanel.getChart().getXYPlot();
        ECUxChartFactory.removeDataset((DefaultXYDataset)plot.getDataset(axis));
        this.yAxis[axis].uncheckAll();

        // Clear preferences for this axis so rebuild() doesn't restore old data
        // Use empty string to mark as explicitly cleared
        this.prefs.put("ykeys" + axis, "");

        // Add a placeholder dataset to show a clean, empty axis
        final DefaultXYDataset placeholderDataset = (DefaultXYDataset)plot.getDataset(axis);
        // ODDITY: X-axis values are NaN to avoid interfering with domain axis autoscaling
        placeholderDataset.addSeries(org.nyet.logfile.Dataset.PLACEHOLDER_KEY, new double[][]{{Double.NaN, Double.NaN}, {0, 0}});

        // Set a clean range for the empty axis
        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis(axis);
        rangeAxis.setRange(0, 100);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabel("");
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
            // ODDITY: Check for and remove placeholder data before adding real data
            // This prevents placeholder data from interfering with real data display
            if(pds.getSeriesCount() > 0 && org.nyet.logfile.Dataset.isPlaceholderKey(pds.getSeriesKey(0))) {
                pds.removeSeries(org.nyet.logfile.Dataset.PLACEHOLDER_KEY);
            }

            final Dataset.Key key = data.new Key(ykey.toString(), data);
            if(this.fileDatasets.size()==1) key.hideFilename();
            addDataset(axis, pds, key);

            // Restore normal axis behavior
            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis(axis);
            rangeAxis.setAutoRange(true);

            // Ensure X-axis remains in auto-range mode
            final NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
            domainAxis.setAutoRange(true);
        } else {
            ECUxChartFactory.removeDataset(pds, ykey);
        }

        // Apply custom axis range calculation after dataset changes
        ECUxChartFactory.applyCustomAxisRange(this.chartPanel.getChart(), axis, pds);
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

        if (p.xkey()==null) {
            try {
                ECUxPreset.getPreferencesStatic().node(p.name()).removeNode();
                //updatePresets();
            } catch (final Exception e) {}
            System.out.printf("Preset '%s' invalid or does not exist\n", p.name());
            return;
        }

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

        // update scatter checkbox to reflect the preset's scatter setting
        // Scatter checkbox is now in OptionsMenu, no need to update separately
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

        // Save last loaded files for auto-loading on next startup
        saveLastLoadedFiles();

        if(this.fatsFrame!=null)
            this.fatsFrame.dispose();
        if(this.eventWindow!=null)
            this.eventWindow.dispose();
        if(this.filterWindow!=null)
            this.filterWindow.dispose();
        if(this.rangeSelectorWindow!=null)
            this.rangeSelectorWindow.dispose();
        System.exit(0);
    }

    /**
     * Notify the Range Selector window to update when FATS data changes
     */
    public void updateRangeSelectorFATS() {
        if(this.rangeSelectorWindow != null && this.fatsDataset != null) {
            this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
        }
    }

    /**
     * Refresh the Range Selector window to reflect current datasets
     */
    public void refreshRangeSelector() {
        if(this.rangeSelectorWindow != null) {
            this.rangeSelectorWindow.setFileDatasets(this.fileDatasets);
            if(this.fatsDataset != null) {
                this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
            }
        }
    }

    /**
     * Update all open windows to reflect current file datasets
     * Called after files are loaded to ensure windows show new data
     */
    private void updateOpenWindows() {
        // Update FilterWindow if open
        if(this.filterWindow != null) {
            this.filterWindow.setFileDatasets(this.fileDatasets);
        }

        // Update RangeSelectorWindow if open - rebuild tree since files were loaded
        if(this.rangeSelectorWindow != null) {
            this.rangeSelectorWindow.setFileDatasets(this.fileDatasets);
            if(this.fatsDataset != null) {
                this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
            }
        }

        // Update FATSChartFrame if open
        // FATS data will be rebuilt by rebuild(), so we just need to refresh the display
        if(this.fatsFrame != null && this.fatsDataset != null) {
            this.fatsFrame.refreshFromFATS();
        }
    }

    /**
     * Rebuild FATS data when filter or FATS settings change
     */
    public void rebuildFATS() {
        if (this.fatsDataset != null && !this.fileDatasets.isEmpty()) {
            this.fatsDataset.rebuild();

            // Update windows that display FATS data
            if (this.fatsFrame != null) {
                this.fatsFrame.refreshFromFATS();
            }
            if (this.rangeSelectorWindow != null) {
                this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
            }
        }
    }

    /**
     * Open or show the Filter window
     */
    public void openFilterWindow() {
        if(this.filterWindow == null) {
            this.filterWindow = new FilterWindow(this.filter, this);
        }
        // Set all datasets for multi-file support
        this.filterWindow.setFileDatasets(this.fileDatasets);
        this.filterWindow.setVisible(true);
    }

    /**
     * Open or show the Range Selector window
     */
    public void openRangeSelectorWindow() {
        // Ensure FATS dataset is created if files are loaded
        if(this.fatsDataset == null && !this.fileDatasets.isEmpty()) {
            this.fatsDataset = new FATSDataset(this.fileDatasets, this.fats, this.filter);
        }

        if(this.rangeSelectorWindow == null) {
            this.rangeSelectorWindow = new RangeSelectorWindow(this.filter, this);
        }

        // Set FATS dataset BEFORE file datasets to ensure it's available for award calculation
        if(this.fatsDataset != null) {
            this.rangeSelectorWindow.setFATSDataset(this.fatsDataset);
        }
        // Set all datasets for multi-file support (this triggers updateList which needs FATS dataset)
        this.rangeSelectorWindow.setFileDatasets(this.fileDatasets);
        this.rangeSelectorWindow.setVisible(true);
    }

    public static final Preferences getPreferences() {
        return Preferences.userNodeForPackage(ECUxPlot.class);
    }

    /**
     * Save the currently loaded files to preferences for auto-loading on next startup
     */
    private void saveLastLoadedFiles() {
        if (this.files != null && !this.files.isEmpty()) {
            // Store the number of files
            this.prefs.putInt("lastFileCount", this.files.size());

            // Store each file path
            for (int i = 0; i < this.files.size(); i++) {
                this.prefs.put("lastFile" + i, this.files.get(i));
            }
        } else {
            // Clear any previously saved files
            this.prefs.remove("lastFileCount");
            for (int i = 0; i < 10; i++) { // Clear up to 10 previous files
                this.prefs.remove("lastFile" + i);
            }
        }
    }

    /**
     * Load the last loaded files from preferences
     * @return ArrayList of file paths, or empty list if none saved
     */
    private static ArrayList<String> loadLastLoadedFiles() {
        final Preferences prefs = getPreferences();
        final int fileCount = prefs.getInt("lastFileCount", 0);
        final ArrayList<String> files = new ArrayList<String>();

        for (int i = 0; i < fileCount; i++) {
            final String filePath = prefs.get("lastFile" + i, null);
            if (filePath != null && new File(filePath).exists()) {
                files.add(filePath);
            }
        }

        return files;
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
        public boolean nogui = false;

        private static void usage() {
            System.out.println("usage:");
            System.out.println("ECUxPlot [-l] [-v[v...]] [--no-gui] [-p Preset] [-o OutputFile] [--width width] [--height height] [LogFiles ... ]");
            System.out.println("         -l          : list presets");
            System.out.println("         -v...       : verbosity level");
            System.out.println("         --no-gui    : just parse file and exit");
            System.out.println("         -h|-?|--help: show usage");
            System.exit(0);
        }

        public Options() { this(new String[0]); }
        public Options(String[] args) {
            final List<String> help = Arrays.asList("h", "?", "-help");
            int width = -1, height = -1;
            Boolean list_presets = false;
            String output = null;
            for(int i=0; i<args.length; i++) {
                if(args[i].charAt(0) == '-') {
                    String option = args[i].substring(1);

                    if(help.contains(option)) usage();

                    if(option.equals("l"))
                        list_presets = true;
                    else if(option.equals("-no-gui"))
                        this.nogui = true;
                    else if(option.charAt(0) == 'v')
                        for (char ch: option.toCharArray()) {
                            if (ch == 'v') this.verbose++;
                            else {
                                System.out.printf("Unknown option '-%c'\n", ch);
                                usage();
                            }
                        }
                    else if(i<args.length-1) {
                        if(option.equals("p"))
                            this.preset = args[i+1];
                        else if(option.equals("o"))
                            output = args[i+1];
                        else if(option.equals("-width"))
                            width = Integer.valueOf(args[i+1]);
                        else if(option.equals("-height"))
                            height = Integer.valueOf(args[i+1]);
                        else {
                            System.out.printf("Unknown option '-%s ...'\n", option);
                            usage();
                        }
                        i++;    // all above take an arg
                    } else {
                        System.out.printf("Unknown option '-%s'\n", option);
                        usage();
                    }
                } else {
                    this.files.add(args[i]);
                }
            }

            if(list_presets) {
                try {
                    for(final String s : ECUxPreset.getPresets())
                    System.out.println(s);
                } catch (final Exception e) {}
                System.exit(0);
            }

            if(output != null) this.output = new File(output);

            if(width>0 && height>0)
                this.size = new java.awt.Dimension(width, height);
        }
    }

    // Axis click handlers for chart panel
    // ODDITY: These methods are called from ECUxChartPanel when axes are clicked
    // The axis menus are no longer in the menu bar but still exist for popup functionality

    public void showXAxisDialog(Point clickPoint) {
        if (this.xAxis != null) {
            showAxisPopupMenu(this.xAxis, "X Axis", clickPoint);
        }
    }

    public void showYAxisDialog(Point clickPoint) {
        if (this.yAxis[0] != null) {
            showAxisPopupMenu(this.yAxis[0], "Y Axis", clickPoint);
        }
    }

    public void showY2AxisDialog(Point clickPoint) {
        if (this.yAxis[1] != null) {
            showAxisPopupMenu(this.yAxis[1], "Y Axis2", clickPoint);
        }
    }

    /**
     * Show axis configuration popup menu at the clicked location.
     *
     * ODDITY: This method leverages the existing AxisMenu.getPopupMenu() instead of
     * trying to recreate the menu structure. This preserves all the original functionality
     * including nested submenus, which would be complex to recreate manually.
     */
    private void showAxisPopupMenu(AxisMenu axisMenu, String parentId, Point clickPoint) {
        // Get the popup menu from the AxisMenu and show it
        // This preserves all the original functionality including submenus
        if (this.chartPanel != null && clickPoint != null) {
            JPopupMenu popup = axisMenu.getPopupMenu();
            if (popup != null) {
                popup.show(this.chartPanel, clickPoint.x, clickPoint.y);
            }
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

                // Configure logging level based on verbosity
                ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
                ch.qos.logback.classic.Logger ecuxLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.nyet.ecuxplot");

                if (o.verbose > 1) {
                    rootLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
                    ecuxLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
                } else if (o.verbose > 0) {
                    rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
                    ecuxLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
                } else {
                    rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
                    ecuxLogger.setLevel(ch.qos.logback.classic.Level.INFO);
                }

                // Set up MessageDialog for --no-gui mode
                MessageDialog.setNoGui(o.nogui);

                // exit on close
                final ECUxPlot plot = new ECUxPlot("ECUxPlot", o, true);
                if (o.nogui) {
                    // just load files and be done
                    plot.loadFiles(o.files);

                    // Handle output file if specified
                    if(o.output!=null) {
                        if (!handleOutputFile(plot, o.output)) {
                            System.exit(1);
                        }
                        System.exit(0);
                    }

                    System.exit(0);
                }

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

                // Load files from command line, or auto-load last files if none provided
                if (o.files != null && !o.files.isEmpty()) {
                    plot.loadFiles(o.files);
                } else {
                    // Auto-load last files if no files provided via command line
                    final ArrayList<String> lastFiles = loadLastLoadedFiles();
                    if (!lastFiles.isEmpty()) {
                        ecuxLogger.info("Auto-loading {} files from last session", lastFiles.size());
                        plot.loadFiles(lastFiles);
                    }
                }

                if(o.preset!=null)
                    plot.loadPreset(o.preset);

                if(o.output!=null) {
                    if (!handleOutputFile(plot, o.output)) {
                        System.exit(1);
                    }
                    System.exit(0);
                }

                plot.setMyVisible(true);
            }
        });
    }

    public Env getEnv() { return this.env; }

    /**
     * Handle the -o output file option by saving the chart as PNG.
     * @param plot the ECUxPlot instance
     * @param outputFile the output file to save to
     * @return true if successful, false if chartPanel is null
     */
    private static boolean handleOutputFile(ECUxPlot plot, File outputFile) {
        try {
            plot.pack();
            if (plot.chartPanel == null) {
                System.err.println("Error: No chart data loaded. Cannot save chart to " + outputFile.getName());
                System.err.println("Please provide CSV files to load data before using -o option.");
                return false;
            }
            plot.chartPanel.saveChartAsPNG(outputFile);
            return true;
        } catch (final IOException e) {
            System.err.println("Error saving chart to " + outputFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
