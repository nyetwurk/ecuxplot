package org.nyet.ecuxplot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.JOptionPane;

import au.com.bytecode.opencsv.CSVReader;
import flanagan.interpolation.CubicSpline;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;
import org.nyet.ecuxplot.Loggers.LoggerType;

public class ECUxDataset extends Dataset {
    private final Column rpm;
    private Column pedal;
    private Column throttle;
    private Column gear;
    private final Column zboost;
    private final Env env;
    private final Filter filter;
    private final double hp_per_watt = 0.00134102209;
    private final double mbar_per_psi = 68.9475729;
    private double time_ticks_per_sec;	// ECUx has time in ms, JB4 in 1/10s
    public double samples_per_sec=0;
    private CubicSpline [] splines;	// rpm vs time splines
    private LoggerType log_detected = LoggerType.LOG_UNKNOWN;

    public ECUxDataset(String filename, Env env, Filter filter, int verbose)
	    throws Exception {
	super(filename, verbose);

	this.env = env;
	this.filter = filter;

	this.pedal = get(Loggers.pedalnames);
	if (this.pedal!=null && this.pedal.data.isZero()) this.pedal=null;

	this.throttle = get(Loggers.throttlenames);
	if (this.throttle!=null && this.throttle.data.isZero()) this.throttle=null;

	this.gear = get(Loggers.gearnames);
	if (this.gear!=null && this.gear.data.isZero()) this.gear=null;

	// look for zeitronix boost for filtering
	this.zboost = get("Zeitronix Boost");
	/*
	if(this.pedal==null && this.throttle==null) {
	    if(this.pedal==null) System.out.println("could not find pedal position data");
	    if(this.throttle==null) System.out.println("could not find throttle position data");
	}
	*/
	/* calculate smallest samples per second */
	final Column time = get("TIME");
	if (time!=null) {
	    for(int i=1;i<time.data.size();i++) {
		final double delta=time.data.get(i)-time.data.get(i-1);
		if(delta>0) {
		    final double rate = 1/delta;
		    if(rate>this.samples_per_sec) this.samples_per_sec=rate;
		}
	    }
	}
	// get RPM AFTER getting TIME, so we have an accurate samples per sec
	this.rpm = get("RPM");
	buildRanges(); // regenerate ranges, splines
    }

    private int MAW() {
        /* assume 10 == 1 sec smoothing */
	return (int)Math.floor((this.samples_per_sec/10.0)*this.filter.HPTQMAW());
    }

    private String [] ParseUnits(String [] h) {
	final String [] u = new String[h.length];
	for(int i=0;i<h.length;i++) {
	    h[i]=h[i].trim();
	    final Pattern unitsRegEx =
		Pattern.compile("([\\S\\s]+)\\(([\\S\\s].*)\\)");
	    final Matcher matcher = unitsRegEx.matcher(h[i]);
	    if(matcher.find()) {
		h[i]=matcher.group(1);
		u[i]=matcher.group(2);
		if(u[i].matches("^PSI/.*")) u[i]="PSI";
	    }
	}
	return u;
    }
    public void ParseHeaders(CSVReader reader) throws Exception {
	ParseHeaders(reader, LoggerType.LOG_DETECT, 0);
    }
    @Override
    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
	ParseHeaders(reader, LoggerType.LOG_DETECT, verbose);
    }
    public void ParseHeaders(CSVReader reader, LoggerType log_req, int verbose)
	    throws Exception {

	String [] h,u,v=null;

        do {
	    h = reader.readNext();
	    if (h==null)
		throw new Exception(this.getFileId() + ": read failed parsing CSV headers");
	    if (verbose>0)
		for(int i=0;i<h.length;i++)
		    System.out.println("h[" + i + "]: " + h[i]);
	} while (h.length<1 || h[0].trim().length() == 0 || h[0].trim().matches("^#.+"));

	this.log_detected = Loggers.detect(h);

	if (verbose>0)
	    System.out.printf("Detected %s based on \"%s\"\n", log_detected, h[0]);

	/*
	  passed     detected
	  DETECT       all ok
	  not DETECT   DETECT and equals ok
	*/
	if(log_req != LoggerType.LOG_DETECT && log_detected != LoggerType.LOG_ERR) {
            if(log_req != log_detected)
		throw new Exception(log_req + "!=" + log_detected);
	}

	final LoggerType log_use = (log_req==LoggerType.LOG_DETECT)?log_detected:log_req;

	if (verbose>0)
	    System.out.printf("Using %s\n", log_use);

	this.time_ticks_per_sec = 1;
	switch(log_use) {
	    case LOG_VCDS:
		String[] e,b,g,h2;
					// 1: date read already during detect
		e = reader.readNext();	// 2: ECU type
		b = reader.readNext();	// 3: blank or GXXX/FXXX headers
		g = reader.readNext();	// 4: Group or blank
		h = reader.readNext();	// 5: headers 1 or Group
		h2 = reader.readNext();	// 6: headers 2 or units or headers
		u = reader.readNext();	// 7: units

		if (verbose>0)
		    System.out.println("in e:"
			+ e.length + ", b:" + b.length + ", g:" + g.length + ", h:"
			+ h.length + ", h2:" + h2.length + ", u:" + u.length);

		if(g.length<=1) {
		    // g is blank. move everything up one
		    g=h;
		    h=h2;
		    h2=new String[h.length];
		}

		if(g.length<h.length) {
		    // extend g to length of h
		    final String[] newg = new String[h.length];
		    System.arraycopy(g, 0, newg, 0, g.length);
		    g=newg;
		}

		if (verbose>0)
		    System.out.println("out e:"
			+ e.length + ", b:" + b.length + ", g:" + g.length + ", h:"
			+ h.length + ", h2:" + h2.length + ", u:" + u.length);

		for(int i=0;i<h.length;i++) {
		    g[i]=(g[i]!=null)?g[i].trim():"";
		    h[i]=(h[i]!=null)?h[i].trim():"";
		    h2[i]=(h2[i]!=null)?h2[i].trim():"";
		    u[i]=(u[i]!=null)?u[i].trim():"";
		    if (verbose>0)
			System.out.printf("in %d (g:h:h2:[u]): '%s' '%s' [%s]\n", i, g[i], h[i], h2[i], u[i]);
		    // g=TIME and h=STAMP means this is a TIME column
		    if(g[i].equals("TIME") && h[i].equals("STAMP")) {
			g[i]="";
			h[i]="TIME";
		    }
		    // if h2 has a copy of units, nuke it
		    if(h2[i].equals(u[i])) h2[i]="";
		    // concat h1 and h2 if both are non zero length
		    if(h[i].length()>0 && h2[i].length()>0)  h[i]+=" ";
		    h[i]+=h2[i];
		    // copy header from u if this h is empty
		    if(h[i].length()==0) h[i]=u[i];
		}

		Loggers.processAliases(h, log_use);

		for(int i=0;i<h.length;i++) {
		    // blacklist Group 24 Accelerator position, it has max of 80%?
		    if(g[i].matches("^Group 24.*") && h[i].equals("Accelerator position"))
			h[i]=("Accelerator position (G024)");
		    if (verbose>0)
			System.out.printf("out %d (g:h:h2:[u]): '%s' '%s' [%s]\n", i, g[i], h[i], h2[i], u[i]);
		}

		break;
	    case LOG_ZEITRONIX:
		if (log_detected == LoggerType.LOG_ZEITRONIX) {
		    // we detected zeitronix header, strip it
		    reader.readNext();     // Date exported
		    do {
			h = reader.readNext(); // headers
			if (h==null)
			    throw new Exception(this.getFileId() + ": read failed parsing zeitronix log");
		    } while (h.length<=1 || h[0].trim().length() == 0);
		}
		// otherwise, the user gave us a zeit log with no header,
		// but asked us to treat it like a zeit log.

		u = ParseUnits(h);

		Loggers.processAliases(h, log_use);

		// prepend Zeitronix
		for(int i=0;i<h.length;i++) {
		    if (!h[i].equals("RPM")) h[i] = "Zeitronix" + h[i];
		}
		break;
	    case LOG_ECUX:
		u = ParseUnits(h);
		this.time_ticks_per_sec = 1000;

		Loggers.processAliases(h, log_use);

		break;
	    case LOG_EVOSCAN:
		u = new String[h.length]; // no units :/

		Loggers.processAliases(h, log_use);

		break;
	    case LOG_ME7LOGGER:
		/* VARS */
		do {
		    v = reader.readNext();
		    if (v==null) {
			throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log variables");
		    }
		} while (v.length<1 || !v[0].equals("TimeStamp"));

		if (v==null || v.length<1)
		    throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log variables");

		/* UNITS */
		do {
		    u = reader.readNext();
		    if (u==null) {
			throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log units");
		    }
		} while (u.length<1 || u[0].trim().length() == 0);

		if (u==null || u.length<1)
		    throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log units");

		/* ALIASES */
		do {
		    h = reader.readNext();
		    if (h==null) {
			throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log aliases");
		    }
		} while (h.length<1 || h[0].trim().length() == 0);

		if (h==null || h.length<1)
		    throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log aliases");

		if (verbose>0)
		    for(int i=0;i<h.length;i++)
			System.out.printf("in: '%s' (%s) [%s]\n", v[i], h[i], u[i]);

		/* process variables */
		for(int i=0;i<v.length;i++) {
		    v[i]=v[i].trim();
		}

		/* process units */
		for(int i=0;i<u.length;i++) {
		    u[i]=u[i].trim();
		    if(u[i].matches("^mbar$")) u[i]="mBar";
		    if(u[i].matches("^-$")) u[i]="";
		}

		Loggers.processAliases(h, log_use);

		/* prepend ME7L tag */
		for(int i=0;i<h.length;i++) {
		    /* remove existing v before seeing whats there */
		    h[i]=h[i].replace(v[i],"").trim();
		    if(h[i].length()==0) {
		        if(v[i].length()>0) h[i]="ME7L " + v[i];
		    }
		}

		break;
	    case LOG_VOLVOLOGGER:
		u = new String[h.length];
		v = new String[h.length];
		final Pattern unitsRegEx =
		    Pattern.compile("([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)");
		for(int i=0;i<h.length;i++) {
		    h[i]=h[i].trim();
		    final Matcher matcher = unitsRegEx.matcher(h[i]);
		    if (matcher.find()) {
			h[i]=matcher.group(1).trim();
			u[i]=matcher.group(2).trim();
			v[i]=matcher.group(3).trim();
			if(h[i].length()==0) {
			    if(v[i].length()>0) h[i]="ME7L " + v[i];
			}
		    }
		}

		Loggers.processAliases(h, log_use);

		break;
	    case LOG_LOGWORKS:
		h = reader.readNext(); // headers
		u = reader.readNext(); // units

		Loggers.processAliases(h, log_use);
		final Pattern unitsRegEx1 =
		    Pattern.compile("\\((.+)\\)");
		for (int i=0; i<u.length; i++) {
		    u[i] = u[i].trim();
		    final Matcher matcher = unitsRegEx1.matcher(u[i]);
		    if (matcher.find()) u[i]=matcher.group(1).trim();
		}
		break;
	    case LOG_JB4:
		this.time_ticks_per_sec = 10;
		reader.readNext(); // Date
		reader.readNext(); // Junk
		reader.readNext(); // Junk
		h = reader.readNext(); // headers
		v = Arrays.copyOf(h, h.length);
		/* hack: use process Aliases to generate units in () for use in ParseUnits */
		Loggers.processAliases(h, log_use);
		u = ParseUnits(h);
		for(int i=0;i<h.length;i++) {
		    h[i]=h[i].trim();
		    v[i]=v[i].trim();
		    if (verbose>0)
			System.out.println("in : " + v[i] + ": " + h[i] + " [" + u[i] + "]");
		}

		break;
	    default:
		u = ParseUnits(h);
		for(int i=0;i<h.length;i++) {
		    if (verbose>0)
			System.out.println("in : " + h[i] + " [" + u[i] + "]");
		}

		Loggers.processAliases(h);

		break;
	}

	if(u.length<h.length) {
	    u = Arrays.copyOf(u, h.length);
	}

	for(int i=0;i<h.length;i++) {
	    if(h[i].length()>0 && (u[i]==null || u[i].length()==0)) {
		u[i]=Units.find(h[i]);
		if (verbose>0 && (u[i]==null || u[i].length()==0)) {
		    System.out.println("Can't find units for " + h[i]);
		}
	    }
	}

	if (verbose>0) {
	    for(int i=0;i<h.length;i++) {
		if (v!=null && i<v.length) {
		    System.out.printf("out: '%s' (%s) [%s]\n", h[i], v[i], u[i]);
		} else {
		    System.out.printf("out: '%s' [%s]\n", h[i], u[i]);
		}
	    }
	}
	//System.exit(0);

	final DatasetId [] ids = new DatasetId[h.length];
	for(int i=0; i<h.length; i++) {
	    ids[i] = new DatasetId(h[i]);
	    if(v!=null && i<v.length) ids[i].id2 = v[i];
	    if(u!=null && i<u.length) ids[i].unit = u[i];
	    ids[i].type = this.log_detected;
	}
	this.setIds(ids);
    }

    private DoubleArray drag (DoubleArray v) {
	final double rho=1.293;	// kg/m^3 air, standard density

	final DoubleArray windDrag = v.pow(3).mult(0.5 * rho * this.env.c.Cd() *
	    this.env.c.FA());

	final DoubleArray rollingDrag = v.mult(this.env.c.rolling_drag() *
	    this.env.c.mass() * 9.80665);

	return windDrag.add(rollingDrag);
    }

    private DoubleArray toPSI(DoubleArray abs) {
	final Column ambient = this.get("BaroPressure");
	if(ambient==null) return abs.add(-1013).div(this.mbar_per_psi);
	return abs.sub(ambient.data).div(this.mbar_per_psi);
    }

    private static DoubleArray toCelcius(DoubleArray f) {
	return f.add(-32).mult(5.0/9.0);
    }

    private static DoubleArray toFahrenheit(DoubleArray c) {
	return c.mult(9.0/5.0).add(32);
    }

    // given a list of id's, find the first that exists
    public Column get(Comparable<?> [] id) {
	for (final Comparable<?> k : id) {
	    Column ret = null;
	    try { ret=_get(k);
	    } catch (final NullPointerException e) {
	    }
	    if(ret!=null) return ret;
	}
	return null;
    }

    @Override
    public Column get(Comparable<?> id) {
	try {
	    return _get(id);
	} catch (final NullPointerException e) {
	    return null;
	}
    }

    private Column _get(Comparable<?> id) {
	Column c=null;
	if(id.equals("Sample")) {
	    final double[] idx = new double[this.length()];
	    for (int i=0;i<this.length();i++)
		idx[i]=i;
	    final DoubleArray a = new DoubleArray(idx);
	    c = new Column("Sample", "#", a);
	} else if(id.equals("TIME")) {
	    final DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(this.time_ticks_per_sec));
	} else if(id.equals("RPM")) {
	    // smooth sampling quantum noise/jitter, RPM is an integer!
	    if (this.samples_per_sec>10) {
		final DoubleArray a = super.get("RPM").data.smooth();
		c = new Column(id, "RPM", a);
	    }
	} else if(id.equals("RPM - raw")) {
	    c = new Column(id, "RPM", super.get("RPM").data);
	} else if(id.equals("Sim Load")) {
	    // g/sec to kg/hr
	    final DoubleArray a = super.get("MassAirFlow").data.mult(3.6);
	    final DoubleArray b = super.get("RPM").data.smooth();

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Sim Load Corrected")) {
	    // g/sec to kg/hr
	    final DoubleArray a = this.get("Sim MAF").data.mult(3.6);
	    final DoubleArray b = this.get("RPM").data;

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("MassAirFlow (kg/hr)")) {
	    // mass in g/sec
	    final DoubleArray maf = super.get("MassAirFlow").data;
	    c = new Column(id, "kg/hr", maf.mult(60.0*60.0/1000.0));
	} else if(id.equals("Sim MAF")) {
	    // mass in g/sec
	    final DoubleArray a = super.get("MassAirFlow").data.
		mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("MassAirFlow df/dt")) {
	    // mass in g/sec
	    final DoubleArray maf = super.get("MassAirFlow").data;
	    final DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "g/sec^s", maf.derivative(time).max(0));
	} else if(id.equals("Turbo Flow")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    c = new Column(id, "m^3/sec", a.div(1225*this.env.f.turbos()));
	} else if(id.equals("Turbo Flow (lb/min)")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    c = new Column(id, "lb/min", a.div(7.55*this.env.f.turbos()));
	} else if(id.equals("Sim Fuel Mass")) {	// based on te
	    final double gps_per_ccmin = 0.0114; // (grams/sec) per (cc/min)
	    final double gps = this.env.f.injector()*gps_per_ccmin;
	    final double cylinders = this.env.f.cylinders();
	    final Column bank1 = this.get("EffInjectorDutyCycle");
	    final Column bank2 = this.get("EffInjectorDutyCycleBank2");
	    DoubleArray duty = bank1.data;
	    /* average two duties for overall mass */
	    if (bank2!=null) duty = duty.add(bank2.data).div(2);
	    final DoubleArray a = duty.mult(cylinders*gps/100);
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("TargetAFRDriverRequest (AFR)")) {
	    final DoubleArray abs = super.get("TargetAFRDriverRequest").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("AirFuelRatioDesired (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioDesired").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("AirFuelRatioCurrent (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioCurrent").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("Sim AFR")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    final DoubleArray b = this.get("Sim Fuel Mass").data;
	    c = new Column(id, "AFR", a.div(b));
	} else if(id.equals("Sim lambda")) {
	    final DoubleArray a = this.get("Sim AFR").data.div(14.7);
	    c = new Column(id, "lambda", a);
	} else if(id.equals("Sim lambda error")) {
	    final DoubleArray a = super.get("AirFuelRatioDesired").data;
	    final DoubleArray b = this.get("Sim lambda").data;
	    c = new Column(id, "%", a.div(b).mult(-1).add(1).mult(100).
		max(-25).min(25));

	} else if(id.equals("FuelInjectorDutyCycle")) {
	    final DoubleArray a = super.get("FuelInjectorOnTime").data.	/* ti */
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffInjectorDutyCycle")) {		/* te */
	    final DoubleArray a = super.get("EffInjectionTime").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffInjectorDutyCycleBank2")) {		/* te */
	    final DoubleArray a = super.get("EffInjectionTimeBank2").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
/*****************************************************************************/
	/* if log contains Engine torque */
	} else if(id.equals("Engine torque (ft-lb)")) {
	    final DoubleArray tq = this.get("Engine torque").data;
	    final DoubleArray value = tq.mult(0.737562149);	// nm to ft-lb
	    c = new Column(id, "ft-lb", value);
	} else if(id.equals("Engine HP")) {
	    final DoubleArray tq = this.get("Engine torque (ft-lb)").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = tq.div(5252).mult(rpm);
	    c = new Column(id, "HP", value);
/*****************************************************************************/
	} else if(id.equals("Calc Velocity")) {
	    // TODO: make a user adjustable checkbox for this
	    // Also, make sure we test units of VehicleSpeed
	    /* Column v = this.get("VehicleSpeed");
	    if (v!=null) {
		c = new Column(id, "m/s", v.data.mult(1000.0/60.0/60.0));
	    } else */{
		final double mph_per_mps = 2.23693629;
		final DoubleArray rpm = this.get("RPM").data;
		c = new Column(id, "m/s", rpm.div(this.env.c.rpm_per_mph()).
		    div(mph_per_mps));
	    }
	} else if(id.equals("Acceleration (RPM/s)")) {
	    final DoubleArray y = this.get("RPM").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x, this.MAW()).max(0));
	} else if(id.equals("Acceleration - raw (RPM/s)")) {
	    final DoubleArray y = this.get("RPM - raw").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x));
	} else if(id.equals("Acceleration (m/s^2)")) {
	    final DoubleArray y = this.get("Calc Velocity").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "m/s^2", y.derivative(x, this.MAW()).max(0));
	} else if(id.equals("Acceleration (g)")) {
	    final DoubleArray a = this.get("Acceleration (m/s^2)").data;
	    c = new Column(id, "g", a.div(9.80665));
/*****************************************************************************/
	} else if(id.equals("WHP")) {
	    final DoubleArray a = this.get("Acceleration (m/s^2)").data;
	    final DoubleArray v = this.get("Calc Velocity").data;
	    final DoubleArray whp = a.mult(v).mult(this.env.c.mass()).
		add(this.drag(v));	// in watts

	    DoubleArray value = whp.mult(this.hp_per_watt);
	    String l = "HP";
	    if(this.env.sae.enabled()) {
		value = value.mult(this.env.sae.correction());
		l += " (SAE)";
	    }
	    c = new Column(id, l, value.movingAverage(this.MAW()));
	} else if(id.equals("HP")) {
	    final DoubleArray whp = this.get("WHP").data;
	    final DoubleArray value = whp.div((1-this.env.c.driveline_loss())).
		    add(this.env.c.static_loss());
	    String l = "HP";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("WTQ")) {
	    final DoubleArray whp = this.get("WHP").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = whp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("TQ")) {
	    final DoubleArray hp = this.get("HP").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = hp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Drag")) {
	    final DoubleArray v = this.get("Calc Velocity").data;
	    final DoubleArray drag = this.drag(v);
	    c = new Column(id, "HP", drag.mult(this.hp_per_watt));
	} else if(id.equals("IntakeAirTemperature")) {
	    c = super.get(id);
	    if (c.getUnits().matches(".*C$"))
		c = new Column(id, "\u00B0 F", ECUxDataset.toFahrenheit(c.data));
	} else if(id.equals("IntakeAirTemperature (C)")) {
	    c = super.get("IntakeAirTemperature");
	    if (c.getUnits().matches(".*F$"))
		c = new Column(id, "\u00B0 C", ECUxDataset.toCelcius(c.data));
	} else if(id.equals("BoostPressureDesired (PSI)")) {
	    c = super.get("BoostPressureDesired");
	    if (!c.getUnits().matches("PSI"))
		c = new Column(id, "PSI", this.toPSI(c.data));
	} else if(id.equals("BoostPressureDesired")) {
	    final Column delta = super.get("BoostPressureDesiredDelta");
	    if (delta != null) {
	        final Column ecu = super.get("ECUBoostPressureDesired");
		if (ecu != null) {
		    c = new Column(id, "PSI", ecu.data.add(delta.data));
		}
	    }
	} else if(id.equals("BoostPressureActual (PSI)")) {
	    c = super.get("BoostPressureActual");
	    if (!c.getUnits().matches("PSI"))
		c = new Column(id, "PSI", this.toPSI(c.data));
	} else if(id.equals("Zeitronix Boost (PSI)")) {
	    final DoubleArray boost = super.get("Zeitronix Boost").data;
	    c = new Column(id, "PSI", boost.movingAverage(this.filter.ZeitMAW()));
	} else if(id.equals("Zeitronix Boost")) {
	    final DoubleArray boost = this.get("Zeitronix Boost (PSI)").data;
	    c = new Column(id, "mBar", boost.mult(this.mbar_per_psi).add(1013));
	} else if(id.equals("Zeitronix AFR (lambda)")) {
	    final DoubleArray abs = super.get("Zeitronix AFR").data;
	    c = new Column(id, "lambda", abs.div(14.7));
	} else if(id.equals("Zeitronix Lambda (AFR)")) {
	    final DoubleArray abs = super.get("Zeitronix Lambda").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("BoostDesired PR")) {
	    final Column act = super.get("BoostPressureDesired");
	    try {
		final DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.data.div(ambient));
	    } catch (final Exception e) {
		if (act.getUnits().matches("PSI"))
		    c = new Column(id, "PR", act.data.div(14.7));
		else
		    c = new Column(id, "PR", act.data.div(1013));
	    }

	} else if(id.equals("BoostActual PR")) {
	    final Column act = super.get("BoostPressureActual");
	    try {
		final DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.data.div(ambient));
	    } catch (final Exception e) {
		if (act.getUnits().matches("PSI"))
		    c = new Column(id, "PR", act.data.div(14.7));
		else
		    c = new Column(id, "PR", act.data.div(1013));
	    }
	} else if(id.equals("Sim evtmod")) {
	    final DoubleArray tans = this.get("IntakeAirTemperature (C)").data;
	    DoubleArray tmot = tans.ident(95);
	    try {
		tmot = this.get("CoolantTemperature").data;
	    } catch (final Exception e) {}

	    // KFFWTBR=0.02
	    // evtmod = tans + (tmot-tans)*KFFWTBR
	    final DoubleArray evtmod = tans.add((tmot.sub(tans)).mult(0.02));
	    c = new Column(id, "\u00B0 C", evtmod);
	} else if(id.equals("Sim ftbr")) {
	    final DoubleArray tans = this.get("IntakeAirTemperature (C)").data;
	    final DoubleArray evtmod = this.get("Sim evtmod").data;
	    // linear fit to stock FWFTBRTA
	    // fwtf = (tans+637.425)/731.334

	    final DoubleArray fwft = tans.add(673.425).div(731.334);

	    // ftbr = 273/(tans+273) * fwft

	    //    (tans+637.425)      273
	    //    -------------- *  -------
	    //      (tans+273)      731.334

	    // ftbr=273/(evtmod-273) * fwft
	    c = new Column(id, "", evtmod.ident(273).div(evtmod.add(273)).mult(fwft));
	} else if(id.equals("Sim BoostIATCorrection")) {
	    final DoubleArray ftbr = this.get("Sim ftbr").data;
	    c = new Column(id, "", ftbr.inverse());
	} else if(id.equals("Sim BoostPressureDesired")) {
	    final boolean SY_BDE = false;
	    final boolean SY_AGR = true;
	    DoubleArray load;
	    DoubleArray ps;

	    try {
		load = super.get("EngineLoadRequested").data; // rlsol
	    } catch (final Exception e) {
		load = super.get("EngineLoadCorrected").data; // rlmax
	    }

	    try {
		ps = super.get("ME7L ps_w").data;
	    } catch (final Exception e) {
		ps = super.get("BoostPressureActual").data;
	    }

	    DoubleArray ambient = ps.ident(1013); // pu
	    try {
		ambient	= super.get("BaroPressure").data;
	    } catch (final Exception e) { }

            DoubleArray fupsrl = load.ident(0.1037); // KFURL
	    try {
		final DoubleArray ftbr = this.get("Sim ftbr").data;
		// fupsrl = KFURL * ftbr
		fupsrl = fupsrl.mult(ftbr);
	    } catch (final Exception e) {}

	    // pirg = fho * KFPRG = (pu/1013) * 70
	    final DoubleArray pirg = ambient.mult(70/1013.0);

	    if (!SY_BDE) {
		//load = load.sub(rlr);
		load = load.max(0);	// rlfgs
		if (SY_AGR) {
		    // pbr = ps * fpbrkds
		    // rfges = (pbr-pirg).max(0)*fupsrl
		    final DoubleArray rfges = (ps.mult(1.106)).sub(pirg).max(0).mult(fupsrl);
		    // psagr = 250??
		    // rfagr = rfges * psagr/ps
		    // load = rlfgs + rfagr;
		    load = load.add(rfges.mult(250).div(ps));
		}
		//load = load.add(rlr);
	    }

	    DoubleArray boost = load.div(fupsrl);

	    if (SY_BDE) {
		boost = boost.add(pirg);
	    }

	    // fpbrkds from KFPBRK/KFPBRKNW
	    boost = boost.div(1.016);	// pssol

	    // vplsspls from KFVPDKSD/KFVPDKSDSE
	    boost = boost.div(1.016);	// plsol

	    c = new Column(id, "mBar", boost.max(ambient));
	} else if(id.equals("Boost Spool Rate (RPM)")) {
	    final DoubleArray abs = super.get("BoostPressureActual").data.smooth();
	    final DoubleArray rpm = this.get("RPM").data;
	    c = new Column(id, "mBar/RPM", abs.derivative(rpm).max(0));
	} else if(id.equals("Boost Spool Rate Zeit (RPM)")) {
	    final DoubleArray boost = this.get("Zeitronix Boost").data.smooth();
	    final DoubleArray rpm =
		this.get("RPM").data.movingAverage(this.filter.ZeitMAW()).smooth();
	    c = new Column(id, "mBar/RPM", boost.derivative(rpm).max(0));
	} else if(id.equals("Boost Spool Rate (time)")) {
	    final DoubleArray abs = this.get("BoostPressureActual (PSI)").data.smooth();
	    final DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "PSI/sec", abs.derivative(time, this.MAW()).max(0));
	} else if(id.equals("LDR error")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    c = new Column(id, "100mBar", set.sub(out).div(100));
	} else if(id.equals("LDR de/dt")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    final DoubleArray t = this.get("TIME").data;
	    final DoubleArray o = set.sub(out).derivative(t,this.MAW());
	    c = new Column(id,"100mBar",o.mult(this.env.pid.time_constant).div(100));
	} else if(id.equals("LDR I e dt")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    final DoubleArray t = this.get("TIME").data;
	    final DoubleArray o = set.sub(out).
		integral(t,0,this.env.pid.I_limit/this.env.pid.I*100);
	    c = new Column(id,"100mBar",o.div(this.env.pid.time_constant).div(100));
	} else if(id.equals("LDR PID")) {
	    final DoubleArray.TransferFunction fP =
		new DoubleArray.TransferFunction() {
		    @Override
		    public final double f(double x, double y) {
			if(Math.abs(x)<ECUxDataset.this.env.pid.P_deadband/100) return 0;
			return x*ECUxDataset.this.env.pid.P;
		    }
	    };
	    final DoubleArray.TransferFunction fD =
		new DoubleArray.TransferFunction() {
		    @Override
		    public final double f(double x, double y) {
			y=Math.abs(y);
			if(y<3) return x*ECUxDataset.this.env.pid.D[0];
			if(y<5) return x*ECUxDataset.this.env.pid.D[1];
			if(y<7) return x*ECUxDataset.this.env.pid.D[2];
			return x*ECUxDataset.this.env.pid.D[3];
		    }
	    };
	    final DoubleArray E = this.get("LDR error").data;
	    final DoubleArray P = E.func(fP);
	    final DoubleArray I = this.get("LDR I e dt").data.mult(this.env.pid.I);
	    final DoubleArray D = this.get("LDR de/dt").data.func(fD,E);
	    c = new Column(id, "%", P.add(I).add(D).max(0).min(95));
	} else if(id.equals("Sim pspvds")) {
	    final DoubleArray ps_w = super.get("ME7L ps_w").data;
	    final DoubleArray pvdkds = super.get("BoostPressureActual").data;
	    c = new Column(id,"",ps_w.div(pvdkds));
/*****************************************************************************/
	} else if(id.equals("IgnitionTimingAngleOverallDesired")) {
	    DoubleArray averetard = null;
	    int count=0;
	    for(int i=0;i<8;i++) {
		final Column retard = this.get("IgnitionRetardCyl" + i);
		if(retard!=null) {
		    if(averetard==null) averetard = retard.data;
		    else averetard = averetard.add(retard.data);
		    count++;
		}
	    }
	    DoubleArray out = this.get("IgnitionTimingAngleOverall").data;
	    if(count>0) {
		// assume retard is always positive... some loggers log it negative
		// abs it to normalize
		out = out.add(averetard.div(count).abs());
	    }
	    c = new Column(id, "\u00B0", out);
/*****************************************************************************/
	} else if(id.equals("Sim LoadSpecified correction")) {
	    final DoubleArray cs = super.get("EngineLoadCorrected").data;
	    final DoubleArray s = super.get("EngineLoadSpecified").data;
	    c = new Column(id, "K", cs.div(s));
/*****************************************************************************/
	}

	if(c==null) {
	    /* Calc True Timing */
	    if (id.toString().endsWith(" (ms)")) {
		String s = id.toString();
		s = s.substring(0, s.length()-5);
		final Column t = this.get(s);
		if (t!=null) {
		    final DoubleArray r = this.get("RPM").data;
		    c = new Column(id, "(ms)", t.data.div(r.mult(.006)));
		}
	    }
	}

	if(c!=null) {
	    this.getColumns().add(c);
	    return c;
	}
	return super.get(id);
    }

    @Override
    protected boolean dataValid(int i) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	final ArrayList<String> reasons = new ArrayList<String>();

	if(this.filter.gear()>=0 && this.gear!=null && Math.round(this.gear.data.get(i)) != this.filter.gear()) {
	    reasons.add("gear " + Math.round(this.gear.data.get(i)) +
		    "!=" + this.filter.gear());
	    ret=false;
	}
	if(this.pedal!=null && this.pedal.data.get(i)<this.filter.minPedal()) {
	    reasons.add("pedal " + this.pedal.data.get(i) +
		    "<" + this.filter.minPedal());
	    ret=false;
	}
	if(this.throttle!=null && this.throttle.data.get(i)<this.filter.minThrottle()) {
	    reasons.add("throttle " + this.throttle.data.get(i) +
		    "<" + this.filter.minThrottle());
	    ret=false;
	}
	if(this.zboost!=null && this.zboost.data.get(i)<0) {
	    reasons.add("zboost " + this.zboost.data.get(i) +
		    "<0");
	    ret=false;
	}
	if(this.rpm!=null) {
	    if(this.rpm.data.get(i)<this.filter.minRPM()) {
		reasons.add("rpm " + this.rpm.data.get(i) +
		    "<" + this.filter.minRPM());
		ret=false;
	    }
	    if(this.rpm.data.get(i)>this.filter.maxRPM()) {
		reasons.add("rpm " + this.rpm.data.get(i) +
		    ">" + this.filter.maxRPM());
		ret=false;
	    }
	    if(i>0 && this.rpm.data.size()>i+2 &&
		this.rpm.data.get(i-1)-this.rpm.data.get(i+1)>this.filter.monotonicRPMfuzz()) {
		reasons.add("rpm delta " +
		    this.rpm.data.get(i-1) + "-" + this.rpm.data.get(i+1) + ">" +
		    this.filter.monotonicRPMfuzz());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    // System.out.println(reasons);
	}

	return ret;
    }

    @Override
    protected boolean rangeValid(Range r) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	final ArrayList<String> reasons = new ArrayList<String>();

	if(r.size()<this.filter.minPoints()) {
	    reasons.add("points " + r.size() + "<" +
		this.filter.minPoints());
	    ret=false;
	}
	if(this.rpm!=null) {
	    if(this.rpm.data.get(r.end)<this.rpm.data.get(r.start)+this.filter.minRPMRange()) {
		reasons.add("RPM Range " + this.rpm.data.get(r.end) +
		    "<" + this.rpm.data.get(r.start) + "+" +this.filter.minRPMRange());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    // System.out.println(reasons);
	}

	return ret;
    }

    @Override
    public void buildRanges() {
	super.buildRanges();
        final ArrayList<Dataset.Range> ranges = this.getRanges();
	this.splines = new CubicSpline[ranges.size()];
        for(int i=0;i<ranges.size();i++) {
	    this.splines[i] = null;
            final Dataset.Range r=ranges.get(i);
            try {
                final double [] rpm = this.getData("RPM", r);
                final double [] time = this.getData("TIME", r);
		if(time.length>0 && time.length==rpm.length)
		    this.splines[i] = new CubicSpline(rpm, time);
		else
		    JOptionPane.showMessageDialog(null,
			"length problem " + time.length + ":" + rpm.length);
            } catch (final Exception e) {}
        }
    }

    public double calcFATS(int run, int RPMStart, int RPMEnd) throws Exception {
	final ArrayList<Dataset.Range> ranges = this.getRanges();
	if(run<0 || run>=ranges.size())
	    throw new Exception("no run found");

	if(this.splines[run]==null)
	    throw new Exception("run interpolation failed");

	final Dataset.Range r=ranges.get(run);
	final double [] rpm = this.getData("RPM", r);

	if(rpm[0]-100>RPMStart || rpm[rpm.length-1]+100<RPMEnd)
	    throw new Exception("run " + rpm[0] + "-" + rpm[rpm.length-1] +
		    " not long enough");

	final double et = this.splines[run].interpolate(RPMEnd) -
		this.splines[run].interpolate(RPMStart);
	if(et<=0)
	    throw new Exception("don't cross the streams");

	return et;
    }

    public double[] calcFATS(int RPMStart, int RPMEnd) {
        final ArrayList<Dataset.Range> ranges = this.getRanges();
	final double [] out = new double[ranges.size()];
        for(int i=0;i<ranges.size();i++) {
	    try {
		out[i]=calcFATS(i, RPMStart, RPMEnd);
	    } catch (final Exception e) {
	    }
	}
	return out;
    }

    public Filter getFilter() { return this.filter; }
    //public void setFilter(Filter f) { this.filter=f; }
    public Env getEnv() { return this.env; }
    //public void setEnv(Env e) { this.env=e; }
    @Override
    public boolean useId2() { return this.env.prefs.getBoolean("altnames", false); }
}
