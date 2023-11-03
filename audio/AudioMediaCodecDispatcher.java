package d2d.testing.streaming.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import d2d.testing.streaming.utils.Logger;

public class AudioMediaCodecDispatcher implements Runnable {

    private static String TAG = "AudioMediaCodecDispatcher";
    private static Thread mThread;
    private static AudioMediaCodecDispatcher mInstance;

    private static final int SAMPLING_RATE = 8000;

    private final int mBufferSize;

    private final AudioRecord mAudioRecord;
    private final Map<MediaCodec, ByteBuffer[]> mMediaCodecsBuffersMap = new HashMap<>();
    private final Map<MediaCodec, Integer> mMediaCodecsCountersMap = new HashMap<>();

    @SuppressLint("NewApi")
    private AudioMediaCodecDispatcher() throws IOException {
        this.mBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*2;
        this.mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);

        if(this.mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG,"An error occurred with the AudioRecord API while initialization!");
        }

        mAudioRecord.startRecording();
        if(mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG,"An error occurred with the AudioRecord API while starting recording!");
        }

        Log.e(TAG,"Constructor finished");
    }

    public static boolean isRunning() {
        return mInstance != null;
    }

    public static AudioMediaCodecDispatcher start() throws IOException {
        if(mInstance == null) {
            mInstance = new AudioMediaCodecDispatcher();
            mThread = new Thread(mInstance);
            mThread.start();
            Log.e(TAG,"Thread started!");
        }
        return mInstance;
    }

    public static void subscribe(MediaCodec mediaCodec) throws IOException {
            start().addInternalMediaCodec(mediaCodec);
    }

    public static void unsubscribe(MediaCodec mediaCodec) {
        if(mInstance != null) {
            mInstance.removeInternalMediaCodec(mediaCodec);
        }
    }

    @SuppressLint("NewApi")
    private void addInternalMediaCodec(MediaCodec mediaCodec) {
        synchronized (mMediaCodecsBuffersMap) {
            mMediaCodecsBuffersMap.put(mediaCodec, mediaCodec.getInputBuffers());
            mMediaCodecsCountersMap.put(mediaCodec, new Integer(0));
            Log.e(TAG,"Added internal media codec to maps!");
        }
    }

    @SuppressLint("NewApi")
    private void removeInternalMediaCodec(MediaCodec mediaCodec) {
        synchronized (mMediaCodecsBuffersMap) {
            mMediaCodecsBuffersMap.remove(mediaCodec);
            Log.e(TAG,"Removed internal media codec from map!");
            if(mMediaCodecsBuffersMap.size() == 0) {
                Log.e(TAG,"No more elements in map lets finish this!");
                mThread.interrupt();
                mThread = null;
                mInstance = null;
            }
        }
    }

    @SuppressLint("NewApi")
    private Map<MediaCodec, Integer> getMediaCodecBufferIndexesMap() {
        Map<MediaCodec, Integer> retMap = new HashMap<>();

        for(MediaCodec codec : mMediaCodecsBuffersMap.keySet()) {
            try {
                int bufferIndex = codec.dequeueInputBuffer(10000);
                if (bufferIndex>=0) {
                    mMediaCodecsBuffersMap.get(codec)[bufferIndex].clear();
                    retMap.put(codec, bufferIndex);
                } else {
                    Logger.e("Error with media coed buffer" + new Integer(mMediaCodecsCountersMap.get(codec) + 1));
                    mMediaCodecsCountersMap.put(codec, new Integer(mMediaCodecsCountersMap.get(codec) + 1));
                }
            } catch (Exception e) {
                Logger.e("Error with media coed buffer", e);
                removeInternalMediaCodec(codec);
            }
        }

        for (Map.Entry<MediaCodec, Integer> entry : mMediaCodecsCountersMap.entrySet()) {
            if(entry.getValue() > 50){
                removeInternalMediaCodec(entry.getKey());
            }
        }

        return retMap;
    }

    @SuppressLint("NewApi")
    @Override
    public void run() {
        int len = 0;

        while (!Thread.interrupted()) {
            synchronized (mMediaCodecsBuffersMap) {
                Map<MediaCodec, Integer> mediaCodecBufferIndexMap = getMediaCodecBufferIndexesMap();

                //SI TENEMOS BUFFERS DE UNO O MAS CODECS LEEMOS DE LO CONTRARIO PASAMOS
                if (!mediaCodecBufferIndexMap.isEmpty()) {
                    ByteBuffer internalBuffer = ByteBuffer.allocateDirect(mBufferSize);
                    //Log.e(TAG,"Media Codecs buffers map not empty: " + mediaCodecBufferIndexMap.size());
                    len = mAudioRecord.read(internalBuffer, mBufferSize);
                    if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "An error occured with the AudioRecord API !");
                    } else {
                        for (Map.Entry<MediaCodec, Integer> entry : mediaCodecBufferIndexMap.entrySet()) {
                            //copiamos nuestro buffer a los buffer del media codec
                            try {
                                ByteBuffer codecBuffer = mMediaCodecsBuffersMap.get(entry.getKey())[entry.getValue()];
                                codecBuffer.clear();
                                codecBuffer.put(internalBuffer);
                                entry.getKey().queueInputBuffer(entry.getValue(), 0, len, System.nanoTime() / 1000, 0);
                                internalBuffer.position(0);
                            } catch (Exception e) {
                                removeInternalMediaCodec(entry.getKey());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        try {
            mAudioRecord.stop();
            mAudioRecord.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
