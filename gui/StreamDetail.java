package d2d.testing.gui.main;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import d2d.testing.streaming.SaveStream;

@SuppressLint("ParcelCreator")
public class StreamDetail implements Parcelable {
    private String ip;
    private String uuid;
    private String name;
    private int port;
    private boolean download;

    private SaveStream saveStream;

    public StreamDetail(String uuid, String name, String ip, int port, boolean download){
        this.uuid = uuid;
        this.ip = ip;
        this.download = download;
        this.port = port;
        this.name = name;
        this.saveStream = null;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isDownload() {
        return download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public SaveStream getSaveStream() {
        return saveStream;
    }

    public void setSaveStream(SaveStream saveStream) {
        this.saveStream = saveStream;
    }

    public boolean equals(Object o) {
        if(o instanceof StreamDetail) {
            StreamDetail streamDetail = (StreamDetail) o;
            return streamDetail.uuid.equals(this.uuid) && streamDetail.ip.equals(this.ip);
        }
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(ip);
        dest.writeString(uuid);
        dest.writeString(name);
        dest.writeInt(port);
        dest.writeBoolean(download);
    }
}
