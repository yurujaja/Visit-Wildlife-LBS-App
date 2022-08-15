package yuru.ikg.ethz.lbsproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;

import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import android.util.Log;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorSession;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorWatcher;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.SessionLogLevel;

import com.google.ar.sceneform.rendering.ModelRenderable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;


public class ARViewActivity extends AppCompatActivity {
    private static final String TAG = ARViewActivity.class.getSimpleName();
    private static String ANCHOR_FILENAME;
    private int user_id = 5;
    private static final String UPLOADING_ANCHOR_KEY = "uploading";
    // boolean for checking if Google Play Services for AR if necessary.
    private boolean mUserRequestedInstall = true;

    // Camera Permission
    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    // Variables for tap and place
    private ArFragment arFragment;


    private float recommendedSessionProgress = 0f;

    // Variables for saving and loading 3d models
    private ConcurrentHashMap<String, Renderable> models = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Float> modelScale = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, AnimalAnchor> animalAnchors = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ViewRenderable> viewRenderables = new ConcurrentHashMap<>();

    // Variables for spatial anchor
    private ArSceneView sceneView;
    private CloudSpatialAnchorSession cloudSession;
    private boolean sessionInited = false;
    private boolean animalToPlace = true;

    private boolean scanningForUpload = false;
    private final Object syncSessionProgress = new Object();
    private final Object renderLock = new Object();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean tapExecuted = false;
    private final Object syncTaps = new Object();

    // ArcGIS query
    private ServiceFeatureTable mAnimalFeatureTable;
    private int countLoadingTimes = 0;
    private HashMap<String, Integer> animalInfo = new HashMap<>();   // e.g. <"deer", 2>

    // UI Components
    private Spinner spinnerAnimal;
    private ArrayList<String> spinnerStr = new ArrayList<>();
    private String selectedAnimal;
    private int lastSpinnerPosition;
    private boolean modelsLoaded = false;

    private boolean loadFromFile = false;
    private boolean enoughForSaving = false;
    private Button actionButton;
    private Button backButton;
    private Button restartButton;
    private TextView scanText;    // show the user the scan progress
    private TextView infoText;   // show the user the current status
    private ArrayList<String> animalModels = new ArrayList<>();
    private final Object modelLoadLock = new Object();
    private final Object progressLock = new Object();

    private int saveCount = 0;
    private Steps currentStep = Steps.Start;
    // For locating mode
    private ArrayList<String> savedAnchorId = new ArrayList<>();
    private HashMap<String, String> anchorAnimalType = new HashMap<>();  // <anchorId, animalType>
    private HashMap<String, Integer> anchorAnimalNum = new HashMap<>();  // <anchorId, animalNumber>

    /**
     * * Different steps to control the action
     * */
    enum Steps {
        Start,
        CreateLocalAnchor,         //  create a local anchor
        ReadyToSaveCloudAnchor,    // ready to save the cloud anchor
        SavingCloudAnchor,         // in the process of saving the cloud anchor
        ReadyToLocate,             // all anchors are placed, wait for locate
        QueryAnchor,               // run the query
        Restart,                   // ready to restart
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arview);

        ANCHOR_FILENAME = getResources().getString(R.string.anchor_csv);

        animalModels.add("Monkey");
        animalModels.add("Kangaroo");
        animalModels.add("Deer");
        animalModels.add("Penguin");
        animalModels.add("Duck");

        //UI components
        backButton = (Button) findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackButtonClicked();
            }
        });
        actionButton = (Button) findViewById(R.id.actionButton);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takeAction();
            }
        });
        restartButton = (Button) findViewById(R.id.restartButton);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onRestartButtonClicked();
            }
        });

        scanText = (TextView) findViewById(R.id.scanProgressText);
        infoText = (TextView) findViewById(R.id.statusText);
        spinnerAnimal = (Spinner) findViewById(R.id.spinnerAnimal);

        // create the service feature table
        mAnimalFeatureTable = new ServiceFeatureTable(getString(R.string.animalLocationURL));

        // Enable AR-related functionality on ARCore supported devices only.
        checkARCoreSupported();

        // initialize the drop down list
        initSpinnerAnimal();
        restartButton.setEnabled(true);
        actionButton.setVisibility(View.INVISIBLE);

        // load local 3D models
        loadModels();
        setModelScale();


        // Setting the on Tap Listener to the fragment
        this.arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        this.arFragment.setOnTapArPlaneListener(this::handleTap);

        currentStep = Steps.Start;
        Log.d(TAG, "currentStep: " + currentStep);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permission to operate.
        if (!(ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    this, new String[] {CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
            //return;
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        try {
            switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                case INSTALL_REQUESTED:
                    mUserRequestedInstall = false;
                    return;
            }
        }
        catch (UnavailableUserDeclinedInstallationException | UnavailableDeviceNotCompatibleException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "Exception creating session: " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        }


        // check local anchor.csv
        Log.d(TAG, "local anchor file exist: "+ checkAnchorFileExist(ANCHOR_FILENAME));
        if(checkAnchorFileExist(ANCHOR_FILENAME)){
            // if local anchor file exists, enter locating mode
            loadFromFile = true;

            currentStep = Steps.ReadyToLocate;
            Log.d(TAG, "currentDemoStep: " + currentStep);
            Toast.makeText(this, "There are animals that have already been placed earlier," +
                    "you can load last anchors from cloud or restart. ", Toast.LENGTH_LONG).show();
        }

        if(currentStep == Steps.ReadyToLocate){
            infoText.setText("");
            spinnerAnimal.setEnabled(false);
            actionButton.setText("Load anchors from cloud");
            actionButton.setVisibility(View.VISIBLE);
        }else if(currentStep == Steps.Start){

            // if local anchor file does not exist, enter placing mode
            startARView();
        }
    }


    /**
     * Check camera permission, and show message when permission is not granted
     * @param requestCode
     * @param permissions
     * @param results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.toast_camera_permission), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    /**
     *  Enable AR-related functionality on ARCore supported devices only.
     */
    void checkARCoreSupported() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Continue to query availability at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkARCoreSupported();
                }
            }, 200);
        }
    }

    /**
     * check if there is existing csv file
     */
    private boolean checkAnchorFileExist(String filename) {
        String path = getFilesDir().getAbsolutePath() + "/" + filename;
        File file = new File(path);
        return file.exists();
    }

    /**
     * delete the local csv file
     */
    private boolean deleteAnchorFile(String filename) {
        String path = getFilesDir().getAbsolutePath() + "/" + filename;
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * When Restart buttion is clicked, clear all existing anchors and delete local csv files.
     */
    private void onRestartButtonClicked() {
        Log.i(TAG, "onRestartButtonClicked()");
        currentStep = Steps.Restart;
        Log.d(TAG, "currentDemoStep: " + currentStep);
        takeAction();
    }

    /**
     * When back button is clicked, return to the main activity
     */
    private void onBackButtonClicked() {
        Log.i(TAG, "onBackButtonClicked()");
        synchronized (renderLock) {
            // destroy session
            stopSession();
            finish();
        }
    }

    /**
     * handleTap function from Spatial Anchor Tutorial
     * For handling the tap on screen
     */
    protected void handleTap(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        Log.d(TAG, "handleTap()");
        synchronized (this.syncTaps) {
            if (this.tapExecuted) {
                return;
            }
            this.tapExecuted = true;
        }

        if(!sessionInited){
            this.sceneView = arFragment.getArSceneView();
            Scene scene = arFragment.getArSceneView().getScene();
            scene.addOnUpdateListener(frameTime -> {
                if (this.cloudSession != null) {
                    this.cloudSession.processFrame(sceneView.getArFrame());
                }
            });

            initializeSession();
        }

        if(modelsLoaded){
            if(currentStep == Steps.CreateLocalAnchor){
                // Disable spinner until uploaded
                runOnUiThread(() -> {
                    backButton.setEnabled(false);
                    spinnerAnimal.setEnabled(false);
                });
                createLocalAnchor(hitResult);
            }

        }else {
            // it takes a little time to load all models
            Toast.makeText(this, "Models not loaded yet, please try again.", Toast.LENGTH_SHORT).show();        }

    }

    /**
     * Based on current step, excute different functions.
     */
    private void takeAction() {
        Log.i(TAG, "takeAction(), currentStep: " + currentStep);
        switch (currentStep){
            case ReadyToSaveCloudAnchor:
                if (!enoughForSaving) {
                    return;
                }
                // Hide the back button until we're done
                runOnUiThread(() -> {
                    backButton.setEnabled(false);
                    restartButton.setEnabled(false);
                });

                // Get newly created animalAnchor
                AnimalAnchor animalAnchor = animalAnchors.get(UPLOADING_ANCHOR_KEY);

                assert animalAnchor != null;
                if (!enoughForSaving) {
                    Log.e(TAG, "ERROR: save when not enoughDataForSaving");
                }

                // Add cloud anchor
                // set expire automatically
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.DATE, 3);
                Date expireTime = cal.getTime();
                animalAnchor.addCloudAnchor(expireTime);

                // Upload cloud anchor async
                uploadCloudAnchorAsync(animalAnchor.getCloudAnchor())
                        .thenAccept(this::onAnchorSaved).exceptionally(thrown -> {
                    Log.e(TAG, "uploadCloudAnchorAsync Error");
                    return null;
                });

                synchronized (progressLock) {
                    runOnUiThread(() -> {
                        scanText.setVisibility(View.GONE);
                        scanText.setText("");
                        actionButton.setVisibility(View.INVISIBLE);
                        infoText.setText("Saving cloud anchor...");
                    });
                    currentStep = Steps.SavingCloudAnchor;
                    Log.d(TAG, "currentStep: " + currentStep);
                }
                break;

            case ReadyToLocate:
                // Prepare for locating
                runOnUiThread(() -> spinnerAnimal.setEnabled(false));
                prepareForLocate();

                if(!sessionInited){
                    this.sceneView = arFragment.getArSceneView();
                    Scene scene = arFragment.getArSceneView().getScene();
                    scene.addOnUpdateListener(frameTime -> {
                        if (this.cloudSession != null) {
                            this.cloudSession.processFrame(sceneView.getArFrame());
                        }
                    });

                    initializeSession();
                }
                break;

            case QueryAnchor:
                // Locate anchors
                AnchorLocateCriteria criteria = new AnchorLocateCriteria();
                criteria.setIdentifiers(this.savedAnchorId.toArray(new String[0]));

                stopLocating();

                // Start locating
                this.cloudSession.createWatcher(criteria);

                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    infoText.setText("Query anchor...");
                });
                break;

            case Restart:
                // Clear all and restart
                clearLocalAnchors();
                deleteAnchorFile(ANCHOR_FILENAME);

                initSpinnerAnimal();

                if(!sessionInited){
                    this.sceneView = arFragment.getArSceneView();
                    Scene scene = arFragment.getArSceneView().getScene();
                    scene.addOnUpdateListener(frameTime -> {
                        if (this.cloudSession != null) {
                            this.cloudSession.processFrame(sceneView.getArFrame());
                        }
                    });

                    initializeSession();
                }

                this.savedAnchorId.clear();
                this.anchorAnimalType.clear();
                this.anchorAnimalNum.clear();

                startARView();
                break;
        }

    }

    /**
     * Clear local anchors
     */
    private void clearLocalAnchors() {
        for (AnimalAnchor animalAnchor : this.animalAnchors.values()) {
            animalAnchor.destroy();
        }

        this.animalAnchors.clear();
        this.saveCount = 0;
    }


    /**
     * When all cloud anchors are located, call this function. Continue to next step.
     */
    private void onLocateAnchorsCompleted(LocateAnchorsCompletedEvent event) {
        runOnUiThread(() -> infoText.setText("Anchor located!"));

        stopLocating();
        runOnUiThread(() -> {
            restartButton.setEnabled(true);
        });
        currentStep = Steps.Restart;
        Log.d(TAG, "currentStep: " + currentStep);
    }

    /**
     * Prepare for locate cloud anchors. Start new session, prepare anchor IDs.
     */
    private void prepareForLocate() {
        Log.d(TAG, "prepareForLocate()");

        // prepare anchor ID and animal type
        Log.d(TAG, "loadFromFile: " + loadFromFile);
        if (!loadFromFile) {
            savedAnchorId.clear();
            anchorAnimalType.clear();
            anchorAnimalNum.clear();
            for (Map.Entry<String, AnimalAnchor> entry : this.animalAnchors.entrySet()) {
                AnimalAnchor animalAnchor = entry.getValue();
                savedAnchorId.add(animalAnchor.getAnchorId());
                anchorAnimalType.put(animalAnchor.getAnchorId(), animalAnchor.getAnimal());
                anchorAnimalNum.put(animalAnchor.getAnchorId(), animalAnchor.getNumber());
            }
        } else {
            readAnchorFromFile(ANCHOR_FILENAME);
            runOnUiThread(() -> {
                infoText.setVisibility(View.VISIBLE);
                infoText.setText("Anchor id loaded from local file");
            });
        }
        Log.d(TAG, "savedAnchorId: " + savedAnchorId.toString());

        onCreateSessionForQuery();

        clearLocalAnchors();

        runOnUiThread(() -> {
            spinnerAnimal.setEnabled(false);
            infoText.setText("");
            Toast.makeText(this, "Please click start locating for loading.", Toast.LENGTH_SHORT).show();
            actionButton.setText("Start locating");
        });

        currentStep = Steps.QueryAnchor;
        Log.d(TAG, "currentStep: " + currentStep);
    }

    /**
     * Reset CloudSpatialAnchorSession for locating.
     */
    private void onCreateSessionForQuery() {
        Log.d(TAG, "onCreateSessionForQuery()");
        stopSession();
        resetSession();
    }

    /**
     * reset CloudSpatialAnchorSession
     */
    private void resetSession() {
        Log.d(TAG, "resetSession()");
        if (sessionInited) {
            stopLocating();
            cloudSession.reset();
        }
    }

    /**
     * After an anchor is saved to cloud, do the following:
     * 1) Save anchor information into file
     * 2) Update UI
     * 3) Remove corresponding animal from spinner
     * 4) Check if all animals are placed or not
     * @param id: anchor id of saved anchor
     */
    private void onAnchorSaved(String id) {
        saveCount++;
        Log.d(TAG, "saveCount: " + saveCount);
        Log.i(TAG, String.format("Cloud Anchor created: %s", id));

        // Update key of anchor
        AnimalAnchor animalAnchor = animalAnchors.get(UPLOADING_ANCHOR_KEY);
        assert animalAnchor != null;
        animalAnchor.setAnchorId(id);
        animalAnchors.put(selectedAnimal, animalAnchor);
        animalAnchors.remove(UPLOADING_ANCHOR_KEY);

        // Save to file
        saveAnchorToFile(ANCHOR_FILENAME);

        runOnUiThread(() -> {
            infoText.setText("Saved.");

            backButton.setEnabled(true);
            restartButton.setEnabled(true);
            spinnerAnimal.setEnabled(true);

            // remove last selected from spinner
            spinnerStr.remove(lastSpinnerPosition);
            setSpinnerAdapter();
            if (spinnerStr.stream().count() > 0) {
                spinnerAnimal.setSelection(0);
            } else {
                animalToPlace = false;
                Log.d(TAG, "animalToPlace: " + animalToPlace);
            }

            if (animalToPlace) {
                // continue Place
                tapExecuted = false;
                Log.d(TAG, "tapExecuted: " + tapExecuted);

                infoText.setText("Tap a surface to create next anchor");
                actionButton.setVisibility(View.INVISIBLE);

                currentStep = Steps.CreateLocalAnchor;
                Log.d(TAG, "currentStep: " + currentStep);
            } else {
                // Place finished
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setEnabled(false);

                currentStep = Steps.ReadyToLocate;
                Log.d(TAG, "currentStep: " + currentStep);
                infoText.setText("Placed all animals");
                actionButton.setText("Clear local anchors");
                actionButton.setEnabled(true);
            }
        });
    }



    /**
     * Function for initializing spatial anchor cloud session
     */
    private void initializeSession() {
        if (this.cloudSession != null){
            this.cloudSession.close();
        }

        this.cloudSession = new CloudSpatialAnchorSession();
        this.cloudSession.setSession(sceneView.getSession());
        this.cloudSession.setLogLevel(SessionLogLevel.Information);
        this.cloudSession.addOnLogDebugListener(args -> Log.d("ASAInfo", args.getMessage()));
        this.cloudSession.addErrorListener(args -> Log.e("ASAError", String.format("%s: %s", args.getErrorCode().name(), args.getErrorMessage())));

        this.cloudSession.addSessionUpdatedListener(args -> {
            synchronized (this.syncSessionProgress) {
                this.recommendedSessionProgress = args.getStatus().getRecommendedForCreateProgress();
                Log.i("ASAInfo", String.format("Session progress: %f", this.recommendedSessionProgress));
                this.enoughForSaving = this.recommendedSessionProgress > 1;
                Log.i(TAG, "enoughDataForSaving: " + enoughForSaving);

                if (currentStep == Steps.ReadyToSaveCloudAnchor) {
                    // Scanning progress
                    DecimalFormat decimalFormat = new DecimalFormat("00");
                    runOnUiThread(() -> {
                        String progressMessage = "Scan progress: " + decimalFormat.format(Math.min(1.0f, this.recommendedSessionProgress) * 100) + "%";
                        scanText.setText(progressMessage);
                    });

                    // Scan finished, ready to save
                    if (enoughForSaving && actionButton.getVisibility() != View.VISIBLE) {
                        // Enable the save button
                        runOnUiThread(() -> {
                            infoText.setText("Ready to save cloud anchor");
                            actionButton.setText("Save cloud anchor");
                            Toast.makeText(this, "Please click on save cloud anchor. ",Toast.LENGTH_LONG).show();
                            actionButton.setVisibility(View.VISIBLE);
                        });
                        currentStep = Steps.ReadyToSaveCloudAnchor;
                    }
                }

                if (!this.scanningForUpload)
                {
                    return;
                }
            }

        });

        this.cloudSession.addAnchorLocatedListener(args -> {
            LocateAnchorStatus status = args.getStatus();

            runOnUiThread(() -> {
                switch (status) {
                    case AlreadyTracked:
                        break;

                    case Located:
                        // Create and render a located cloud anchor. Save the anchor into HashMap.
                        String id = args.getAnchor().getIdentifier();
                        String animal = anchorAnimalType.get(id);
                        int number = anchorAnimalNum.get(id);
                        Log.i(TAG, "renderLocatedAnchor: id=" + id + ", animal=" + animal);

                        AnimalAnchor animalAnchor = new AnimalAnchor(animal, number, arFragment, args.getAnchor().getLocalAnchor());
                        animalAnchor.setCloudAnchor(args.getAnchor());
                        animalAnchor.getAnchorNode().setParent(arFragment.getArSceneView().getScene());

                        animalAnchor.setModel(models.get(animal), modelScale.get(animal));
                        animalAnchor.setViewRenderable(viewRenderables.get(animal));
                        animalAnchor.render(arFragment);
                        animalAnchors.put(animal, animalAnchor);

                        break;

                    case NotLocatedAnchorDoesNotExist:
                        Toast.makeText(this, "Anchor does not exist ",Toast.LENGTH_LONG).show();
                        break;
                }
            });

        });

        this.cloudSession.addLocateAnchorsCompletedListener(this::onLocateAnchorsCompleted);

        this.cloudSession.getConfiguration().setAccountId(getString(R.string.accountID));
        this.cloudSession.getConfiguration().setAccountKey(getString(R.string.accountKey));
        this.cloudSession.getConfiguration().setAccountDomain(getString(R.string.accountDomain));
        this.cloudSession.start();

        sessionInited = true;
    }


    /**
     * Initialize the UI in the start
     */
    private void startARView() {
        Log.d(TAG,"startARView()");

        //reset UI
        tapExecuted = false;
        animalToPlace = true;
        enoughForSaving = false;

        actionButton.setVisibility(View.INVISIBLE);
        backButton.setEnabled(true);
        restartButton.setVisibility(View.VISIBLE);
        restartButton.setEnabled(false);
        spinnerAnimal.setEnabled(true);
        infoText.setText("Tap the surface to place the animal");
        Toast.makeText(this, "Select the animal from the drop down list.", Toast.LENGTH_LONG).show();
        scanText.setVisibility(View.GONE);

        currentStep = Steps.CreateLocalAnchor;
        Log.d(TAG, "currentStep: " + currentStep);

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     *  Save all located anchors to a csv file
     */
    private void saveAnchorToFile(String filename) {
        try {
            FileOutputStream fos = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "utf8");
            BufferedWriter bufferedWriter = new BufferedWriter(osw);
            int count = 0;
            for (AnimalAnchor animalAnchor : animalAnchors.values()) {
                if (0 != count) {
                    bufferedWriter.newLine();
                }
                String anchorId = animalAnchor.getAnchorId();
                String animal = animalAnchor.getAnimal();
                String number = Integer.toString(animalAnchor.getNumber());
                String line = anchorId + "," + animal + "," + number;
                bufferedWriter.write(line);
                count++;
            }
            bufferedWriter.flush();
            bufferedWriter.close();
            osw.close();
            fos.close();
            Log.i(TAG, Integer.toString(count) + " anchors saved to file " + filename);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Fail to save the local file, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(this, "Fail to save the local file, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Read csv file and save anchor information: anchor_id, animal_name,animal_number
     */
    private void readAnchorFromFile(String filename) {
        try {
            FileInputStream fis = openFileInput(filename);
            InputStreamReader isr = new InputStreamReader(fis, "utf8");
            BufferedReader bufferedReader = new BufferedReader(isr);

            savedAnchorId.clear();
            anchorAnimalNum.clear();
            anchorAnimalType.clear();

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] lineSplit = line.split(",");
                Log.d(TAG, Arrays.toString(lineSplit));
                String anchorId = lineSplit[0];
                String animal = lineSplit[1];
                int number = Integer.parseInt(lineSplit[2]);
                savedAnchorId.add(anchorId);
                anchorAnimalType.put(anchorId, animal);
                anchorAnimalNum.put(anchorId, number);
            }

            bufferedReader.close();
            isr.close();
            fis.close();

        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Fail to read local file, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(this, "Fail to read local file, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }


    /**
     * Create local anchors, render in AR, and save into a HashMap
     *
     * @param hitResult: hit result containing anchor information
     */
    private void createLocalAnchor(HitResult hitResult) {
        Log.i(TAG, "createLocalAnchor()");
        int number = animalInfo.get(selectedAnimal);
        Log.d(TAG, "number: " + number);
        AnimalAnchor animalAnchor = new AnimalAnchor(selectedAnimal, number, arFragment, hitResult.createAnchor());

        // Draw a 3D object with glFT file type
        Renderable gltfModel = this.models.get(selectedAnimal);
        float scale = this.modelScale.get(selectedAnimal);
        ViewRenderable viewRenderable = this.viewRenderables.get(selectedAnimal);
        animalAnchor.setModel(gltfModel, scale);
        animalAnchor.setViewRenderable(viewRenderable);
        animalAnchor.render(arFragment);

        // Save into HashMap
        this.animalAnchors.put(UPLOADING_ANCHOR_KEY, animalAnchor);

        runOnUiThread(() -> {
            scanText.setVisibility(View.VISIBLE);

            if (enoughForSaving) {
                infoText.setText("Ready to save cloud anchor");
                actionButton.setText("Save cloud anchor");
                Toast.makeText(this, "Please click on save cloud anchor. ",Toast.LENGTH_LONG).show();
                actionButton.setVisibility(View.VISIBLE);
            } else {
                infoText.setText("Please move around...");
            }
        });

        currentStep = Steps.ReadyToSaveCloudAnchor;
        Log.d(TAG, "currentStep: " + currentStep);
    }


    /**
     * Load all 3D models
     */
    public void loadModels() {
        WeakReference<ARViewActivity> weakActivity = new WeakReference<>(this);

        this.models.clear();

        for(String animal:animalModels){
            loadSingleModel(animal.toLowerCase(Locale.ROOT));
            loadSingleView(animal.toLowerCase(Locale.ROOT));
        }

    }

    /**
     * Load a single 3D model from assests
     * @param name animal name
     */
    public void loadSingleModel(String name) {
        WeakReference<ARViewActivity> weakActivity = new WeakReference<>(this);

        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/"+name+".gltf"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    ARViewActivity activity = weakActivity.get();
                    if (activity != null) {
                        //activity.model = model;
                        activity.models.put(name, model);
                        Log.d("models",""+models.get(name));
                        checkModelLoad();
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });

    }

    /**
     * Load a TextView layout to render as a label in AR
     * @param animal: animal name
     */
    private void loadSingleView(String animal) {
        WeakReference<ARViewActivity> weakActivity = new WeakReference<>(this);

        ViewRenderable.builder()
                .setView(this, R.layout.view_card)
                .build()
                .thenAccept(viewRenderable -> {
                    ARViewActivity activity = weakActivity.get();
                    if (activity != null) {
                        Log.i(TAG, "viewRenderable loaded: " + animal);
                        activity.viewRenderables.put(animal, viewRenderable);
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Unable to load model: " + throwable.toString());
                    return null;
                });
    }



    /**
     * Function for uploading to cloud. As soon as enough frames are collected from your device,
     * it will start uploading the local Azure Spatial Anchor into the cloud.
     * Once the upload finishes, the code will return an anchor identifier.
     * @param anchor
     * @return
     */
    private CompletableFuture<String> uploadCloudAnchorAsync(CloudSpatialAnchor anchor) {
        synchronized (this.syncSessionProgress) {
            this.enoughForSaving = false;
        }

        return CompletableFuture.runAsync(() -> {
            try {
                float currentSessionProgress;
                do {
                    synchronized (this.syncSessionProgress) {
                        currentSessionProgress = this.recommendedSessionProgress;
                    }
                    if (currentSessionProgress < 1.0) {
                        Thread.sleep(500);
                    }
                }
                while (currentSessionProgress < 1.0);

                // Scan finished
                synchronized (this.syncSessionProgress) {
                    this.enoughForSaving = true;
                    Log.i(TAG, "enoughDataForSaving: " + enoughForSaving);
                }

                this.cloudSession.createAnchorAsync(anchor).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e("ASAError", e.toString());
                throw new RuntimeException(e);
            }
        }, executorService).thenApply(ignore -> anchor.getIdentifier());
    }


    /**
     * Initialize the drop list. Do the following:
     * 1) query the ArcGIS server to acquire all animals and their numbers.
     * 2) set the spinner only with queried animals and numbers
     */
    private void initSpinnerAnimal(){
        //create query parameters
        QueryParameters queryParams = new QueryParameters();
        for (String animal : animalModels) {
            String queryClause = "animal_type LIKE '" + animal + "' AND user_id=" + user_id;
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
                    animalInfo.put(animal.toLowerCase(Locale.ROOT), count);


                    Log.d("Feature Query count:",  animal + " " + count);

                    // only when all the query results are acquired, set the spinner
                    if (countLoadingTimes == animalModels.size()) {
                        setSpinner();
                    }
                } catch (Exception e) {
                    String error = "Feature search failed for: " + queryClause + ". Error: " + e.getMessage();
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    Log.e("Track Reviewer", error);
                }
            });
        }
    }

    private void setSpinner() {
        // only for test
//        if(animalInfo.isEmpty()){
//            animalInfo.put("Monkey",3);
//            animalInfo.put("Duck",4);
//        }
        spinnerStr.clear();

        for (String animal : animalInfo.keySet()) {
            if(animalInfo.get(animal)>0){
                spinnerStr.add(animal + "(" + animalInfo.get(animal)+")");
            }
        }

        spinnerStr.sort(null);
        setSpinnerAdapter();

        spinnerAnimal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                lastSpinnerPosition = position;
                onAnimalSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    /**
     * Setup spinner adapter. Called when spinner initialized and content updated
     */
    private void setSpinnerAdapter() {
        SpinnerAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerStr);
        spinnerAnimal.setAdapter(adapter);
    }

    /**
     * Check if all 3D animal models are loaded
     */
    private void checkModelLoad() {
        if (models.mappingCount() == animalModels.size()) {
            synchronized (modelLoadLock) {
                this.modelsLoaded = true;
                for (String animal : animalModels) {
                    animal = animal.toLowerCase(Locale.ROOT);
                    if (!models.containsKey(animal) | null == models.get(animal)) {
                        this.modelsLoaded = false;
                        break;
                    }
                }
                Toast.makeText(this, "Models load" + (this.modelsLoaded ? "ed" : " fail"), Toast.LENGTH_SHORT).show();
                Log.i(TAG, "modelsLoaded: " + modelsLoaded);
            }
        }
    }

    /**
     * Get the selected animal from spinner
     */
    private void onAnimalSelected(){
        this.selectedAnimal = spinnerAnimal.getSelectedItem().toString().split("\\(")[0].toLowerCase(Locale.ROOT);
        Log.i("Spinner selection", "onAnimalSelected(), selected: " + selectedAnimal);
    }

    /**
     * Stop CloudSpatialAnchorSession
     */
    private void stopSession() {
        Log.d(TAG, "stopSession()");
        if (sessionInited) {
            cloudSession.stop();
            sessionInited = false;
        }
        stopLocating();
    }

    /**
     * Setup different scales for models, and save to hash map
     */
    private void setModelScale() {
        for (String animal : animalModels) {
            switch (animal) {
                case "Monkey":
                    modelScale.put(animal.toLowerCase(Locale.ROOT), 0.01f);
                    break;
                case "Kangaroo":
                    modelScale.put(animal.toLowerCase(Locale.ROOT), 0.03f);
                    break;
                case "Deer":
                    modelScale.put(animal.toLowerCase(Locale.ROOT), 0.02f);
                    break;
                case "Penguin":
                    modelScale.put(animal.toLowerCase(Locale.ROOT), 0.012f);
                    break;
                case "Duck":
                    modelScale.put(animal.toLowerCase(Locale.ROOT), 0.015f);
                    break;
            }
        }
    }

    /**
     * Stop locating procedure
     */
    public void stopLocating() {
        Log.d(TAG, "stopLocating()");
        if (sessionInited) {
            List<CloudSpatialAnchorWatcher> watchers = cloudSession.getActiveWatchers();
            if (watchers.isEmpty()) {
                return;
            }

            // Only 1 watcher is at a time is currently permitted.
            CloudSpatialAnchorWatcher watcher = watchers.get(0);

            watcher.stop();
        }
    }
}


