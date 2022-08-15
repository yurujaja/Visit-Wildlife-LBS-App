package yuru.ikg.ethz.lbsproject;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.io.Serializable;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;


public class ConfirmActivity extends AppCompatActivity {

    private Button findnextBtn;
    private Button visualizeBtn;
    private Button uploadBtn;


    private String animal;
    private double lat;
    private double lon;
    private String locName;
    private String confirmTime;
    private String appearTime;
    private double avgspeed;
    private double initialDist;
    private List<ImageView> imgList;
    private HashMap<Location, String > userTrack;

    private String currentTimeMillis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);

        Bundle extras = getIntent().getExtras();

        imgList = new ArrayList();
        ImageView monkeyImg = (ImageView) findViewById(R.id.Monkey);
        ImageView penguinImg = (ImageView) findViewById(R.id.Penguin);
        ImageView duckImg = (ImageView) findViewById(R.id.Duck);
        ImageView deerImg = (ImageView) findViewById(R.id.Deer);
        ImageView kangarooImg = (ImageView) findViewById(R.id.Kangaroo);
        imgList.add(monkeyImg);
        imgList.add(penguinImg);
        imgList.add(duckImg);
        imgList.add(deerImg);
        imgList.add(kangarooImg);

        if (extras != null) {
            animal = extras.getString("animal");
            lat = extras.getDouble("lat");
            lon = extras.getDouble("lon");
            locName = extras.getString("locName");
            confirmTime = extras.getString("confirmTime").replace("T", " ");
            appearTime = extras.getString("appearTime").replace("T", " ");
            avgspeed = extras.getDouble("avgspeed");
            initialDist = extras.getDouble("initialDist");
            userTrack = (HashMap<Location, String >)extras.getSerializable("userTrack");

            // change TextViews to contain the content from main activity
            TextView animaltxt = (TextView) findViewById(R.id.animalType);
            animaltxt.setText(animal);

            TextView lattxt = (TextView) findViewById(R.id.animalLocation);
            lattxt.setText(locName + "\n(lon: " + String.format("%.3f", lon) + ", lat: " + String.format("%.3f", lat) + ")");

            TextView conftimetxt = (TextView) findViewById(R.id.confirmTime);
            conftimetxt.setText(confirmTime);

            TextView speedtxt = (TextView) findViewById(R.id.avgSpeed);
            speedtxt.setText(String.format("%.2f", avgspeed) + " m/s");

            TextView initdistxt = (TextView) findViewById(R.id.initialDist);
            initdistxt.setText(String.format("%.2f", initialDist) + " m");

            TextView appetimetxt = (TextView) findViewById(R.id.appearTime);
            appetimetxt.setText(appearTime);

            //set the visibility of the picture to show animals
            showAnimalImage(animal);


            // inform the user of the animal finding
            AlertDialog alertDialog = new AlertDialog.Builder(ConfirmActivity.this).create();
            alertDialog.setTitle("You Found a " + animal);
            alertDialog.setMessage("Please upload the information to share! Visualize all the locations and animals!"
                    + "\nAnd go to find next animal! ");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        }

        currentTimeMillis = System.currentTimeMillis() + "";

        writeCSV();

        // restart the app one the "find next" button is clicked
        findnextBtn = (Button) findViewById(R.id.findnextBtn);
        findnextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                restartApp();
            }
        });



        // Visualizing animal habitat map
        visualizeBtn = (Button) findViewById(R.id.visualizeData);
        visualizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                visualizeAnimalMap();
            }
        });

        // New Module: FOR Assignment2.  2022.05.05
        //upload the data to Esri feature layers
        uploadBtn = (Button) findViewById(R.id.uploadData);
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadFeatures();
            }
        });


    }

    /**
     * This function show the current animal cartoon picture by setting the
     * visibility of the imageview resource.
     *
     * @param animal
     */
    private void showAnimalImage(String animal) {
        for (ImageView img : imgList) {
            String id = img.getResources().getResourceEntryName(img.getId());
            Log.d("SetVisibility", "animal:" + animal + ",id:" + id);
            if (id.contains(animal)) {
                img.setVisibility(View.VISIBLE);
            } else {
                img.setVisibility(View.INVISIBLE);
            }
        }

    }


    /**
     * Write the confirmed information to the output csv file.
     */
    public void writeCSV() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            try {
                File file = new File(getExternalFilesDir(null), "confirmed_animals.csv");
                FileOutputStream outputStream = new FileOutputStream(file, true);
                PrintWriter writer = new PrintWriter(outputStream);

                if (file.length() == 0) {
                    writer.println("appearTime,confirmTime,animal,lat,lon,initial_distance,average_speed");
                }
                writer.print(appearTime + ",");
                writer.print(confirmTime + ",");
                writer.print(animal + ",");
                writer.print(lat + ",");
                writer.print(lon + ",");
                writer.print(initialDist + ",");
                writer.println(avgspeed);

                writer.close();
                outputStream.close();
                Log.d("FileLog", "File Saved :  " + file.getPath());

            } catch (IOException e) {
                Log.e("FileLog", "Fail to write file");
            }
        } else {
            Log.e("FileLog", "SD card not mounted");
        }
    }

    /**
     * Restart the program
     */
    public void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        // this basically provides animation
        overridePendingTransition(0, 0);

        this.finish();
    }


    /**
     * New function for Assignment2. Last edit: 2022.05.05
     * Show the types and the numbers of animals which have appeared at the possible locations on a map.
     */
    public void visualizeAnimalMap() {
        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
    }

    /**
     * New function for Assignment2. Last edit: 2022.05.05
     * The user can upload the data to Esri feature layers using the Android ArcGIS SDK
     */
    public void uploadFeatures() {

        String uploadTimeMillis = System.currentTimeMillis() + "";

        Intent uploadIntent = new Intent(this, UploadFeatureActivity.class);
        uploadIntent.putExtra("confirmTime", currentTimeMillis);
        uploadIntent.putExtra("animalType", animal);
        uploadIntent.putExtra("locationName", locName);

        uploadIntent.putExtra("userTimeStamp", uploadTimeMillis);
        uploadIntent.putExtra("animalLon", lon);
        uploadIntent.putExtra("animalLat", lat);
        uploadIntent.putExtra("userTrack", userTrack);

        startActivity(uploadIntent);

    }

}