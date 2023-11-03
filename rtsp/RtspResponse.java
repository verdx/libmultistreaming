package d2d.testing.streaming.rtsp;

import android.util.Log;

import java.io.IOException;

public class RtspResponse {
    public final static String LOG_TAG = "RtspResponse";

    public static String SERVER_NAME = "D2D Video Streaming Server";

    // Status code definitions
    public static final String STATUS_OK = "200 OK";
    public static final String STATUS_BAD_REQUEST = "400 Bad Request";
    public static final String STATUS_UNAUTHORIZED = "401 Unauthorized";
    public static final String STATUS_FORBIDDEN = "403 Forbidden";
    public static final String STATUS_NOT_FOUND = "404 Not Found";
    public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";

    public String status = STATUS_INTERNAL_SERVER_ERROR;
    public String content = "";
    public String attributes = "";

    private final RtspRequest mRequest;

    public RtspResponse(RtspRequest request) {
        this.mRequest = request;
    }

    public RtspResponse() {
        // Be carefull if you modify the send() method because request might be null !
        mRequest = null;
    }

    public String build() throws IOException {
        int seqid = -1;

        try {
            seqid = Integer.parseInt(mRequest.headers.get("cseq").replace(" ",""));
        } catch (Exception e) {
            Log.e(LOG_TAG,"Error parsing CSeq: "+(e.getMessage()!=null?e.getMessage():""));
        }

        String response = 	"RTSP/1.0 "+status+"\r\n" +
                "Server: "+SERVER_NAME+"\r\n" +
                (seqid>=0?("Cseq: " + seqid + "\r\n"):"") +
                "Content-Length: " + content.length() + "\r\n" +
                attributes +
                "\r\n" +
                content;

        Log.d(LOG_TAG,response.replace("\r", ""));

        return response;
    }
}
