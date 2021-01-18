package com.example.heartbeat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    Button toggleBtn;
    LineChart ecgChart, oxiChart;
    TextView statusLabel, pulseText, spO2Text, tempText;
    BluetoothDevice mBTDevice;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final String address = "98:D3:91:FD:A1:D5";
    BluetoothConnectionService mBluetoothConnection;
    boolean connected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve UI elements
        ecgChart = (LineChart) findViewById(R.id.ecgLineChart);
        oxiChart = (LineChart) findViewById(R.id.oximeterLineChart);
        toggleBtn = (Button) findViewById(R.id.toggleConnectionButton);
        statusLabel = (TextView) findViewById(R.id.statusLabelTextView);
        ecgChart = (LineChart) findViewById(R.id.ecgLineChart);
        oxiChart = (LineChart) findViewById(R.id.oximeterLineChart);
        pulseText = (TextView) findViewById(R.id.pulseTextView);
        tempText = (TextView) findViewById(R.id.temperatureTextView);
        spO2Text = (TextView) findViewById(R.id.spO2TextView);


        ecgChart.setScaleEnabled(false);
        ecgChart.setDrawGridBackground(false);
        ecgChart.setTouchEnabled(false);
        ecgChart.setDragEnabled(false);
        ecgChart.setPinchZoom(false);
        ecgChart.setDrawBorders(false);

        ecgChart.getLegend().setEnabled(false);
        ecgChart.getDescription().setEnabled(false);

        ecgChart.getXAxis().setDrawAxisLine(false);
        ecgChart.getXAxis().setDrawLabels(false);
        ecgChart.getXAxis().setDrawGridLines(false);
        ecgChart.getAxisRight().setDrawAxisLine(false);
        ecgChart.getAxisRight().setDrawGridLines(false);
        ecgChart.getAxisRight().setDrawLabels(false);
        ecgChart.getAxisLeft().setDrawAxisLine(false);
        ecgChart.getAxisLeft().setDrawGridLines(false);

        ecgChart.invalidate();

        oxiChart.setScaleEnabled(false);
        oxiChart.setDrawGridBackground(false);
        oxiChart.setTouchEnabled(false);
        oxiChart.setDragEnabled(false);
        oxiChart.setPinchZoom(false);
        oxiChart.setDrawBorders(false);

        oxiChart.getLegend().setEnabled(false);
        oxiChart.getDescription().setEnabled(false);

        oxiChart.getXAxis().setDrawAxisLine(false);
        oxiChart.getXAxis().setDrawLabels(false);
        oxiChart.getXAxis().setDrawGridLines(false);
        oxiChart.getAxisRight().setDrawAxisLine(false);
        oxiChart.getAxisRight().setDrawGridLines(false);
        oxiChart.getAxisRight().setDrawLabels(false);
        oxiChart.getAxisLeft().setDrawAxisLine(false);
        oxiChart.getAxisLeft().setDrawGridLines(false);

        oxiChart.invalidate();

        // Setup bluetooth service
        mBluetoothConnection = new BluetoothConnectionService(MainActivity.this);
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        mBTDevice = btAdapter.getRemoteDevice(address);

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiverConnectionChanged,
                new IntentFilter("conectionChanged"));
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiverNewData,
                new IntentFilter("newData"));


        //Link toggleButton
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!connected){
                    startConnection();
                }else {
                    closeConnection();
                }
            }
        });
    }

    BroadcastReceiver mReceiverNewData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            char tag = intent.getCharExtra("tag",'\0');
            byte[] data = intent.getByteArrayExtra("data");
            updateData(tag, data);
        }
    };

    private void updateData(char tag, byte[] data) {
        //TODO: desenpaquetar la data
        switch (tag){
            case 'E':
                addEntry(ecgChart, unpackFloatValue(data));
                break;
            case 'O':
                addEntry(oxiChart, unpackFloatValue(data));
                break;
            case 'P':
                pulseText.setText(String.valueOf((int)unpackFloatValue(data)));
                break;
            case 'S':
                spO2Text.setText(String.valueOf((int)unpackFloatValue(data)));
                break;
            case 'T':
                tempText.setText(String.valueOf(unpackFloatValue(data)));
                break;
            default:
                break;
        }
    }

    private float unpackFloatValue(byte[] data) {
        float f = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        return f;
    }

    BroadcastReceiver mReceiverConnectionChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("status");
            if (text.equals("connected")) {
                connected = true;
                toggleBtn.setText(R.string.disconnect);
                statusLabel.setText(R.string.status_connected);
            }
            if (text.equals("disconnected")) {
                connected = false;
                toggleBtn.setText(R.string.connect);
                statusLabel.setText(R.string.status_disconnected);
                // TODO: manejar la desconexion asincronica.
            }
        }
    };

    private void closeConnection(){
        mBluetoothConnection.mConnectedThread.cancel();
    }

    private void startConnection() {
        startBTConnection(mBTDevice, myUUID);
    }

    /**
     * starting chat service method
     */
    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.");
        mBluetoothConnection.startClient(device,uuid);
    }

    private void addEntry(LineChart chart, float value) {

        LineData data = chart.getData();

        if (data == null) {
            data = new LineData();
            chart.setData(data);
        }

        ILineDataSet set = data.getDataSetByIndex(0);
        // set.addEntry(...); // can be called as well

        if (set == null) {
            set = createSet();
            data.addDataSet(set);
        }

        // choose a random dataSet
        int randomDataSetIndex = (int) (Math.random() * data.getDataSetCount());
        ILineDataSet randomSet = data.getDataSetByIndex(randomDataSetIndex);

        data.addEntry(new Entry(randomSet.getEntryCount(), value), randomDataSetIndex);
        data.notifyDataChanged();

        // let the chart know it's data has changed
        chart.notifyDataSetChanged();

        chart.setVisibleXRangeMaximum(200);
        chart.moveViewTo(data.getEntryCount() - 7, 50f, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "DataSet 1");
        set.setLineWidth(2.5f);
        set.setCircleRadius(4.5f);
        set.setColor(Color.rgb(240, 99, 99));
        set.setCircleColor(Color.rgb(240, 99, 99));
        set.setHighLightColor(Color.rgb(190, 190, 190));
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setValueTextSize(10f);
        set.setDrawCircles(false);
        set.setDrawValues(false);

        return set;
    }
}