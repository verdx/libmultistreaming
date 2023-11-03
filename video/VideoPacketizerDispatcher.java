package d2d.testing.streaming.video;


import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import d2d.testing.streaming.hw.EncoderDebugger;
import d2d.testing.streaming.rtp.AbstractPacketizer;
import d2d.testing.streaming.rtp.ByteBufferInputStream;
import d2d.testing.streaming.rtp.MediaCodecBufferReader;
import d2d.testing.streaming.rtp.MediaCodecInputStream;

public class VideoPacketizerDispatcher{

    private static final String TAG = "VideoPacketizerDispatcher";

    private Thread mReaderThread;
    private static VideoPacketizerDispatcher mInstance;

    private VideoQuality mQuality;
    private SharedPreferences mSettings;

    private MediaCodec mMediaCodec;
    private MediaCodecInputStream mMediaCodecInputStream;
    private  Surface mEncoderSurface;
    private final Map<AbstractPacketizer, InputStream> mPacketizersInputsMap = new HashMap<>();


    private VideoPacketizerDispatcher(SharedPreferences settings, VideoQuality quality) throws IOException {

        mSettings = settings;
        mQuality = quality;

        EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);

        mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoderSurface = mMediaCodec.createInputSurface();
        mMediaCodec.start();

        mMediaCodecInputStream = new MediaCodecInputStream(mMediaCodec);
        mReaderThread = new Thread(new MediaCodecBufferReader(64000, mMediaCodecInputStream, mPacketizersInputsMap));
        mReaderThread.start();
    }

    public static synchronized boolean isRunning() {
        return mInstance != null;
    }

    public static synchronized void start(SharedPreferences settings, VideoQuality quality) throws IOException {
        if (mInstance == null) {
            mInstance = new VideoPacketizerDispatcher(settings, quality);

            Log.e(TAG, "Thread started!");
        }
    }

    public static synchronized void stop(){
        if(mInstance != null){
            mInstance.internalStop();
            mInstance = null;
        }
    }

    public static synchronized Surface getEncoderInputSurface(){
        Surface surface = null;
        if(mInstance != null){
            surface = mInstance.mEncoderSurface;
        }
        return surface;
    }

    public void internalStop() {
        Log.e(TAG,"Stopping dispatcher...");

        if (mReaderThread != null) {
            try {
                mMediaCodecInputStream.close();
            } catch (IOException ignore) {}
            mReaderThread.interrupt();
            try {
                mReaderThread.join();
            } catch (InterruptedException ignored) {}
            Log.e(TAG, "Reader Thread interrupted!");
            mReaderThread = null;
        }

        mMediaCodec.stop();
        mMediaCodec.release();
        mEncoderSurface.release();
        mMediaCodec = null;
        mEncoderSurface = null;
        mMediaCodecInputStream = null;
        mQuality = null;
        mSettings = null;
    }


    public static synchronized void subscribe(AbstractPacketizer packetizer){
        if (mInstance != null) {
            mInstance.addInternalPacketizer(packetizer);
        }
    }

    public static synchronized void unsubscribe(AbstractPacketizer packetizer) {
        if (mInstance != null) {
            mInstance.removeInternalPacketizer(packetizer);
        }
    }

    private void addInternalPacketizer(AbstractPacketizer packetizer) {
        InputStream packetizerInput = new ByteBufferInputStream();
        packetizer.setInputStream(packetizerInput);
        synchronized (mPacketizersInputsMap){
            mPacketizersInputsMap.put(packetizer, packetizerInput);
        }
        packetizer.start();
        Log.e(TAG, "Added internal packetizer to inputStreamMap!");
    }

    private void removeInternalPacketizer(AbstractPacketizer packetizer) {
        synchronized (mPacketizersInputsMap){
            mPacketizersInputsMap.remove(packetizer);
            packetizer.stop();
            Log.e(TAG, "Removed internal packetizer from map!");
        }
    }

}
