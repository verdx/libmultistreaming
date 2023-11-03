package d2d.testing.streaming.sessions;

import android.net.Network;
import android.os.HandlerThread;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import static java.util.UUID.randomUUID;

public class ReceiveSession {

    public final static String TAG = "ReceiveSession";

    private InetAddress mOrigin;
    private InetAddress mDestination;
    private int mDestPort;
    private int mTimeToLive = 64;
    public final String mSessionID;
    private TrackInfo mVideoTrackInfo;
    private TrackInfo mAudioTrackInfo;
    private String path;
    public String mStreamingName;
    private Network mReceiveNet;



    private byte[] mProofFileByteArr;

    private String mProofFilename;


    /**
     * Creates a streaming session that can be customized by adding tracks.
     */
    public ReceiveSession() {
        long uptime = System.currentTimeMillis();

        HandlerThread thread = new HandlerThread("d2d.testing.streaming.sessions.Session");
        thread.start();

        mSessionID = randomUUID().toString();
        path = "";
        mReceiveNet = null;
    }

    /**
     * The origin address of the session.
     * It appears in the session description.
     */
    public void setOrigin(InetAddress origin) {
        mOrigin = origin;
    }

    /**
     * The origin address of the session.
     * It appears in the session description.
     */
    public InetAddress getOriginAddress() {
        return mOrigin;
    }

    /**
     * The destination address for all the streams of the session. <br />
     * Changes will be taken into account the next time you start the session.
     * @param destination The destination address
     */
    public void setDestination(InetAddress destination) {
        mDestination =  destination;
    }

    public void setDestinationPort(int port){
        mDestPort = port;
    }

    public void setStreamingName(String name) {
        mStreamingName = name;
    }

    public String getStreamingName(){
        return mStreamingName;
    }

    public int getDestinationPort(){
        return mDestPort;
    }

    public void setReceiveNet(Network net){
        mReceiveNet = net;
    }

    public Network getReceiveNet(){
        return mReceiveNet;
    }

    public String getSessionID() {
        return mSessionID;
    }

    public InetAddress getDestinationAddress() {
        return mDestination;
    }




    public byte[] getProofByteArr() {
        return mProofFileByteArr;
    }

    public void setProofArr(byte[] proof) {
        this.mProofFileByteArr = proof;
    }

    /**
     * Asynchronously starts all streams of the session.
     **/
    public void start() throws IOException {
        if(trackExists(0)) {
            mAudioTrackInfo.startServer(mReceiveNet);
        }
        if(trackExists(1)) {
            mVideoTrackInfo.startServer(mReceiveNet);
        }
    }
    /** Stops all existing streams. */
    public void stop() {
        if(trackExists(0)) {
            mAudioTrackInfo.stopServer();
        }
        if(trackExists(1)) {
            mVideoTrackInfo.stopServer();
        }
    }

    public boolean trackExists(int id) {
        if (id==0)
            return mAudioTrackInfo!=null;
        else
            return mVideoTrackInfo!=null;
    }

    public void addVideoTrack(TrackInfo track) {
        mVideoTrackInfo = track;
    }

    public void addAudioTrack(TrackInfo track) {
        mAudioTrackInfo = track;
    }

    public TrackInfo getTrack(int id) {
        if (id==0)
            return mAudioTrackInfo;
        else
            return mVideoTrackInfo;
    }

    public void release(){

    }

    public String getTimeout() {
        return "60";
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setProofFilename(String filename) {
        mProofFilename = filename;
    }

    public String getProofFilename() {
        return mProofFilename;
    }
}
