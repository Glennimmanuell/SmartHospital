package com.example.smarthospital;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

public class max30102 extends AppCompatActivity implements MqttCallback {

    private IMqttClient client;
    private TextView bpmVal, bpmRes;
    private DatabaseReference bpmValRef, bpmResRef;

    // Change the database name
    private static final String DATABASE_NAME = "Heart_Rate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_max30102);

        // Initialize UI components
        bpmVal = findViewById(R.id.bpmValue);
        bpmRes = findViewById(R.id.bpmResult);

        // Initialize Firebase references with the new database name
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference rootRef = database.getReference();
        bpmValRef = rootRef.child(DATABASE_NAME).child("bpmValue");
        bpmResRef = rootRef.child(DATABASE_NAME).child("bpmResult");

        // Read previous data from Firebase and update UI
        readPreviousData();

        // Connect to MQTT broker and subscribe to the topic
        connectToMqttBroker();
    }

    private void connectToMqttBroker() {
        try {
            client = new MqttClient("tcp://192.168.46.215:1883", "client02_max30102", new MemoryPersistence());
            client.connect();
            client.setCallback(this);
            client.subscribe("esp/max30102/heartrate_glenn");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        String msg = new String(message.getPayload(), StandardCharsets.UTF_8);
        int bpm = Integer.parseInt(msg);

        // Classify BPM into categories
        String bpmCategory;
        if (bpm > 100) {
            bpmCategory = "Poor";
        } else if (bpm >= 70 && bpm <= 100) {
            bpmCategory = "Average";
        } else if (bpm >= 50 && bpm <= 69) {
            bpmCategory = "Good";
        } else {
            bpmCategory = "Very Good";
        }

        // Update UI with received data
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(), "Bpm Arrived: " + msg, Toast.LENGTH_SHORT).show();
            bpmVal.setText(msg);
            bpmRes.setText(bpmCategory);

            // Store new data to Firebase
            bpmValRef.setValue(msg);
            bpmResRef.setValue(bpmCategory);
        });
    }

    private void readPreviousData() {
        // Read previous BPM value from Firebase
        bpmValRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String previousBpmVal = dataSnapshot.getValue(String.class);
                if (previousBpmVal != null) {
                    bpmVal.setText(previousBpmVal);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle read error
            }
        });

        // Read previous BPM category from Firebase
        bpmResRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String previousBpmRes = dataSnapshot.getValue(String.class);
                if (previousBpmRes != null) {
                    bpmRes.setText(previousBpmRes);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle read error
            }
        });
    }

    @Override
    public void connectionLost(Throwable cause) {
        // Handle connection lost
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // Delegate message handling to handleMessage method
        handleMessage(topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Handle message delivery complete
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect MQTT client on activity destroy
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}
