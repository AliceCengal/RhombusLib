package me.cosmodro.app.rhombus;

import android.media.AudioFormat;
import android.media.AudioRecord;

/**
* Created by athran on 10/26/14.
*/
public class AudioConfig {
    final int frequency;
    final int channelConfiguration;
    final int audioEncoding;
    final int bufferSize;
    final int silenceLevel;

    AudioConfig(int frequency,
                int channelConfiguration,
                int audioEncoding,
                int silenceLevel) {
        this.frequency = frequency;
        this.channelConfiguration = channelConfiguration;
        this.audioEncoding = audioEncoding;
        this.bufferSize =
                AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding) * 2;
        this.silenceLevel = silenceLevel;
    }

    public static final AudioConfig DEFAULT =
            new AudioConfig(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    500);

    public static AudioConfig create(int frequency,
                                     int channelConfiguration,
                                     int audioEncoding,
                                     int silenceLevel) {
        AudioConfig ac = new AudioConfig(frequency,
                channelConfiguration,
                audioEncoding,
                silenceLevel);
        if (ac.bufferSize < 0) {
            throw new IllegalStateException("Couldn't set sample rate as requested.");
        } else {
            return ac;
        }
    }

    public static AudioConfig withFrequency(int frequency) {
        return create(frequency,
                DEFAULT.channelConfiguration,
                DEFAULT.audioEncoding,
                DEFAULT.silenceLevel);
    }
}
