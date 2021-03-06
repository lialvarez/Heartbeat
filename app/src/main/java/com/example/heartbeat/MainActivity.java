package com.example.heartbeat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.w3c.dom.Text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQ_BT_ENABLE = 1;
    Button toggleBtn;
    LineChart ecgChart, oxiChart;
    TextView pulseText, spO2Text, tempText;
    TextView pulseTitle, spO2Title, tempTitle;
    TextView bpmTextView, percentageTextView, degreeTextView;
    CardView pulseCard, spO2Card, tempCard;
    BluetoothDevice mBTDevice;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final String address = "00:18:E4:35:11:C3";
    static int ecg_count = 0;
    private List<Float> avrg_ecg = new ArrayList<>();

    static final int PLOT_MAX_SAMPLES = 200;
    private List<Float> ecg_list = new ArrayList<>();
    private List<Float> oxi_list = new ArrayList<>();

    static final int OXIMETER_SAMPLE_RATE = 25;
    static final int ECG_SAMPLE_RATE = 180;

    static final int PLOT_SPAN_SECONDS = 3;

    BluetoothConnectionService mBluetoothConnection;
    boolean connected = false;
    // Variable que indica si alguna medicion esta generando alarma.
    boolean pulseAlarm, spO2Alarm, tempAlarm, alarmSoundOn;
    Uri notification;
    Ringtone r;
    // Variable que indica si el toggle de la alarma debe colorear o no (para que esten sincronizados)
    boolean highlightAlarm;
    // Color original y de highlight del texto de las lecturas
    int baseColor, highlightColor, baseCardColor, highlightCardColor, whiteColor, baseButtonColor, buttonBaseTextColor;

    private enum Measurement{
        ECG,
        OXIMETER,
        PULSE,
        SPO2,
        TEMPERATURE
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Disable dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQ_BT_ENABLE);
            Toast.makeText(getApplicationContext(), "Enabling Bluetooth!!", Toast.LENGTH_LONG).show();
        }

        // Retrieve UI elements
        ecgChart = (LineChart) findViewById(R.id.ecgLineChart);
        oxiChart = (LineChart) findViewById(R.id.oximeterLineChart);
        toggleBtn = (Button) findViewById(R.id.toggleConnectionButton);
        ecgChart = (LineChart) findViewById(R.id.ecgLineChart);
        oxiChart = (LineChart) findViewById(R.id.oximeterLineChart);
        pulseText = (TextView) findViewById(R.id.pulseTextView);
        tempText = (TextView) findViewById(R.id.temperatureTextView);
        spO2Text = (TextView) findViewById(R.id.spO2TextView);
        pulseCard = (CardView) findViewById(R.id.pulseCadView);
        spO2Card = (CardView) findViewById(R.id.spO2CardView);
        tempCard = (CardView) findViewById(R.id.temperatureCardView);
        pulseTitle = (TextView) findViewById(R.id.pulseTitleTextView);
        spO2Title = (TextView) findViewById(R.id.spO2TitleTextView);
        tempTitle = (TextView) findViewById(R.id.temperatureTitleTextView);
        bpmTextView = (TextView) findViewById(R.id.bpmTextView);
        percentageTextView = (TextView) findViewById(R.id.percentTextView);
        degreeTextView = (TextView) findViewById(R.id.degreesTextView);

        // initialize alarms
        pulseAlarm = true;
        spO2Alarm = false;
        tempAlarm = false;
        highlightAlarm = false;
        alarmSoundOn = false;

        notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        r = RingtoneManager.getRingtone(getApplicationContext(), notification);

        baseColor = pulseText.getCurrentTextColor();
        highlightColor = 0xFFFF0000;
        baseCardColor = pulseCard.getCardBackgroundColor().getDefaultColor();
        highlightCardColor = 0xFFed0027;
        whiteColor = 0xFFFFFFFF;
        Drawable bg = toggleBtn.getBackground();
        if (bg instanceof ColorDrawable){
            baseButtonColor = ((ColorDrawable) bg).getColor();
        }
        buttonBaseTextColor = toggleBtn.getCurrentTextColor();



        // Setup charts

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQ_BT_ENABLE){
            if (resultCode == RESULT_OK){
                Toast.makeText(getApplicationContext(), "BlueTooth is now Enabled", Toast.LENGTH_LONG).show();
            }
            if(resultCode == RESULT_CANCELED){
                Toast.makeText(getApplicationContext(), "Error occured while enabling.Leaving the application..", Toast.LENGTH_LONG).show();
                ///finish();
            }
        }
    }//onActivityResult

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
        float max;
        float min;
        switch (tag){
            case 'E':
                addEntry(ecgChart, unpackFloatValue(data),
                        PLOT_SPAN_SECONDS * ECG_SAMPLE_RATE);
                ecg_list.add(unpackFloatValue(data));
                if(ecg_list.size() > PLOT_SPAN_SECONDS * ECG_SAMPLE_RATE)
                    ecg_list.remove(0);
                max = Collections.max(ecg_list);
                min = Collections.min(ecg_list);
                ecgChart.getAxisLeft().setAxisMaximum(max);
                ecgChart.getAxisLeft().setAxisMinimum(min);
                break;
            case 'O':
                addEntry(oxiChart, unpackFloatValue(data),
                        PLOT_SPAN_SECONDS * OXIMETER_SAMPLE_RATE);
                oxi_list.add(unpackFloatValue(data));
                if(oxi_list.size() > PLOT_SPAN_SECONDS * OXIMETER_SAMPLE_RATE)
                    oxi_list.remove(0);
                max = Collections.max(oxi_list);
                min = Collections.min(oxi_list);
                oxiChart.getAxisLeft().setAxisMaximum(max);
                oxiChart.getAxisLeft().setAxisMinimum(min);
                break;
            case 'P':
                pulseText.setText(String.valueOf((int)unpackFloatValue(data)));
                break;
            case 'S':
                spO2Text.setText(String.valueOf((int)unpackFloatValue(data)));
                break;
            case 'T':
                tempText.setText(String.format("%.1f", unpackFloatValue(data)));
                break;
            case 'A':
                alarmHandler(new String(data));
                break;
            case 'W':
                errorHandler(new String(data));
                break;
            default:
                break;
        }
    }

    private void errorHandler(String message) {
        char source = message.charAt(0);
        switch (source){
            case 'P':
                pulseText.setText("");
                setCardAlarm(Measurement.PULSE, false);
                break;
            case 'S':
                spO2Text.setText("");
                setCardAlarm(Measurement.SPO2, false);
                break;
            case 'T':
                tempText.setText("");
                setCardAlarm(Measurement.TEMPERATURE, false);
                break;
            default:
                break;
        }
    }

    private void alarmHandler(String message) {
        char source = message.charAt(0);
        switch (source){
            case 'P':
                if (message.charAt(1) == 'S'){
                    setCardAlarm(Measurement.PULSE, true);
                }else if (message.charAt(1) == 'R'){
                    setCardAlarm(Measurement.PULSE, false);
                }
                break;
            case 'S':
                if (message.charAt(1) == 'S'){
                    setCardAlarm(Measurement.SPO2, true);
                }else if (message.charAt(1) == 'R'){
                    setCardAlarm(Measurement.SPO2, false);
                }
                break;
            case 'T':
                if (message.charAt(1) == 'S'){
                    setCardAlarm(Measurement.TEMPERATURE, true);
                }else if (message.charAt(1) == 'R'){
                    setCardAlarm(Measurement.TEMPERATURE, false);
                }
                break;
            default:
                break;
        }
    }

    private void setCardAlarm(Measurement meas, boolean set) {
        int cardColor = set ? highlightCardColor : baseCardColor;
        int textColor = set ? whiteColor : baseColor;
        TextView title = null;
        TextView unit = null;
        TextView value = null;
        CardView card = null;
        switch (meas){
            case PULSE:
                if(!pulseAlarm && set)
                    r.play();
                pulseAlarm = set;
                card = pulseCard;
                title = pulseTitle;
                unit = bpmTextView;
                value = pulseText;
                break;
            case SPO2:
                if(!spO2Alarm && set)
                    r.play();
                spO2Alarm = set;
                card = spO2Card;
                title = spO2Title;
                unit = percentageTextView;
                value = spO2Text;
                break;
            case TEMPERATURE:
                if(!tempAlarm && set)
                    r.play();
                tempAlarm = set;
                card = tempCard;
                title = tempTitle;
                unit = degreeTextView;
                value = tempText;
                break;
            default:
                break;
        }
        if (card != null && title != null && unit != null && value != null){
            card.setCardBackgroundColor(cardColor);
            title.setTextColor(textColor);
            unit.setTextColor(textColor);
            value.setTextColor(textColor);
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
                toggleBtn.setBackgroundColor(highlightCardColor);
                toggleBtn.setTextColor(whiteColor);
            }
            if (text.equals("disconnected")) {
                connected = false;
                toggleBtn.setText(R.string.connect);
                toggleBtn.setBackgroundColor(baseButtonColor);
                toggleBtn.setTextColor(buttonBaseTextColor);
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

    private void addEntry(LineChart chart, float value, int plot_span) {

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

        chart.setVisibleXRangeMaximum(plot_span);
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