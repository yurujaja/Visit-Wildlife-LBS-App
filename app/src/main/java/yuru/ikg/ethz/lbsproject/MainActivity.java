package yuru.ikg.ethz.lbsproject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final String PROX_ALERT_INTENT = "yuru.ikg.ethz.lbsproject.PROXIMITY_ALERT";
    private List<MyLocation> ethLocations;   // a list of potential locations
    private Map<MyLocation, MyLocation> closestLocs;

    private MyLocation targetLocation;  // The user will go to this location to confirm the animal.
    private int targetDensity;

    private double temperature = -99;
    private LocalDateTime initialTime;
    private LocalDateTime currentDateTime;
    private double curSpeed;
    private LocationManager locationManager;


    private double initialDistance;
    private double finalDistance = 0;
    private double initialLocationlon;
    private double initialLocationLat;
    private double lastLocationLon;
    private double lastLocationLat;
    private double currentLocationLon;
    private double currentLocationLat;


    private String checkAnimal;
    private Map<String, Float> distanceAlert;
    private Map<String, Float> speedAlert;

    // interfaces for main activity
    private TextView targettxt;
    private TextView temptxt;
    private TextView timetxt;
    private TextView speedtxt;
    private TextView disttxt;

    private int loadCountTimes = 0;


    // radar display
    private RadarDisplay mRadarDisplay = null;

    private ServiceFeatureTable mUserFeatureTable;
    private FeatureLayer featureLayer;

    private HashMap<Location, String> userTrack = new HashMap<>();

    private Button buttonMap;

    // assignment3: AR view
    private Button buttonAR;

    // assignment3: User Experience
    private Button buttonHelp;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // acquire temperature from temperature activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            temperature = extras.getDouble("temperature");
            Log.d("temperature", temperature + "");

        }
        if (temperature == -99) {
            Intent tmpIntent = new Intent(this, Temperature.class);
            startActivity(tmpIntent);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        ethLocations = readLocationCSV();
        closestLocs = getClosestLoc(ethLocations);

        distanceAlert = new HashMap<>();
        distanceAlert.put("Monkey", 15.0F);
        distanceAlert.put("Kangaroo", 20.0F);
        distanceAlert.put("Deer", 20.0F);
        distanceAlert.put("Penguin", 15.0F);
        distanceAlert.put("Duck", 10.0F);

        speedAlert = new HashMap<>();
        speedAlert.put("Monkey", 4.0F);
        speedAlert.put("Kangaroo", 3.0F);
        speedAlert.put("Deer", 0.5F);
        speedAlert.put("Penguin", 2.0F);
        speedAlert.put("Duck", 5.0F);

        temptxt = (TextView) findViewById(R.id.temperature);
        timetxt = (TextView) findViewById(R.id.time);
        speedtxt = (TextView) findViewById(R.id.speed);
        disttxt = (TextView) findViewById(R.id.distance);
        targettxt = (TextView) findViewById(R.id.targetLoc);

        mRadarDisplay = (RadarDisplay) findViewById(R.id.radarDisplay);
        mRadarDisplay.setShowCircles(true);
        if (mRadarDisplay != null) {
            mRadarDisplay.startAnimation();
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        temptxt.setText(String.format("%.1f", temperature) + " °C");

        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.arcGISAPIKey));

        buttonMap = (Button) findViewById(R.id.mapViewButton);
        buttonMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                visualizeAnimalMap();
            }
        });


        buttonAR = (Button) findViewById(R.id.AR_view);
        buttonAR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goARView();
            }
        });

        buttonHelp = (Button) findViewById(R.id.buttonHelp);
        buttonHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showHelpInfo();
            }
        });

    }


    @Override
    protected void onStart() {
        // randomly pick one target location
        targetLocation = randomSelectOneLocation(ethLocations);
        targetDensity = getDensity(targetLocation.getCheckpointName());
        //targetDensity = getDensityRanking(densityMap, targetLocation.getCheckpointName());

        Log.d("FinallyGetDensity", targetDensity + "");
        //targetDensity = 5;

        String targetLocationName = targetLocation.getCheckpointName();
        targettxt.setText(targetLocationName);
        Log.d("TargetLocation", targetLocationName);

        double targetLocationLon = targetLocation.getLon();
        double targetLocationLat = targetLocation.getLat();

        Location target = new Location("");
        target.setLongitude(targetLocationLon);
        target.setLatitude(targetLocationLat);

        // acquire initial location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
            Location initial = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            initialLocationLat = initial.getLatitude();
            initialLocationlon = initial.getLongitude();

            lastLocationLat = initialLocationLat;
            lastLocationLon = initialLocationlon;

            initialDistance = initial.distanceTo(target);

            Log.d("InitialDistance", String.format("%.2f", initialDistance));
        }


        initialTime = LocalDateTime.now();
        Log.d("now", initialTime.getHour() + "");
        checkAnimal = getPotentialAnimal(temperature, initialTime.getHour(), initialDistance, targetDensity);
        Log.d("checkAnimal", checkAnimal);

        super.onStart();

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

        // get the location from current time and last time, and the distance can be computed,
        //the computed distance will be added to the final distance

        currentLocationLon = location.getLongitude();
        currentLocationLat = location.getLatitude();

        Location last = new Location("");
        last.setLongitude(lastLocationLon);
        last.setLatitude(lastLocationLat);

        Location current = new Location("");
        current.setLatitude(currentLocationLat);
        current.setLongitude(currentLocationLon);

        // store the user's tracks
        userTrack.put(current, System.currentTimeMillis() + "");

        finalDistance = finalDistance + current.distanceTo(last);

        lastLocationLon = currentLocationLon;
        lastLocationLat = currentLocationLat;
        Log.d("LocationChange", initialLocationLat + "");

        curSpeed = location.getSpeed();

        Log.d("CheckSpeed", curSpeed + " m/s");

        speedtxt.setText(String.format("%.1f", curSpeed) + " m/s");

        currentDateTime = LocalDateTime.now();
        timetxt.setText(currentDateTime.format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")));


        Location target = new Location("");
        target.setLongitude(targetLocation.getLon());
        target.setLatitude(targetLocation.getLat());


        double curDistance = current.distanceTo(target);
        disttxt.setText(String.format("%.2f", curDistance) + " m");

        updateRadarView(current, target, curDistance);

        checkCurrentDist(curSpeed, curDistance);
    }

    /**
     * Based on current distance to check weather the user has arrrived to the target location
     * or triggered the speed alert.
     *
     * @param speed
     * @param curDistance
     */
    private void checkCurrentDist(double speed, double curDistance) {
        if (curDistance <= 10) {
            goConfirmation();
        } else if (curDistance < distanceAlert.get(checkAnimal)) {
            if (speed > speedAlert.get(checkAnimal)) {
                animalRelocation();
            }
        } else {
            // keep going
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Read the provided CSV file to acquire a list of potential locations.
     */
    public List<MyLocation> readLocationCSV() {
        List<MyLocation> myLocations = new ArrayList<>();
        // read the raw csv file
        InputStream is = getResources().openRawResource(R.raw.locations);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        //initialization
        String line = "";

        try {
            // step over the headers
            reader.readLine();

            // if buffer is not empty
            while ((line = reader.readLine()) != null) {
                Log.d("ReadCSV", "Line:" + line);
                String[] tokens = line.split(";");
                MyLocation sample = new MyLocation();
                sample.setCheckpointName(tokens[0]);
                sample.setLon(Double.parseDouble(tokens[1]));
                sample.setLat(Double.parseDouble(tokens[2]));

                myLocations.add(sample);

                //Log the object
                Log.d("ReadCSV", "Just created:" + sample);
            }
        } catch (IOException e) {
            Log.e("ReadCSV", "Error" + line, e);
            e.printStackTrace();
        }
        return myLocations;
    }

    /**
     * Randomly select one location from the given list
     */
    public MyLocation randomSelectOneLocation(List<MyLocation> locationList) {
        int num = locationList.size();

        // generate a random number
        Random random = new Random();
        int randomNumber = random.nextInt(num);

        MyLocation target = locationList.get(randomNumber);
        return target;

    }

    /**
     * Determine the animal type according to the current environmental conditions
     * <last-edit: 2022-5-5>
     * Assignment2-EDIT:  Revising the animal appearance logic, adding the density ranking condition
     */
    public String getPotentialAnimal(double temperature, int now, double initialDist, int densityRanking) {
        List<String> possibleAnimals = new ArrayList<>();

        // conditions for different animals
        if (temperature >= 5 && temperature <= 20 && initialDist > 30
                && now > 9 && now < 18 && densityRanking >= 1 && densityRanking <= 3) {
            possibleAnimals.add("Monkey");
        }
        if (temperature > 20 && initialDist > 50 && now > 18 && now < 22
                && densityRanking >= 4 && densityRanking <= 6) {
            possibleAnimals.add("Kangaroo");
        }
        if (temperature >= 5 && temperature <= 20 && initialDist > 100 && now > 5 && now < 12
                && densityRanking >= 7 && densityRanking <= 11) {
            possibleAnimals.add("Deer");
        }
        if (temperature < 5 && initialDist > 70 && now > 14 && now < 21
                && densityRanking >= 4 && densityRanking <= 6) {
            possibleAnimals.add("Penguin");
        }

        // If no condition is met, the default animal (duck) should appear. If multiple animals (not including
        //duck) can appear at that location, the application should randomly select one of these animals.
        if (possibleAnimals.isEmpty()) {
            return "Duck";
        } else if (possibleAnimals.size() == 1) {
            return possibleAnimals.get(0);
        } else {
            Random random = new Random();
            int randomIdx = random.nextInt(possibleAnimals.size());
            return possibleAnimals.get(randomIdx);
        }
    }

    /**
     * Generate a map to store the closest location for all potential locations
     */
    public Map<MyLocation, MyLocation> getClosestLoc(List<MyLocation> locations) {
        Map<MyLocation, MyLocation> closest = new HashMap<>();
        int n = locations.size();
        for (MyLocation curLoc : locations) {
            Location cur = new Location("");
            cur.setLongitude(curLoc.getLon());
            cur.setLatitude(curLoc.getLat());

            double closestDis = Double.MAX_VALUE;
            MyLocation closestLoc = new MyLocation();
            for (MyLocation tempLoc : locations) {
                if (tempLoc.equals(curLoc)) {
                    continue;
                }
                Location temp = new Location("");
                temp.setLongitude(tempLoc.getLon());
                temp.setLatitude(tempLoc.getLat());
                if (cur.distanceTo(temp) < closestDis) {
                    closestDis = cur.distanceTo(temp);
                    closestLoc = tempLoc;
                }
            }
            closest.put(curLoc, closestLoc);
        }

        return closest;

    }

    /**
     * The animal moves to the closest possible location from its current location.
     * In this function, the following functions are implemented:
     * 1) trigger the alert of informing the user
     * 2) change the target location
     * 3) update new distance to the target
     * 4) update the new textview information of target location name
     * 5) update the radar display, new target location will be on the biggest circle of radar.
     */
    public void animalRelocation() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Speed Alert");
        alertDialog.setMessage("Go slow! You scared the animal away..." + "\n Now please go to the new location");
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();

        MyLocation newLoc = closestLocs.get(targetLocation);
        targetLocation = newLoc;

        Location current = new Location("");
        current.setLatitude(currentLocationLat);
        current.setLongitude(currentLocationLon);

        Location newloc = new Location("");
        newloc.setLatitude(newLoc.getLat());
        newloc.setLongitude(newLoc.getLon());


        double newcurDistance = current.distanceTo(newloc);
        disttxt.setText(String.format("%.2f", newcurDistance) + " m");

        targettxt.setText(targetLocation.getCheckpointName());

        updateRadarView(current, newloc, newcurDistance);

    }


    /**
     * To confirm the appearance of the animal when the user arrived the location
     * This will send intent with relevant information and go to another activity and
     */
    public void goConfirmation() {
        Intent confirmIntent = new Intent(this, ConfirmActivity.class);

        LocalDateTime confirmTime = LocalDateTime.now();
        Duration duration = Duration.between(initialTime, confirmTime);
        double durationSeconds = duration.getSeconds();

        Log.d("finaldistance", finalDistance + "");
        Log.d("durationSeconds", durationSeconds + "");
        double avgSpeed = finalDistance / durationSeconds;

        confirmIntent.putExtra("animal", checkAnimal);
        confirmIntent.putExtra("locName", targetLocation.getCheckpointName());
        confirmIntent.putExtra("lat", targetLocation.getLat());
        confirmIntent.putExtra("lon", targetLocation.getLon());
        confirmIntent.putExtra("confirmTime", confirmTime.toString());
        confirmIntent.putExtra("appearTime", initialTime.toString());
        confirmIntent.putExtra("initialDist", initialDistance);
        confirmIntent.putExtra("avgspeed", avgSpeed);
        confirmIntent.putExtra("userTrack", userTrack);
//        confirmIntent.putExtra("ethLocations", (Serializable) ethLocations);
        startActivity(confirmIntent);
    }


    /**
     * Update the location of the target on radar display
     * Given the current location and target location, calculate the relative direction(bearingTo())
     * Given the current distance to the target, calculate the ratio to the initial distance.
     *
     * @param current
     * @param target
     * @param distance
     */
    public void updateRadarView(Location current, Location target, double distance) {
        //Returns the approximate initial bearing in degrees East of true North when traveling along the
        // shortest path between this location and the given location.
        double bearing = Math.toRadians(current.bearingTo(target));
        double i = Math.sin(bearing);
        double j = -1 * Math.cos(bearing);

        Log.d("testBearing", current.bearingTo(target) + "");
        double ratio = distance / initialDistance;
        Log.d("ratio", ratio + "");
        mRadarDisplay.setLocation(i, j, ratio);
    }


    /**
     * Get the density ranking of a location:
     * Query the user feature table and get the number of records which locate in the threshold extent
     * of each location. And then sort the density in a descending way.
     * @param name
     * @return the density number of the location with the query name
     */
    public int getDensity(String name) {
        AtomicInteger rank = new AtomicInteger();
        HashMap<MyLocation, Integer> density = new HashMap<>();

        mUserFeatureTable = new ServiceFeatureTable(getString(R.string.userPositionURL));
        mUserFeatureTable.loadAsync();

        mUserFeatureTable.addDoneLoadingListener(() -> {
            Log.d("getLoadStatus() of user feature table", String.valueOf(mUserFeatureTable.getLoadStatus()));

            if (mUserFeatureTable.getLoadStatus() == LoadStatus.LOADED) {
                for (MyLocation loc : ethLocations) {
                    Point locPoint = new Point(loc.getLon(), loc.getLat());

                    // Define the envelope around the tap location for selecting features.
                    // Use approximate calculations to get the extant based on the conversion from meters to degrees(lontitude, lattitude)
                    Envelope selectionEnvelope = new Envelope(locPoint.getX() - 0.0001, locPoint.getY() - 0.0001, locPoint.getX() + 0.0001,
                            locPoint.getY() + 0.0001, SpatialReferences.getWgs84());

                    //Point projected_loc = (Point) GeometryEngine.project(wgsPoint, SpatialReference.create(4326),userGeom.getSpatialReference());
                    QueryParameters queryParams = new QueryParameters();
                    queryParams.setGeometry(selectionEnvelope);
                    queryParams.setSpatialRelationship(QueryParameters.SpatialRelationship.INTERSECTS);

                    final ListenableFuture<FeatureQueryResult> future = mUserFeatureTable.queryFeaturesAsync(queryParams);
                    Log.d("Get Density test", loc.getCheckpointName());

                    future.addDoneListener(() -> {
                        try {
                            // call get on the future to get the result
                            FeatureQueryResult result = future.get();

                            loadCountTimes ++;
                            Log.d("Count location", loadCountTimes + "");
                            Iterator<Feature> resultIterator = result.iterator();

                            Integer count = 0;
                            while (resultIterator.hasNext()) {
                                count = count + 1;
                                Feature feature = resultIterator.next();
                                Geometry userGeom = feature.getGeometry();

                            }
                            density.put(loc, count);

                            Log.d("Feature Query count:", count + "");

                            if(loadCountTimes == ethLocations.size()){
                                rank.set(getDensityRanking(density, name));
                            }
                        } catch (Exception e) {
                            String error = "Feature search failed for: " + ". Error: " + e.getMessage();
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                            Log.e("Track Reviewer", error);
                        }
                    });
                }
            } else {
                Log.e("Query Feature:", "Not Loaded");
            }
        });

        return rank.get();

    }


    /**
     * Given a density hashmap which stores the numbers of records, get the density ranking of a location
     * @param densityMap: stores the absolute density number of each location
     * @param name: the location name we want to query
     * @return the relative density ranking of a location
     */
    public int getDensityRanking(HashMap<MyLocation, Integer> densityMap, String name) {

        // sort the densityRanking hash map based on the count value
        Map<String, Integer> densityRanking = new HashMap<String, Integer>();
        List<Map.Entry<MyLocation, Integer>> list = new ArrayList(densityMap.entrySet());
        Collections.sort(list, (o1, o2) -> (o1.getValue() - o2.getValue()));
        for (int i = 0; i < list.size(); i++) {
            densityRanking.put(list.get(i).getKey().getCheckpointName(), i);
            Log.d("MyDensitySort:", i + "  " + list.get(i).getKey().getCheckpointName() + "  " + list.get(i).getValue());
        }


        if (densityMap.isEmpty()) {
            Log.e("DensityRankingEmpty:", "Is Empty, 0");
            return 5;
        }

        if (densityMap.size() < 11) {
            Log.e("DensityRankingEmpty:", "Has empty, not eleven, but" + densityRanking.size());
            return 5;
        }

        return densityRanking.get(name);
    }


    public void visualizeAnimalMap() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("fromMain", true);
        startActivity(intent);
    }


    /**
     * Assignment3: integrate augmented reality (AR)
     * Last edit: 2022.05.25
     */
    public void goARView(){
        Log.d("AR_VIEW", "buttonAR clicked");

        Intent arIntent = new Intent(this, ARViewActivity.class);
        startActivity(arIntent);
    }

    public void showHelpInfo(){
        // inform the user of the animal finding
        AlertDialog helpDialog = new AlertDialog.Builder(MainActivity.this).create();
        helpDialog.setTitle("App information");
        String helpText = "Let's find wild animals living on the Hönggerberg campus!" + "\n\n"
                 + "Go to the target location: when you find an animal, you can upload the animal and check it in the map view. " + "\n\n"
                 + "Visualize in the map view: click on map view to check the occurrence of animals." + "\n\n"
                 + "Visualize in the AR view: you can place the animals you found in the real world. " + "\n\n";
        helpDialog.setMessage(helpText);
        helpDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        helpDialog.show();
    }

}