package net.verdx.libstreaming.threads.workers;

import net.verdx.libstreaming.packets.DataReceived;
import net.verdx.libstreaming.threads.selectors.AbstractSelector;

public class EchoWorker extends AbstractWorker {

    public EchoWorker(AbstractSelector selector){
        super(selector);
    }

    @Override
    protected void onWorkerRelease() {}

    @Override
    protected void parsePackets(DataReceived dataReceived) {
        dataReceived.getSelector().send(dataReceived.getData());
    }


}