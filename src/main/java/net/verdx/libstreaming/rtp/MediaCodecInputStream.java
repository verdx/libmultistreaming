/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.verdx.libstreaming.rtp;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !  
 */
@SuppressLint("NewApi")
public class MediaCodecInputStream extends BufferInfoInputStream {

	public final String TAG = "MediaCodecInputStream"; 

	private final MediaCodec mMediaCodec;
	private ByteBuffer mBuffer = null;
	private int mIndex;
	private boolean mClosed = false;
	
	public MediaFormat mMediaFormat;

	public MediaCodecInputStream(MediaCodec mediaCodec) {
		mMediaCodec = mediaCodec;
	}

	@Override
	public void close() throws IOException  {
		mClosed = true;
	}

	@Override
	public int read() {
		return 0;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int min = 0;

		try {
			if (mBuffer==null) {
				while (!Thread.interrupted() && !mClosed) {
					mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
					if (mIndex>=0 ){
						//Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);
						mBuffer = mMediaCodec.getOutputBuffer(mIndex);
						mMediaFormat = mMediaCodec.getOutputFormat(mIndex);
						// old mBuffer = mBuffers[mIndex];
						// old mBuffer.position(0);
						break;
					//} else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					//	mMediaFormat = mMediaCodec.getOutputFormat();
					//	Log.i(TAG,mMediaFormat.toString());
					} else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
						Log.v(TAG,"No buffer available...");
						//return 0;
					} else {
						Log.e(TAG,"Message: "+mIndex);
						//return 0;
					}
				}			
			}
			
			if (mClosed) throw new IOException("This InputStream was closed");
			
			min = Math.min(length, mBufferInfo.size - mBuffer.position());
			mBuffer.get(buffer, offset, min);
			if (mBuffer.position()>=mBufferInfo.size) {
				mMediaCodec.releaseOutputBuffer(mIndex, false);
				mBuffer = null;
			}
			
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		return min;
	}
	
	public int available() {
		if (mBuffer != null) 
			return mBufferInfo.size - mBuffer.position();
		else 
			return 0;
	}
}
