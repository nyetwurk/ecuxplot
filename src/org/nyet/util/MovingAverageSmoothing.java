package org.nyet.util;

import vec_math.LinearSmoothing;

public class MovingAverageSmoothing extends LinearSmoothing
{
    public MovingAverageSmoothing(int window)
    {
        window |= 1; // make sure window is odd
        this.cn = new double[window];
        for(int i=0;i<window;i++) {
            this.cn[i]=1.0/window;
        }
        this.nk = (1-window)/2;
        setType();
    }

    @Override
    protected void setType() { this.type = FIR; }
}

// vim: set sw=4 ts=8 expandtab:
