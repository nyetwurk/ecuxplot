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
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.logfile.Dataset;
import org.nyet.util.WaitCursor;
import org.nyet.util.GenericFileFilter;
import org.nyet.util.EChartFactory;
import org.nyet.util.MenuListener;
import org.nyet.util.SubActionListener;

public class ECUxPlot extends ApplicationFrame implements SubActionListener {
    private Dataset dataSet;
    private ChartPanel chart;
    private JMenuBar menuBar;
    private JMenu[] axisTriplet;
    private Comparable xkey="TIME";

    private static JMenu[] createMenuTriplet(String label)
    {
	JMenu[] out={
	    new JMenu(label),
	    new JMenu(label),
	    new JMenu(label)};
	return out;
    }

    private static JMenu[] createMenuTriplet(String[] label)
    {
	JMenu[] out={
	    new JMenu(label[0]),
	    new JMenu(label[1]),
	    new JMenu(label[2])};
	return out;
    }

    private static void addMenuTriplet(JMenu[] dest, AbstractButton[] src) {
	dest[0].add(src[0]);
	dest[1].add(src[1]);
	dest[2].add(src[2]);
    }

    private static AbstractButton[] createItemTriplet(String id, MenuListener[] ltriplet) {
	AbstractButton[] item = {
	    new JRadioButtonMenuItem(id, id.equals("TIME")),
	    new JCheckBox(id,id.equals("RPM")),
	    new JCheckBox(id,id.equals("EngineLoad"))
	};

	item[0].addActionListener(ltriplet[0]);
	item[1].addActionListener(ltriplet[1]);
	item[2].addActionListener(ltriplet[2]);
	return item;
    }

    private void setupAxisMenus(String[] headers) {
	if(headers.length<=0) return;

	if(menuBar.getMenuCount()>1) {
	    menuBar.remove(axisTriplet[0]);
	    menuBar.remove(axisTriplet[1]);
	    menuBar.remove(axisTriplet[2]);
	}

	String[] menus = {"X Axis", "Y Axis", "Y Axis2"};
	axisTriplet = createMenuTriplet(menus);

	MenuListener[] ltriplet = {
	    new MenuListener(this, menus[0]),
	    new MenuListener(this, menus[1]),
	    new MenuListener(this, menus[2])
	};

	menuBar.add(axisTriplet[0]);
	menuBar.add(axisTriplet[1]);
	menuBar.add(axisTriplet[2]);

	ButtonGroup bg = new ButtonGroup();
	JMenu[] boost = null;
	JMenu[] ignition = null;
	JMenu[] knock = null;
	JMenu[] load = null;
	JMenu[] egt = null;

	for(int i=0;i<headers.length;i++) {
	    AbstractButton[] item = createItemTriplet(headers[i], ltriplet);
	    bg.add(item[0]);

	    if(headers[i].matches("^Boost.*")) {
		if(boost==null) {
		    boost=createMenuTriplet("Boost...");
		    addMenuTriplet(axisTriplet, boost);
		}
		addMenuTriplet(boost, item);
	    } else if(headers[i].matches("^Ignition.*")) {
		if(ignition==null) {
		    ignition=createMenuTriplet("Ignition...");
		    addMenuTriplet(axisTriplet, ignition);
		}
		addMenuTriplet(ignition, item);
	    } else if(headers[i].matches("^Knock.*")) {
		if(knock==null) {
		    knock=createMenuTriplet("Knock...");
		    addMenuTriplet(axisTriplet, knock);
		}
		addMenuTriplet(knock, item);
	    } else if(headers[i].matches(".*Load.*")) {
		if(load==null) {
		    load=createMenuTriplet("Load...");
		    addMenuTriplet(axisTriplet, load);
		}
		addMenuTriplet(load, item);
	    } else if(headers[i].matches("^EGT.*")) {
		if(egt==null) {
		    egt=createMenuTriplet("EGT...");
		    addMenuTriplet(axisTriplet, egt);
		}
		addMenuTriplet(egt, item);
	    } else {
		addMenuTriplet(axisTriplet, item);
	    }
	}
	JMenu[] calcs=createMenuTriplet("Calcs...");
	addMenuTriplet(axisTriplet, calcs);

	AbstractButton[] calcLoad = createItemTriplet("CalcLoad", ltriplet);
	bg.add(calcLoad[0]);
	addMenuTriplet(calcs, calcLoad);

	AbstractButton[] calcIDC = createItemTriplet("CalcIDC", ltriplet);
	bg.add(calcIDC[0]);
	addMenuTriplet(calcs, calcIDC);
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
	} else if(source.getText().equals("Export Chart")) {
	    if(chart == null) {
		JOptionPane.showMessageDialog(this, "Open a CSV first");
	    } else {
		try {
		    this.chart.doSaveAs();
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
	    //final JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
	    final JFileChooser fc = new JFileChooser();
	    fc.setFileFilter(new GenericFileFilter("csv", "CSV File"));
	    int ret = fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		File file = fc.getSelectedFile();
		WaitCursor.startWaitCursor(this);
		try {
		    dataSet = new Dataset(file.getAbsolutePath());
		    this.chart = CreateChartPanel(dataSet, this.xkey);
		    setContentPane(this.chart);
		    this.setTitle("ECUxPlot " + file.getName());
		    setupAxisMenus(dataSet.headers);
		} catch (Exception e) {
		    JOptionPane.showMessageDialog(this, e);
		    e.printStackTrace();
		}
		WaitCursor.stopWaitCursor(this);
	    }
	}
    }

    public void actionPerformed(ActionEvent event, Comparable id) {
	AbstractButton source = (AbstractButton) (event.getSource());
	if(id.equals("X Axis")) {
	    this.xkey=source.getText();
	    EChartFactory.setChartX(this.chart, dataSet, this.xkey);
	} else if(id.equals("Y Axis")) {
	    EChartFactory.editChartY(this.chart, dataSet, this.xkey, source.getText(),0,source.isSelected());
	} else if(id.equals("Y Axis2")) {
	    EChartFactory.editChartY(this.chart, dataSet, this.xkey, source.getText(),1,source.isSelected());
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
		KeyEvent.VK_F4, ActionEvent.ALT_MASK));
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

    public ECUxPlot(final String title) {
        super(title);

	menuBar = new JMenuBar();

	FileMenu filemenu = new FileMenu("File", this);
	menuBar.add(filemenu);

	setJMenuBar(menuBar);

	setPreferredSize(new java.awt.Dimension(800,600));
    }

    private static ChartPanel CreateChartPanel(Dataset data, Comparable xkey) throws Exception {
        final JFreeChart chart = EChartFactory.create2AxisScatterPlot(
            "", // title
	    "", "", "", // xaxis, yaxis, yaxis2 label
            new DefaultXYDataset(), new DefaultXYDataset(),
	    PlotOrientation.VERTICAL,
            true,	// show legend
            true,	// show tooltips
            false	// show urls
        );

	EChartFactory.setChartX(chart, data, xkey);
	EChartFactory.addChartY(chart, data, xkey, "RPM", 0);
	EChartFactory.addChartY(chart, data, xkey, "EngineLoad", 1);

        return new ChartPanel(chart);
    }

    public static void main(final String[] args) {
	final ECUxPlot plot = new ECUxPlot("ECUxPlot");
	plot.pack();
	RefineryUtilities.centerFrameOnScreen(plot);
	plot.setVisible(true);
    }
}
