package yuru.ikg.ethz.lbsproject;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Point;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;

import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;



public class MapActivity extends AppCompatActivity {

    private static final String TAG = MapActivity.class.getSimpleName();

    private MapView mMapView;
    private Callout mCallout;
    private List<MyLocation> ethLocations;
    private boolean fromMainActivity = false;

    private ServiceFeatureTable mServiceFeatureTable;
    private ServiceFeatureTable mAnimalFeatureTable;
    private FeatureLayer featureLayer;
    private List<String> locNames = new ArrayList<>();
    private int countLoadingTimes = 0;

    private Button returnBtn;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Bundle extras = getIntent().getExtras();
        if(extras!=null){
            fromMainActivity = true;
        }

        ethLocations = readLocationCSV();

        for (MyLocation loc : ethLocations) {
            locNames.add(loc.getCheckpointName());
        }

        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.arcGISAPIKey));

        mMapView = findViewById(R.id.mapView);

        ArcGISMap map = new ArcGISMap(BasemapStyle.ARCGIS_STREETS_NIGHT);
        mMapView.setMap(map);
        mMapView.setViewpoint(new Viewpoint(47.407951, 8.506564, 13000));//Hong: 8.506564; 47.407951, 12000

        // get the callout that shows attributes
        mCallout = mMapView.getCallout();

        // create the service feature table
        mServiceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.animalLocationURL));

        featureLayer = new FeatureLayer(mServiceFeatureTable);

        // add the layer to the map
        map.getOperationalLayers().add(featureLayer);

        // query the information and display on the map
        queryLocInfo();

        // return to the confirmation activity
        returnBtn = (Button) findViewById(R.id.returnBtn);
        returnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                if(fromMainActivity){
                    finish();
                }

            }

        });

    }

    /**
     * This function will query the animal feature layer to acquire the information and display
     * them on a map. And following implementation are done:
     * 1) query the animal feature table
     * 2) store all query results in a hashmap
     * 3) using callout of arcgis to display information
     */
    private void queryLocInfo() {

        List<String> animals = new ArrayList<>();
        animals.add("Monkey");
        animals.add("Kangaroo");
        animals.add("Deer");
        animals.add("Penguin");
        animals.add("Duck");

        // create the service feature table
        mAnimalFeatureTable = new ServiceFeatureTable(getString(R.string.animalLocationURL));

        HashMap<String, HashMap<String, Integer>> CalloutText = new HashMap<>();
        for (String locName : locNames) {
            for (String animal : animals) {
                HashMap<String, Integer> animalAppearance = new HashMap<>();

                //create query parameters
                QueryParameters queryParams = new QueryParameters();
                String queryClause = "location_name LIKE '" + locName + "' AND animal_type LIKE '" + animal + "'";
                queryParams.setWhereClause(queryClause);

                final ListenableFuture<FeatureQueryResult> future = mAnimalFeatureTable.queryFeaturesAsync(queryParams);
                future.addDoneListener(() -> {
                    try {
                        // call get on the future to get the result
                        FeatureQueryResult result = future.get();

                        // count the numbers of addDoneListener() to deal with async problem
                        countLoadingTimes++;

                        // count the number of records that meet the query condition
                        int count = 0;

                        Iterator<Feature> resultIterator = result.iterator();
                        while (resultIterator.hasNext()) {
                            count++;
                            Feature feature = resultIterator.next();
                        }
                        animalAppearance.put(animal, count);

                        if (CalloutText.containsKey(locName)) {
                            CalloutText.get(locName).put(animal, count);
                        } else {
                            CalloutText.put(locName, animalAppearance);
                        }

                        Log.d("Feature Query count:", locName + " " + animal + " " + count);

                        // when all the query results are acquired, display function can be called
                        if (countLoadingTimes == locNames.size() * animals.size()) {
                            SetCallout(CalloutText);
                        }
                    } catch (Exception e) {
                        String error = "Feature search failed for: " + queryClause + ". Error: " + e.getMessage();
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        Log.e("Track Reviewer", error);
                    }
                });
            }
        }

    }

    /**
     * Get the location name, animal appearance and types of a location
     * @param calloutText  the hash map which stores all 11 locations
     * @param loc the location name we want to query
     * @return the text that will be displayed of the query location
     */
    public String getCalloutText(HashMap<String, HashMap<String, Integer>> calloutText, String loc) {
        String text = "";

        text += String.format("Location: %s\n", loc);

        HashMap<String, Integer> animalInfo = calloutText.get(loc);
        for (String animal : animalInfo.keySet()) {
            text += animal + ": " + animalInfo.get(animal) + "\n";
        }

        return text;
    }

    /**
     * Show attributes of the animal feature layer using the Android ArcGIS SDK callout
     * @param CalloutText
     */
    public void SetCallout(HashMap<String, HashMap<String, Integer>> CalloutText) {
        Log.d("SetCallout size", CalloutText.size() + "");
        for (String name : CalloutText.keySet()) {
            Log.d("SetCallout size", name + "  " + CalloutText.get(name));
        }
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // remove any existing callouts
                if (mCallout.isShowing()) {
                    mCallout.dismiss();
                }
                // get the point that was clicked and convert it to a point in map coordinates
                final Point screenPoint = new Point(Math.round(e.getX()), Math.round(e.getY()));
                // create a selection tolerance
                int tolerance = 10;
                // use identifyLayerAsync to get tapped features
                final ListenableFuture<IdentifyLayerResult> identifyLayerResultListenableFuture = mMapView
                        .identifyLayerAsync(featureLayer, screenPoint, tolerance, false, 1);
                identifyLayerResultListenableFuture.addDoneListener(() -> {
                    try {
                        IdentifyLayerResult identifyLayerResult = identifyLayerResultListenableFuture.get();
                        // create a textview to display field values
                        TextView calloutContent = new TextView(getApplicationContext());
                        calloutContent.setTextColor(Color.BLACK);
                        calloutContent.setSingleLine(false);
                        calloutContent.setVerticalScrollBarEnabled(true);
                        calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                        calloutContent.setMovementMethod(new ScrollingMovementMethod());
                        calloutContent.setLines(6);
                        for (GeoElement element : identifyLayerResult.getElements()) {
                            Feature feature = (Feature) element;
                            // create a map of all available attributes as name value pairs
                            Map<String, Object> attr = feature.getAttributes();

                            String calloutText = getCalloutText(CalloutText, (String) attr.get("location_name"));
                            calloutContent.setText(calloutText);

                            // center the mapview on selected feature
                            Envelope envelope = feature.getGeometry().getExtent();
                            mMapView.setViewpointGeometryAsync(envelope, 200);
                            // show callout
                            mCallout.setLocation(envelope.getCenter());
                            mCallout.setContent(calloutContent);
                            mCallout.show();
                        }
                    } catch (Exception e1) {
                        Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e1.getMessage());
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });
    }


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
}