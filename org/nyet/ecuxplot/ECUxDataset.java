package org.nyet.ecuxplot;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;

public class ECUxDataset extends Dataset {
    private Column rpm, pedal, gear;
    public Filter filter = new Filter();

    public class Filter {
	public boolean enabled = false;
	public boolean monotonicRPM = true;
	public double monotonicRPMfuzz = 100;
	public double minRPM = 2500;
	public double maxRPM = 8000;
	public double minPedal = 95;
	public int gear = 3;
    }

    public ECUxDataset(String filename) throws Exception {
	super(filename);
	this.rpm = find("RPM");
	this.pedal = find("AcceleratorPedalPosition");
	this.gear = find("Gear");
    }

    public Column find(Comparable id) {
	Column c=null;
	if(id.equals("TIME")) {
	    DoubleArray a = super.find("TIME").data;
	    c = new Column("TIME", "s", a.div(1000));	// msec to seconds
	} else if(id.equals("Calc Load")) {
	    DoubleArray a = super.find("MassAirFlow").data.mult(3.6); // g/sec to kg/hr
	    DoubleArray b = super.find("RPM").data;
	    c = new Column(id, "%", a.div(b).div(.001072)); // KUMSRL
	} else if(id.equals("FuelInjectorDutyCycle")) {
	    DoubleArray a = super.find("FuelInjectorOnTime").data.div(60*1000); // msec to minutes
	    DoubleArray b = super.find("RPM").data.div(2); // half cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("Calc Acceleration")) {
	    final double rpm_per_mph = 72.1;
	    final double mph_per_mps = 2.23693629;
	    DoubleArray y = super.find("RPM").data.div(rpm_per_mph).div(mph_per_mps);	// in mps
	    DoubleArray x = this.find("TIME").data;
	    c = new Column(id, "g", y.derivative(x,true).div(9.80665));
	}
	if(c!=null) {
	    this.getColumns().add(c);
	    return c;
	}
	return super.find(id);
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
}
