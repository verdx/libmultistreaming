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

package d2d.testing.streaming.rtsp;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import d2d.testing.streaming.network.INetworkManager;
import d2d.testing.streaming.network.ProofManager;
import d2d.testing.streaming.Stream;
import d2d.testing.streaming.Streaming;
import d2d.testing.streaming.StreamingRecord;
import d2d.testing.streaming.StreamingRecordObserver;
import d2d.testing.streaming.rtp.RtpSocket;
import d2d.testing.streaming.sessions.RebroadcastSession;
import d2d.testing.streaming.sessions.Session;
import d2d.testing.streaming.sessions.SessionBuilder;

/**
 * RFC 2326.
 * A basic and asynchronous RTSP client.
 * The original purpose of this class was to implement a small RTSP client compatible with Wowza.
 * It implements Digest Access Authentication according to RFC 2069. 
 */
public class RtspClient implements StreamingRecordObserver {

	public final static String TAG = "RtspClient";

	/** Message sent when the connection to the RTSP server failed. */
	public final static int ERROR_CONNECTION_FAILED = 0x01;

	public final static int ERROR_NETWORK_LOST = 0x02;

	/** Message sent when the credentials are wrong. */
	public final static int ERROR_WRONG_CREDENTIALS = 0x03;


	/** Use this to use UDP for the transport protocol. */
	public final static int TRANSPORT_UDP = RtpSocket.TRANSPORT_UDP;

	/** Use this to use TCP for the transport protocol. */
	public final static int TRANSPORT_TCP = RtpSocket.TRANSPORT_TCP;

	/**
	 * Message sent when the connection with the RTSP server has been lost for
	 * some reason (for example, the user is going under a bridge).
	 * When the connection with the server is lost, the client will automatically try to
	 * reconnect as long as {@link #stop()} is not called.
	 **/
	public final static int ERROR_CONNECTION_LOST = 0x04;

	/**
	 * Message sent when the connection with the RTSP server has been reestablished.
	 * When the connection with the server is lost, the client will automatically try to
	 * reconnect as long as {@link #stop()} is not called.
	 */
	public final static int MESSAGE_CONNECTION_RECOVERED = 0x05;

	protected final static int STATE_STARTED = 0x00;

	protected final static int STATE_STOPPING = 0x01;
	protected final static int STATE_STOPPED = 0x02;
	protected int mState = 0;

	protected final static int MAX_NETWORK_REQUESTS = 100;

	protected UUID mLocalStreamingUUID = null;
	String mLocalStreamingName = null;
	protected Session mLocalStreamingSession;
	protected Map<UUID, RebroadcastSession> mRebroadcastStreamings;

	protected Socket mSocket;
	protected BufferedReader mBufferedReader;
	protected OutputStream mOutputStream;
	protected Callback mCallback;
	protected final Handler mMainHandler;
	protected Handler mHandler;

	protected int mTotalNetworkRequests;
	protected SessionBuilder mSessionBuilder;
	protected INetworkManager mNetworkManager;
	protected Parameters mTmpParameters;
	protected Parameters mParameters;
	protected StreamingState mLocalStreamingState;
	protected Map<UUID, StreamingState> mRebroadcastStreamingStates;
	/**
	 * The callback interface you need to implement to know what's going on with the
	 * RTSP server (for example your Wowza Media Server).
	 */

	protected class Parameters {
		public String host;
		public String username;
		public String password;

//		public String path;
//		public Session session;

		public int port;
		public int transport;

		public Parameters clone() {
			Parameters params = new Parameters();
			params.host = host;
			params.username = username;
			params.password = password;

			//params.path = path;
			//params.session = session;

			params.port = port;
			params.transport = transport;
			return params;
		}
	}

	protected class StreamingState{
		public int mCSeq;
		public String mSessionID;
		public String mAuthorization;
		public StreamingState(){
			mCSeq = 0;
			mAuthorization = null;
			mSessionID = null;
		}
	}


	public interface Callback {
		void onRtspUpdate(int message, Exception exception);
	}

	public RtspClient(INetworkManager netMana) {
		mTmpParameters = new Parameters();
		mTmpParameters.port = 1935;

		mTmpParameters.transport = TRANSPORT_UDP;

		mCallback = null;
		mMainHandler = new Handler(Looper.getMainLooper());
		mState = STATE_STOPPED;

		mNetworkManager = netMana;

		final Semaphore signal = new Semaphore(0);
		new HandlerThread("d2d.testing.streaming.RtspClient"){
			@Override
			protected void onLooperPrepared() {
				mHandler = new Handler();
				signal.release();
			}
		}.start();
		signal.acquireUninterruptibly();

		mRebroadcastStreamingStates = new HashMap<>();
		mRebroadcastStreamings = new HashMap<>();
		mTotalNetworkRequests = 0;
	}

	/**
	 * Sets the callback interface that will be called on status updates of the connection
	 * with the RTSP server.
	 * @param cb The implementation of the {@link Callback} interface
	 */
	public void setCallback(Callback cb) {
		mCallback = cb;
	}

	/**
	 * The {@link Session} that will be used to stream to the server.
	 * If not called before startStream(), a it will be created.

	 public void setSession(Session session) {
	 mTmpParameters.session = session;
	 }

	 public Session getSession() {
	 return mTmpParameters.session;
	 }

	 /**
	 * The path to which the stream will be sent to.

	 public void setStreamPath(String path) {
	 mTmpParameters.path = path;
	 }
	 */

	public void setSessionBuilder(SessionBuilder builder){
		mSessionBuilder = builder;
	}

	/**
	 * Sets the destination address of the RTSP server.
	 * @param host The destination address
	 * @param port The destination port
	 */
	public void setServerAddress(String host, int port) {
		mTmpParameters.port = port;
		mTmpParameters.host = host;
	}

	/**
	 * If authentication is enabled on the server, you need to call this with a valid login/password pair.
	 * Only implements Digest Access Authentication according to RFC 2069.
	 * @param username The login
	 * @param password The password
	 */
	public void setCredentials(String username, String password) {
		mTmpParameters.username = username;
		mTmpParameters.password = password;
	}


	/**
	 * Call this with {@link #TRANSPORT_TCP} or {@value #TRANSPORT_UDP} to choose the
	 * transport protocol that will be used to send RTP/RTCP packets.
	 * Not ready yet !
	 */
	public void setTransportMode(int mode) {
		mTmpParameters.transport = mode;
	}

	public boolean isStreaming() {
		return mState==STATE_STARTED;
	}

	/*
		En un thread, crea solicitud de red wfa
		Esta funcionalidad se podría desacoplar para que sea más generalizado
	 */

	public void connectionCreated(){
		mTotalNetworkRequests = 0;
		mState = STATE_STARTED;
	}


	public boolean isConnected(){
		if(mSocket!=null){
			String aux = "req";
			try {
				mOutputStream.write(aux.getBytes("UTF-8"));
				mOutputStream.flush();
			} catch (SocketException e){
				return false;
			} catch (IOException e) {
				return false;
			}

			return !mSocket.isClosed();

		}
		return false;
	}


	/*
		Obtiene red y crea socket a partir de ella
	 */
	public void start(){
		mHandler.post(new Runnable () {
			@Override
			public void run() {

				if(mState == STATE_STARTED) {

					try {
						String peerAddr = mTmpParameters.host;
						int peerPort = mTmpParameters.port;

						Log.d(TAG,"Connecting to RTSP server...");

						mSocket = new Socket(peerAddr, peerPort);
						mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
						mOutputStream = new BufferedOutputStream(mSocket.getOutputStream());
						// If the user calls some methods to configure the client, it won't modify
						// its behavior until the stream is restarted

						mParameters = mTmpParameters.clone();

						if (mParameters.transport == TRANSPORT_UDP) {
							mHandler.post(mConnectionMonitor);
						}
						StreamingRecord.getInstance().addObserver(RtspClient.this);
					} catch (IOException e) {
						Log.e(TAG,"Failed to connect to RTSP server", e);
						postError(ERROR_CONNECTION_FAILED, e);

						// Start mete un ejecutable a la cola de un hilo. No es recursivo llamar start dentro de otro.
						//Para la versión sin WFA, se reintentará la conexión en unos segundos
					}
				}
			}
		});
	}

	protected void onFailedStart(){
		start();
	}

	/**
	 * Stops the stream, and informs the RTSP server.
	 */
	public void stop() { //Restaurar para poder hacer onConnectionCreated
		mHandler.post(new Runnable () {
			@Override
			public void run() {
				restartClient();
			}
		});
	}

	public void release() {
		stop();
		mHandler.getLooper().quitSafely();
	}


	protected void restartClient(){
		StreamingRecord.getInstance().removeObserver(this);
		closeConnections();
		clearClient();
	}

	private void closeLocalStreaming(){
		if(mLocalStreamingUUID != null){
			try {
				sendRequestTeardown(mLocalStreamingState, mLocalStreamingUUID.toString());
			} catch (Exception ignore) {}
			if(mLocalStreamingSession != null){
				if (mLocalStreamingSession.isStreaming()) {
					mLocalStreamingSession.syncStop();
				}
				mLocalStreamingSession.release();
			}
		}
		mLocalStreamingSession = null;
		mLocalStreamingState = null;
		mLocalStreamingUUID = null;
		mLocalStreamingName = null;
		mSessionBuilder = null;
	}

	private void closeStreaming(UUID id){
		RebroadcastSession session = mRebroadcastStreamings.remove(id);
		if(session != null){
			try {
				sendRequestTeardown(mRebroadcastStreamingStates.remove(id), id.toString());
			} catch (Exception ignore) {}
			session.stop();
		}
	}

	private void closeConnections(){
		closeLocalStreaming();
		for(Map.Entry<UUID, RebroadcastSession> entry : mRebroadcastStreamings.entrySet()){
			try {
				sendRequestTeardown(mRebroadcastStreamingStates.get(entry.getKey()), entry.getKey().toString());
			} catch (Exception ignore) {}
			entry.getValue().stop();
		}
		mRebroadcastStreamings.clear();
		mRebroadcastStreamingStates.clear();
	}


	protected void clearClient(){
		mState = STATE_STOPPED;

		try {
			if(mSocket != null) mSocket.close();
		} catch (Exception ignore) {}
		mSocket = null;
		mBufferedReader = null;
		mOutputStream = null;

		mCallback = null;
		mHandler.removeCallbacks(mConnectionMonitor);
	}

	@Override
	public void localStreamingAvailable(final UUID id, final String name, final SessionBuilder sessionBuilder) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mLocalStreamingUUID = id;
				mSessionBuilder = sessionBuilder;
				mLocalStreamingName = name;
				mLocalStreamingState = new StreamingState();
				sendLocalStreaming();
			}
		});
	}

	@Override
	public void localStreamingUnavailable() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				closeLocalStreaming();
			}
		});
	}


	@Override
	public void streamingAvailable(final Streaming streaming, final boolean bAllowDispatch) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				UUID streamingUUID = streaming.getUUID();
				StreamingState st = mRebroadcastStreamingStates.get(streamingUUID);
				if(!bAllowDispatch){
					if(st != null){
						closeStreaming(streamingUUID);
					}
				}
				else{
					if(st == null){
						st = new StreamingState();
						RebroadcastSession session = new RebroadcastSession();
						session.setServerSession(streaming.getReceiveSession());

						mRebroadcastStreamings.put(streamingUUID, session);
						mRebroadcastStreamingStates.put(streamingUUID, st);
						sendStreaming(streamingUUID);
					}
				}
			}
		});
	}

	@Override
	public void streamingUnavailable(final Streaming streaming) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				UUID streamingUUID = streaming.getUUID();
				StreamingState st = mRebroadcastStreamingStates.get(streamingUUID);
				if(st != null){
					closeStreaming(streamingUUID);
				}
			}
		});
	}

	@Override
	public void streamingDownloadStateChanged(Streaming streaming, boolean bIsDownload) {

	}

	protected void sendLocalStreaming(){
		if(mState == STATE_STARTED){
			try {
				mLocalStreamingSession = mSessionBuilder.build();
				mLocalStreamingSession.setNameStreaming(mLocalStreamingName);
				mLocalStreamingSession.setDestinationAddress(InetAddress.getByName(mParameters.host), true);
				mLocalStreamingSession.setDestinationPort(mParameters.port);
				mLocalStreamingSession.setOriginAddress(mSocket.getLocalAddress(), true);
				mLocalStreamingSession.syncConfigure();
			} catch (Exception e) {
				mLocalStreamingSession = null;
				return;
			}

			try {
				tryLocalStreamingConnection();
			}catch(SecurityException e){ //Credenciales de conexion invalidas
				postError(ERROR_WRONG_CREDENTIALS, new Exception("Credenciales invalidas para streaming " + mLocalStreamingUUID.toString(), e));
				mLocalStreamingSession = null;
				return;
			}
			catch (IOException e) { //Se perdio la conexion con el RTSPServer
				postError(ERROR_CONNECTION_FAILED, e);
				restartClient();
				return;
			}
			catch(IllegalStateException e){ //Fallo en protocolo o en configuracion del cliente
				restartClient();
				return;
			}
			catch(RuntimeException e){ //El servidor rechazo el envio
				//Como de momento solo rechaza por bucles no volvemos a intentar el envio
				mLocalStreamingSession = null;
				return;
			}
			try {
				mLocalStreamingSession.syncStart();
			} catch (Exception e) { //Se perdio la conexion con el RTSPServer
				postError(ERROR_CONNECTION_FAILED, e);
				restartClient();
			}
		}
		else{
			postError(ERROR_NETWORK_LOST, null);
			restartClient();
		}
	}

	private void sendStreaming(UUID streamUUID){
		if(mState == STATE_STARTED){
			StreamingState st = mRebroadcastStreamingStates.get(streamUUID);
			RebroadcastSession session = mRebroadcastStreamings.get(streamUUID);
			try {
				session.setDestinationAddress(InetAddress.getByName(mParameters.host), true);
				session.setOriginAddress(mSocket.getLocalAddress(), true);
			} catch (Exception e) {
				mRebroadcastStreamingStates.remove(streamUUID);
				mRebroadcastStreamings.remove(streamUUID);
			}

			try {
				tryConnection(st, streamUUID.toString(), session);
				session.startTrack(0);	//0=audio
				session.startTrack(1);	//1=video
			}catch(SecurityException e){ //Credenciales de conexion invalidas
				postError(ERROR_WRONG_CREDENTIALS, new Exception("Credenciales invalidas para streaming " + streamUUID.toString(), e));
				mRebroadcastStreamingStates.remove(streamUUID);
				mRebroadcastStreamings.remove(streamUUID);
			}
			catch (IOException e) { //Se perdio la conexion con el RTSPServer
				postError(ERROR_CONNECTION_FAILED, e);
				restartClient();
			}
			catch(IllegalStateException e){ //Fallo en protocolo o en configuracion del cliente
				restartClient();
			}
			catch(RuntimeException e){ //El servidor rechazo el envio
				//Como de momento solo rechaza por bucles no volvemos a intentar el envio
				mRebroadcastStreamingStates.remove(streamUUID);
				mRebroadcastStreamings.remove(streamUUID);
				session.stop();
			}
		}
		else{
			postError(ERROR_NETWORK_LOST, null);
			restartClient();
		}
	}

	/*
		LocalClient --> LocalServer
	 */
	private void tryLocalStreamingConnection() throws SecurityException, IOException, IllegalStateException, RuntimeException {
		mLocalStreamingState.mCSeq = 0;
		String path = mLocalStreamingUUID.toString();
		Log.d(TAG, "pipi" +  path);

//		sendProofFile();

		sendRequestAnnounce(mLocalStreamingState, path, mLocalStreamingSession.getSessionDescription());
		sendRequestSetup(mLocalStreamingState, path, mLocalStreamingSession.getTrack(0), 0);
		sendRequestSetup(mLocalStreamingState, path, mLocalStreamingSession.getTrack(1), 1);
		sendRequestRecord(mLocalStreamingState, path);
	}

	private void tryConnection(StreamingState st, String path, RebroadcastSession session) throws IOException {
		st.mCSeq = 0;
		sendRequestAnnounce(st, path, session.getSessionDescription());
		sendRequestSetup(st, path, session, 0);
		sendRequestSetup(st, path, session, 1);
		sendRequestRecord(st, path);
	}




	private void sendProofFile(){
		File proofFile = ProofManager.getInstance().getProofZipFile();

		try {
			FileInputStream fis = new FileInputStream(proofFile);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			// Read the contents of the ZIP file into a byte array
			byte[] buffer = new byte[1024];
			int len;
			while ((len = fis.read(buffer)) > 0) {
				baos.write(buffer, 0, len);
			}

			// Encode the byte array as a Base64 string
			String encodedData = Base64.getEncoder().encodeToString(baos.toByteArray());

			mOutputStream.write(encodedData.getBytes(StandardCharsets.UTF_8));
			mOutputStream.flush();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}





	/**
	 * Forges and sends the ANNOUNCE request
	 */
	//IOException fallo de conexion
	//IllegalStateException fallo en protocolo o configuracion de cliente
	//
	private void sendRequestAnnounce(StreamingState st, String path, String sessionDesc) throws SecurityException, IOException, IllegalStateException, RuntimeException{
		String body = sessionDesc;
		String request = "ANNOUNCE rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+" RTSP/1.0\r\n" +
				"CSeq: " + (++st.mCSeq) + "\r\n" +
				"Content-Length: " + body.length() + "\r\n" +
				"Content-Type: application/sdp\r\n\r\n" +
				body;
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response response = Response.parseResponse(mBufferedReader);

		if (response.headers.containsKey("server")) {
			Log.v(TAG,"RTSP server name:" + response.headers.get("server"));
		} else {
			Log.v(TAG,"RTSP server name unknown");
		}

		if (response.headers.containsKey("session")) {
			try {
				Matcher m = Response.rexegSession.matcher(response.headers.get("session"));
				m.find();
				st.mSessionID = m.group(1);
			} catch (Exception e) {
				throw new IllegalStateException("Invalid response from server. Session id: "+st.mSessionID);
			}
		}

		if (response.status == 401) {
			String nonce, realm;
			Matcher m;

			if (mParameters.username == null || mParameters.password == null) throw new IllegalStateException("Authentication is enabled and setCredentials(String,String) was not called !");

			try {
				m = Response.rexegAuthenticate.matcher(response.headers.get("www-authenticate")); m.find();
				nonce = m.group(2);
				realm = m.group(1);
			} catch (Exception e) {
				throw new IllegalStateException("Invalid response from server");
			}

			String uri = "rtsp://"+mParameters.host+":"+mParameters.port+"/"+path;
			String hash1 = computeMd5Hash(mParameters.username+":"+m.group(1)+":"+mParameters.password);
			String hash2 = computeMd5Hash("ANNOUNCE"+":"+uri);
			String hash3 = computeMd5Hash(hash1+":"+m.group(2)+":"+hash2);

			st.mAuthorization = "Digest username=\""+mParameters.username+"\",realm=\""+realm+"\",nonce=\""+nonce+"\",uri=\""+uri+"\",response=\""+hash3+"\"";

			request = "ANNOUNCE rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+" RTSP/1.0\r\n" +
					"CSeq: " + (++st.mCSeq) + "\r\n" +
					"Content-Length: " + body.length() + "\r\n" +
					"Authorization: " + st.mAuthorization + "\r\n" +
					"Session: " + st.mSessionID + "\r\n" +
					"Content-Type: application/sdp\r\n\r\n" +
					body;

			Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

			mOutputStream.write(request.getBytes("UTF-8"));
			mOutputStream.flush();
			response = Response.parseResponse(mBufferedReader);

			if (response.status == 401) throw new SecurityException("Bad credentials !");

		} else if (response.status == 403) {
			Log.d(TAG, "Streaming " + path + " refused by server");
			throw new RuntimeException("Streaming " + path + " refused by server");
		}
	}

	/**
	 * Forges and sends the SETUP request
	 */
	private void sendRequestSetup(StreamingState st, String path, Stream stream, int trackNo) throws IllegalStateException, IOException {
		if (stream != null) {
			String params = mParameters.transport==TRANSPORT_TCP ?
					("TCP;interleaved="+2*trackNo+"-"+(2*trackNo+1)) : ("UDP;unicast;client_port="+(5000+2*trackNo)+"-"+(5000+2*trackNo+1)+";mode=receive");
			String request = "SETUP rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+"/trackID="+trackNo+" RTSP/1.0\r\n" +
					"Transport: RTP/AVP/"+params+"\r\n" +
					addHeaders(st);

			Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

			mOutputStream.write(request.getBytes("UTF-8"));
			mOutputStream.flush();
			Response response = Response.parseResponse(mBufferedReader);
			Matcher m;

			if (response.headers.containsKey("session")) {
				try {
					m = Response.rexegSession.matcher(response.headers.get("session"));
					m.find();
					st.mSessionID = m.group(1);
				} catch (Exception e) {
					throw new IllegalStateException("Invalid response from server. Session id: "+st.mSessionID);
				}
			}

			if (mParameters.transport == TRANSPORT_UDP) {
				try {
					m = Response.rexegTransport.matcher(response.headers.get("transport")); m.find();
					stream.setDestinationPorts(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
					Log.d(TAG, "Setting destination ports: "+Integer.parseInt(m.group(3))+", "+Integer.parseInt(m.group(4)));
				} catch (Exception e) {
					e.printStackTrace();
					int[] ports = stream.getDestinationPorts();
					Log.d(TAG,"Server did not specify ports, using default ports: "+ports[0]+"-"+ports[1]);
				}
			} else {
				stream.setOutputStream(mOutputStream, (byte)(2*trackNo));
			}
		}
	}

	/**
	 * Forges and sends the SETUP request
	 */
	private void sendRequestSetup(StreamingState st, String path, RebroadcastSession session, int trackNo) throws IllegalStateException, IOException {
		if (session.serverTrackExists(trackNo)) {
			String params = mParameters.transport==TRANSPORT_TCP
					? ("TCP;interleaved="+2*trackNo+"-"+(2*trackNo+1))
					: ("UDP;unicast;client_port="+(5000+2*trackNo)+"-"+(5000+2*trackNo+1)+";mode=receive");
			String request = "SETUP rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+"/trackID="+trackNo+" RTSP/1.0\r\n" +
					"Transport: RTP/AVP/"+params+"\r\n" +
					addHeaders(st);

			Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

			mOutputStream.write(request.getBytes("UTF-8"));
			mOutputStream.flush();
			Response response = Response.parseResponse(mBufferedReader);
			Matcher m;

			if (response.headers.containsKey("session")) {
				try {
					m = Response.rexegSession.matcher(response.headers.get("session"));
					m.find();
					st.mSessionID = m.group(1);
				} catch (Exception e) {
					throw new IllegalStateException("Invalid response from server. Session id: "+st.mSessionID);
				}
			}
			RebroadcastSession.RebroadcastTrackInfo rebroadcastTrackInfo = session.getRebroadcastTrack(trackNo);

			if (mParameters.transport == TRANSPORT_UDP) {
				try {
					m = Response.rexegTransport.matcher(response.headers.get("transport")); m.find();
					rebroadcastTrackInfo.setRemotePorts(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
					Log.d(TAG, "Setting destination ports: "+Integer.parseInt(m.group(3))+", "+Integer.parseInt(m.group(4)));
				} catch (Exception e) {
					e.printStackTrace();
					int[] ports = rebroadcastTrackInfo.getRemotePorts();
					Log.d(TAG,"Server did not specify ports, using default ports: "+ports[0]+"-"+ports[1]);
				}
			} else {
				throw new IOException("TCP no implementado");
			}
		}
	}

	/**
	 * Forges and sends the RECORD request
	 */
	private void sendRequestRecord(StreamingState st, String path) throws IOException, RuntimeException{
		String request = "RECORD rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+" RTSP/1.0\r\n" +
				"Range: npt=0.000-\r\n" +
				addHeaders(st);
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response response =  Response.parseResponse(mBufferedReader);
		if (response.status == 403) {
			Log.d(TAG, "Streaming " + path + " refused by server");
			throw new RuntimeException("Streaming " + path + " refused by server");
		}
	}

	/**
	 * Forges and sends the TEARDOWN request
	 */
	private void sendRequestTeardown(StreamingState st, String path) throws IOException {
		String request = "TEARDOWN rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+" RTSP/1.0\r\n" + addHeaders(st);
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response.parseResponse(mBufferedReader);
	}

	/**
	 * Forges and sends the OPTIONS request
	 */
	private void sendRequestOption(StreamingState st, String path) throws IOException {
		String request = "OPTIONS rtsp://"+mParameters.host+":"+mParameters.port+"/"+path+" RTSP/1.0\r\n" + addHeaders(st);
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response.parseResponse(mBufferedReader);
	}

	private String addHeaders(StreamingState st) {
		return "CSeq: " + (++st.mCSeq) + "\r\n" +
				"Content-Length: 0\r\n" +
				(st.mSessionID != null ? "Session: " + st.mSessionID + "\r\n" : "") +
				// For some reason you may have to remove last "\r\n" in the next line to make the RTSP client work with your wowza server :/
				(st.mAuthorization != null ? "Authorization: " + st.mAuthorization + "\r\n":"") + "\r\n";
	}

	/**
	 * If the connection with the RTSP server is lost, we try to reconnect to it as
	 * long as {@link #stop()} is not called.
	 */
	protected Runnable mConnectionMonitor = new Runnable() {
		@Override
		public void run() {
			if (mState == STATE_STARTED) {
				try {
					// We poll the RTSP server with OPTION requests
					StreamingState st = new StreamingState();
					sendRequestOption(st, "");
					mHandler.postDelayed(mConnectionMonitor, 15000);
				} catch (IOException e) {
					// Happens if the OPTION request fails
					postError(ERROR_CONNECTION_LOST, null);
					Log.e(TAG, "Connection lost with the server...");
					restartClient();
				}
			}
		}
	};

	final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for ( int j = 0; j < bytes.length; j++ ) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/** Needed for the Digest Access Authentication. */
	private String computeMd5Hash(String buffer) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			return bytesToHex(md.digest(buffer.getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException ignore) {
		} catch (UnsupportedEncodingException e) {}
		return "";
	}

	private void postMessage(final int message) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onRtspUpdate(message, null);
				}
			}
		});
	}

	protected void postError(final int message, final Exception e) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onRtspUpdate(message, e);
				}
				Log.e(TAG, String.valueOf(message));
				if(e != null){
					Log.e(TAG, e.getLocalizedMessage());
					e.printStackTrace();
				}

			}
		});
	}

	static class Response {

		// Parses method & uri
		public static final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)",Pattern.CASE_INSENSITIVE);
		// Parses a request header
		public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);
		// Parses a WWW-Authenticate header
		public static final Pattern rexegAuthenticate = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"",Pattern.CASE_INSENSITIVE);
		// Parses a Session header
		public static final Pattern rexegSession = Pattern.compile("(\\d+)",Pattern.CASE_INSENSITIVE);
		// Parses a Transport header
		public static final Pattern rexegTransport = Pattern.compile("client_port=(\\d+)-(\\d+).+server_port=(\\d+)-(\\d+)",Pattern.CASE_INSENSITIVE);

		public static final Pattern rexegCustomMeta = Pattern.compile("my-custom-metadata=(\\S+)",Pattern.CASE_INSENSITIVE);


		public int status;
		public Map<String,String> headers = new HashMap<>();

		/** Parse the method, URI & headers of a RTSP request */
		public static Response parseResponse(BufferedReader input) throws IOException, IllegalStateException, SocketException {
			Response response = new Response();
			String line;
			Matcher matcher;
			// Parsing request method & URI
			if ((line = input.readLine())==null) throw new SocketException("Connection lost");
			matcher = regexStatus.matcher(line);
			matcher.find();
			response.status = Integer.parseInt(matcher.group(1));

			// Parsing headers of the request
			while ( (line = input.readLine()) != null) {
				Log.d(TAG,"l: "+line.length()+", c: "+line);
				if (line.length()>3) {
					matcher = rexegHeader.matcher(line);
					matcher.find();
					response.headers.put(matcher.group(1).toLowerCase(Locale.US),matcher.group(2));
				}
				if(line.length() == 0) break;
			}
			if (line==null) throw new SocketException("Connection lost");

			Log.d(TAG, "Response from server: "+response.status);

			return response;
		}
	}

}
