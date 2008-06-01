package org.nyet.ecuxplot;

public class Filter {
    public boolean enabled = true;
    public boolean monotonicRPM = true;
    public int monotonicRPMfuzz = 100;
    public int minRPM = 2500;
    public int maxRPM = 8000;
    public int minPedal = 95;
    public int gear = 3;
    public int minPoints = 30;
}
