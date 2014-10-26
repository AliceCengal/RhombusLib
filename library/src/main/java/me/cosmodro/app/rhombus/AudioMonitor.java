package me.cosmodro.app.rhombus;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AudioMonitor {
    public static String TAG = "Rhombus AudioMonitor";

    private static final boolean DEBUG = true;

    private Handler mHandler;

    private AudioConfig mConfig;

    private AudioRecord audioRecord;

    //private int minLevel = silenceLevel; //adaptive minimum level, should vary with each swipe.
    //private double smoothing = 0.1;
    //private double minLevelCoeff = 0.5;

    private boolean recording = false;

    public AudioMonitor(Handler handler) {
        this(handler, AudioConfig.DEFAULT);
    }

    public AudioMonitor(Handler handler, AudioConfig config) {
        mHandler = handler;
        mConfig = config;
    }

    /**
     * get the sample_rate used in recording audio
     *
     * @return
     */
    public int getFrequency() {
        return mConfig.frequency;
    }

    /**
     * get the level below which we consider audio data to be silent
     *
     * @return
     */
    public int getSilenceLevel() {
        return mConfig.silenceLevel;
    }

    /**
     * get whether currently recording
     *
     * @return
     */
    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        debug(TAG, "start recording");
        debug(TAG, "bufferSize: " + mConfig.bufferSize);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                mConfig.frequency, mConfig.channelConfiguration,
                mConfig.audioEncoding, mConfig.bufferSize);
        audioRecord.startRecording();
        recording = true;
    }

    public void stopRecording() {
        debug(TAG, "stop recording");
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        recording = false;
    }

    //begin monitoring mic input for > threshold values.  When one is detected, go to "record" mode
    public void monitor() {
        Message msg = Message.obtain();
        msg.what = MessageType.NO_DATA_PRESENT.ordinal();
        mHandler.sendMessage(msg);
        short[] buffer = new short[mConfig.bufferSize];
        boolean silent = true;
        short bufferVal;
        startRecording();
        int found = 0;
        int quorum = 5; //number of non-silent samples to find before we begin recording.
        int bufferReadResult = 0;

        while (silent && recording) {
            bufferReadResult = audioRecord.read(buffer, 0, mConfig.bufferSize);
            found = 0;
            for (int i = 0; i < bufferReadResult; i++) {
                bufferVal = buffer[i];
                //debug(TAG, "monitor val:"+bufferVal+", found:"+found);
                final boolean effectivelySilent = Math.abs(bufferVal) < mConfig.silenceLevel;
                if (silent && !effectivelySilent) {
                    found++;
                    if (found > quorum) {
                        silent = false;
                        msg = Message.obtain();
                        msg.what = MessageType.DATA_PRESENT.ordinal();
                        mHandler.sendMessage(msg);
                    }
                } else { //need non-silent samples to be next to each other.
                    found = 0;
                }
            }
        }
        if (!silent) {
            recordData(buffer, bufferReadResult); //pass because we're going to consider this part of the swipe
        }
    }

    private void recordData(short[] initialBuffer, int initialBufferSize) {
        debug(TAG, "recording data");
        Message msg; // = Message.obtain();
        // Create a DataOutputStream to write the audio data
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        DataOutputStream dos = new DataOutputStream(bos);

        short bufferVal;
        short[] buffer = new short[mConfig.bufferSize];
        boolean effectivelySilent;
        int silenceAtEndThreshold = mConfig.frequency; //get one second of (near) silence
        int silentSamples = 0;
        int maxSamples = mConfig.frequency * 10;
        int totalSamples = 0;
        boolean done = false; //have we recorded 1 second of silence
        int bufferReadResult = 0;
        try {
            //copy stuff from intialBuffer to dos.
            for (int i = 0; i < initialBufferSize; i++) {
                dos.writeShort(initialBuffer[i]);
            }
            int nonSilentAtEndFound = 0;
            int quorum = 5;
            while (!done && recording && totalSamples < maxSamples) {
                bufferReadResult = audioRecord.read(buffer, 0, mConfig.bufferSize);
                for (int i = 0; i < bufferReadResult; i++) {
                    bufferVal = buffer[i];
                    effectivelySilent = Math.abs(bufferVal) < mConfig.silenceLevel;
                    dos.writeShort(buffer[i]);
                    if (effectivelySilent) {
                        nonSilentAtEndFound = 0;
                        silentSamples++;
                        if (silentSamples > silenceAtEndThreshold) {
                            done = true;
                            msg = Message.obtain();
                            msg.what = MessageType.NO_DATA_PRESENT.ordinal();
                            mHandler.sendMessage(msg);
                        }
                    } else {
                        nonSilentAtEndFound++;
                        if (nonSilentAtEndFound > quorum) { //filter out noise blips
                            silentSamples = 0;
                        }
                    }
                    totalSamples++;
                }

            }
            dos.close();
            if (!recording) {
                debug(TAG, "not recording after loop in recorddata, assuming aborted");
                msg = Message.obtain();
                msg.what = MessageType.NO_DATA_PRESENT.ordinal();
                mHandler.sendMessage(msg);
                return;
            }
            byte[] audioBytes = os.toByteArray();
            msg = Message.obtain();
            msg.what = MessageType.DATA.ordinal();
            msg.obj = getSamples(audioBytes);
            mHandler.sendMessage(msg);

            //reportResult(processData(getSamples(audioBytes)));

        } catch (Exception e) {
            Log.e(TAG, "Recording Failed", e);
            e.printStackTrace();
            stopRecording();
            msg = Message.obtain();
            msg.what = MessageType.RECORDING_ERROR.ordinal();
            mHandler.sendMessage(msg);
        }

    }

    /**
     * extracts 16 bit samples from an array of bytes
     *
     * @param bytes source
     * @return List<Integer> of samples.
     * @throws IOException
     */
    private List<Integer> getSamples(byte[] bytes) throws IOException {
        ArrayList<Integer> result = new ArrayList<Integer>(bytes.length / 2);
        InputStream is = new ByteArrayInputStream(bytes);
        BufferedInputStream bis = new BufferedInputStream(is);
        DataInputStream dis = new DataInputStream(bis);
        while (dis.available() > 0) {
            result.add((int)dis.readShort());
        }
        return result;
    }


    private void debug(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message);
        }
    }

}
