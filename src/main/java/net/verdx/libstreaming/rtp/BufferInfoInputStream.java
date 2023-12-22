package net.verdx.libstreaming.rtp;

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
