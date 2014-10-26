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

    public AudioConfig getConfiguration() {
        return mConfig;
    }

    private void startRecording() {
        debug(TAG, "start recording");
        debug(TAG, "bufferSize: " + mConfig.bufferSize);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                mConfig.frequency,
                mConfig.channelConfiguration,
                mConfig.audioEncoding,
                mConfig.bufferSize);
        while (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        audioRecord.startRecording();
        recording = true;
    }

    private void stopRecording() {
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

        final int quorum = 5; //number of non-silent samples to find before we begin recording.
        final short[] buffer = new short[mConfig.bufferSize];

        boolean silent = true;

        startRecording();

        int bufferReadResult = 0;

        while (silent && recording) {
            bufferReadResult = audioRecord.read(buffer, 0, mConfig.bufferSize);
            int found = 0;

            for (int i = 0; i < bufferReadResult; i++) {
                short bufferVal = buffer[i];
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

        // Create a DataOutputStream to write the audio data
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final BufferedOutputStream bos = new BufferedOutputStream(os);
        final DataOutputStream dos = new DataOutputStream(bos);

        final short[] buffer = new short[mConfig.bufferSize];
        final int silenceAtEndThreshold = mConfig.frequency; //get one second of (near) silence
        final int maxSamples = mConfig.frequency * 10;
        final int quorum = 5;

        int silentSamples = 0;
        int totalSamples = 0;
        boolean done = false; //have we recorded 1 second of silence

        try {
            //copy stuff from intialBuffer to dos.
            for (int i = 0; i < initialBufferSize; i++) {
                dos.writeShort(initialBuffer[i]);
            }

            int nonSilentAtEndFound = 0;

            while (!done && recording && totalSamples < maxSamples) {
                final int bufferReadResult = audioRecord.read(buffer, 0, mConfig.bufferSize);

                for (int i = 0; i < bufferReadResult; i++) {
                    boolean effectivelySilent = Math.abs(buffer[i]) < mConfig.silenceLevel;
                    dos.writeShort(buffer[i]);

                    if (effectivelySilent) {
                        nonSilentAtEndFound = 0;
                        silentSamples++;
                        if (silentSamples > silenceAtEndThreshold) {
                            done = true;
                            Message msg = Message.obtain();
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
                Message msg = Message.obtain();
                msg.what = MessageType.NO_DATA_PRESENT.ordinal();
                mHandler.sendMessage(msg);
                return;
            }
            byte[] audioBytes = os.toByteArray();
            Message msg = Message.obtain();
            msg.what = MessageType.DATA.ordinal();
            msg.obj = getSamples(audioBytes);
            mHandler.sendMessage(msg);

        } catch (Exception e) {
            Log.e(TAG, "Recording Failed", e);
            e.printStackTrace();
            stopRecording();
            Message msg = Message.obtain();
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
    private static List<Integer> getSamples(byte[] bytes) throws IOException {
        ArrayList<Integer> result = new ArrayList<Integer>(bytes.length / 2);
        InputStream is = new ByteArrayInputStream(bytes);
        BufferedInputStream bis = new BufferedInputStream(is);
        DataInputStream dis = new DataInputStream(bis);
        while (dis.available() > 0) {
            result.add((int)dis.readShort());
        }
        return result;
    }

    private static void debug(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message);
        }
    }

}
