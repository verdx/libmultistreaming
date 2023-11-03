package d2d.testing.streaming.threads.selectors;

import java.nio.channels.SelectableChannel;

public class ChangeRequest {
    public static final int REGISTER = 1;
    public static final int CHANGE_OPS = 2;
    public static final int REMOVE = 3;
    public static final int REMOVE_AND_NOTIFY = 4; //Se borra y cierra el canal y se llama a onCLientDisconected

    private final SelectableChannel mChannel;
    private final int mType;
    private final int mOps;

    public ChangeRequest(SelectableChannel channel, int type, int ops) {
        this.mChannel = channel;
        this.mType = type;
        this.mOps = ops;
    }

    public SelectableChannel getChannel() {
        return mChannel;
    }

    public int getType() {
        return mType;
    }

    public int getOps() {
        return mOps;
    }
}
