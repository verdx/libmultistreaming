package d2d.testing.streaming.threads.selectors;

import android.net.ConnectivityManager;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import d2d.testing.streaming.threads.workers.AbstractWorker;


/**
 * Esta clase se encarga de gestionar las peticiones de los canales de comunicacion.
 * Funciona de manera similar a un servidor nativo en linux, la clase Selector hace de interfaz para
 * controlar la peticiones de todos los canales de los clientes a la vez, desde un mismo thread, al contrario de otras
 * metodologias que plantean un nuevo thread para responder a cada cliente que se conecta.
 *
 * En vez de utilizar la clase Socket o ServerSocket para la comunicacion se utiliza la clase ServerSocketChannel y SocketChannel.
 * Esta clase se iniciliza con un puerto y direccion como los Sokets y proporcionan la misma funcionalidad.
 * La diferencia es que los sockets son bloqueantes, si lees de ellos el thread se bloquea hasta que recibe datos y en cambio el Channel no.
 *
 * El Selector registra los canales sobre los que tienen que escuchar, por cada llamada a .select() el thread se bloquea hasta que uno de los canales tiene un evento que procesar.
 * Se puede indicar al selector que eventos escuchar para cada canal, estos son:
 * --Connect – when a client attempts to connect to the server. Represented by SelectionKey.OP_CONNECT
 * --Accept – when the server accepts a connection from a client. Represented by SelectionKey.OP_ACCEPT
 * --Read – when the server is ready to read from the channel. Represented by SelectionKey.OP_READ
 * --Write – when the server is ready to write to the channel. Represented by SelectionKey.OP_WRITE
 *
 * Esta clase representa el trabajo general que deben realizar todos los selectores.
 * Cuando se inicializa crea un Selector y cuando se arranque el thread y se ejectute run se llamara a la funcion abstracta initiateConnection().
 * Esta funcion se utiliza en el RSTPServerSelector para crear el ServerSocketChannel y añadirlo al Selector.
 * El bucle del thread se encarga de procesar las ChangeRequest, que especifican un canal y los eventos que se deben escuchar sobre el, añadiendolos al selector
 * y ejecutando la funcion select
 * Esta devuelve un conjunto de claves que identifican los canales que tienen eventos a procesar. Se procesan y se vuelve a empezar.
 *
 * TODO: Estudiar si se pueden integrar los sockets, que devuelven los canales, con WifiAware. Para cada conexion entre dos dispositivos por WifiAware, en principio,
 * TODO: hay que asociar el serversocket y el socket del cliente a un objeto Network, que lo aisla de la comunicacion con otros sockets no asociados. Hay que ver si se puede integrar esto con el Selector y los canales.
 * TODO: Si no hay que cambiar a una metodologia multithread.
 */
public abstract class AbstractSelector implements Runnable{
    private static final String TAG = "AbstractSelector";

    private static final int BUFFER_SIZE = 8192;

    //protected static final int PORT_TCP = 3462;
    //protected static final int PORT_UDP = 3463;

    protected static final int STATUS_DISCONNECTED = 0;
    protected static final int STATUS_LISTENING = 1;
    protected static final int STATUS_CONNECTING = 2;
    protected static final int STATUS_CONNECTED = 4;

    protected final Selector mSelector;
    protected ConnectivityManager mConManager;

    // A list of ChangeRequest instances and Data/socket map
    protected final List<SelectableChannel> mConnections = new ArrayList<>();
    protected final List<ChangeRequest> mPendingChangeRequests = new LinkedList<>();
    protected final Map<SelectableChannel, Queue<ByteBuffer>> mPendingData = new HashMap<>();
    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    //protected int mPortTCP = PORT_TCP;

    protected AtomicBoolean mEnabled;
    protected int mStatusTCP = STATUS_DISCONNECTED;
    protected int mStatusUDP = STATUS_DISCONNECTED;


    protected AbstractWorker mWorker;
    private Thread mSelectorThread;
    private WriterThread mWriterThread;

    public abstract void send(byte[] data);
    protected abstract void initiateConnection();


    public AbstractSelector(ConnectivityManager connManager) throws IOException {
        mConManager = connManager;
        mEnabled = new AtomicBoolean(false);

        this.mSelector = SelectorProvider.provider().openSelector();
        //Lo mismo que Selector.open();
        //this.initiateConnectionUDP();
    }


    public void stop(){
        if(mEnabled.compareAndSet(true, false)){
            mWriterThread.stop();
            mWriterThread = null;
            mSelectorThread.interrupt();
            try {
                mSelectorThread.join();
            } catch (InterruptedException ignored) {}
            mSelectorThread = null;
        }
    }

    public void start() {
        if(mEnabled.compareAndSet(false, true)) {
            mWriterThread = new WriterThread(this);
            mWriterThread.start();
            mSelectorThread = new Thread(this);
            mSelectorThread.start();
        }
    }


    public void run(){
        try {
            this.initiateConnection();
            while (mEnabled.get() && (mStatusTCP != STATUS_DISCONNECTED || mStatusUDP != STATUS_DISCONNECTED)) {
                this.processChangeRequests();

                mSelector.select();

                Iterator<SelectionKey> itKeys = mSelector.selectedKeys().iterator();
                while (itKeys.hasNext()) {
                    SelectionKey myKey = itKeys.next();
                    itKeys.remove();
                    if (!myKey.isValid()) {
                        continue;
                    }
                    try{
                        if (myKey.isAcceptable()) {
                            this.accept(myKey);
                        } else if (myKey.isConnectable()) {
                            this.finishConnection(myKey);
                        } else if (myKey.isReadable()) {
                            this.read(myKey);
                        } else if (myKey.isWritable()) {
                            synchronized (mPendingData){
                                Queue<ByteBuffer> queue = mPendingData.get(myKey.channel());
                                if(queue != null && !queue.isEmpty()) this.write(myKey);
                            }
                        }
                    }catch (IOException ex){
                        removeClient(myKey.channel());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            onServerRelease();
            processChangeRequests();

            for (SelectionKey key : mSelector.keys()) {
                removeClient(key.channel(), true, false);
            }

            mWorker.stop();

            try {
                mSelector.close();
            } catch (IOException ignored) {}

        }
    }


    public void send(SelectableChannel socket, byte[] data) {
        synchronized (mPendingData) {
            Queue<ByteBuffer> queue = mPendingData.get(socket);
            if (queue == null) {
                queue = new LinkedList<>();
                mPendingData.put(socket, queue);
            }
            queue.add(ByteBuffer.wrap(data));
        }
    }

    protected void accept(SelectionKey key) throws IOException {
        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();//serverSocketChannel.accept();
        socketChannel.configureBlocking(false);// Accept the connection and make it non-blocking

        // Register the SocketChannel with our Selector, indicating to be notified for READING
        socketChannel.register(mSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        mConnections.add(socketChannel);
        onClientConnected(socketChannel);
        Log.d(TAG,"Connection Accepted from IP " + socketChannel.socket().getInetAddress().toString() + ":" + socketChannel.socket().getPort());
    }



    protected void finishConnection(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            if(socketChannel.finishConnect()) { //Finish connecting.
                this.mStatusTCP = STATUS_CONNECTED;
                key.interestOps(SelectionKey.OP_READ);  // Register an interest in reading till send
                Log.d(TAG,"Client (" + socketChannel.socket().getLocalAddress() + ") finished connecting...");
                onClientConnected(socketChannel);
            }
        } catch (IOException e) {
            this.mStatusTCP = STATUS_DISCONNECTED;
            key.cancel();               // Cancel the channel's registration with our selector
            Log.d(TAG,"FinishConnection: " + e.toString());
        }
    }

    protected void read(SelectionKey key) throws IOException {
        int numRead;
        SelectableChannel socketChannel = key.channel();
        mReadBuffer.clear();   //Clear out our read buffer

        if (socketChannel instanceof SocketChannel || (socketChannel instanceof DatagramChannel && ((DatagramChannel) socketChannel).isConnected())) {
            numRead = ((ByteChannel) socketChannel).read(mReadBuffer); // Attempt to read off the channel
            if (numRead == -1) {
                throw new IOException("Can not read from socket");
            }

            mWorker.addData(this, socketChannel, mReadBuffer.array(), numRead);
        } else if(socketChannel instanceof DatagramChannel) {
            ((DatagramChannel) socketChannel).receive(mReadBuffer);
            mReadBuffer.flip();

            if (mReadBuffer.limit() <= 0) {
                throw new IOException("Read buffer limit under 0");
            }

            mWorker.addData(this, socketChannel, mReadBuffer.array(), mReadBuffer.limit());
        }
    }



    public void disconnectClient(SelectableChannel channel) {
        this.addChangeRequest(new ChangeRequest(channel, ChangeRequest.REMOVE_AND_NOTIFY, 0));
    }

    protected void removeClient(SelectableChannel channel) {
        removeClient(channel, true, true);
    }

    protected void removeClient(SelectableChannel channel, boolean notify, boolean printLogs) {
        try {
            SelectionKey key = channel.keyFor(mSelector);
            if(key == null) return;
            key.cancel();
            channel.close();
        } catch (IOException ignored) {}

        mConnections.remove(channel);
        if(notify) onClientDisconnected(channel);

        if(printLogs){
            if(channel instanceof SocketChannel) {
                SocketChannel sockChan = ((SocketChannel) channel);
                Log.d(TAG, ": Client " + sockChan.socket().getInetAddress() + ":" + sockChan.socket().getPort() + " disconnected");
            } else  if (channel instanceof  DatagramChannel) {
                DatagramChannel datagramChannel = ((DatagramChannel) channel);
                Log.d(TAG, ": Client " + datagramChannel.socket().getInetAddress() + ":" + datagramChannel.socket().getPort() + " disconnected");
            }
            else if (channel instanceof ServerSocketChannel){
                ServerSocketChannel serverChan = ((ServerSocketChannel) channel);
                Log.d(TAG, ": Server socket at " + serverChan.socket().getLocalSocketAddress() + ":" + serverChan.socket().getLocalPort() + " disconnected");
            }
        }
    }


    protected abstract void onClientDisconnected(SelectableChannel socketChannel);
    protected void onClientConnected(SelectableChannel socketChannel) {}
    protected void onServerRelease(){}

    protected void write(SelectionKey key){
        SelectableChannel socketChannel = key.channel();

        Queue<ByteBuffer> queue = mPendingData.get(socketChannel);
        while (!queue.isEmpty()) {
            mWriterThread.addWrite(socketChannel, queue.poll());
        }
    }


    public void addChangeRequest(ChangeRequest changeRequest) {
        synchronized(this.mPendingChangeRequests) {         // Queue a channel registration
            this.mPendingChangeRequests.add(changeRequest);
            mSelector.wakeup();
        }
    }


    /**
     * Process any pending key changes on our selector
     *
     * It processes mPendingChangeRequests list in a synchronized way
     *
     * @throws Exception
     */

    protected void processChangeRequests(){
        synchronized (mPendingChangeRequests) {
            for (ChangeRequest changeRequest : mPendingChangeRequests) {
                try {
                    switch (changeRequest.getType()) {
                        case ChangeRequest.CHANGE_OPS:
                            changeRequest.getChannel().keyFor(mSelector).interestOps(changeRequest.getOps());
                            break;
                        case ChangeRequest.REGISTER:
                            changeRequest.getChannel().register(mSelector, changeRequest.getOps());
                            break;
                        case ChangeRequest.REMOVE:
                            removeClient(changeRequest.getChannel(), false, true);
                            break;
                        case ChangeRequest.REMOVE_AND_NOTIFY:
                            removeClient(changeRequest.getChannel(), true, true);
                            break;
                    }
                } catch (Exception e) {
                    Log.e("AbstractSelector", "Error in process change Request probably client disconnected...");
                    e.printStackTrace();
                }
            }
            this.mPendingChangeRequests.clear();
        }
    }

    static class WriterThread implements Runnable{
        private Thread mThread;
        private final AtomicBoolean mEnabled;
        private final Queue<SelectableChannel> mPendingChannels;
        private final Map<SelectableChannel, Queue<ByteBuffer>> mPendingBuffers;
        private final Lock mLock = new ReentrantLock();
        private final Condition mDataToWrite = mLock.newCondition();
        private final AbstractSelector mSelector;

        public WriterThread(AbstractSelector selector){
            mThread = null;
            mEnabled = new AtomicBoolean(false);
            mPendingChannels = new LinkedList<>();
            mPendingBuffers = new HashMap<>();
            mSelector = selector;
        }

        public void start(){
            if(mEnabled.compareAndSet(false, true)){
                mThread = new Thread(this);
                mThread.start();
            }
        }

        public void stop(){
            if(mEnabled.compareAndSet(true, false)){
                mThread.interrupt();
                try {
                    mThread.join();
                } catch (InterruptedException ignored) {}
                mThread = null;
            }
        }

        public void addWrite(SelectableChannel chan, ByteBuffer buff){
            mLock.lock();
            Queue<ByteBuffer> buffers = mPendingBuffers.get(chan);
            if(buffers == null){
                buffers = new LinkedList<>();
                mPendingBuffers.put(chan, buffers);
                mPendingChannels.add(chan);
            }
            buffers.add(buff);
            mDataToWrite.signal();
            mLock.unlock();
        }

        @Override
        public void run() {
            try{
                while(mEnabled.get()){
                    mLock.lock();
                    while(mPendingChannels.isEmpty()){
                        mDataToWrite.await();
                    }
                    SelectableChannel chan = mPendingChannels.poll();
                    Queue<ByteBuffer> buffers = mPendingBuffers.get(chan);
                    ByteBuffer buff = buffers.peek();
                    try {
                        ((ByteChannel) chan).write(buff);
                        if(buff.remaining() <= 0){
                            buffers.remove();
                        }
                        if(buffers.isEmpty()){
                            mPendingBuffers.remove(chan);
                        }
                        else{
                            mPendingChannels.add(chan);
                        }
                    } catch (IOException e) {
                        mPendingBuffers.remove(chan);
                        mSelector.disconnectClient(chan);
                    }
                    mLock.unlock();
                }
            }
            catch (InterruptedException ignored){
                mLock.unlock();
            }
            finally {
                mPendingChannels.clear();
                mPendingBuffers.clear();
            }
        }
    }
}
