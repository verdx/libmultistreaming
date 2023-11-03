package d2d.testing.streaming.threads.selectors;

import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import d2d.testing.streaming.network.RTSPServerModel;
import d2d.testing.streaming.threads.workers.RTSPServerWorker;

/**
 * Implementacion del AbstractSelector. Se encarga de crear un ServerSocketChannel y asociarlo al
 * Selector para recibir eventos de tipo Accept (initiateInstance).
 * Cuando los clientes intenten conectar con el ServerSocketChannel se creara, en la funcion accept()
 * de AbstractSelector, un SocketChannel con el cliente y se añadira al Selector para recibir eventos de tipo Read.
 * Cuando se comuniquen por el canal se guardaran los datos en mReadBuffer y se pasaran al thread
 * del RTSPServerWorker, que es creado por esta clase.
 */
public class RTSPServerSelector extends AbstractSelector {

    RTSPServerModel mController;

    public RTSPServerSelector(RTSPServerModel controller, ConnectivityManager connManager) throws IOException {
        super(connManager);
        mController = controller;
        mWorker = new RTSPServerWorker(null, null, this);
        mWorker.start();
    }

    public AtomicBoolean getEnabled() {
        return mEnabled;
    }

    public synchronized boolean addNewConnection(String serverIP, int serverPort){

        if(!mEnabled.get()) return false;
        ServerSocketChannel serverSocketChannel = null;
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(serverIP ,serverPort));
            this.addChangeRequest(new ChangeRequest(serverSocketChannel, ChangeRequest.REGISTER, SelectionKey.OP_ACCEPT));
            mConnections.add(serverSocketChannel);

            mController.addServerSocketChannel(serverSocketChannel);

        } catch (IOException e) {
            return false;
        }
        return true;
    }


    public synchronized boolean upListeningPort(){

        RTSPServerModel.Connection conn = null;
        try {
            //Crea un ServerSocketChannel para escuchar peticiones
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);

            serverSocketChannel.socket().bind(new InetSocketAddress(8080));

            this.addChangeRequest(new ChangeRequest(serverSocketChannel,
                    ChangeRequest.REGISTER,
                    SelectionKey.OP_ACCEPT));

            conn = new RTSPServerModel.Connection(serverSocketChannel);

            mController.addNewConnection(serverSocketChannel,conn);

        } catch (IOException e) {
            return false;
        }
        return true;
    }

    protected void accept(@NonNull SelectionKey key) {

        synchronized (this){
            ServerSocketChannel serverChan = (ServerSocketChannel) key.channel();
            SocketChannel socketChannel = null;
            try {
                if(!mController.accept(serverChan, mSelector)){
                    super.accept(key);
                    return;
                }

            } catch (IOException e) {
                mController.handleAcceptException(serverChan);
            }
        }
    }


//    @Override
//    protected void onClientConnected(SelectableChannel socketChannel) {
//        mController.onClientConnected();
//    }

    @Override
    protected void initiateConnection() { //No se crea un canal de escucha para el servidor, como en UDPServerSelector, si no que por cada subscriber se crea un canal de escucha en addNewConnection
        mStatusTCP = STATUS_LISTENING;
    }

    @Override
    protected void onClientDisconnected(SelectableChannel channel) {
        ((RTSPServerWorker) mWorker).onClientDisconnected(channel);
    }

    @Override
    public void send(byte[] data) {
        for (SelectableChannel socket : mConnections) {
            this.send(socket,data);
        }
    }

    @Override
    protected synchronized void onServerRelease() {
        mController.onServerRelease();

    }

    public synchronized Network getChannelNetwork(SelectableChannel chan){
        return mController.getChannelNetwork(chan);
    }



}