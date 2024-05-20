package net.verdx.libstreaming.network;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.verdx.libstreaming.rtsp.RTSPServerModel;
import net.verdx.libstreaming.rtsp.RtspClient;

public class DefaultNetwork extends INetworkManager {

    public static int DEFAULT_PORT = 8080;
    private final Handler workerHandle;
    private final Map<String, RtspClient> mClients; //IP, cliente
    private RTSPServerModel mServerModel;
    private static ConnectivityManager mConManager;

    public DefaultNetwork(Application app) {

        mServerModel = null;
        mClients = new HashMap<>();
        HandlerThread worker = new HandlerThread("DefaultNetwork Worker");
        worker.start();
        workerHandle = new Handler(worker.getLooper());
        mConManager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        DestinationIPReader.setEmptyDestinationIps();
    }

    private synchronized void checkDestinationsConnectivity() {

        for(DestinationInfo info: DestinationIPReader.mDestinationList){
            RtspClient client = mClients.get(info.ip);
            if(!info.isConnected){
                if(client!=null){
                    client.start(); //Retry connection
                    info.isConnected = client.isConnected();
                }
                else{
                    connectToDestination(info);
                }
            }
            else{
                if (client != null) {
                    info.isConnected = client.isConnected();
                }
            }
        }
    }

    private void connectToDestination(DestinationInfo dest) {
        RtspClient client = new RtspClient(this);
        client.setServerAddress(dest.ip, dest.port);
        client.connectionCreated();
        client.start();

        mClients.put(dest.ip, client);
    }

    public void startLocalServer() throws IOException{
        synchronized (DefaultNetwork.this){
            if (mServerModel == null) {
                mServerModel = new RTSPServerModel(getConnectivityManager());
                mServerModel.startServer();
            }

            Log.e("DefaultNetwork", "Adding new connection, addres: " + "127.0.0.1");
            mServerModel.addNewConnection("127.0.0.1", 1234);
        }

        if(mServerModel.isServerEnabled()) {
            mServerModel.addNewConnection();
        } else {
            throw new IOException("Server not enabled");
        }
    }

    public void stopLocalServer() {
        mServerModel.stopServer();
    }

    public void startClient() {
        int delayms = 10000;
        workerHandle.postDelayed(new Runnable() {
            public void run() {
                checkDestinationsConnectivity();
                workerHandle.postDelayed(this, delayms);
            }
        }, delayms);
    }

    public void stopClient() {
        workerHandle.removeCallbacksAndMessages(null);
    }

    @Override
    public ConnectivityManager getConnectivityManager() {
        return mConManager;
    }

    @Override
    public InetAddress getInetAddress(NetworkCapabilities networkCapabilities) {
        TransportInfo ti;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ti = networkCapabilities.getTransportInfo();
            return (InetAddress) ti;
        } else {
            return null;
        }
    }

    @Override
    public int getPort(NetworkCapabilities networkCapabilities) {
        return DEFAULT_PORT;
    }

    public ArrayList<String> getDestinationIps() {
        ArrayList<String> ips = new ArrayList<>();
        for(DestinationInfo info: DestinationIPReader.mDestinationList){
            ips.add(info.ip);
        }
        return ips;
    }

    private static class DestinationInfo{
        private final String ip;
        private final int port;
        private boolean isConnected;

        private DestinationInfo(String ip, int port, boolean isConnected){
            this.ip = ip;
            this.port = port;
            this.isConnected = isConnected;
        }
    }

    private static class DestinationIPReader{
        private static List<DestinationInfo> mDestinationList;

        private static void setDestinationIpsStream(InputStream inputStream){
            mDestinationList = new ArrayList<>();
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] res = line.split(":");

                    mDestinationList.add(new DestinationInfo(res[0], Integer.parseInt(res[1]), false));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void setDestinationIpsSetting(Application app){

            SharedPreferences mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(app.getApplicationContext());
            String[] ipAddresses = mSharedPrefs.getString("PREF_IP_ADDRESS", "").split("\\n");

            mDestinationList = new ArrayList<>();

            for(String ipaddr: ipAddresses){
                if(!ipaddr.equals(""))
                    mDestinationList.add(new DestinationInfo(ipaddr, DEFAULT_PORT, false));
            }
        }

        private static void setDestinationIpsArray(ArrayList<String> ipAddresses){
            mDestinationList = new ArrayList<>();

            for(String ipaddr: ipAddresses){
                if(!ipaddr.equals(""))
                    mDestinationList.add(new DestinationInfo(ipaddr, DEFAULT_PORT, false));
            }
        }

        private static void setEmptyDestinationIps(){
            mDestinationList = new ArrayList<>();
        }
    }

    public void setDestinationIpsArray(ArrayList<String> ipAddresses){
        DestinationIPReader.setDestinationIpsArray(ipAddresses);
    }
    public void setDestinationIpsSettings(Application app){
        DestinationIPReader.setDestinationIpsSetting(app);
    }

    public void setDestinationIpsStream(InputStream inputStream){
        DestinationIPReader.setDestinationIpsStream(inputStream);
    }
}
