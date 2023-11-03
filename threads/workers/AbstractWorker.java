package d2d.testing.streaming.threads.workers;

import java.nio.channels.SelectableChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import d2d.testing.streaming.packets.DataReceived;
import d2d.testing.streaming.threads.selectors.AbstractSelector;

/**
 * Define el flujo general de trabajo que tiene que realizar un cliente-servidor.
 * Se puede ejecutar en un thread externo o llamando a start se creara un nuevo.
 * La clase recibe los bytes del Selector a traves de la funcion addData() y los mete en una cola.
 * El thread del worker posteriormente se despierta y llama a la funcion parsePackets() para procesar los bytes.
 *
 * La implementacion de la funcion parsePackets() y el mapa mOpenPaketsMap son usadas solamente
 * por el protocolo que idearon para transmitir mensajes y archivos por wifidirect.
 * Este protocolo usa las clases DataPacket, DataPacketBuilder, ServerSelector, ServerWorker, ClientSelector y ClientWorker.
 * La funcion y las clases se pueden ignorar y borrar en un futuro ya que no estan relacionadas con los streamings, lo usaban para hacer pruebas.
 *
 * En nuestro caso tenemos que considerar la funcion parsePackets() como abstracta.
 * los worker RTSPServerWorker y EchoWorker la reimplemntan para tratar los bytes recibidos.
 */

public abstract class AbstractWorker implements Runnable {
    private final List<DataReceived> mDataReceivedQueue;

    private Thread mThread;
    protected AbstractSelector mSelector;
    private final AtomicBoolean mEnabled;

    protected AbstractWorker(AbstractSelector selector) {
        mDataReceivedQueue = new LinkedList<>();
        mEnabled = new AtomicBoolean(false);
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

    @Override
    public void run() {
        DataReceived dataReceived;

        while(mEnabled.get()) {                       // Wait for data to become available
            synchronized(mDataReceivedQueue) {
                while(mDataReceivedQueue.isEmpty()) {
                    try {
                        mDataReceivedQueue.wait();
                    } catch (InterruptedException ignored) {}
                }
                dataReceived = mDataReceivedQueue.remove(0);
            }
            this.parsePackets(dataReceived);
        }
        onWorkerRelease();
    }

    public void addData(AbstractSelector selectorThread, SelectableChannel socket, byte[] data, int count) {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        synchronized(mDataReceivedQueue) {
            mDataReceivedQueue.add(new DataReceived(selectorThread, socket, dataCopy));
            mDataReceivedQueue.notify();
        }
    }

    protected abstract void onWorkerRelease();
    protected abstract void parsePackets(DataReceived dataReceived);  //los worker RTSPServerWorker y EchoWorker la implementan para tratar los bytes recibidos.
}
