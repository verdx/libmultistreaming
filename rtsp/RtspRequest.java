package d2d.testing.streaming.rtsp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspRequest {
    public final static String LOG_TAG = "RtspRequest";

    // Parse method & uri
    public static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP",Pattern.CASE_INSENSITIVE);
    // Parse a request header
    public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);

    public String method;
    public String uri;
    public String path;
    public String body;
    public Map<String,String> headers = new HashMap<>();

    /** Parse the method, uri & headers of a RTSP request */
    public static RtspRequest parseRequest(BufferedReader input) throws IOException, IllegalStateException, SocketException {
        boolean headerEnded = false;
        RtspRequest request = new RtspRequest();
        String line;
        Matcher matcher;

        // Parsing request method & uri
        if ((line = input.readLine())==null) throw new SocketException("Client disconnected");
        matcher = regexMethod.matcher(line);
        matcher.find();
        request.method = matcher.group(1);
        request.uri = matcher.group(2);

        // Parsing headers of the request
        while ( (line = input.readLine()) != null && line.length()>3 && (matcher = rexegHeader.matcher(line)).find()) {
            request.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));
        }
        while ((line = input.readLine()) != null) {
            request.body += line;
        }

        // It's not an error, it's just easier to follow what's happening in logcat with the request in red
        Log.e(LOG_TAG,request.method+" "+request.uri);

        return request;
    }
}
