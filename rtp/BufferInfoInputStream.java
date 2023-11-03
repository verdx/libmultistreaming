package d2d.testing.streaming.rtp;

import android.annotation.SuppressLint;
import android.media.MediaCodec.BufferInfo;

import java.io.InputStream;

public abstract class BufferInfoInputStream extends InputStream {
    @SuppressLint("NewApi")
    protected BufferInfo mBufferInfo = new BufferInfo();

    public BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }
}
