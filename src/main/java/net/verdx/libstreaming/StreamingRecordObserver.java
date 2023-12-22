package net.verdx.libstreaming;

import java.util.UUID;

import net.verdx.libstreaming.sessions.SessionBuilder;

public interface StreamingRecordObserver {
    void onLocalStreamingAvailable(UUID id, String name, SessionBuilder sessionBuilder);
    void onLocalStreamingUnavailable();
    void onStreamingAvailable(Streaming streaming, boolean bAllowDispatch);
    void onStreamingUnavailable(Streaming streaming);
    void onStreamingDownloadStateChanged(Streaming streaming, boolean bIsDownloading);
}
