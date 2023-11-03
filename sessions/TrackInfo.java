package d2d.testing.streaming.sessions;

import android.net.Network;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectableChannel;
import java.util.Random;

import d2d.testing.streaming.network.INetworkManager;
import d2d.testing.streaming.threads.selectors.UDPServerSelector;

public class TrackInfo {
    private int mLocalRtpPort;
    private int mLocalRtcpPort;

    private int mRemoteRtpPort;
    private int mRemoteRtcpPort;

    private UDPServerSelector mRtpUdpServer;
    private UDPServerSelector mRtcpUdpServer;

    private InetAddress mLocalAddress;


    private String mSSRCHex;
    private String mSessionDescription;

    private INetworkManager mNetworkManager;

    public TrackInfo() {
        setLocalPorts(16000 + new Random().nextInt(2000));
        setRemotePorts(14000 + new Random().nextInt(2000));
    }

    public void setLocalAddress(InetAddress localAddress){
        mLocalAddress = localAddress;
    }

    public void startServer(Network receiveNet) throws IOException {
        mRtcpUdpServer = new UDPServerSelector(mLocalAddress, mLocalRtcpPort, receiveNet, mNetworkManager.getConnectivityManager());
        mRtcpUdpServer.start();

        mRtpUdpServer = new UDPServerSelector(mLocalAddress, mLocalRtpPort, receiveNet, mNetworkManager.getConnectivityManager());
        mRtpUdpServer.start();
    }

    public void stopServer() {
        if(mRtcpUdpServer != null) {
            mRtcpUdpServer.stop();
            mRtcpUdpServer = null;
        }

        if(mRtpUdpServer != null){
            mRtpUdpServer.stop();
            mRtpUdpServer = null;
        }
    }

    public SelectableChannel addRtcpEchoSession(String address, int rtcpPort) {
        SelectableChannel channel = null;
        try {
            channel = mRtcpUdpServer.addConnectionUDP(InetAddress.getByName(address), rtcpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public SelectableChannel addRtpEchoSession(String address, int rtpPort) {
        SelectableChannel channel = null;
        try {
            channel = mRtpUdpServer.addConnectionUDP(InetAddress.getByName(address), rtpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public void removeSession(SelectableChannel rtcpChannel, SelectableChannel rtpChannel) {
        if(mRtcpUdpServer != null && rtcpChannel != null) {
            mRtcpUdpServer.disconnectClient(rtcpChannel);
        }

        if(mRtpUdpServer != null && rtpChannel != null) {
            mRtpUdpServer.disconnectClient(rtpChannel);
        }
    }

    public int[] getRemotePorts() {
        return new int[]{mRemoteRtpPort, mRemoteRtcpPort};
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

    public int[] getLocalPorts() {
        return new int[]{mLocalRtpPort, mLocalRtcpPort};
    }

    public void setLocalPorts(int dport) {
        if (dport % 2 == 1) {
            mLocalRtpPort = dport-1;
            mLocalRtcpPort = dport;
        } else {
            mLocalRtpPort = dport;
            mLocalRtcpPort = dport+1;
        }
    }
    public void setLocalPorts(int rtpPort, int rtcpPort) {
        mLocalRtpPort = rtpPort;
        mLocalRtcpPort = rtcpPort;
    }

    public String getSSRCHex() {
        return mSSRCHex;
    }

    public void setSSRCHex(String ssrcHex) {
        this.mSSRCHex = ssrcHex;
    }

    public String getSessionDescription() {
        return mSessionDescription;
    }

    public void setSessionDescription(String sessionDescription) {
        this.mSessionDescription = sessionDescription;
    }
}