package d2d.testing.streaming.network;

import java.io.File;

public class ProofManager {
    private static ProofManager mInstance;

    File mProofZipFile;
    String mDownloadDir;
    String mFileHash;     //Use it as filename

    public static ProofManager getInstance(){
        if(mInstance==null)
            mInstance = new ProofManager();

        return mInstance;
    }

    public void setProofZipFile(String filehash, File f){
        mFileHash = filehash;
        mProofZipFile = f;
    }

    public File getProofZipFile() {
        return mProofZipFile;
    }

    public void setProofDir(String dir) {
        mDownloadDir = dir;
    }

    public String getDownloadDir() {
        return mDownloadDir;
    }

    public String getFileName() {
        return mFileHash;
    }

}
