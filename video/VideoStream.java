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

package d2d.testing.streaming.video;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;

import d2d.testing.streaming.MediaStream;
import d2d.testing.streaming.Stream;
import d2d.testing.streaming.exceptions.ConfNotSupportedException;

/** 
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected VideoQuality mQuality = mRequestedQuality.clone();
	protected SharedPreferences mSettings = null;
	protected int mVideoEncoder;
	
	protected String mMimeType;

	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mRequestedQuality.equals(videoQuality)) {
			mRequestedQuality = videoQuality.clone();
		}
	}

	/** 
	 * Returns the quality of the stream.  
	 */
	public VideoQuality getVideoQuality() {
		return mRequestedQuality;
	}

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()} 
	 * to apply your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
	}	
	
	/**
	 * Starts the stream.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		//if (!mPreviewStarted) mCameraOpenedManually = false;
		super.start();
		Log.d(TAG,"Stream configuration: FPS: "+mQuality.framerate+" Width: "+mQuality.resX+" Height: "+mQuality.resY);
	}

	/** Stops the stream. */
	public synchronized void stop() {
		/*
		if (mCamera != null) {
			if (mMode == MODE_MEDIACODEC_API) {
				// lo cerramos en el dispatcher
				VideoPacketizerDispatcher.unsubscribe(mPacketizer);
				//mCamera.setPreviewCallbackWithBuffer(null);
			}
			if (mMode == MODE_MEDIACODEC_API_2) {
				((SurfaceView)mSurfaceView).removeMediaCodecSurface();
			}
			super.stop();
			// We need to restart the preview
			if (!mCameraOpenedManually) {
				destroyCamera();
			} else {
				try {
					startPreview();
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}

		 */
		VideoPacketizerDispatcher.unsubscribe(mPacketizer);
		super.stop();
	}


	/**
	 * Video encoding is done by a MediaCodec.
	 */
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {
		/*
		if (mMode == MODE_MEDIACODEC_API_2) {
			// Uses the method MediaCodec.createInputSurface to feed the encoder
			encodeWithMediaCodecMethod2();
		} else {
			// Uses dequeueInputBuffer to feed the encoder
			encodeWithMediaCodecMethod2();
		}

		 */
		encodeWithMediaCodecMethod2();
	}	


	/**
	 * Video encoding is done by a MediaCodec.
	 * But here we will use the buffer-to-surface method
	 */
	@SuppressLint({ "InlinedApi", "NewApi" })	
	protected void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {

		Log.d(TAG,"Video encoded using the MediaCodec API with a surface");

		// Updates the parameters of the camera if needed
		//createCamera();
		//updateCamera();

		// Estimates the frame rate of the camera
		//measureFramerate();

		/*
		EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);

		mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);	
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		Surface surface = mMediaCodec.createInputSurface();
		VideoPacketizerDispatcher.mEncoderSurface = surface;
		//((SurfaceView)mSurfaceView).addMediaCodecSurface(surface);
		mMediaCodec.start();

		StreamActivity.startCamer(surface);

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mPacketizer.start();

		mStreaming = true;

		 */

		VideoPacketizerDispatcher.subscribe(mPacketizer);
		mStreaming = true;

	}

	/**
	 * Returns a description of the stream using SDP. 
	 * This method can only be called after {@link Stream#configure()}.
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */	
	public abstract String getSessionDescription() throws IllegalStateException;


	/**
	 * Video encoding is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException, ConfNotSupportedException {

		throw new RuntimeException("Encode with MediaRecorder not implemented");
		/*
		Log.d(TAG,"Video encoded using the MediaRecorder API");

		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		// Reopens the camera if needed
		destroyCamera();
		createCamera();

		// The camera must be unlocked before the MediaRecorder can use it
		unlockCamera();

		try {
			mMediaRecorder = new MediaRecorder();
			mMediaRecorder.setCamera(mCamera);
			mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mMediaRecorder.setVideoEncoder(mVideoEncoder);
			mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
			mMediaRecorder.setVideoSize(mRequestedQuality.resX,mRequestedQuality.resY);
			mMediaRecorder.setVideoFrameRate(mRequestedQuality.framerate);

			// The bandwidth actually consumed is often above what was requested
			mMediaRecorder.setVideoEncodingBitRate((int)(mRequestedQuality.bitrate*0.8));

			// We write the output of the camera in a local socket instead of a file !
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			FileDescriptor fd = null;
			if (sPipeApi == PIPE_API_PFD) {
				fd = mParcelWrite.getFileDescriptor();
			} else  {
				fd = mSender.getFileDescriptor();
			}
			mMediaRecorder.setOutputFile(fd);

			mMediaRecorder.prepare();
			mMediaRecorder.start();

		} catch (Exception e) {
			throw new ConfNotSupportedException(e.getMessage());
		}

		InputStream is = null;

		if (sPipeApi == PIPE_API_PFD) {
			is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
		} else  {
			is = mReceiver.getInputStream();
		}

		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		try {
			byte[] buffer = new byte[4];
			// Skip all atoms preceding mdat atom
			while (!Thread.interrupted()) {
				while (is.read() != 'm');
				is.read(buffer,0,3);
				if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
			}
		} catch (IOException e) {
			Log.e(TAG,"Couldn't skip mp4 header :/");
			stop();
			throw e;
		}

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(is);
		mPacketizer.start();

		mStreaming = true;
		*/
	}


	/*
	 * Computes the average frame rate at which the preview callback is called.
	 * We will then use this average frame rate with the MediaCodec.  
	 * Blocks the thread in which this function is called.

	private void measureFramerate() {
		final Semaphore lock = new Semaphore(0);

		final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			int i = 0, t = 0;
			long now, oldnow, count = 0;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				i++;
				now = System.nanoTime()/1000;
				if (i>3) {
					t += now - oldnow;
					count++;
				}
				if (i>20) {
					mQuality.framerate = (int) (1000000/(t/count)+1);
					lock.release();
				}
				oldnow = now;
			}
		};

		mCamera.setPreviewCallback(callback);

		try {
			lock.tryAcquire(2,TimeUnit.SECONDS);
			Log.d(TAG,"Actual framerate: "+mQuality.framerate);
			if (mSettings != null) {
				Editor editor = mSettings.edit();
				editor.putInt(PREF_PREFIX+"fps"+mRequestedQuality.framerate+","+mCameraImageFormat+","+mRequestedQuality.resX+mRequestedQuality.resY, mQuality.framerate);
				editor.commit();
			}
		} catch (InterruptedException e) {}

		mCamera.setPreviewCallback(null);

	}	
	 */
}
