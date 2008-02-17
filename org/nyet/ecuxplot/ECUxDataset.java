package org.nyet.ecuxplot;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;

public class ECUxDataset extends Dataset {
    private Column rpm, pedal, gear;
    public Filter filter = new Filter();
    private String filename;
    private double mass = 1700;

    public class Filter {
	public boolean enabled = false;
	public boolean monotonicRPM = true;
	public double monotonicRPMfuzz = 100;
	public double minRPM = 2500;
	public double maxRPM = 8000;
	public double minPedal = 95;
	public int gear = 3;
	public int minimumPoints = 10;
    }

    public ECUxDataset(String filename) throws Exception {
	super(filename);
	this.rpm = get("RPM");
	this.pedal = get("AcceleratorPedalPosition");
	this.gear = get("Gear");
	this.filename = filename;
    }

    private DoubleArray drag () {
	DoubleArray v=this.get("Calc Velocity").data;

	final double Cd=0.31;
	final double FA=2.034;
	final double D=1.293;	// kg/m^3 air, standard density

	DoubleArray windDrag = v.pow(3).mult(0.5 * Cd * D * FA);

	final double rolling_drag=0.015;

	DoubleArray rollingDrag = v.mult(rolling_drag * this.mass * 9.80665);

	return windDrag.add(rollingDrag);
    }

    private DoubleArray toPSI(DoubleArray abs) {
	final double mbar_per_psi=68.9475729;
	DoubleArray ambient = this.get("BaroPressure").data;
	if(ambient==null) return abs.add(-1013).div(mbar_per_psi);
	return abs.add(ambient.mult(-1)).div(mbar_per_psi);
    }

    public Column get(Comparable id) {
	Column c=null;
	if(id.equals("TIME")) {
	    DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(1000));	// msec to seconds
	} else if(id.equals("Calc Load")) {
	    DoubleArray a = super.get("MassAirFlow").data.mult(3.6); // g/sec to kg/hr
	    DoubleArray b = super.get("RPM").data;
	    c = new Column(id, "%", a.div(b).div(.001072)); // KUMSRL
	} else if(id.equals("FuelInjectorDutyCycle")) {
	    DoubleArray a = super.get("FuelInjectorOnTime").data.div(60*1000); // msec to minutes
	    DoubleArray b = super.get("RPM").data.div(2); // half cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("Calc Velocity")) {
	    final double rpm_per_mph = 72.1;
	    final double mph_per_mps = 2.23693629;
	    DoubleArray v = super.get("RPM").data;
	    c = new Column(id, "m/s", v.div(rpm_per_mph).div(mph_per_mps));	// in m/s
	} else if(id.equals("Calc Acceleration (RPM/s)")) {
	    DoubleArray y = super.get("RPM").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x,true).max(0));
	} else if(id.equals("Calc Acceleration (m/s^2)")) {
	    DoubleArray y = this.get("Calc Velocity").data;
	    DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "m/s^2", y.derivative(x,true).max(0));
	} else if(id.equals("Calc Acceleration (g)")) {
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    c = new Column(id, "g", a.div(9.80665));
	} else if(id.equals("Calc WHP")) {
	    final double static_loss=0;
	    final double driveline_loss=.25;
	    final double hp_per_watt = 0.00134102209;
	    DoubleArray a = this.get("Calc Acceleration (m/s^2)").data;
	    DoubleArray v = this.get("Calc Velocity").data;
	    DoubleArray whp = a.mult(v).mult(this.mass).add(this.drag().smooth());	// in watts
	    c = new Column(id, "HP", whp.mult(hp_per_watt));
	} else if(id.equals("Calc WTQ")) {
	    DoubleArray whp = this.get("Calc WHP").data;
	    DoubleArray rpm = super.get("RPM").data;
	    c = new Column(id, "ft-lb", whp.mult(5252).div(rpm).smooth());
	} else if(id.equals("BoostPressureDesired (PSI)")) {
	    DoubleArray abs = this.get("BoostPressureDesired").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
	} else if(id.equals("BoostPressureActual (PSI)")) {
	    DoubleArray abs = this.get("BoostPressureActual").data;
	    c = new Column(id, "PSI", this.toPSI(abs));
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
	if(!this.filter.enabled) return true;
	if(gear!=null && Math.round(gear.data.get(i)) != filter.gear) return false;
	if(rpm.data.get(i)<filter.minRPM) return false;
	if(rpm.data.get(i)>filter.maxRPM) return false;
	if(pedal!=null && pedal.data.get(i)<filter.minPedal) return false;
	if(i<1 || rpm.data.get(i-1) - rpm.data.get(i) > filter.monotonicRPMfuzz) return false;
	return true;
    }

    protected boolean rangeValid(Range r) {
	return (r.size()>filter.minimumPoints);
    }

    public String getFilename() { return this.filename; }
}
