package org.nyet.ecuxplot;

public class ECUxFilter {
    public boolean enabled = true;
    public boolean monotonicRPM = true;
    public double monotonicRPMfuzz = 100;
    public double minRPM = 2500;
    public double maxRPM = 8000;
    public double minPedal = 95;
    public int gear = 3;
    public int minimumPoints = 10;
}
