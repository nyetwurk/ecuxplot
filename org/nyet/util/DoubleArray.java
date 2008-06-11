package org.nyet.util;

import vec_math.SavitzkyGolaySmoothing;
import ru.sscc.spline.Spline;
import ru.sscc.spline.polynomial.POddSplineCreator;
import ru.sscc.util.CalculatingException;

public class DoubleArray
{
    private int sp = 0; // "stack pointer" to keep track of position in the array
    private double[] array;
    private int growthSize;

    public interface TransferFunction {
	public double f(double x, double y);
    }

    public DoubleArray()
    {
        this( 1024 );
    }

    public DoubleArray( double[] a)
    {
	this(a.length);
	System.arraycopy( a, 0, this.array, 0, a.length );
	this.sp=a.length;
    }

    public DoubleArray( double[] a, int initialSize)
    {
	this(initialSize);
	System.arraycopy( a, 0, this.array, 0, a.length );
	this.sp=initialSize;
    }

    public DoubleArray( int initialSize )
    {
        this( initialSize, initialSize );
    }

    public DoubleArray( int initialSize, int growthSize )
    {
        this.growthSize = growthSize;
        array = new double[ initialSize ];
    }

    public void append( double d )
    {
        if( sp >= array.length ) // time to grow!
        {
            double[] tmpArray = new double[ array.length + growthSize ];
            System.arraycopy( array, 0, tmpArray, 0, array.length );
            array = tmpArray;
        }
        array[ sp ] = d;
        sp += 1;
    }

    public int size() { return sp; }

    public double[] toArray()
    {
        double[] trimmedArray = new double[ sp ];
        System.arraycopy( array, 0, trimmedArray, 0, trimmedArray.length );
        return trimmedArray;
    }

    public double[] toArray(int start, int end)	// end is inclusive
    {
        double[] trimmedArray = new double[ end-start+1 ];
	double[] out = this.array;
	System.arraycopy( out, start, trimmedArray, 0, trimmedArray.length );
        return trimmedArray;
    }

    public double[] toArray(int start)
    {
	return this.toArray(start, this.sp-1);
    }

    public double get(int i) {
	return i<this.sp?array[i]:0;
    }

    public double[] _func(TransferFunction f, double d) {
        double[] out = new double[ sp ];
	for(int i=0;i<this.sp;i++) {
	    out[i]=f.f(this.array[i], d);
	}
	return out;
    }
    public DoubleArray func(TransferFunction f) {
	return new DoubleArray(this._func(f, Double.NaN));
    }
    public DoubleArray func(TransferFunction f, double x) {
	return new DoubleArray(this._func(f, x));
    }

    public double[] _func(TransferFunction f, double[] d) {
        double[] out = new double[ sp ];
	for(int i=0;i<this.sp;i++) {
	    out[i]=f.f(this.array[i], d[i]);
	}
	return out;
    }
    public DoubleArray func(TransferFunction f, double[] x) {
	return new DoubleArray(this._func(f, x));
    }
    public DoubleArray func(TransferFunction f, DoubleArray x) {
	return new DoubleArray(this._func(f, x.toArray()));
    }

    private static TransferFunction fAdd = new TransferFunction() {
	public final double f(double x, double y) {
	    return x+y;
    }};
    public DoubleArray add(double d) { return func(fAdd, d); }
    public DoubleArray add(DoubleArray d) { return func(fAdd, d); }

    private static TransferFunction fSub = new TransferFunction() {
	public final double f(double x, double y) {
	    return x-y;
    }};
    public DoubleArray sub(double d) { return func(fSub, d); }
    public DoubleArray sub(DoubleArray d) { return func(fSub, d); }

    private static TransferFunction fMult = new TransferFunction() {
	public final double f(double x, double y) {
	    return x*y;
    }};
    public DoubleArray mult(double d) { return func(fMult, d); }
    public DoubleArray mult(DoubleArray d) { return func(fMult, d.toArray()); }

    private static TransferFunction fDiv = new TransferFunction() {
	public final double f(double x, double y) {
	    return x/y;
    }};
    public DoubleArray div(double d) { return func(fDiv, d); }
    public DoubleArray div(DoubleArray d) { return func(fDiv, d); }

    private static TransferFunction fPow = new TransferFunction() {
	public final double f(double x, double y) {
	    return Math.pow(x,y);
    }};
    public DoubleArray pow(double d) {
	return new DoubleArray(this._func(fPow, d));
    }

    public double[] _min(double d) {
        double[] out = new double[ sp ];
	for(int i=0;i<this.sp;i++) {
	    out[i]=Math.min(this.array[i],d);
	}
	return out;
    }
    public DoubleArray min(double d) {
	return new DoubleArray(this._min(d));
    }

    public double[] _max(double d) {
        double[] out = new double[ sp ];
	for(int i=0;i<this.sp;i++) {
	    out[i]=Math.max(this.array[i],d);
	}
	return out;
    }
    public DoubleArray max(double d) {
	return new DoubleArray(this._max(d));
    }

    public double[] _derivative(double[] d, int window) {
        double[] out = new double[ sp ];
        if(sp==1 || d.length<2 || d.length!=sp) {
            System.out.println("sp: " + sp +", d.len: " + d.length +
            ", sp=" + sp);
        }
        for(int i=0;i<this.sp;i++) {
            int i0=Math.max(i-1, 0), i1=Math.min(i+1,this.sp-1);
            out[i]=(this.get(i1)-this.get(i0))/(d[i1]-d[i0]);
        }
	if(window>0) {
	    MovingAverageSmoothing s = new MovingAverageSmoothing(window);
	    return s.smoothAll(out);
	} else {
	    return out;
	}
    }
    public DoubleArray derivative(DoubleArray d) {
	return new DoubleArray(this._derivative(d.toArray(), 0));
    }
    public DoubleArray derivative(DoubleArray d, boolean smooth) {
	return new DoubleArray(this._derivative(d.toArray(), smooth?11:0));
    }

    public double[] _integral(double[] d, double min, double max) {
        double[] out = new double[ sp ];
        if(sp==1 || d.length<2 || d.length!=sp) {
            System.out.println("sp: " + sp +", d.len: " + d.length +
            ", sp=" + sp);
        }
        for(int i=0;i<this.sp;i++) {
            int i0 = Math.max(i-1, 0);
	    out[i] = out[i0]+this.get(i)*(d[i]-d[i0]);
	    if(out[i]<min) out[i]=min;
	    else if(out[i]>max) out[i]=max;
        }
	return out;
    }
    public DoubleArray integral(DoubleArray d) {
	return new DoubleArray(this._integral(d.toArray(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }
    public DoubleArray integral(DoubleArray d, double min) {
	return new DoubleArray(this._integral(d.toArray(), min, Double.POSITIVE_INFINITY));
    }
    public DoubleArray integral(DoubleArray d, double min, double max) {
	return new DoubleArray(this._integral(d.toArray(), min, max));
    }

    public DoubleArray smooth() {
	SavitzkyGolaySmoothing s = new SavitzkyGolaySmoothing(5,5);
	return new DoubleArray(s.smoothAll(this.toArray()));
    }

    public DoubleArray movingAverage(int window) {
	MovingAverageSmoothing s = new MovingAverageSmoothing(window);
	return new DoubleArray(s.smoothAll(this.toArray()));
    }

    public Spline spline(int order, double[] mesh) {
	try {
	    return POddSplineCreator.createSpline(order, mesh, this.toArray());
	} catch (CalculatingException e){
	    System.out.println(e);
	}
	return null;
    }
    public Spline spline(double[] mesh) { return this.spline(2, mesh); }
    public Spline spline(int order) {
	try {
	    return POddSplineCreator.createSpline(order, 0, 1, this.sp, this.toArray());
	} catch (CalculatingException e){
	    System.out.println(e);
	}
	return null;
    }
    public Spline spline() throws CalculatingException { return this.spline(2); }

    public double[] _splineDerivative(double[] d) {
        double[] out = new double[ sp ];
	Spline spl = this.spline(d);
        for(int i=0;i<this.sp;i++) {
	    out[i]=spl.value(d[i], 1);
	}
	return out;
    }
}
