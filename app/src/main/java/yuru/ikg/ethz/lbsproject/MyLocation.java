package yuru.ikg.ethz.lbsproject;

public class MyLocation {
    private String checkpointName;
    private double lon;
    private double lat;

    /**
    * The constructor
     */
    public MyLocation(String checkpointName, double lon, double lat) {
        this.checkpointName = checkpointName;
        this.lon = lon;
        this.lat = lat;
    }

    public MyLocation() {

    }

    /**
     * The getter and setter
     */
    public String getCheckpointName() {
        return checkpointName;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public void setCheckpointName(String checkpointName) {
        this.checkpointName = checkpointName;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }
}
