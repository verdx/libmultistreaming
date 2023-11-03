package d2d.testing.streaming.rtp;

import android.annotation.SuppressLint;
import android.media.MediaCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class ByteBufferInputStream extends BufferInfoInputStream {

    private static final String TAG = "ByteBufferInputStream";

    LinkedList<ByteBufferInfo> mByteBufferInfos = new LinkedList<>();
    ByteBuffer mByteBuffer = null;
    private boolean mClosed = false;

    public int read() throws IOException {
        return 0;
    }

    @Override
    public void close() {
        mClosed = true;
    }

    public synchronized int read(byte[] bytes, int off, int len) throws IOException {
        int min = 0;

        try {
            while (!Thread.interrupted() && !mClosed && mByteBufferInfos.isEmpty() && mByteBuffer == null) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }

            if (mClosed)
                throw new IOException("This InputStream was closed");

            if(mByteBuffer == null && !mByteBufferInfos.isEmpty()) {
                //Log.v(TAG,"mByteBufferInfos not empty...!");

                ByteBufferInfo byteBufferInfo = mByteBufferInfos.removeFirst();
                mByteBuffer = byteBufferInfo.getByteBuffer();
                mBufferInfo = byteBufferInfo.getBufferInfo();
            }

            if(mByteBuffer != null){
                if(mByteBuffer.hasRemaining()) {
                    min = Math.min(len, mByteBuffer.remaining());
                    //Log.v(TAG,"byteBuffer has remaining..." + mByteBuffer.remaining() + " reading..." + min + "with offset" + off);
                    mByteBuffer.get(bytes, off, min);
                }
                if(!mByteBuffer.hasRemaining()) {
                    //Log.v(TAG,"byteBuffer doesnt have remaining... nullifying...!");
                    mByteBuffer = null;
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return min;
    }

    public synchronized int available() {
        return mByteBuffer.remaining();
    }

    public synchronized void addBufferInput(byte[] buffer, long presentationTime) {
        mByteBufferInfos.add(new ByteBufferInfo(ByteBuffer.wrap(buffer), presentationTime));
        notifyAll();
        //mByteBuffersMap.put(byteBuffer, bufferInfo);
        //mByteBuffer = byteBuffer;
        //mBufferInfo = bufferInfo;
    }

    @Override
    public synchronized MediaCodec.BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }

    public class ByteBufferInfo {
        @SuppressLint("NewApi")
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer mByteBuffer;

        @SuppressLint("NewApi")
        public ByteBufferInfo(ByteBuffer mByteBuffer, long presentationTime) {
            this.bufferInfo.presentationTimeUs = presentationTime;
            this.mByteBuffer = mByteBuffer;
        }

        public MediaCodec.BufferInfo getBufferInfo() {
            return this.bufferInfo;
        }

        public ByteBuffer getByteBuffer() {
            return mByteBuffer;
        }
    }
}