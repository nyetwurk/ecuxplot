package org.nyet.Util;

public class SmartIntArray
{
    int sp = 0; // "stack pointer" to keep track of position in the array
    private int[] array;
    private int growthSize;

    public SmartIntArray()
    {
        this( 1024 );
    }

    public SmartIntArray( int initialSize )
    {
        this( initialSize, (int)( initialSize / 4 ) );
    }

    public SmartIntArray( int initialSize, int growthSize )
    {
        this.growthSize = growthSize;
        array = new int[ initialSize ];
    }

    public void add( int i )
    {
        if( sp >= array.length ) // time to grow!
        {
            int[] tmpArray = new int[ array.length + growthSize ];
            System.arraycopy( array, 0, tmpArray, 0, array.length );
            array = tmpArray;
        }
        array[ sp ] = i;
        sp += 1;
    }

    public int[] toArray()
    {
        int[] trimmedArray = new int[ sp ];
        System.arraycopy( array, 0, trimmedArray, 0, trimmedArray.length );
        return trimmedArray;
    }
}
