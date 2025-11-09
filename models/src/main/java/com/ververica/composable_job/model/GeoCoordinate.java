package com.ververica.composable_job.model;

public class GeoCoordinate {
    public double latitude;
    public double longitude;
    public double depth;

    public GeoCoordinate() {
    }

    public GeoCoordinate(double latitude, double longitude, double depth) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.depth = depth;
    }
}
