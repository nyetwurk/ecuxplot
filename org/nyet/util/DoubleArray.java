package org.nyet.util;

public class DoubleArray
{
    int sp = 0; // "stack pointer" to keep track of position in the array
    private double[] array;
    private int growthSize;

    public DoubleArray()
    {
        this( 1024 );
    }

    public DoubleArray( int initialSize )
    {
        this( initialSize, (int)( initialSize / 4 ) );
    }

    public DoubleArray( int initialSize, int growthSize )
    {
        this.growthSize = growthSize;
        array = new double[ initialSize ];
    }

    public void append( double i )
    {
        if( sp >= array.length ) // time to grow!
        {
            double[] tmpArray = new double[ array.length + growthSize ];
            System.arraycopy( array, 0, tmpArray, 0, array.length );
            array = tmpArray;
        }
        array[ sp ] = i;
        sp += 1;
    }

    public double[] toArray()
    {
        double[] trimmedArray = new double[ sp ];
        System.arraycopy( array, 0, trimmedArray, 0, trimmedArray.length );
        return trimmedArray;
    }
}
