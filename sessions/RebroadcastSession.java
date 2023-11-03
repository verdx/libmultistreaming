package d2d.testing.streaming.sessions;

import android.os.HandlerThread;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectableChannel;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.UUID.randomUUID;

public class RebroadcastSession {

    public final static String TAG = "ReceiveSession";

    private InetAddress mOrigin;
    private boolean mOriginIPv6;
    private InetAddress mDestination;
    private boolean mDestIPv6;
    private int mTimeToLive = 64;
    private long mTimestamp;
    public final String mSessionID;
    private RebroadcastTrackInfo mVideoRebroadcastTrackInfo;
    private RebroadcastTrackInfo mAudioRebroadcastTrackInfo;
    private ReceiveSession mReceiveSession;
    private SelectableChannel rtcpVideoTrackChannel;
    private SelectableChannel rtpVideoTrackChannel;
    private SelectableChannel rtcpAudioTrackChannel;
    private SelectableChannel rtpAudioTrackChannel;
    private SelectableChannel mRtspChannel;
    public String mStreamingName;

    /**
     * Creates a streaming session that can be customized by adding tracks.
     */
    public RebroadcastSession() {
        long uptime = System.currentTimeMillis();

        HandlerThread thread = new HandlerThread("d2d.testing.streaming.sessions.Session");
        thread.start();

        mTimestamp = (uptime/1000)<<32 & (((uptime-((uptime/1000)*1000))>>32)/1000); // NTP timestamp
        try {
            mOrigin = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {}
        mSessionID = randomUUID().toString();
        mVideoRebroadcastTrackInfo = new RebroadcastTrackInfo();
        mAudioRebroadcastTrackInfo = new RebroadcastTrackInfo();
    }

    /**
     * The origin address of the session.
     * It appears in the session description.
     * @param origin The origin address
     */
    public void setOriginAddress(InetAddress origin, boolean isIPv6) {
        mOrigin = origin;
        mOriginIPv6 = isIPv6;
    }

    /**
     * The destination address for all the streams of the session. <br />
     * Changes will be taken into account the next time you start the session.
     * @param destination The destination address
     */
    public void setDestinationAddress(InetAddress destination, boolean isIPv6) {
        mDestination =  destination;
        mDestIPv6 = isIPv6;
    }

    public void setNameStreaming(String name){
        mStreamingName = name;
    }

    public String getPath(){
        if(mReceiveSession != null){
            return mReceiveSession.getPath();
        }
        return null;
    }

    public void setRtspChannel(SelectableChannel chan){
        mRtspChannel = chan;
    }

    public SelectableChannel getRtspChannel(){
        return mRtspChannel;
    }

    /**
     * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
     * @return The Session Description.
     * @throws IllegalStateException Thrown when {@link #setDestinationAddress(InetAddress, boolean)} (String)} has never been called.
     */
    public String getSessionDescription() {
        final Pattern regexAudioDescription = Pattern.compile("m=audio (\\d+) (.*)", Pattern.CASE_INSENSITIVE);
        final Pattern regexVideoDescription = Pattern.compile("m=video (\\d+) (.*)", Pattern.CASE_INSENSITIVE);

        StringBuilder sessionDescription = new StringBuilder();
        sessionDescription.append("v=0\r\n");
        // TODO: Add IPV6 support
        if(mOriginIPv6) sessionDescription.append("o=- "+mTimestamp+" "+mTimestamp+" IN IP6 "+mOrigin.getHostAddress()+"\r\n");
        else sessionDescription.append("o=- "+mTimestamp+" "+mTimestamp+" IN IP4 "+mOrigin.getHostAddress()+"\r\n");
        sessionDescription.append("s="+ mStreamingName + "\r\n");
        sessionDescription.append("i=N/A\r\n");
        if(mDestIPv6) sessionDescription.append("c=IN IP6 "+mDestination.getHostAddress()+"\r\n");
        else sessionDescription.append("c=IN IP4 "+mDestination.getHostAddress()+"\r\n");
        // t=0 0 means the session is permanent (we don't know when it will stop)
        sessionDescription.append("t=0 0\r\n");
        sessionDescription.append("a=recvonly\r\n");

        if(serverTrackExists(0)) {
            Matcher m = regexAudioDescription.matcher(getServerTrack(0).getSessionDescription());
            sessionDescription.append(m.replaceFirst("m=audio "+getRebroadcastTrack(0).getRemoteRtpPortString()+" $2"));
            sessionDescription.append("a=control:trackID="+0+"\r\n");
        }

        if(serverTrackExists(1)) {
            Matcher m = regexVideoDescription.matcher(getServerTrack(1).getSessionDescription());
            sessionDescription.append(m.replaceFirst("m=video "+getRebroadcastTrack(1).getRemoteRtpPortString()+" $2"));
            sessionDescription.append("a=control:trackID="+1+"\r\n");
        }
        return sessionDescription.toString();
    }


    public String getSessionID() {
        return mSessionID;
    }

    /** Returns the destination set with {@link #setDestinationAddress(InetAddress, boolean)} }. */
    public InetAddress getDestination() {
        return mDestination;
    }


    /**
     * Asynchronously starts all streams of the session.
     **/
    public void start() {

        //....
    }

    /** Stops all existing streams. */
    public void stop() {
        if(serverTrackExists(0)) {
            getServerTrack(0).removeSession(rtcpVideoTrackChannel,rtpVideoTrackChannel);
        }

        if(serverTrackExists(1)) {
            getServerTrack(1).removeSession(rtcpAudioTrackChannel,rtpAudioTrackChannel);
        }
    }

    public boolean serverTrackExists(int id) {
        return mReceiveSession.trackExists(id);
    }

    public TrackInfo getServerTrack(int id) {
        return mReceiveSession.getTrack(id);
    }

    public ReceiveSession getServerSession() {
        return mReceiveSession;
    }

    public void setServerSession(ReceiveSession receiveSession) {
        this.mReceiveSession = receiveSession;
        this.setNameStreaming(receiveSession.getStreamingName());
    }

    public RebroadcastTrackInfo getRebroadcastTrack(int trackId) {
        if (trackId==0)
            return mAudioRebroadcastTrackInfo;
        else
            return mVideoRebroadcastTrackInfo;
    }

    public void startTrack(int trackId) {
        if (trackId == 0 && serverTrackExists(0)){
            rtcpAudioTrackChannel = getServerTrack(0).addRtcpEchoSession(
                    getDestination().getHostAddress(),
                    getRebroadcastTrack(0).getRemoteRctpPort()
            );

            rtpAudioTrackChannel = getServerTrack(0).addRtpEchoSession(
                    getDestination().getHostAddress(),
                    getRebroadcastTrack(0).getRemoteRtpPort()
            );
        }
        if (trackId == 1 && serverTrackExists(1)){
            rtcpVideoTrackChannel = getServerTrack(1).addRtcpEchoSession(
                    getDestination().getHostAddress(),
                    getRebroadcastTrack(1).getRemoteRctpPort()
            );

            rtpVideoTrackChannel = getServerTrack(1).addRtpEchoSession(
                    getDestination().getHostAddress(),
                    getRebroadcastTrack(1).getRemoteRtpPort()
            );
        }
    }

    public static class RebroadcastTrackInfo {
        private int mRemoteRtpPort;
        private int mRemoteRtcpPort;

        public RebroadcastTrackInfo() {
            setRemotePorts(18000 + new Random().nextInt(2000));
        }

        public int[] getRemotePorts() {
            return new int[]{mRemoteRtpPort, mRemoteRtcpPort};
        }

        public int getRemoteRtpPort() {
            return mRemoteRtpPort;
        }

        public String getRemoteRtpPortString() {
            return Integer.toString(mRemoteRtpPort);
        }


        public int getRemoteRctpPort() {
            return mRemoteRtcpPort;
        }

        public void setRemotePorts(int dport) {
            if (dport % 2 == 1) {
                mRemoteRtpPort = dport-1;
                mRemoteRtcpPort = dport;
            } else {
                mRemoteRtpPort = dport;
                mRemoteRtcpPort = dport+1;
            }
        }
        public void setRemotePorts(int rtpPort, int rtcpPort) {
            mRemoteRtpPort = rtpPort;
            mRemoteRtcpPort = rtcpPort;
        }
    }
}
