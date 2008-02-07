package vec_math;

/**
   Base class for linear digital smoothing. Linear in this sense means that
   it uses <i>linear</i> combination of the input values and output values.
   The most general formular for linear filtering runs as
   <pre>
             y<sub>n</sub>=&Sigma;<sup>M</sup>c<sub>k</sub>x<sub>n+k</sub>+&Sigma;<sup>N</sup>d<sub>j</sub>y<sub>n+j</sub>
   </pre>
   <p>
   Both summations starts at some (normally negative) index <i>K,J</i>, which
   means that in filtering, data from the 'past' and 'future' can be taken
   into account.
   The <i>M+1 c<sub>k</sub></i> and the <i>N d<sub>k</sub></i>
   coefficients are fixed and define the filter response. Finite impulse
   response filter have an <i>N</i> of zero, while <i>N&ne;0</i>
   characterizes infinite (recursive) impulse response filters. Recursive
   filters normally have a superior performance to FIR filters, but can suffer
   the problem of instability. This makes them more prone to runaway behaviour
   for all-purpose filtering than FIR filters. Generally, use IIR filters,
   if you know precisely, which kind of signal you expect.<p>
   <b>Literature:</b><br>
   <i>Numerical Recipies for C</i>, p 558ff.
*/

public abstract class LinearSmoothing extends Object
{
    /** Used for nonrecursive filters. */
    public static final int FIR = 1;
    /** Used for recursive filters. */
    public static final int IIR = 2;

    protected int type;	// FIR or IIR

    protected double[] cn;	// M follows from the length of cn
    protected double[] dn;	// N follows from the length of Dn

    protected int nk;		// starting index in cn
    protected int nj;		// starting index in Dn

    protected LinearSmoothing ()
    {
	super();
	cn = null;
	dn = null;
    }

    /**
       Sets the type of this filter. Is generic to each filter, so no
       arguments can be passed to this method. The method
       should be protected, because we do not want anybody to manipulate
       it from outside.
    */
    protected abstract void setType();

    /**
       Returns the type of this filter. FIR filter return the value of
       <tt>FIR</tt>, while IIR filter return <tt>IIR</tt>. If the type is
       not set, an <tt>IllegalArgumentException</tt> is thrown.
    */
    public int getType()
    {
	if (type == FIR)
	    return FIR;
	if (type == IIR)
	    return IIR;
	throw new IllegalArgumentException("Not initialized");
    }

    /**
       Returns true, if smoothing can be done using this linear filter.
       This method checks if at least one of the two coefficient arrays
       {@link #cn} or {@link #dn} is not equal <tt>null</tt>.
    */
    public boolean isValid()
    {
	return (cn != null || dn != null);
    }

    /**
       Returns the offset of data into the past. If the measurements are
       not used (<tt>cn == null</tt>), zero is returned.
       @return A (negative) int, specifiying the look-back size.
    */
    public int getNk()
    {
	if (cn == null)
	    return 0;
	else
	    return nk;
    }

    /**
       Returns the offset of smoothed data into the past. If the smoothed
       measurements are
       not used (<tt>dn == null</tt>), zero is returned.
       @return A (negative) int, specifiying the look-back size.
    */
    public int getNj()
    {
	if (dn == null)
	    return 0;
	else
	    return nj;
    }

    /**
       Returns the number of coefficients used from the data side. Returns
       zero if <tt>cn == null</tt>.
       @return The length of the data coefficient array.
    */
    public int getM()
    {
	if (cn == null)
	    return 0;
	else
	    return cn.length;
    }

    /**
       Returns the number of coefficients used from the smoothened data side.
       Returns zero if <tt>dn == null</tt>.
       @return The length of the smoothened data coefficient array.
    */
    public int getN()
    {
	if (dn == null)
	    return 0;
	else
	    return dn.length;
    }


    /**
       Smoothes one data point at the given indices. The calculation of
       the indices is a little bit complicated, since a single index k
       (negative/positve) may translate to different indices in <tt>input</tt>
       and <tt>output</tt>.<br>
       The first index in
       <tt>input</tt> that is used is <tt>input[Nk+ix]</tt>, therefore
       <tt>ix</tt> must carry k plus the offset in the input array for
       <tt>k=0</tt>. In particular, if your data set is at the minimum size,
       so that
       you use <i>all</i> of your input data, <tt>ix = -Nk</tt>. The same
       is true for the output index <tt>ox</tt>.<p>
       The input and output data
       arrays must be large enough to contain all relevant smoothing
       parameters. In particular that means that <tt>ix+Nk</tt> is
       non-negative, <tt>ix+Nk+cn.length</tt> is a valid index in input,
       <tt>ox+Nj</tt> is also non-negative, and that
       <tt>ox+Nj+dn.length</tt> is a valid index in output. Otherwise,
       an <tt>IllegalArgumentException</tt> is thrown.
       @param input  The array of input point
       @param output The array of (already calculated) output points.
       @param ix The index where to evaluate the input points.
    */
    public double smoothAt(double[] input, double[] output, int ix, int ox)
    {
	// System.out.println("input="+input.length+", nk="+nk+", cn.length="+cn.length+", ix="+ix+", ox="+ox);
	if (cn != null &&
	    (input == null || ix+nk<0 || ix+nk+cn.length>input.length))
	    throw new IllegalArgumentException("Cannot filter from input "+ix+": "+
		(ix+nk) + ":"+ (ix+nk) +":"+ cn.length + ":" + input.length);
	if (dn != null &&
	    (output== null || ox+nj<0 || ox+nj+dn.length>output.length))
	    throw new IllegalArgumentException("Cannot filter to output "+ox+": "+
		(ox+nk) + ":"+ (ox+nk) +":"+ cn.length + ":" + output.length);

	int i1;
	double ret = 0.;

	if (cn != null) {
	    for (i1 = 0; i1 != cn.length; i1 ++)
		ret += cn[i1]*input[i1+nk+ix];
	}

	if (dn != null) {
	    for (i1 = 0; i1 != dn.length; i1 ++)
		ret += dn[i1]*output[i1+nj+ox];
	}

	return ret;
    }

    /**
       Smoothes an entire data set. In the (you want use this in normal cases)
       very rare case, where future smoothed output data is used to construct
       the output data at index <tt>i</tt> (<tt>-Nk > dn.length</tt>, an
       iterative process is spawned, starting with output that is identically
       to the input, but processed further, until stability is reached.
       Note that the data set of the input data must still be larger than the
       output data set.<p>
       <blink>This method was never checked!</blink>
       @param input The input points
       @param start The index of the starting smooth.
       @param end The index of the end element to smooth.
       @return A smoothed array of points, starting at index start.
    */
    public double[] smoothAll(double[] input, int start, int end)
    {
	// System.out.println("input="+input.length+", nk="+nk+", cn.length="+cn.length+", start="+start+", end="+end);
	if (cn != null && (start+nk < 0 || end+nk+cn.length > input.length))
	    throw new IllegalArgumentException("Cannot filter cn set: " +
		(start+nk) + ":"+ (end+nk) +":"+ cn.length + ":" + input.length);

	double[] output = null;

	if (type == IIR) {
	    output = new double[input.length];
	    if (dn != null && (start+nj < 0 || end+nj+dn.length > output.length))
		throw new IllegalArgumentException("Cannot filter dn set: " +
		    (start+nj) + ":"+ (end+nj) +":"+ dn.length + ":" + output.length);

	    System.arraycopy(input, 0, output, 0, input.length);
	}

	int i1;

	double[] ret = new double[end-start+1];
	double maxoff = 0.;


	do {
	    for (i1 = 0; i1 < ret.length; i1++)
		ret[i1] = smoothAt(input, output, start+i1, start+i1);
	    if (type != IIR || dn.length <= -nj)
		return ret;
	    for (i1 = 0; i1 < ret.length; i1++) {
		if (Math.abs(output[i1+start] - ret[i1])/
		    Math.max(1.,Math.abs(output[i1+start] + ret[i1])) > maxoff)
		    maxoff = Math.abs(output[i1+start] - ret[i1])/
			Math.max(1.,Math.abs(output[i1+start] + ret[i1]));
		output[i1+start] = ret[i1];
	    }
	} while (maxoff >= 1.e-5);

	return ret;
    }
    public double[] smoothAll(double[] input)
    {
	// System.out.println("input="+input.length+", nk="+nk);
	double[] a = smoothAll(input, -nk, input.length+nk-1);
	double[] output = input.clone();
	System.arraycopy(a, 0, output, -nk, a.length);

	return output;
    }
}










