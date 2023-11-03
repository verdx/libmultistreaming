package d2d.testing.streaming;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import d2d.testing.streaming.network.ProofManager;
import d2d.testing.streaming.sessions.ReceiveSession;

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
        if(downloading)
            saveProofFile();
    }

    private void saveProofFile(){

        try {
            // Save the decoded data to a file with the .zip extension
            File outputFile = new File(ProofManager.getInstance().getDownloadDir(), mReceiveSession.getProofFilename() + ".zip");
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(mReceiveSession.getProofByteArr());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
