package com.example.smarthospital;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
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

public class mq135 extends AppCompatActivity implements MqttCallback {

    MqttClient client;
    TextView AQIval, AQIresult;
    LineChart lineChart;
    List<Entry> AQIData = new ArrayList<>();
    private static final int MAX_DATA_POINTS = 6; // maximum number of data points to display

    // Firebase reference
    DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mq135);
        AQIval = findViewById(R.id.AQIval);
        AQIresult = findViewById(R.id.AQIresult);
        lineChart = findViewById(R.id.line_chart);

        // Initialize Firebase reference
        databaseReference = FirebaseDatabase.getInstance().getReference("air_quality_glenn");

        // Load existing data from Firebase
        loadExistingData();

        try {
            client = new MqttClient("tcp://192.168.46.215:1883", "client01", new MemoryPersistence());
            client.setCallback(this);
            client.connect();
            client.subscribe("esp/mq135/air_quality_glenn");
        } catch (MqttException e) {
            e.printStackTrace();
        }

        setupChart();
    }

    private void setupChart() {
        lineChart.getDescription().setText("Air Quality Index");
        lineChart.getDescription().setTextSize(12f);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setGranularity(1f);
        lineChart.getXAxis().setGranularityEnabled(true);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setDrawGridLines(false);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void updateChart() {
        // Ensure only the last 6 data points are displayed
        int dataSize = AQIData.size();
        int startIndex = dataSize > MAX_DATA_POINTS ? dataSize - MAX_DATA_POINTS : 0;
        List<Entry> displayedData = new ArrayList<>(AQIData.subList(startIndex, dataSize));

        LineDataSet AQIDataSet = new LineDataSet(displayedData, "Air Quality Index");
        AQIDataSet.setColor(Color.rgb(139, 69, 19)); // Yellow color
        AQIDataSet.setCircleColor(Color.rgb(139, 69, 19));
        AQIDataSet.setCircleRadius(4f);
        AQIDataSet.setLineWidth(2f);
        AQIDataSet.setValueTextSize(10f);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(AQIDataSet);

        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);
        lineChart.moveViewToX(lineData.getEntryCount()); // Move chart to the right
        lineChart.invalidate(); // refresh
    }

    private void loadExistingData() {
        databaseReference.orderByKey().limitToLast(MAX_DATA_POINTS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int index = 0;
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Float AQI = dataSnapshot.getValue(Float.class);
                    if (AQI != null) {
                        AQIData.add(new Entry(index++, AQI));
                        classifyAQI(AQI);
                    }
                }
                // Update the chart with existing data
                updateChart();

                // Update AQIval with the latest AQI value
                if (!AQIData.isEmpty()) {
                    AQIval.setText(AQIData.get(AQIData.size() - 1).getY() + "%");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
            }
        });
    }

    private void classifyAQI(float AQIValue) {
        String classification;
        if (AQIValue < 50) {
            classification = "Good";
        } else if (AQIValue >= 50 && AQIValue < 60) {
            classification = "Average";
        } else {
            classification = "Bad";
        }
        AQIresult.setText(classification);
    }

    @Override
    public void connectionLost(Throwable cause) {
        // Handle connection lost
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String msg = new String(message.getPayload(), StandardCharsets.UTF_8);

        runOnUiThread(() -> {
            // Update TextView based on topic
            if (topic.equals("esp/mq135/air_quality_glenn")) {
                AQIval.setText(msg + "%");
                try {
                    float AQI = Float.parseFloat(msg);
                    AQIData.add(new Entry(AQIData.size(), AQI));

                    // Store data to Firebase
                    databaseReference.push().setValue(AQI);

                    // Check AQI result
                    if (AQI < 50) {
                        AQIresult.setText("Good");
                    } else if (AQI >= 50 && AQI < 60){
                        AQIresult.setText("Average");
                    } else {
                        AQIresult.setText("Bad");
                    }

                    // Update the chart with new data
                    updateChart();
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Handle delivery complete
    }
}
