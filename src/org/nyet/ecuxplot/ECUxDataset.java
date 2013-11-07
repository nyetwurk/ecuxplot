package org.nyet.ecuxplot;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.JOptionPane;

import au.com.bytecode.opencsv.CSVReader;
import flanagan.interpolation.CubicSpline;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;
import org.nyet.util.Files;

public class ECUxDataset extends Dataset {
    private Column rpm, pedal, throttle, gear, zboost;
    private Env env;
    private Filter filter;
    private final double hp_per_watt = 0.00134102209;
    private final double mbar_per_psi = 68.9475729;
    private double time_ticks_per_sec;	// ECUx has time in ms. Nobody else does.
    public double samples_per_sec=0;
    private CubicSpline [] splines;	// rpm vs time splines

    public ECUxDataset(String filename, Env env, Filter filter)
	    throws Exception {
	super(filename);

	this.env = env;
	this.filter = filter;

	this.pedal = get(new String []
		{"AcceleratorPedalPosition", "AccelPedalPosition", "Zeitronix TPS", "Accelerator position"});
	if (this.pedal!=null && this.pedal.data.isZero()) this.pedal=null;

	this.throttle = get(new String []
		{"ThrottlePlateAngle", "Throttle Angle", "Throttle Valve Angle"});
	if (this.throttle!=null && this.throttle.data.isZero()) this.throttle=null;

	this.gear = get(new String []
		{"Gear", "SelectedGear"});
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
	Column time = get("TIME");
	if (time!=null) {
	    for(int i=1;i<time.data.size();i++) {
		double delta=time.data.get(i)-time.data.get(i-1);
		if(delta>0) {
		    double rate = 1/delta;
		    if(rate>samples_per_sec) this.samples_per_sec=rate;
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

    private static final int LOG_UNKNOWN = -2;
    public static final int LOG_ERR = -1;
    public static final int LOG_DETECT = 0;
    public static final int LOG_ECUX = 1;
    public static final int LOG_VCDS = 2;
    public static final int LOG_ZEITRONIX = 3;
    public static final int LOG_ME7LOGGER = 4;
    public static final int LOG_EVOSCAN = 5;
    public int logType;

    private int detect(String [] h) {
	h[0]=h[0].trim();
	if(h[0].matches("^.*day$")) return LOG_VCDS;
	if(h[0].matches("^Filename:.*")) {
	    if(Files.extension(h[0]).equals("zto") ||
	       Files.extension(h[0]).equals("zdl") ||
		h[0].matches(".*<unnamed file>$"))
	    return LOG_ZEITRONIX;
	}
	if(h[0].matches("^TIME$")) return LOG_ECUX;

	if(h[0].matches(".*ME7-Logger.*")) return LOG_ME7LOGGER;

	if(h[0].matches("^LogID$")) return LOG_EVOSCAN;

	return LOG_UNKNOWN;
    }

    private String [] ParseUnits(String [] h) {
	String [] u = new String[h.length];
	for(int i=0;i<h.length;i++) {
	    h[i]=h[i].trim();
	    final Pattern unitsRegEx =
		Pattern.compile("([\\w\\s]+)\\(([\\w\\s].*)\\)");
	    Matcher matcher = unitsRegEx.matcher(h[i]);
	    if(matcher.find()) {
		h[i]=matcher.group(1);
		u[i]=matcher.group(2);
		if(u[i].matches("^PSI/.*")) u[i]="PSI";
	    }
	}
	return u;
    }
    public void ParseHeaders(CSVReader reader) throws Exception {
	ParseHeaders(reader, LOG_DETECT);
    }
    public void ParseHeaders(CSVReader reader, int log_req)
	    throws Exception {
	boolean verbose = false;
	if (log_req<0)
	    throw new Exception(this.getFileId() + ": invalid log_req" + log_req);
	String [] h,u;

        do {
	    h = reader.readNext();
	    if (h==null)
		throw new Exception(this.getFileId() + ": read failed parsing CSV headers");
	    if (verbose)
		for(int i=0;i<h.length;i++)
		    System.out.println("h[" + i + "]: " + h[i]);
	} while (h.length<1 || h[0].trim().length() == 0 || h[0].trim().matches("^#.+"));

	int log_detected = detect(h);

	/*
	  passed     detected
	  DETECT       all ok
	  not DETECT   DETECT and equals ok
	*/
	if(log_req != LOG_DETECT && log_detected != LOG_UNKNOWN) {
            if(log_req != log_detected)
		throw new Exception(log_req + "!=" + log_detected);
	}

	int log_use = (log_req==LOG_DETECT)?log_detected:log_req;

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

		if (verbose)
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
		    String[] newg = new String[h.length];
		    System.arraycopy(g, 0, newg, 0, g.length);
		    g=newg;
		}

		if (verbose)
		    System.out.println("out e:"
			+ e.length + ", b:" + b.length + ", g:" + g.length + ", h:"
			+ h.length + ", h2:" + h2.length + ", u:" + u.length);

		for(int i=0;i<h.length;i++) {
		    g[i]=(g[i]!=null)?g[i].trim():"";
		    h[i]=(h[i]!=null)?h[i].trim():"";
		    h2[i]=(h2[i]!=null)?h2[i].trim():"";
		    u[i]=(u[i]!=null)?u[i].trim():"";
		    if (verbose)
			System.out.println("in: '" + h[i] +"': '" + h2[i] + "' [" + u[i] + "]");
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
		    // remap engine speed to "RPM'
		    if(h[i].matches("^Engine [Ss]peed.*")) h[i]="RPM";
		    // ignore weird letter case for throttle angle
		    if(h[i].matches("^Throttle [Aa]ngle.*")) h[i]="Throttle Angle";
		    // ignore weird spacing for MAF
		    if(h[i].matches("^Mass [Aa]ir [Ff]low.*")) h[i]="MassAirFlow";
		    if(h[i].matches("^Mass Flow$")) h[i]="MassAirFlow";
		    if(h[i].matches("^Ign timing.*")) h[i]="Ignition Timing Angle";
		    // copy header from u if this h is empty
		    if(h[i].length()==0) h[i]=u[i];
		    // blacklist Group 24 Accelerator position, it has max of 80%?
		    if(g[i].matches("^Group 24.*") && h[i].equals("Accelerator position"))
			h[i]=("Accelerator position (G024)");
		    if (verbose)
			System.out.println("out:'" + h[i] +"': '" + h2[i] + "' [" + u[i] + "]");
		}
		break;
	    case LOG_ZEITRONIX:
		if (log_detected == LOG_ZEITRONIX) {
		    // we detected zeitronix header, strip it
		    reader.readNext();     // Date exported
		    do {
			h = reader.readNext(); // headers
			if (h==null)
			    throw new Exception(this.getFileId() + ": read failed parsing zeitronix log");
		    } while (h.length<=1);
		}
		// otherwise, the user gave us a zeit log with no header,
		// but asked us to treat it like a zeit log.

		u = ParseUnits(h);
		for(int i=0;i<h.length;i++) {
		    if (verbose)
			System.out.println("in : " + h[i] + " [" + u[i] + "]");
		    if(h[i].matches(".*RPM$")) h[i]="RPM";
		    if(h[i].matches(".*Boost$")) h[i]="Zeitronix Boost";
		    if(h[i].matches(".*TPS$")) h[i]="Zeitronix TPS";
		    if(h[i].matches(".*AFR$")) h[i]="Zeitronix AFR";
		    if(h[i].matches(".*Lambda$")) h[i]="Zeitronix Lambda";
		    if(h[i].matches(".*EGT$")) h[i]="Zeitronix EGT";
		    if(h[i].equals("Time")) h[i]="Zeitronix Time";
		    if (verbose)
			System.out.println("out: " + h[i] + " [" + u[i] + "]");
		}
		break;
	    case LOG_ECUX:
		u = ParseUnits(h);
		this.time_ticks_per_sec = 1000;
		break;
	    case LOG_EVOSCAN:
		u = new String[h.length]; // no units :/
		for(int i=0;i<h.length;i++) {
		    if(h[i].matches(".*RPM$")) h[i]="RPM";
		    if(h[i].equals("LogEntrySeconds")) h[i]="TIME";
		    if(h[i].equals("TPS")) h[i]="ThrottlePlateAngle";
		    if(h[i].equals("APP")) h[i]="AccelPedalPosition";
		    if(h[i].equals("IAT")) h[i]="IntakeAirTemperature";
		}
		break;
	    case LOG_ME7LOGGER:
		String[] v;	// ME7 variable name
		do {
		    v = reader.readNext();
		    if (v==null) {
			throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log");
		    }
		} while (v.length<1 || !v[0].equals("TimeStamp"));

		if (v==null || v.length<1)
		    throw new Exception(this.getFileId() + ": read failed parsing ME7Logger log");

		u = reader.readNext();
		for(int i=0;i<u.length;i++) {
		    u[i]=u[i].trim();
		    if(u[i].matches("^mbar$")) u[i]="mBar";
		}

		h = reader.readNext();
		for(int i=0;i<h.length;i++) {
		    h[i]=h[i].trim();
		    if(h[i].matches("^Engine[Ss]peed.*")) h[i]="RPM";
		    if(h[i].matches("^BoostPressureSpecified$")) h[i]="BoostPressureDesired";
		    if(h[i].matches("^AtmosphericPressure$")) h[i]="BaroPressure";
		    if(h[i].matches("^AirFuelRatioRequired$")) h[i]="AirFuelRatioDesired";
		    if(h[i].matches("^InjectionTime$")) h[i]="EffFuelInjectonTime";	// is this te or ti? Assume te?
		    if(h[i].matches("^InjectionTimeBank2$")) h[i]="EffFuelInjectonTimeBank2";	// is this te or ti? Assume te?
		    if(h[i].length()==0) {
			v[i]=v[i].trim();
		        if(v[i].length()>0) h[i]="ME7L " + v[i];
		    }
		}

		if (verbose)
		    for(int i=0;i<h.length;i++)
			System.out.println("out: " + h[i] + " [" + u[i] + "]");
		break;
	    default:
		u = ParseUnits(h);
		for(int i=0;i<h.length;i++) {
		    if (verbose)
			System.out.println("in : " + h[i] + " [" + u[i] + "]");
		    if(h[i].matches("^Time$")) h[i]="TIME";
		    if(h[i].matches("^Engine [Ss]peed.*")) h[i]="RPM";
		    if(h[i].matches("^Mass air flow$")) h[i]="MassAirFlow";
		    if (verbose)
			System.out.println("out: " + h[i] + " [" + u[i] + "]");
		}
		break;
	}
	for(int i=0;i<h.length;i++) {
	    if(u[i]==null || u[i].length()==0)
		u[i]=Units.find(h[i]);
	}
	this.logType=log_use;
	this.setIds(h);
	this.setUnits(u);
    }

    private DoubleArray drag (DoubleArray v) {

	final double rho=1.293;	// kg/m^3 air, standard density

	DoubleArray windDrag = v.pow(3).mult(0.5 * rho * this.env.c.Cd() * 
	    this.env.c.FA());

	DoubleArray rollingDrag = v.mult(this.env.c.rolling_drag() *
	    this.env.c.mass() * 9.80665);

	return windDrag.add(rollingDrag);
    }

    private DoubleArray toPSI(DoubleArray abs) {
	Column ambient = this.get("BaroPressure");
	if(ambient==null) return abs.add(-1013).div(mbar_per_psi);
	return abs.sub(ambient.data).div(mbar_per_psi);
    }

    private static DoubleArray toCelcius(DoubleArray f) {
	return f.add(-32).mult(5.0/9.0);
    }

    private static DoubleArray toFahrenheit(DoubleArray c) {
	return c.mult(9.0/5.0).add(32);
    }

    // given a list of id's, find the first that exists
    public Column get(Comparable<?> [] id) {
	for (Comparable<?> k : id) {
	    Column ret = null;
	    try { ret=_get(k);
	    } catch (NullPointerException e) {
	    }
	    if(ret!=null) return ret;
	}
	return null;
    }

    public Column get(Comparable<?> id) {
	try {
	    return _get(id);
	} catch (NullPointerException e) {
	    return null;
	}
    }

    private Column _get(Comparable<?> id) {
	Column c=null;
	if(id.equals("Sample")) {
	    double[] idx = new double[this.length()];
	    for (int i=0;i<this.length();i++)
		idx[i]=i;
	    DoubleArray a = new DoubleArray(idx);
	    c = new Column("Sample", "#", a);
	} else if(id.equals("TIME")) {
	    DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(this.time_ticks_per_sec));
	} else if(id.equals("RPM")) {
	    // smooth sampling quantum noise/jitter, RPM is an integer!
	    if (this.samples_per_sec>10) {
		DoubleArray a = super.get("RPM").data.smooth();
		c = new Column(id, "RPM", a);
	    }
	} else if(id.equals("RPM - raw")) {
	    c = new Column(id, "RPM", super.get("RPM").data);
	} else if(id.equals("Calc Load")) {
	    // g/sec to kg/hr
	    DoubleArray a = super.get("MassAirFlow").data.mult(3.6);
	    DoubleArray b = super.get("RPM").data.smooth();

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Calc Load Corrected")) {
	    // g/sec to kg/hr
	    DoubleArray a = this.get("Calc MAF").data.mult(3.6);
	    DoubleArray b = this.get("RPM").data;

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Calc MAF")) {
	    // mass in g/sec
	    DoubleArray a = super.get("MassAirFlow").data.
		mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("Calc MassAirFlow df/dt")) {
	    // mass in g/sec
	    DoubleArray maf = super.get("MassAirFlow").data;
	    DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "g/sec^s", maf.derivative(time).max(0));
	} else if(id.equals("Calc Turbo Flow")) {
	    DoubleArray a = this.get("Calc MAF").data;
	    c = new Column(id, "m^3/sec", a.div(1225*this.env.f.turbos()));
	} else if(id.equals("Calc Turbo Flow (lb/min)")) {
	    DoubleArray a = this.get("Calc MAF").data;
	    c = new Column(id, "lb/min", a.div(7.55*this.env.f.turbos()));
	} else if(id.equals("Calc Fuel Mass")) {	// based on te
	    final double gps_per_ccmin = 0.0114; // (grams/sec) per (cc/min)
	    final double gps = this.env.f.injector()*gps_per_ccmin;
	    final double cylinders = this.env.f.cylinders();
	    Column bank1 = this.get("EffFuelInjectorDutyCycle");
	    Column bank2 = this.get("EffFuelInjectorDutyCycleBank2");
	    DoubleArray duty = bank1.data;
	    /* average two duties for overall mass */
	    if (bank2!=null) duty = duty.add(bank2.data).div(2);
	    DoubleArray a = duty.mult(cylinders*gps/100);
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("TargetAFRDriverRequest (AFR)")) {
	    DoubleArray abs = super.get("TargetAFRDriverRequest").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("AirFuelRatioDesired (AFR)")) {
	    DoubleArray abs = super.get("AirFuelRatioDesired").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("AirFuelRatioCurrent (AFR)")) {
	    DoubleArray abs = super.get("AirFuelRatioCurrent").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("Calc AFR")) {
	    DoubleArray a = this.get("Calc MAF").data;
	    DoubleArray b = this.get("Calc Fuel Mass").data;
	    c = new Column(id, "AFR", a.div(b));
	} else if(id.equals("Calc lambda")) {
	    DoubleArray a = this.get("Calc AFR").data.div(14.7);
	    c = new Column(id, "lambda", a);
	} else if(id.equals("Calc lambda error")) {
	    DoubleArray a = super.get("AirFuelRatioDesired").data;
	    DoubleArray b = this.get("Calc lambda").data;
	    c = new Column(id, "%", a.div(b).mult(-1).add(1).mult(100).
		max(-25).min(25));

	} else if(id.equals("FuelInjectorDutyCycle")) {
	    DoubleArray a = super.get("FuelInjectorOnTime").data.	/* ti */
		div(60*1000);	/* assumes injector on time is in ms */

	    DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffFuelInjectorDutyCycle")) {		/* te */
	    DoubleArray a = super.get("EffFuelInjectionTime").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffFuelInjectorDutyCycleBank2")) {		/* te */
	    DoubleArray a = super.get("EffFuelInjectionTimeBank2").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
/*****************************************************************************/
	/* if log contains Engine torque */
	} else if(id.equals("Engine torque (ft-lb)")) {
	    DoubleArray tq = this.get("Engine torque").data;
	    DoubleArray value = tq.mult(0.737562149);	// nm to ft-lb
	    c = new Column(id, "ft-lb", value);
	} else if(id.equals("Engine HP")) {
	    DoubleArray tq = this.get("Engine torque (ft-lb)").data;
	    DoubleArray rpm = this.get("RPM").data;
	    DoubleArray value = tq.div(5252).mult(rpm);
	    c = new Column(id, "HP", value);
/*****************************************************************************/
	} else if(id.equals("Calc Velocity")) {
	    final double mph_per_mps = 2.23693629;
	    DoubleArray v = this.get("RPM").data;
	    c = new Column(id, "m/s", v.div(this.env.c.rpm_per_mph()).
		div(mph_per_mps));
	} else if(id.equals("Calc Acceleration (RPM/s)")) {
	    DoubleArray y = this.get("RPM").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x, this.MAW()).max(0));
	} else if(id.equals("Calc Acceleration - raw (RPM/s)")) {
	    DoubleArray y = this.get("RPM - raw").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x));
	} else if(id.equals("Calc Acceleration (m/s^2)")) {
	    DoubleArray y = this.get("Calc Velocity").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "m/s^2", y.derivative(x, this.MAW()).max(0));
	} else if(id.equals("Calc Acceleration (g)")) {
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    c = new Column(id, "g", a.div(9.80665));
/*****************************************************************************/
	} else if(id.equals("Calc WHP")) {
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray whp = a.mult(v).mult(this.env.c.mass()).
		add(this.drag(v));	// in watts

	    DoubleArray value = whp.mult(hp_per_watt);
	    String l = "HP";
	    if(this.env.sae.enabled()) {
		value = value.mult(this.env.sae.correction());
		l += " (SAE)";
	    }
	    c = new Column(id, l, value.movingAverage(this.MAW()));
	} else if(id.equals("Calc HP")) {
	    DoubleArray whp = this.get("Calc WHP").data;
	    DoubleArray value = whp.div((1-this.env.c.driveline_loss())).
		    add(this.env.c.static_loss());
	    String l = "HP";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Calc WTQ")) {
	    DoubleArray whp = this.get("Calc WHP").data;
	    DoubleArray rpm = this.get("RPM").data;
	    DoubleArray value = whp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Calc TQ")) {
	    DoubleArray hp = this.get("Calc HP").data;
	    DoubleArray rpm = this.get("RPM").data;
	    DoubleArray value = hp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	/* TODO */
	/*
	} else if(id.equals("Calc Drag")) {
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray drag = this.drag(v);	// in watts
	*/
	} else if(id.equals("IntakeAirTemperature")) {
	    c = super.get(id);
	    if (c.getUnits().matches(".*C$"))
		c = new Column(id, "\u00B0 F", ECUxDataset.toFahrenheit(c.data));
	} else if(id.equals("IntakeAirTemperature (C)")) {
	    c = super.get("IntakeAirTemperature");
	    if (c.getUnits().matches(".*F$"))
		c = new Column(id, "\u00B0 C", ECUxDataset.toCelcius(c.data));
	} else if(id.equals("BoostPressureDesired (PSI)")) {
	    DoubleArray abs = super.get("BoostPressureDesired").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
	} else if(id.equals("BoostPressureActual (PSI)")) {
	    DoubleArray abs = super.get("BoostPressureActual").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
	} else if(id.equals("Zeitronix Boost (PSI)")) {
	    DoubleArray boost = super.get("Zeitronix Boost").data;
	    c = new Column(id, "PSI", boost.movingAverage(this.filter.ZeitMAW()));
	} else if(id.equals("Zeitronix Boost")) {
	    DoubleArray boost = this.get("Zeitronix Boost (PSI)").data;
	    c = new Column(id, "mBar", boost.mult(mbar_per_psi).add(1013));
	} else if(id.equals("Zeitronix AFR (lambda)")) {
	    DoubleArray abs = super.get("Zeitronix AFR").data;
	    c = new Column(id, "lambda", abs.div(14.7));
	} else if(id.equals("Zeitronix Lambda (AFR)")) {
	    DoubleArray abs = super.get("Zeitronix Lambda").data;
	    c = new Column(id, "AFR", abs.mult(14.7));
	} else if(id.equals("Calc BoostDesired PR")) {
	    DoubleArray act = super.get("BoostPressureDesired").data;
	    try {
		DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.div(ambient));
	    } catch (Exception e) {
		c = new Column(id, "PR", act.div(1013));
	    }

	} else if(id.equals("Calc BoostActual PR")) {
	    DoubleArray act = super.get("BoostPressureActual").data;
	    try {
		DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.div(ambient));
	    } catch (Exception e) {
		c = new Column(id, "PR", act.div(1013));
	    }
	} else if(id.equals("Calc SimBoostPressureDesired")) {
	    DoubleArray ambient = super.get("BaroPressure").data;
	    DoubleArray load = super.get("EngineLoadDesired").data;
	    c = new Column(id, "mBar", load.mult(10).add(300).max(ambient));
	} else if(id.equals("Calc Boost Spool Rate (RPM)")) {
	    DoubleArray abs = super.get("BoostPressureActual").data.smooth();
	    DoubleArray rpm = this.get("RPM").data;
	    c = new Column(id, "mBar/RPM", abs.derivative(rpm).max(0));
	} else if(id.equals("Calc Boost Spool Rate Zeit (RPM)")) {
	    DoubleArray boost = this.get("Zeitronix Boost").data.smooth();
	    DoubleArray rpm =
		this.get("RPM").data.movingAverage(this.filter.ZeitMAW()).smooth();
	    c = new Column(id, "mBar/RPM", boost.derivative(rpm).max(0));
	} else if(id.equals("Calc Boost Spool Rate (time)")) {
	    DoubleArray abs = this.get("BoostPressureActual (PSI)").data.smooth();
	    DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "PSI/sec", abs.derivative(time, this.MAW()).max(0));
	} else if(id.equals("Calc LDR error")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    c = new Column(id, "100mBar", set.sub(out).div(100));
	} else if(id.equals("Calc LDR de/dt")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    DoubleArray t = this.get("TIME").data;
	    DoubleArray o = set.sub(out).derivative(t,this.MAW());
	    c = new Column(id,"100mBar",o.mult(env.pid.time_constant).div(100));
	} else if(id.equals("Calc LDR I e dt")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    DoubleArray t = this.get("TIME").data;
	    DoubleArray o = set.sub(out).
		integral(t,0,env.pid.I_limit/env.pid.I*100);
	    c = new Column(id,"100mBar",o.div(env.pid.time_constant).div(100));
	} else if(id.equals("Calc LDR PID")) {
	    final DoubleArray.TransferFunction fP =
		new DoubleArray.TransferFunction() {
		    public final double f(double x, double y) {
			if(Math.abs(x)<env.pid.P_deadband/100) return 0;
			return x*env.pid.P;
		    }
	    };
	    final DoubleArray.TransferFunction fD =
		new DoubleArray.TransferFunction() {
		    public final double f(double x, double y) {
			y=Math.abs(y);
			if(y<3) return x*env.pid.D[0];
			if(y<5) return x*env.pid.D[1];
			if(y<7) return x*env.pid.D[2];
			return x*env.pid.D[3];
		    }
	    };
	    DoubleArray E = this.get("Calc LDR error").data;
	    DoubleArray P = E.func(fP);
	    DoubleArray I = this.get("Calc LDR I e dt").data.mult(env.pid.I);
	    DoubleArray D = this.get("Calc LDR de/dt").data.func(fD,E);
	    c = new Column(id, "%", P.add(I).add(D).max(0).min(95));
/*****************************************************************************/
	} else if(id.equals("IgnitionTimingAngleOverallDesired")) {
	    DoubleArray averetard = null;
	    int count=0;
	    for(int i=0;i<8;i++) {
		Column retard = this.get("IgnitionRetardCyl" + i);
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
	} else if(id.equals("Calc LoadSpecified correction")) {
	    DoubleArray cs = super.get("EngineLoadCorrectedSpecified").data;
	    DoubleArray s = super.get("EngineLoadSpecified").data;
	    c = new Column(id, "K", cs.div(s));
/*****************************************************************************/
	}
	if(c!=null) {
	    this.getColumns().add(c);
	    return c;
	}
	return super.get(id);
    }

    protected boolean dataValid(int i) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	ArrayList<String> reasons = new ArrayList<String>();

	if(filter.gear()>=0 && gear!=null && Math.round(gear.data.get(i)) != filter.gear()) {
	    reasons.add("gear " + Math.round(gear.data.get(i)) +
		    "!=" + filter.gear());
	    ret=false;
	}
	if(pedal!=null && pedal.data.get(i)<filter.minPedal()) {
	    reasons.add("pedal " + pedal.data.get(i) +
		    "<" + filter.minPedal());
	    ret=false;
	}
	if(throttle!=null && throttle.data.get(i)<filter.minThrottle()) {
	    reasons.add("throttle " + throttle.data.get(i) +
		    "<" + filter.minThrottle());
	    ret=false;
	}
	if(zboost!=null && zboost.data.get(i)<0) {
	    reasons.add("zboost " + zboost.data.get(i) +
		    "<0");
	    ret=false;
	}
	if(rpm!=null) {
	    if(rpm.data.get(i)<filter.minRPM()) {
		reasons.add("rpm " + rpm.data.get(i) +
		    "<" + filter.minRPM());
		ret=false;
	    }
	    if(rpm.data.get(i)>filter.maxRPM()) {
		reasons.add("rpm " + rpm.data.get(i) +
		    ">" + filter.maxRPM());
		ret=false;
	    }
	    if(i>0 && rpm.data.size()>i+2 &&
		rpm.data.get(i-1)-rpm.data.get(i+1)>filter.monotonicRPMfuzz()) {
		reasons.add("rpm delta " +
		    rpm.data.get(i-1) + "-" + rpm.data.get(i+1) + ">" +
		    filter.monotonicRPMfuzz());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    // System.out.println(reasons);
	}

	return ret;
    }

    protected boolean rangeValid(Range r) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	ArrayList<String> reasons = new ArrayList<String>();

	if(r.size()<filter.minPoints()) {
	    reasons.add("points " + r.size() + "<" +
		filter.minPoints());
	    ret=false;
	}
	if(rpm!=null) {
	    if(rpm.data.get(r.end)<rpm.data.get(r.start)+filter.minRPMRange()) {
		reasons.add("RPM Range " + rpm.data.get(r.end) +
		    "<" + rpm.data.get(r.start) + "+" +filter.minRPMRange());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    // System.out.println(reasons);
	}

	return ret;
    }

    public void buildRanges() {
	super.buildRanges();
        ArrayList<Dataset.Range> ranges = this.getRanges();
	this.splines = new CubicSpline[ranges.size()];
        for(int i=0;i<ranges.size();i++) {
	    splines[i] = null;
            Dataset.Range r=ranges.get(i);
            try {
                double [] rpm = this.getData("RPM", r);
                double [] time = this.getData("TIME", r);
		if(time.length>0 && time.length==rpm.length)
		    splines[i] = new CubicSpline(rpm, time);
		else
		    JOptionPane.showMessageDialog(null,
			"length problem " + time.length + ":" + rpm.length);
            } catch (Exception e) {}
        }
    }

    public double calcFATS(int run, int RPMStart, int RPMEnd) throws Exception {
	    ArrayList<Dataset.Range> ranges = this.getRanges();
	    if(run<0 || run>=ranges.size())
		throw new Exception("no run found");

	    if(splines[run]==null)
		throw new Exception("run interpolation failed");

	    Dataset.Range r=ranges.get(run);
	    double [] rpm = this.getData("RPM", r);
	    
	    if(rpm[0]-100>RPMStart || rpm[rpm.length-1]+100<RPMEnd)
		throw new Exception("run " + rpm[0] + "-" + rpm[rpm.length-1] +
			" not long enough");

	    double et = splines[run].interpolate(RPMEnd) -
		splines[run].interpolate(RPMStart);
	    if(et<=0)
		throw new Exception("don't cross the streams");

	    return et;
    }

    public double[] calcFATS(int RPMStart, int RPMEnd) {
        ArrayList<Dataset.Range> ranges = this.getRanges();
	double [] out = new double[ranges.size()];
        for(int i=0;i<ranges.size();i++) {
	    try {
		out[i]=calcFATS(i, RPMStart, RPMEnd);
	    } catch (Exception e) {
	    }
	}
	return out;
    }

    public Filter getFilter() { return this.filter; }
    // public void setFilter(Filter f) { this.filter=f; }
    public Env getEnv() { return this.env; }
    //public void setEnv(Env e) { this.env=e; }
}
