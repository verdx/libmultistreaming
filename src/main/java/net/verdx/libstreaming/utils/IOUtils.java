package net.verdx.libstreaming.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;

public class IOUtils {

    /**
     * https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
     */
    public static byte[] intToByteArray(int value) {
        return new byte[] {(byte)(value >> 24),(byte)(value >> 16), (byte)(value >> 8), (byte)value};
    }

    public static int fromByteArray(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public static boolean contains(final byte[] array, final byte search) {
        boolean result = false;

        for(byte b : array){
            if(b == search){
                result = true;
                break;
            }
        }

        return result;
    }

    public static byte[] copyMax(byte[] byteArr, int offset, int max)
    {
        if(byteArr.length > offset + max){
            return Arrays.copyOfRange(byteArr, offset, offset + max);
        } else {
            return Arrays.copyOfRange(byteArr, offset, byteArr.length);
        }
    }

    public static String uuidToBase64(String str) {
        UUID uuid = UUID.fromString(str);
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return Base64.encodeToString(bb.array(), Base64.DEFAULT);
    }

    public static String createVideoFilePath(Context c){
        String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        String filename = sdf.format(cal.getTime());
        filename = filename.replaceAll(" ", "_");
        filename = filename.replaceAll(":", "");
        return c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + filename + ".mp4";
    }
}
