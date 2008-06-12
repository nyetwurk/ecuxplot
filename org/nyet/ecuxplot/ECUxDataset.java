package org.nyet.ecuxplot;

import au.com.bytecode.opencsv.CSVReader;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;

public class ECUxDataset extends Dataset {
    private Column rpm, pedal, throttle, gear;
    private String filename;
    private Env env;
    private Filter filter;
    private final double hp_per_watt = 0.00134102209;
    private final double mbar_per_psi = 68.9475729;
    private int ticks_per_sec;

    public ECUxDataset(String filename, Env env, Filter filter) throws Exception {
	super(filename);

	this.filename = filename;
	this.env = env;
	this.filter = filter;

	this.rpm = get("RPM");
	this.pedal = get("AcceleratorPedalPosition");
	this.throttle = get("ThrottlePlateAngle");
	if(this.throttle == null)
	    this.throttle = get("Throttle Valve Angle");
	this.gear = get("Gear");
    }

    public void ParseHeaders(CSVReader reader) throws Exception {
	String [] h = reader.readNext();
	String [] u = new String[h.length];
	int i;
	if(h[0].matches("^.*day$")) {
	    reader.readNext();	// ECU type
	    reader.readNext();	// blank
	    reader.readNext();	// group
	    h = reader.readNext(); // headers
	    String[] h2 = reader.readNext(); // headers
	    u = reader.readNext(); // units
	    for(i=0;i<h.length;i++) {
		h[i]=h[i].trim();
		h2[i]=h2[i].trim();
		u[i]=u[i].trim();
		if(h[i].length()>0 && h2[i].length()>0)  h[i]+=" ";
		h[i]+=h2[i];
		if(h[i].matches("^Engine Speed.*")) h[i]="RPM";
		if(h[i].length()==0) h[i]=u[i];
		// System.out.println(h[i] + " [" + u[i] + "]");
	    }
	    this.ticks_per_sec = 1;	// VAGCOM is in seconds
	} else {
	    this.ticks_per_sec = 1000;
	}
	for(i=0;i<h.length;i++) {
	    if(u[i]==null || u[i].length()==0)
		u[i]=Units.find(h[i]);
	}
	this.setUnits(u);
	this.setHeaders(h);
    }

    private DoubleArray drag (DoubleArray v) {

	final double rho=1.293;	// kg/m^3 air, standard density

	DoubleArray windDrag = v.pow(3).mult(0.5 * rho * this.env.c.Cd() *this.env.c.FA());
	DoubleArray rollingDrag = v.mult(this.env.c.rolling_drag() * this.env.c.mass() * 9.80665);

	return windDrag.add(rollingDrag);
    }

    private DoubleArray toPSI(DoubleArray abs) {
	DoubleArray ambient = this.get("BaroPressure").data;
	if(ambient==null) return abs.add(-1013).div(mbar_per_psi);
	return abs.sub(ambient).div(mbar_per_psi);
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
	if(id.equals("TIME")) {
	    DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(this.ticks_per_sec));
	} else if(id.equals("Calc Load")) {
	    DoubleArray a = super.get("MassAirFlow").data.mult(3.6); // g/sec to kg/hr
	    DoubleArray b = super.get("RPM").data;
	    c = new Column(id, "%", a.div(b).div(.001072)); // KUMSRL
	} else if(id.equals("Calc MAF")) {
	    double maf = this.env.f.MAF(); // diameter in mm
	    double maf_scale = ((maf*maf)/(73*73));
	    DoubleArray a = super.get("MassAirFlow").data.mult(maf_scale);	// g/sec
	    c = new Column(id, "g/sec", a.add(this.env.f.MAF_offset()));
	} else if(id.equals("Calc Fuel Mass")) {
	    final double gps_per_ccmin = 0.0114; // (grams/sec) per (cc/min)
	    final double gps = this.env.f.injector()*gps_per_ccmin;
	    final double cylinders = 6;
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
	    c = new Column(id, "%", a.div(b).mult(-1).add(1).mult(100).max(-25).min(25));
	} else if(id.equals("FuelInjectorDutyCycle")) {
	    DoubleArray a = super.get("FuelInjectorOnTime").data.div(60*this.ticks_per_sec);
	    DoubleArray b = super.get("RPM").data.smooth().div(2); // half cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("Calc Velocity")) {
	    final double mph_per_mps = 2.23693629;
	    DoubleArray v = super.get("RPM").data.smooth();
	    c = new Column(id, "m/s", v.div(this.env.c.rpm_per_mph()).div(mph_per_mps));	// in m/s
	} else if(id.equals("Calc Acceleration (RPM/s)")) {
	    DoubleArray y = super.get("RPM").data.smooth();
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x,true).max(0));
	} else if(id.equals("Calc Acceleration (m/s^2)")) {
	    DoubleArray y = this.get("Calc Velocity").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "m/s^2", y.derivative(x,true).max(0));
	} else if(id.equals("Calc Acceleration (g)")) {
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    c = new Column(id, "g", a.div(9.80665));
/*********************************************************************************/
	} else if(id.equals("Calc WHP")) {
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray whp = a.mult(v).mult(this.env.c.mass()).add(this.drag(v).smooth());	// in watts
	    c = new Column(id, "HP", whp.mult(hp_per_watt).movingAverage(this.filter.HPTQMAW()));
	} else if(id.equals("Calc Drag")) {
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray drag = this.drag(v).smooth();	// in watts
	    c = new Column(id, "HP", drag.mult(hp_per_watt));
	} else if(id.equals("Calc HP")) {
	    DoubleArray whp = this.get("Calc WHP").data;
	    c = new Column(id, "HP", whp.div((1-this.env.c.driveline_loss())).add(this.env.c.static_loss()));
	} else if(id.equals("Calc WTQ")) {
	    DoubleArray whp = this.get("Calc WHP").data;
	    DoubleArray rpm = super.get("RPM").data;
	    c = new Column(id, "ft-lb", whp.mult(5252).div(rpm).smooth());
	} else if(id.equals("Calc TQ")) {
	    DoubleArray hp = this.get("Calc HP").data;
	    DoubleArray rpm = super.get("RPM").data;
	    c = new Column(id, "ft-lb", hp.mult(5252).div(rpm).smooth());
/*********************************************************************************/
	} else if(id.equals("BoostPressureDesired (PSI)")) {
	    DoubleArray abs = super.get("BoostPressureDesired").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
	} else if(id.equals("BoostPressureActual (PSI)")) {
	    DoubleArray abs = super.get("BoostPressureActual").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
	} else if(id.equals("Calc LDR error")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    c = new Column(id, "100mBar", set.sub(out).div(100));
	} else if(id.equals("Calc LDR de/dt")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    DoubleArray t = this.get("TIME").data;
	    DoubleArray o = set.sub(out).derivative(t,true);
	    c = new Column(id,"100mBar",o.mult(env.pid.time_constant).div(100));
	} else if(id.equals("Calc LDR I e dt")) {
	    DoubleArray set = super.get("BoostPressureDesired").data;
	    DoubleArray out = super.get("BoostPressureActual").data;
	    DoubleArray t = this.get("TIME").data;
	    DoubleArray o = set.sub(out).integral(t,0,env.pid.I_limit/env.pid.I*100);
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
	}
	if(c!=null) {
	    this.getColumns().add(c);
	    return c;
	}
	return super.get(id);
    }

    protected boolean dataValid(int i) {
	if(!this.filter.enabled()) return true;
	if(gear!=null && Math.round(gear.data.get(i)) != filter.gear()) return false;
	if(pedal!=null && pedal.data.get(i)<filter.minPedal()) return false;
	if(throttle!=null && throttle.data.get(i)<filter.minThrottle()) return false;
	if(rpm!=null) {
	    if(rpm.data.get(i)<filter.minRPM()) return false;
	    if(rpm.data.get(i)>filter.maxRPM()) return false;
	    if(i<1 || rpm.data.get(i-1) - rpm.data.get(i) > filter.monotonicRPMfuzz()) return false;
	}
	return true;
    }

    protected boolean rangeValid(Range r) {
	return (r.size()>filter.minPoints());
    }

    public String getFilename() { return this.filename; }
    public Filter getFilter() { return this.filter; }
    public void setFilter(Filter f) { this.filter=f; }
    public Env getEnv() { return this.env; }
    public void setEnv(Env e) { this.env=e; }
}
