package com.example.smarthospital;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class dht11 extends AppCompatActivity implements MqttCallback {

    MqttClient client;
    TextView temp, hum;
    BarChart bc;
    List<Float> temperatureData = new ArrayList<>();
    List<Float> humidityData = new ArrayList<>();
    private static final int MAX_DATA_POINTS = 6; // maximum number of data points to display

    // Firebase references
    FirebaseDatabase database;
    DatabaseReference tempRef;
    DatabaseReference humRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dht11);
        temp = findViewById(R.id.temp);
        hum = findViewById(R.id.hum);
        bc = findViewById(R.id.mychart);

        // Initialize Firebase references
        database = FirebaseDatabase.getInstance();
        tempRef = database.getReference("temperature");
        humRef = database.getReference("humidity");

        // Read initial data from Firebase for displaying in TextViews
        readInitialDataFromFirebaseForTextViews();

        try {
            client = new MqttClient("tcp://broker.hivemq.com:1883", "client01", new MemoryPersistence());
            client.setCallback(this);
            client.connect();
            client.subscribe("esp/dht/temperature_glenn");
            client.subscribe("esp/dht/humidity_glenn");
        } catch (MqttException e) {
            e.printStackTrace();
        }

        setupChart();
    }


    private void readInitialDataFromFirebaseForTextViews() {
        tempRef.limitToLast(MAX_DATA_POINTS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Float value = snapshot.getValue(Float.class);
                    if (value != null) {
                        temp.setText("Temperature: " + value + "°C");
                        temperatureData.add(value);
                    }
                }
                updateChart(); // Update chart after retrieving temperature data
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });

        humRef.limitToLast(MAX_DATA_POINTS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Float value = snapshot.getValue(Float.class);
                    if (value != null) {
                        hum.setText("Humidity: " + value + "%");
                        humidityData.add(value);
                    }
                }
                updateChart(); // Update chart after retrieving humidity data
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }



    private void setupChart() {
        bc.getDescription().setText("Temperature and Humidity Data");
        bc.getDescription().setTextSize(12f);
        bc.setFitBars(true); //menyesuaikan ukuran
        bc.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        bc.getXAxis().setGranularity(1f);
        bc.getXAxis().setGranularityEnabled(true); //menentukan granularitas sumbu X, yaitu jarak minimum antara setiap label atau data point.
        bc.getXAxis().setDrawGridLines(false);
        bc.getAxisLeft().setDrawGridLines(false);
        bc.getAxisRight().setEnabled(false);
    }

    private void updateChart() {
        BarData barData = getBarData();
        bc.setData(barData);

        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f;

        bc.getBarData().setBarWidth(barWidth);
        bc.groupBars(0, groupSpace, barSpace);
        bc.invalidate(); // refresh
    }

    private BarData getBarData() {
        ArrayList<BarEntry> tempValues = new ArrayList<>();
        ArrayList<BarEntry> humValues = new ArrayList<>();

        for (int i = 0; i < temperatureData.size(); i++) {
            tempValues.add(new BarEntry(i, temperatureData.get(i)));
        }

        for (int i = 0; i < humidityData.size(); i++) {
            humValues.add(new BarEntry(i, humidityData.get(i)));
        }

        BarDataSet tempDataSet = new BarDataSet(tempValues, "Temperature Data");
        tempDataSet.setColor(Color.rgb(255, 235, 59));
        BarDataSet humDataSet = new BarDataSet(humValues, "Humidity Data");
        humDataSet.setColor(Color.rgb(139, 69, 19));

        List<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(tempDataSet);
        dataSets.add(humDataSet);

        return new BarData(dataSets);
    }

    @Override
    public void connectionLost(Throwable cause) {
        // Handle connection lost
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String msg = new String(message.getPayload(), StandardCharsets.UTF_8);

        runOnUiThread(() -> {
            // Update TextView and Firebase based on topic
            if (topic.equals("esp/dht/temperature_glenn")) {
                temp.setText("Temperature: " + msg + "°C");
                try {
                    float temperature = Float.parseFloat(msg);
                    temperatureData.add(temperature);
                    if (temperatureData.size() > MAX_DATA_POINTS) {
                        temperatureData.remove(0); // remove oldest data point
                    }
                    tempRef.push().setValue(temperature); // Write to Firebase
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            } else if (topic.equals("esp/dht/humidity_glenn")) {
                hum.setText("Humidity: " + msg + "%");
                try {
                    float humidity = Float.parseFloat(msg);
                    humidityData.add(humidity);
                    if (humidityData.size() > MAX_DATA_POINTS) {
                        humidityData.remove(0); // remove oldest data point
                    }
                    humRef.push().setValue(humidity); // Write to Firebase
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            // Update the chart with new data
            updateChart();
        });
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Handle delivery complete
    }
}
