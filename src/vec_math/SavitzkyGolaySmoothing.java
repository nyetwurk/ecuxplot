package vec_math;

import java.io.*;
import java.util.StringTokenizer;

/**
   This class provides a Savitky-Golay Digital Smoothing filter. It is a
   sort of low-bandpass digital filter that preserves higher moments.
   For each point <i>f<sub>i</sub></i>, a least-square fit of a
   polynomial of given degree within all points in the smoothing window
   is generated. The smoothed value, <i>g<sub>i</sub></i> is the value
   of the smoothing polynom at position <i>i</i>. Typical degrees of
   the smoothing polynom are two or four. The smoothing window must not
   be symmetric around <i>i</i>, in fact it can even extend to only one
   side of <i>i</i>.<p>
   <b>Literature:</b><br>
   Numerical Recipies in C, p. 650ff
*/

public class SavitzkyGolaySmoothing extends LinearSmoothing
{
    private static final double M2L2R2[]={-0.086, 0.343, 0.486, 0.343, -0.086};
    private static final double M2L3R1[]={-0.143, 0.171, 0.343, 0.371, 0.257};
    private static final double M2L4R0[]={0.086, -0.143,-0.086, 0.257, 0.886};
    private static final double M2L5R5[]={-0.084, 0.021, 0.103, 0.161, 0.196,
					  0.207,
					  0.196, 0.161, 0.103,0.021, -0.084};
    private static final double M4L6R0D1[]={0.26876, -0.85585, 0.49695,0.8628,
					    -0.54272, -1.82409, 1.5942};
    private static final double M4L4R4[]={0.035, -0.128, 0.070, 0.315, 0.417,
					  0.315,  0.070,-0.128, 0.035};
    private static final double M4L5R5[]={0.042, -0.105, -0.023, 0.140, 0.280,
					  0.333,
					  0.280,  0.140, -0.023,-0.105, 0.042};

    protected int degree;
    protected int derivative;

    /**
       Defaults to M2L4R0.
    */
    public SavitzkyGolaySmoothing()
    {
	this (2, 4, 0, 0);
    }

    /**
       Defaults to a polynomial degree of 2.
    */
    public SavitzkyGolaySmoothing(int nl, int nr)
    {
	this(2, nl, nr, 0);
    }

    public SavitzkyGolaySmoothing(int deg, int nl, int nr, int der)
    {
	degree     = deg;
	derivative = der;
	nk         = -nl;
	nj         = 0;

	// in the default implementation, only the 'standard' coef are allowed

	boolean allowed = false;

	if (der != 0) {
	    if (der == 1 && nl == 6 && nr == 0 && deg == 4) {
		allowed = true;
		cn = M4L6R0D1;
	    }
	    else throw new IllegalArgumentException("Not implemented!");
	}

	if (deg == 2 && nl == 2 && nr == 2) {
	    allowed = true;
	    cn    = M2L2R2;
	}

	if (deg == 2 && nl == 3 && nr == 1) {
	    allowed = true;
	    cn    = M2L3R1;
	}

	if (deg == 2 && nl == 4 && nr == 0) {
	    allowed = true;
	    cn    = M2L4R0;
	}

	if (deg == 2 && nl == 5 && nr == 5) {
	    allowed = true;
	    cn    = M2L5R5;
	}

	if (deg == 4 && nl == 4 && nr == 4) {
	    allowed = true;
	    cn    = M4L4R4;
	}

	if (deg == 4 && nl == 5 && nr == 5) {
	    allowed = true;
	    cn    = M4L5R5;
	}

	if (!allowed) throw new IllegalArgumentException("Not implemented!");

	setType();
    }

    /**
       This is the version of Savitzky-Golay smoothing that expects
       pre-calculated coefficients. They must be stored in the readable
       ASCII-file coef and must obay the following format:<p>
       <ul>
       <li> The first line starts with <tt>#savitzky-golay</tt>, followed
       by <tt>nl</tt>, <tt>nr</tt>, and the degree of the smoothing
       polynomial, <tt>m</tt> (separated by spaces). </li>
       <li> All following lines consits of a single double, starting with
       <tt>cn</tt> for <tt>n=-nl</tt>.</li>
       </ul>
       One can generate a coefficient file that follows this format by
       using the C-program <tt>savgol</tt>.
       @param coef The ascii-file holding the smoothing parameters.
    */
    public SavitzkyGolaySmoothing(InputStream coef)
    {
	if (coef != null) {
	    calcInputCoeff(coef);
	}
    }

    @Override
    protected void setType()
    {
	type = FIR;
    }

    /**
       This method is called only at construct. The coefficients are not
       calculated, but read in from an ascii-file. If the file does not
       follow the desired format, an <tt>IllegalArgumentException</tt> is
       thrown
       Refer to {@link #SavitzkyGolaySmoothing(File)} for valid file formats.
    */
    protected void calcInputCoeff(InputStream instream)
    {
	BufferedReader in;
	try {
	    in = new BufferedReader(new InputStreamReader(instream));
	    String line;
	    line = in.readLine();
	    if (!line.startsWith("#savitzky-golay"))
		throw new IllegalArgumentException("wrong header in file!");
	    StringTokenizer breakup = new StringTokenizer(line);
	    if (breakup.countTokens() != 4)
		throw new IllegalArgumentException("wrong header arguments");
	    breakup.nextToken();	// intro

	    int nl = Integer.parseInt(breakup.nextToken());
	    int nr = Integer.parseInt(breakup.nextToken());
	    int m  = Integer.parseInt(breakup.nextToken());

	    degree = m;
	    nk     = -nl;

	    cn = new double[nl+nr+1];

	    for (int i1 = 0; i1 != nl+nr+1; i1 ++)
		cn[i1] = Double.valueOf(in.readLine().trim()).doubleValue();
	    // if we got that far, we had no conversion exceptions
	    in.close();
	} catch (IOException ioe) {
	    throw new IllegalArgumentException("wrong file");
	}

	return;
    }

    /**
       A method to return the degree of the smoothing polynom. The smoothing
       polynom is specific to a Savitzky-Golay smoothing, therefore it is
       not present in the base class.
    */
    public int getDegree()
    {
	return degree;
    }

    /**
       A method to return the derivative of the smoothing polynom.
       @return The nth derivative this smoothing was designed for
    */
    public int getDerivative()
    {
	return derivative;
    }

    /**
       Test purpose. Read a block data file and smooth the data.
    */
    /*
    public static void main(String arg[]) throws IOException
    {
	FileMatrix             data;
	SavitzkyGolaySmoothing smooth;

	if (arg.length != 2) {
	    System.err.println("usage: java vec_math.SavitzkyGolaySmoothing "+
			       "<blockdata.file> <smoother.file>");
	    return;
	}

	data   = new FileMatrix(            new File(arg[0]));
	smooth = new SavitzkyGolaySmoothing(new FileInputStream(arg[1]));

	NVector leveled = new NVector(data.rows());
	NVector rough   = data.getColumn(1);
	int     i1;

	for (i1 = 0; i1 !=-smooth.getNk(); i1 ++) {
	    if (smooth.getDegree() == 2)
		leveled.set(i1, rough.get(i1));
	    else
		leveled.set(i1, 0.);
	}

	for (i1 = -smooth.getNk(); i1 != rough.dimension(); i1 ++) {
	    leveled.set(i1, smooth.smoothAt(rough.a, null, i1, 0));
	    System.out.println("#"+i1+": "+leveled.get(i1));
	}

	Matrix aug = new Matrix(data.rows(), data.columns()+1);

	for (i1 = 0; i1 != data.columns(); i1 ++)
	    aug.setOneCol(i1, data.getColumn(i1));

	aug.setOneCol(data.columns(), leveled);
	aug.evalRow();
	FileMatrix.toFile(new File(arg[0]), aug);
    }
    */
}

