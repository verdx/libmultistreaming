package net.verdx.libstreaming.packets;

import java.nio.channels.SelectableChannel;

import net.verdx.libstreaming.threads.selectors.AbstractSelector;

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
