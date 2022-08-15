package yuru.ikg.ethz.lbsproject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.Manifest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.Bundle;
import android.util.Log;

import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureEditResult;

import com.esri.arcgisruntime.data.ServiceFeatureTable;

import com.esri.arcgisruntime.geometry.Point;

import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class UploadFeatureActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = UploadFeatureActivity.class.getSimpleName();

    // progress bar UI
    private ProgressBar progressBar;

    private ServiceFeatureTable mAnimalFeatureTable;
    private ServiceFeatureTable mUserFeatureTable;

    private Boolean finishFlag;

    private String confirmation_timestamp;
    private String animal_type;
    private String location_name;
    private Integer user_id=5;

    private Double animal_lon;
    private Double animal_lat;

    private HashMap<Location, String > userTrack;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        // UI
        setContentView(R.layout.activity_upload_feature);

        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(100);
        progressBar.setMin(0);

        // init variables
        finishFlag = false;

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if(extras != null){
            confirmation_timestamp = extras.getString("confirmTime");
            animal_type = extras.getString("animalType");
            location_name = extras.getString("locationName");

            animal_lon = extras.getDouble("animalLon");
            animal_lat = extras.getDouble("animalLat");
            userTrack = (HashMap<Location, String >)extras.getSerializable("userTrack");
            Log.d(TAG, "data loaded");
        }else{
            Log.d(TAG, "get null intent");
        }

        progressBar.setProgress(10);


        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.arcGISAPIKey));


        mAnimalFeatureTable = new ServiceFeatureTable(getString(R.string.animalLocationURL));
        mUserFeatureTable = new ServiceFeatureTable(getString(R.string.userPositionURL));

        progressBar.setProgress(progressBar.getProgress() + 30);

    }

    @Override
    protected void onStart() {
        super.onStart();

        FeatureLayer animalFeatureLayer = new FeatureLayer(mAnimalFeatureTable);
        animalFeatureLayer.loadAsync();
        FeatureLayer userFeatureLayer = new FeatureLayer(mUserFeatureTable);
        userFeatureLayer.loadAsync();



        Log.d("IF lOADING:", String.valueOf(mAnimalFeatureTable));
        Log.d("IF lOADING:", String.valueOf(mUserFeatureTable));

        mUserFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                uploadUser();
                Log.d(TAG, "User Features uploaded");
            }
        });

        mAnimalFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                uploadAnimal();
                Log.d(TAG, "Animal Features uploaded");
            }
        });
    }


    /**
     *  upload the data related to the animals found by the user
     */
    private void uploadAnimal(){
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("confirmation_timestamp", confirmation_timestamp);
        attributes.put("animal_type", animal_type);
        attributes.put("location_name", location_name);
        attributes.put("user_id", user_id);


        Point mapPoint = new Point(animal_lon, animal_lat, SpatialReferences.getWgs84());

        Feature feature = mAnimalFeatureTable.createFeature(attributes, mapPoint);

        if (mAnimalFeatureTable.canAdd()){
            Log.d(TAG, "UPLOAD Animal");
            mAnimalFeatureTable.addFeatureAsync(feature).addDoneListener(()->applyEdits(mAnimalFeatureTable));
        }else{
            runOnUiThread(()->logToUser(true, getString(R.string.error_cannot_add_to_feature_table)));
        }
    }

    /**
     *  upload the feature lists of user track
     */
    private void uploadUser(){
        List<Feature> userTrackFeatures = new ArrayList<>();
        userTrack.forEach((loc, timestamp)->{
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("user_timestamp", timestamp);
            attributes.put("user_id ", user_id);

            Point mapPoint = new Point(loc.getLongitude(), loc.getLatitude(), SpatialReferences.getWgs84());
            Feature feature = mUserFeatureTable.createFeature(attributes, mapPoint);
            userTrackFeatures.add(feature);

        });

        if (mUserFeatureTable.canAdd()){
            Log.d(TAG, "UPLOAD USER");
            mUserFeatureTable.addFeaturesAsync(userTrackFeatures).addDoneListener(()->applyEdits(mUserFeatureTable));
        }else{
            runOnUiThread(()->logToUser(true, getString(R.string.error_cannot_add_to_feature_table)));
        }
    }

    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable) {

        // apply the changes to the server
        final ListenableFuture<List<FeatureEditResult>> editResult = featureTable.applyEditsAsync();

        editResult.addDoneListener(() -> {
            try {
                List<FeatureEditResult> editResults = editResult.get();
                // check if the server edit was successful
                if (editResults != null && !editResults.isEmpty()) {
                    if (!editResults.get(0).hasCompletedWithErrors()) {
                        runOnUiThread(() -> logToUser(false, getString(R.string.feature_added)));
                    } else {
                        throw editResults.get(0).getError();
                    }
                } else {
                    Log.d("Upload", "Empty result.");
                }
            } catch (InterruptedException | ExecutionException e) {
                runOnUiThread(() -> logToUser(true, getString(R.string.error_applying_edits, e.getCause().getMessage())));
            }finally {
                progressBar.setProgress(progressBar.getProgress() + 30);
                // if the other one has been added -> finish(), if not, set the flag to true
                if (finishFlag) {
                    myFinish();
                } else {
                    finishFlag = true;
                }
            }
        });

    }

    private void myFinish() {
        Log.d(TAG, "myFinish()");
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Shows a Toast to user and logs to logcat.
     *
     * @param isError whether message is an error. Determines log level.
     * @param message message to display
     */
    private void logToUser(boolean isError, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (isError) {
            Log.e(TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }
}

