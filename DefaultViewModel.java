package d2d.testing.streaming;

import static android.content.Context.WIFI_SERVICE;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;

import d2d.testing.R;
import d2d.testing.gui.main.WifiAwareNetwork;
import d2d.testing.streaming.network.DefaultNetwork;

public class DefaultViewModel extends BasicViewModel {

    private final DefaultNetwork mNetwork;
    public static String SERVER_IP = "";
    public static int SERVER_PORT = 8080;

    public DefaultViewModel(@NonNull Application app) {
        super(app);
        mNetwork = new DefaultNetwork(app);
        SERVER_IP = getLocalIpAddress();

        mIsNetworkAvailable = new MutableLiveData<>(Boolean.TRUE);

        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        BroadcastReceiver myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkNetworkInterfaceAvailability();
            }
        };
        app.registerReceiver(myReceiver, intentFilter);

        checkNetworkInterfaceAvailability();

    }

    @Override
    public String getNetworkAvailabilityString(Context c, boolean available){
        if(available){
            return SERVER_IP + ":" + SERVER_PORT;
        }
        return c.getString(R.string.defaultnet_unavailable_str);
    }

    @Override
    public void initNetwork(){

        if(mNetwork.startLocalServer()){
            Toast.makeText(getApplication().getApplicationContext(), "Server Started", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(getApplication().getApplicationContext(), "ServerStart Error", Toast.LENGTH_LONG).show();
        }

        if(mNetwork.startClient()){
            Toast.makeText(getApplication().getApplicationContext(), "Client Started", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(getApplication().getApplicationContext(), "ClientStart Error", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onCleared() {
        mNetwork.stopLocalServer();
        mNetwork.stopClient();
        super.onCleared();
    }

    public void checkNetworkInterfaceAvailability() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        // Verifica si la dirección IP es válida y no es de bucle local (loopback)
                        if (!address.isLoopbackAddress() && !address.isLinkLocalAddress() && !address.isMulticastAddress()) {
                            mIsNetworkAvailable.postValue(Boolean.TRUE); // Hay una interfaz de red activa que puede acceder a direcciones IP en general
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mIsNetworkAvailable.postValue(Boolean.FALSE); // No se encontró una interfaz de red activa para acceder a direcciones IP
    }

    public String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) this.getApplication().getApplicationContext().getSystemService(WIFI_SERVICE);
        assert wifiManager!=null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();

        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
        } catch (UnknownHostException e) {
            return "Local address not found";
        }
    }
}
