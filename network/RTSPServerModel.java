package d2d.testing.streaming.network;

import android.net.ConnectivityManager;
import android.net.Network;
import android.util.Log;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import d2d.testing.streaming.threads.selectors.ChangeRequest;
import d2d.testing.streaming.threads.selectors.RTSPServerSelector;

public class RTSPServerModel {
    public static final String TAG = "RTSPServerModel";

    public RTSPServerSelector getmServer() {
        return mServer;
    }

    protected RTSPServerSelector mServer;
    private final Map<ServerSocketChannel, Connection> mServerChannelsMap;

    public RTSPServerModel(ConnectivityManager connManager) throws IOException {
        mServer = new RTSPServerSelector(this, connManager);
        mServerChannelsMap = new HashMap<>();
    }

    public synchronized boolean addNewConnection(String serverIP, int serverPort){
        return mServer.addNewConnection(serverIP,serverPort);
    }

    public void addNewConnection(ServerSocketChannel serverSocketChannel, Connection conn) {
        mServerChannelsMap.put(serverSocketChannel, conn);
    }

    public void startServer(){
        mServer.start();
    }

    public void addChangeRequest(ChangeRequest changeRequest) {
        mServer.addChangeRequest(changeRequest);
    }

    public void stopServer(){
        mServer.stop();
    }

    public boolean isServerEnabled(){
        return mServer.getEnabled().get();
    }

    public RTSPServerSelector getServer(){
        return mServer;
    }



    public boolean accept(ServerSocketChannel serverChan, Selector selector){
        synchronized (this){
            Connection conn = mServerChannelsMap.get(serverChan);
            if(conn == null){
                return false;
            }
            SocketChannel socketChannel = null;
            try {
                socketChannel = serverChan.accept();
                socketChannel.configureBlocking(false);// Accept the connection and make it non-blocking
                socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            } catch (IOException e) {
                mServerChannelsMap.remove(serverChan);
                conn.closeConnection(mServer);
            }
        }
        return true;
    }


    public void handleAcceptException(ServerSocketChannel serverChan) {
        Log.e(TAG, "Error accepting client connection");
    }

    public void onServerRelease() {}
    public synchronized Network getChannelNetwork(SelectableChannel chan){return null;}

    public void addServerSocketChannel(ServerSocketChannel serverSocketChannel) {
        mServerChannelsMap.put(serverSocketChannel, new Connection(serverSocketChannel));
    }

    public boolean addNewConnection() {
        return mServer.upListeningPort();
    }

    public static class Connection{
        public Connection(ServerSocketChannel serverChan){
            mServerSocketChannel = serverChan;
            mComChannels = new ArrayList<>();
        }

        //Socket del server
        public ServerSocketChannel mServerSocketChannel;
        //Lista de sockets clientes conectados al socket server
        public List<SocketChannel> mComChannels;

        public void closeConnection(RTSPServerSelector serverSelector){
            serverSelector.addChangeRequest(new ChangeRequest(mServerSocketChannel, ChangeRequest.REMOVE, 0));
            for(SocketChannel chan : this.mComChannels){
                serverSelector.addChangeRequest(new ChangeRequest(chan, ChangeRequest.REMOVE_AND_NOTIFY, 0));
            }
            this.mServerSocketChannel = null;
            this.mComChannels.clear();
        }
    }

}
