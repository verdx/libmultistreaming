package net.verdx.libstreaming.network;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.os.Handler;
import android.os.HandlerThread;

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

    private Handler workerHandle;
    private final HandlerThread worker;

    private final Map<String, RtspClient> mClients; //IP, cliente
    private RTSPServerModel mServerModel;
    private DestinationIPReader mDestinationReader;
    private SharedPreferences mSharedPrefs;
    private static ConnectivityManager mConManager;

    public DefaultNetwork(Application app) {

        mServerModel = null;

        mClients = new HashMap<>();

        worker = new HandlerThread("DefaultNetwork Worker");
        worker.start();
        workerHandle = new Handler(worker.getLooper());

//        InputStream inputStream = app.getResources().openRawResource(R.raw.destinations);
//        mDestinationReader = new DestinationIPReader(inputStream);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(app.getApplicationContext());
        mDestinationReader = new DestinationIPReader();
        mConManager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private synchronized void checkDestinationsConnectivity() {

        for(DestinationInfo info: mDestinationReader.mDestinationList){
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
                info.isConnected = client.isConnected();
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

    public boolean startLocalServer(){
        synchronized (DefaultNetwork.this){
            try {
                if (mServerModel == null) {
                    mServerModel = new RTSPServerModel(getConnectivityManager());
                    mServerModel.startServer();
                }

                if (!mServerModel.addNewConnection("127.0.0.1", 1234)) {
                    throw new IOException();
                }
            } catch (IOException e) {
                return false;
            }
        }

        if(mServerModel.isServerEnabled()) {
            return mServerModel.addNewConnection();
        } else {
            return false;
        }
    }

    public void stopLocalServer() {
        mServerModel.stopServer();
    }

    public boolean startClient() {
        int delayms = 10000;
        workerHandle.postDelayed(new Runnable() {
            public void run() {
                checkDestinationsConnectivity();
                workerHandle.postDelayed(this, delayms);
            }
        }, delayms);

        return true;
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
        TransportInfo ti = networkCapabilities.getTransportInfo();
        return (InetAddress) ti;
    }

    @Override
    public int getPort(NetworkCapabilities networkCapabilities) {
        return DEFAULT_PORT;
    }

    static class DestinationInfo{
        String ip;
        int port;
        boolean isConnected;

        public DestinationInfo(String ip, int port, boolean isConnected){
            this.ip = ip;
            this.port = port;
            this.isConnected = isConnected;
        }
    }

    public class DestinationIPReader{

        List<DestinationInfo> mDestinationList;

        public DestinationIPReader(InputStream inputStream){
            mDestinationList = new ArrayList<>();
            getDestinationIps(inputStream);
        }

        public DestinationIPReader(){
            mDestinationList = new ArrayList<>();
            getDestinationIps();
        }

        private void getDestinationIps(InputStream inputStream){
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] res = line.split(":");

                    mDestinationList.add(new DestinationInfo(res[0], Integer.valueOf(res[1]), false));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void getDestinationIps(){
            String[] ipAddresses = mSharedPrefs.getString("PREF_IP_ADDRESS", "").split("\\n");

            mDestinationList.clear();

            for(String ipaddr: ipAddresses){
                if(!ipaddr.equals(""))
                    mDestinationList.add(new DestinationInfo(ipaddr, DEFAULT_PORT, false));
            }
        }
    }

}
