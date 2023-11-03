package d2d.testing.streaming.rtp;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * En vez de hacer una nueva copia para cada packetizer, del buffer leido del MediaCodecInputStream, se podria cambiar para hacer solo una (.asReadOnlyBuffer()) y distribuirla a todos.
 * Se puede porque ByteBufferInputStream solo guarda el buffer añadido y hace una copia en el que se pasa a la llamada read que hace cada packetizer.
 */
public class MediaCodecBufferReader implements Runnable {
    private String TAG = "MediaCodecBufferReader";
    private final int mBufferSize;
    private final MediaCodecInputStream mMediaCodecInputStream;
    private final Map<AbstractPacketizer, InputStream> mPacketizersInputsMap;
    private boolean mRunning = true;

    public MediaCodecBufferReader(int BuffSize, MediaCodecInputStream mediaCodecInputStream, Map<AbstractPacketizer, InputStream> map){
        mBufferSize = BuffSize;
        mMediaCodecInputStream = mediaCodecInputStream;
        mPacketizersInputsMap = map;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[mBufferSize];
        int read = 0;
        while (!Thread.interrupted() && mRunning) {
            try {
                read += mMediaCodecInputStream.read(buffer, read, mBufferSize - read);
                //Log.v(TAG, "readen from MediaCodecInputStream: " + read);
                //Log.v(TAG, "readen from MediaCodecInputStream: " + mMediaCodecInputStream.getLastBufferInfo().presentationTimeUs);

                if(read > 0) {
                    //Log.v(TAG, "readen from MediaCodecInputStream >= bufferSize: " + read);
                    synchronized (mPacketizersInputsMap) {
                        for(Map.Entry<AbstractPacketizer, InputStream> entry : mPacketizersInputsMap.entrySet()) {
                            ((ByteBufferInputStream) entry.getValue())
                                    .addBufferInput(Arrays.copyOfRange(buffer,0, read), mMediaCodecInputStream.getLastBufferInfo().presentationTimeUs);
                        }
                    }
                    read = 0;
                }
            } catch (IOException e) {
                mRunning = false;
            }
        }

        Log.v(TAG, "Thread has been interrupted and its stopping...");
    }
}
