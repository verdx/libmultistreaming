package d2d.testing.streaming.network;

import android.net.NetworkCapabilities;
import android.net.ConnectivityManager;

import java.net.InetAddress;

public abstract class INetworkManager {

    public abstract ConnectivityManager getConnectivityManager();

    public abstract InetAddress getInetAddress(NetworkCapabilities networkCapabilities);
    public abstract int getPort(NetworkCapabilities networkCapabilities);

}
