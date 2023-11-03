package d2d.testing.streaming.packets;

import java.nio.channels.SelectableChannel;

import d2d.testing.streaming.threads.selectors.AbstractSelector;

public class DataReceived {
    private final AbstractSelector mSelector;
    private final SelectableChannel mChannel;
    private final byte[] mData;

    public DataReceived(AbstractSelector selector, SelectableChannel socket, byte[] data) {
        this.mSelector = selector;
        this.mChannel = socket;
        this.mData = data;
    }

    public AbstractSelector getSelector() {
        return mSelector;
    }

    public SelectableChannel getSocket() {
        return mChannel;
    }

    public byte[] getData() {
        return mData;
    }
}
