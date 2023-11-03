package d2d.testing.streaming.threads.selectors;

import android.net.ConnectivityManager;
import android.net.Network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import d2d.testing.streaming.threads.workers.EchoWorker;
import d2d.testing.streaming.utils.Logger;

public class UDPServerSelector extends AbstractSelector {
    private DatagramChannel mDatagramChannel;
    private int mPortUDP;
    private InetAddress mLocalAddress;
    private Network mSocketNet;
    private Map<SelectableChannel, ByteBuffer> mBuffers = new HashMap<>();

    public UDPServerSelector(InetAddress localAddress, int port, Network net, ConnectivityManager conManager) throws IOException {
        super(conManager);
        if(conManager != null && net == null) throw new IllegalArgumentException("Network object cannot be null");
        mSocketNet = net;
        mPortUDP = port;
        mLocalAddress = localAddress;
        mWorker = new EchoWorker(this);
        mWorker.start();
    }

    public UDPServerSelector(InetAddress localAddress, int port) throws IOException {
        this(localAddress, port, null, null);
    }


    @Override
    protected void onClientDisconnected(SelectableChannel socketChannel) {}

    @Override
    protected void initiateConnection() {
        try {
            if(mConManager != null && !mConManager.bindProcessToNetwork(mSocketNet)) throw new IOException("Error bind to net");
            mDatagramChannel = (DatagramChannel) DatagramChannel.open().configureBlocking(false);
            mDatagramChannel.socket().bind(new InetSocketAddress(mLocalAddress, mPortUDP));
            mStatusUDP = STATUS_LISTENING;
            this.addChangeRequest(new ChangeRequest(mDatagramChannel, ChangeRequest.REGISTER, SelectionKey.OP_READ));
            if(mConManager != null) mConManager.bindProcessToNetwork(null);
            Logger.d("UDPServerSelector: initiateConnection as server listening UDP on port " + mLocalAddress.getHostAddress() + ":" + mPortUDP);
        } catch (IOException e) {
            mStatusUDP = STATUS_DISCONNECTED;
            e.printStackTrace();
        }
    }

    public SelectableChannel addConnectionUDP(InetAddress address, int port) throws IOException {

        DatagramChannel datagramChannel =  (DatagramChannel) DatagramChannel.open().configureBlocking(false);
        datagramChannel.connect(new InetSocketAddress(address.getHostAddress(), port));
        addChangeRequest(new ChangeRequest(datagramChannel, ChangeRequest.REGISTER, SelectionKey.OP_WRITE));
        mConnections.add(datagramChannel);
        Logger.d("UDPServerSelector: initiateConnection UDP client 'connected' to " + address.getHostAddress() + ":" + port);
        return datagramChannel;
    }

    /*
    @Override
    public void send(byte[] data) {
        Logger.d("UDPServerSelector: sending " + data.length + "bytes to " + mConnections.size());
        for (SelectableChannel socket : mConnections) {
            mBuffers.put(socket, ByteBuffer.wrap(data));
        }
        while(!mBuffers.isEmpty()){
            for(Iterator<Map.Entry<SelectableChannel, ByteBuffer>> it = mBuffers.entrySet().iterator(); it.hasNext();){
                Map.Entry<SelectableChannel, ByteBuffer> entry = it.next();
                try {
                    ((ByteChannel) entry.getKey()).write(entry.getValue());
                    if(entry.getValue().remaining() <= 0) it.remove();
                } catch (IOException ignored) {it.remove();}
            }
        }

    }

     */
    @Override
    public void send(byte[] data) {
        for (SelectableChannel socket : mConnections) {
            Logger.d("UDPServerSelector: sending " + data.length + "bytes to " + mConnections.size());
            this.send(socket, data);
        }
    }


}