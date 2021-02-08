package com.example.heartbeat;

import java.util.Arrays;

public class ReaderFSM {
    private static final int ECG_TAG = 'E';
    private static final int OX_TAG = 'O';
    private static final int SPO2_TAG = 'S';
    private static final int PULSE_TAG = 'P';
    private static final int TEMP_TAG = 'T';
    private static final int ALARM_TAG = 'A';
    private static final int ERROR_TAG = 'W';
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 15;
    private static final String HEADER = "F";



    private enum State {
        WAITING_HEADER,
        WAITING_TAG,
        WAITING_LENGTH,
        WAITING_DATA
    }

    private enum Event {
        TAG_RECEIVED,
        LENGTH_RECEIVED,
        DATA_RECEIVED,
        LAST_DATA_RECEIVED,
        NO_EVENT
    }

    private State current_state;
    private Event last_event;
    private int data_counter;
    private int header_count;

    private byte[] buffer;
    private char[] header_buffer;
    private int tag;
    private int length;

    public ReaderFSM(){
        current_state = State.WAITING_HEADER;
        last_event = Event.NO_EVENT;
        data_counter = 0;
        header_count = 0;
        buffer = new byte[100];
        header_buffer = new char[32];
    }

    boolean packageCompleted(int newByte){
        boolean ret;
        switch (current_state){
            case WAITING_HEADER:
                if(newByte == 'F'){
                    header_buffer[header_count++] = (char) newByte;
                    if(isHeaderCompleted()){
                        header_count = 0;
                        current_state = State.WAITING_TAG;
                    } else if(header_count > 4){
                        data_counter = 0;
                        current_state = State.WAITING_HEADER;
                        Arrays.fill(header_buffer, (char) 0);
                    }
                }else{
                    header_count = 0;
                    current_state = State.WAITING_HEADER;
                }
                ret = false;
                break;
            case WAITING_TAG:
                if(isTagValid(newByte)){
                    tag = newByte;
                    data_counter = 0;
                    current_state = State.WAITING_LENGTH;
                } else {
                    current_state = State.WAITING_TAG;
                }
                ret = false;
                break;
            case WAITING_LENGTH:
                if (isLengthValid(newByte)){
                    length = newByte;
                    current_state = State.WAITING_DATA;
                }else {
                    // se resetea la maquina de estados
                    current_state = State.WAITING_HEADER;
                }
                ret = false;
                break;
            case WAITING_DATA:
                buffer[data_counter++] = (byte) newByte;
                if (data_counter == length) {
                    current_state = State.WAITING_HEADER;
                    ret = true;
                } else{
                    ret = false;
                }
                break;
            default:
                ret = false;
                break;
        }
        return ret;
    }

    private boolean isHeaderCompleted() {
        boolean ret = false;
        // Convert buffer data to string
        String bufferedData = new String(Arrays.copyOfRange(header_buffer, 0, HEADER.length()));
        if (bufferedData.equals(HEADER))
            ret = true;
        return ret;
    }

    private boolean isLengthValid(int _length) {
        return _length >= MIN_LENGTH && _length <= MAX_LENGTH;
    }

    private boolean isTagValid(int _tag) {
        return _tag == ECG_TAG || _tag == OX_TAG || _tag == SPO2_TAG || _tag == PULSE_TAG
                || _tag == TEMP_TAG || _tag == ALARM_TAG || _tag == ERROR_TAG;
    }

    public char getTag(){
        return (char)tag;
    }

    public byte[] getData(){
        // convert char array to string
        return Arrays.copyOfRange(buffer, 0, length);
    }

}
