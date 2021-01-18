package com.example.heartbeat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    Button toggleBtn;
    LineChart ecgChart, oxiChart;
    TextView pulseText, spO2Text, tempText;
    TextView pulseTitle, spO2Title, tempTitle;
    TextView bpmTextView, percentageTextView, degreeTextView;
    CardView pulseCard, spO2Card, tempCard;
    BluetoothDevice mBTDevice;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final String address = "98:D3:91:FD:A1:D5";
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

        pulseCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardClicked(Measurement.PULSE);
            }
        });


        spO2Card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardClicked(Measurement.SPO2);
            }
        });


        tempCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardClicked(Measurement.TEMPERATURE);
            }
        });


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

    private void cardClicked(Measurement measurement){
        switch (measurement){
            case PULSE:
                pulseCard.setCardBackgroundColor(baseCardColor);
                pulseText.setTextColor(baseColor);
                pulseTitle.setTextColor(baseColor);
                bpmTextView.setTextColor(baseColor);
                break;
            case SPO2:
                spO2Card.setCardBackgroundColor(baseCardColor);
                spO2Text.setTextColor(baseColor);
                spO2Title.setTextColor(baseColor);
                percentageTextView.setTextColor(baseColor);
                break;
            case TEMPERATURE:
                tempCard.setCardBackgroundColor(baseCardColor);
                tempText.setTextColor(baseColor);
                tempTitle.setTextColor(baseColor);
                degreeTextView.setTextColor(baseColor);
                break;
            default:
                break;
        }
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
            case 'A':
                alarmHandler(new String(data));
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
                pulseAlarm = set;
                card = pulseCard;
                title = pulseTitle;
                unit = bpmTextView;
                value = pulseText;
                break;
            case SPO2:
                pulseAlarm = set;
                card = spO2Card;
                title = spO2Title;
                unit = percentageTextView;
                value = spO2Text;
                break;
            case TEMPERATURE:
                pulseAlarm = set;
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
        if(set){
            r.play();
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