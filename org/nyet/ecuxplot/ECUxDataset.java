package org.nyet.ecuxplot;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.JOptionPane;

import au.com.bytecode.opencsv.CSVReader;
import flanagan.interpolation.CubicSpline;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;
import org.nyet.util.Files;

public class ECUxDataset extends Dataset {
    private Column rpm, pedal, throttle, gear, boost;
    private Env env;
    private Filter filter;
    private final double hp_per_watt = 0.00134102209;
    private final double mbar_per_psi = 68.9475729;
    private double ticks_per_sec;
    public double samples_per_sec=0;
    private CubicSpline [] splines;	// rpm vs time splines

    public ECUxDataset(String filename, Env env, Filter filter)
	    throws Exception {
	super(filename);

	this.env = env;
	this.filter = filter;

	this.rpm = get("RPM");
	this.pedal = get(new String []
		{"AcceleratorPedalPosition", "Zeitronix TPS", "Accelerator position"});
	this.throttle = get(new String []
		{"ThrottlePlateAngle", "Throttle Angle", "Throttle Valve Angle"});
	this.gear = get("Gear");
	// look for zeitronix boost for filtering
	this.boost = get("Zeitronix Boost");
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
    public int logType;
    private int detect(String [] h) {
	int ret = LOG_UNKNOWN;
	h[0]=h[0].trim();
	if(h[0].matches("^.*day$")) return LOG_VCDS;
	if(h[0].matches("^Filename:.*")) {
	    if(Files.extension(h[0]).equals("zto") ||
	       Files.extension(h[0]).equals("zdl") ||
		h[0].matches(".*<unnamed file>$"))
	    return LOG_ZEITRONIX;
	}
	if(h[0].matches("^TIME$")) return LOG_ECUX;
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
	String [] h = reader.readNext();

	if (h==null)
	    throw new Exception(this.getFileId() + ": read failed");

	String [] u = ParseUnits(h);

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

	this.ticks_per_sec = 1;
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
		    if(h[i].matches("^Mass Air Flow$")) h[i]="MassAirFlow";
		    if(h[i].matches("^Mass Flow$")) h[i]="MassAirFlow";
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
		    } while (h!=null && h.length<=1);
		}
		// otherwise, the user gave us a zeit log with no header,
		// but asked us to treat it like a zeit log.

		// reparse units
		u = ParseUnits(h);
		for(int i=0;i<h.length;i++) {
		    if (verbose)
			System.out.println("in : " + h[i] + " [" + u[i] + "]");
		    if(h[i].equals("Boost")) h[i]="Zeitronix Boost";
		    if(h[i].equals("TPS")) h[i]="Zeitronix TPS";
		    if(h[i].equals("AFR")) h[i]="Zeitronix AFR";
		    if(h[i].equals("Lambda")) h[i]="Zeitronix Lambda";
		    // time is broken
		    if(h[i].equals("Time")) h[i]=null;
		    if (verbose)
			System.out.println("out: " + h[i] + " [" + u[i] + "]");
		}
		break;
	    case LOG_ECUX:
		this.ticks_per_sec = 1000;
		break;
	    default:
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
	DoubleArray ambient = this.get("BaroPressure").data;
	if(ambient==null) return abs.add(-1013).div(mbar_per_psi);
	return abs.sub(ambient).div(mbar_per_psi);
    }

    // given a list of id's, find the first that exists
    public Column get(Comparable [] id) {
	for (Comparable k : id) {
	    Column ret = null;
	    try { ret=_get(k);
	    } catch (NullPointerException e) {
	    }
	    if(ret!=null) return ret;
	}
	return null;
    }

    public Column get(Comparable id) {
	try {
	    return _get(id);
	} catch (NullPointerException e) {
	    return null;
	}
    }

    private Column _get(Comparable id) {
	Column c=null;
	if(id.equals("Sample")) {
	    double[] idx = new double[this.length()];
	    for (int i=0;i<this.length();i++)
		idx[i]=i;
	    DoubleArray a = new DoubleArray(idx);
	    c = new Column("Sample", "#", a);
	} else if(id.equals("TIME")) {
	    DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(this.ticks_per_sec));
	} else if(id.equals("Calc Load")) {
	    // g/sec to kg/hr
	    DoubleArray a = super.get("MassAirFlow").data.mult(3.6);
	    DoubleArray b = super.get("RPM").data.smooth();

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Calc Load Corrected")) {
	    // g/sec to kg/hr
	    DoubleArray a = this.get("Calc MAF").data.mult(3.6);
	    DoubleArray b = super.get("RPM").data.smooth();

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Calc MAF")) {
	    // mass in g/sec
	    DoubleArray a = super.get("MassAirFlow").data.
		mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("Calc Turbo Flow")) {
	    DoubleArray a = this.get("Calc MAF").data;
	    c = new Column(id, "m^3/sec", a.div(1225*this.env.f.turbos()));
	} else if(id.equals("Calc Turbo Flow (lb/min)")) {
	    DoubleArray a = this.get("Calc MAF").data;
	    c = new Column(id, "lb/min", a.div(7.55*this.env.f.turbos()));
	} else if(id.equals("Calc Fuel Mass")) {
	    final double gps_per_ccmin = 0.0114; // (grams/sec) per (cc/min)
	    final double gps = this.env.f.injector()*gps_per_ccmin;
	    final double cylinders = this.env.f.cylinders();
	    DoubleArray a = this.get("FuelInjectorDutyCycle").data.mult(cylinders*gps/100);
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("AirFuelRatioDesired (AFR)")) {
	    DoubleArray abs = super.get("AirFuelRatioDesired").data;
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
	    DoubleArray a = super.get("FuelInjectorOnTime").data.
		div(60*this.ticks_per_sec);

	    DoubleArray b = super.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
/*****************************************************************************/
	/* if log contains Engine torque */
	} else if(id.equals("Engine torque (ft-lb)")) {
	    DoubleArray tq = this.get("Engine torque").data;
	    DoubleArray value = tq.mult(0.737562149);	// nm to ft-lb
	    c = new Column(id, "ft-lb", value);
	} else if(id.equals("Engine HP")) {
	    DoubleArray tq = this.get("Engine torque (ft-lb)").data;
	    DoubleArray rpm = super.get("RPM").data;
	    DoubleArray value = tq.div(5252).mult(rpm);
	    c = new Column(id, "HP", value);
/*****************************************************************************/
	} else if(id.equals("Calc Velocity")) {
	    final double mph_per_mps = 2.23693629;
	    DoubleArray v = super.get("RPM").data;
	    c = new Column(id, "m/s", v.div(this.env.c.rpm_per_mph()).
		div(mph_per_mps));
	} else if(id.equals("Calc Acceleration (RPM/s)")) {
	    DoubleArray y = super.get("RPM").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x, this.MAW()).max(0));
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
	    DoubleArray rpm = super.get("RPM").data;
	    DoubleArray value = whp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Calc TQ")) {
	    DoubleArray hp = this.get("Calc HP").data;
	    DoubleArray rpm = super.get("RPM").data;
	    DoubleArray value = hp.mult(5252).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Calc Drag")) {
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray drag = this.drag(v);	// in watts
	    c = new Column(id, "HP", drag.mult(hp_per_watt));
/*****************************************************************************/
/*
	} else if(id.equals("RPM (MA)")) {
	    DoubleArray rpm = super.get("RPM").data;
	    c = new Column(id, "RPM", rpm.movingAverage(this.filter.ZeitMAW()));
*/
/*****************************************************************************/
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
	} else if(id.equals("Calc BoostDesired PR")) {
	    DoubleArray act = super.get("BoostPressureDesired").data;
	    DoubleArray ambient = super.get("BaroPressure").data;
	    c = new Column(id, "PR", act.div(ambient));
	} else if(id.equals("Calc BoostActual PR")) {
	    DoubleArray act = super.get("BoostPressureActual").data;
	    DoubleArray ambient = super.get("BaroPressure").data;
	    c = new Column(id, "PR", act.div(ambient));
	} else if(id.equals("Calc SimBoostPressureDesired")) {
	    DoubleArray ambient = super.get("BaroPressure").data;
	    DoubleArray load = super.get("EngineLoadDesired").data;
	    c = new Column(id, "mBar", load.mult(10).add(300).max(ambient));
	} else if(id.equals("Calc Boost Spool Rate (RPM)")) {
	    DoubleArray abs = super.get("BoostPressureActual").data.smooth();
	    DoubleArray rpm = super.get("RPM").data.smooth();
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
	    for(int i=0;i<6;i++) {
		Column retard = this.get("IgnitionRetardCyl" + i);
		if(retard!=null) {
		    if(averetard==null) averetard = retard.data;
		    else averetard = averetard.add(retard.data);
		    count++;
		}
	    }
	    DoubleArray out = this.get("IgnitionTimingAngleOverall").data;
	    if(count>0) {
		out = out.add(averetard.div(count));
	    }
	    c = new Column(id, "degrees", out);
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
	if(this.filter==null) return true;
	if(!this.filter.enabled()) return true;
	if(gear!=null && Math.round(gear.data.get(i)) != filter.gear()) {
	    this.lastFilterReason = "gear " + Math.round(gear.data.get(i)) +
		    "!=" + filter.gear();
	    return false;
	}
	if(pedal!=null && pedal.data.get(i)<filter.minPedal()) {
	    this.lastFilterReason = "pedal " + pedal.data.get(i) +
		    "<" + filter.minPedal();
	    return false;
	}
	if(throttle!=null && throttle.data.get(i)<filter.minThrottle()) {
	    this.lastFilterReason = "throttle " + throttle.data.get(i) +
		    "<" + filter.minThrottle();
	    return false;
	}
	if(boost!=null && boost.data.get(i)<0) {
	    this.lastFilterReason = "boost " + boost.data.get(i) +
		    "<0";
	    return false;
	}
	if(rpm!=null) {
	    if(rpm.data.get(i)<filter.minRPM()) {
		this.lastFilterReason = "rpm " + rpm.data.get(i) +
		    "<" + filter.minRPM();
		return false;
	    }
	    if(rpm.data.get(i)>filter.maxRPM()) {
		this.lastFilterReason = "rpm " + rpm.data.get(i) +
		    ">" + filter.maxRPM();
		return false;
	    }
	    if(rpm.data.size()>i+3 &&
		rpm.data.get(i)-rpm.data.get(i+2)>filter.monotonicRPMfuzz()) {
		this.lastFilterReason = "rpm delta " + 
		    (rpm.data.get(i)-rpm.data.get(i+2)) + ">" +
		    filter.monotonicRPMfuzz();
		return false;
	    }
	}
	return true;
    }

    protected boolean rangeValid(Range r) {
	if(this.filter==null) return true;
	if(!this.filter.enabled()) return true;
	if(r.size()<filter.minPoints()) {
	    this.lastFilterReason = "points " + r.size() + "<" +
		filter.minPoints();
	    return false;
	}
	if(rpm!=null) {
	    if(rpm.data.get(r.end)<rpm.data.get(r.start)+filter.minRPMRange()) {
		this.lastFilterReason = "RPM Range " + rpm.data.get(r.end) +
		    "<" + rpm.data.get(r.start) + "+" +filter.minRPMRange();
		return false;
	    }
	}
	return true;
    }

    public void buildRanges() {
	super.buildRanges();
        java.util.ArrayList<Dataset.Range> ranges = this.getRanges();
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
	    java.util.ArrayList<Dataset.Range> ranges = this.getRanges();
	    if(run<0 || run>=ranges.size())
		throw new Exception("no run found");

	    if(splines[run]==null)
		throw new Exception("run interpolation failed");

	    Dataset.Range r=ranges.get(run);
	    double [] rpm = this.getData("RPM", r);
	    double [] time = this.getData("TIME", r);
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
        java.util.ArrayList<Dataset.Range> ranges = this.getRanges();
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
