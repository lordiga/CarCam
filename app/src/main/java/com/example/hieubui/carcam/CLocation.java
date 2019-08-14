package com.example.hieubui.carcam;

import android.location.Location;

public class CLocation extends Location {

    public CLocation(Location location) {
        super(location);
    }

    @Override
    public float distanceTo(Location dest) {
        float nDistance = super.distanceTo(dest);
        //Convert meters to feet
        nDistance = nDistance * 3.28083989501312f;
        return nDistance;
    }

    @Override
    public float getAccuracy() {
        float nAccuracy = super.getAccuracy();
        //Convert meters to feet
        nAccuracy = nAccuracy * 3.28083989501312f;
        return nAccuracy;
    }

    @Override
    public double getAltitude() {
        double nAltitude = super.getAltitude();
        //Convert meters to feet
        nAltitude = nAltitude * 3.28083989501312d;
        return nAltitude;
    }

    @Override
    public float getSpeed() {
        float nSpeed = super.getSpeed();
        //Convert meters/second to miles/hour
        nSpeed = nSpeed * 2.2369362920544f;
        return nSpeed;
    }


}
