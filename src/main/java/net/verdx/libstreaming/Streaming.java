package net.verdx.libstreaming;

import java.util.UUID;

import net.verdx.libstreaming.sessions.ReceiveSession;

public class Streaming {
    private UUID mUUID;
    private String mName;

    private boolean isDownloading;
    private ReceiveSession mReceiveSession;

    public Streaming(UUID id, String name, ReceiveSession receiveSession){
        mUUID = id;
        mReceiveSession = receiveSession;
        mName = name;
        isDownloading = false;
    }

    public UUID getUUID() {
        return mUUID;
    }

    public ReceiveSession getReceiveSession() {
        return mReceiveSession;
    }

    public String getName() {
        return mName;
    }

    public void setReceiveSession(ReceiveSession mReceiveSession) {
        this.mReceiveSession = mReceiveSession;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public void setDownloadState(boolean downloading) {
        isDownloading = downloading;
    }

}
