package d2d.testing.streaming.utils;

import android.util.Log;

public class Logger {
    public static final String TAG = "WIFI-AWARE-NETWORK";

    //Methods Log.VERBOSE level
    public static void v(String str) {
        if (Log.isLoggable(TAG, Log.VERBOSE))
            Log.v(TAG, str);
    }
    public static void v(String str, Throwable t) {
        if (Log.isLoggable(TAG, Log.VERBOSE))
            Log.v(TAG, str, t);
    }

    //Methods Log.DEBUG level
    public static void d(String str) {
        //if (Log.isLoggable(TAG, Log.DEBUG))
        Log.d(TAG, str);
    }
    public static void d(String str, Throwable t) {
        //if (Log.isLoggable(TAG, Log.DEBUG))
        Log.d(TAG, str, t);
    }

    //Methods Log.ERROR level
    public static void e(String str) {
        //if (Log.isLoggable(TAG, Log.ERROR))
        Log.d(TAG, str);
    }
    public static void e(String str, Throwable t) {
        //if (Log.isLoggable(TAG, Log.ERROR))
        Log.d(TAG, str, t);
    }
}
