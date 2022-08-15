package yuru.ikg.ethz.lbsproject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class Temperature extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor tempSensor;
    private Boolean isTemperatureAvailable;
    private double tempValue;
    private Boolean flag = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // run this activity in background
        moveTaskToBack(true);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // check if environmental seonsor is available on the device
        if(sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null){
            tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            isTemperatureAvailable = true;
            Log.d("TemperatureClass:", "The temperature sensor is available!");
        }else{
            Log.d("TemperatureClass:", "The temperature sensor is not available!");
            isTemperatureAvailable = false;
        }

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        tempValue = sensorEvent.values[0];
        String tempMsg = sensorEvent.values[0] + " °C";
        if(flag){
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("temperature", tempValue);
            this.startActivity(intent);
            Log.d("loop", "in the loop");
        }
        flag = false;

//        Intent intent = new Intent(this, MainActivity.class);
//        intent.putExtra("temperature", tempValue);
//        this.startActivity(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isTemperatureAvailable){
            sensorManager.registerListener(this, tempSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(isTemperatureAvailable){
            sensorManager.unregisterListener(this);
        }
    }

}
