package com.weidi.mirrorcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/***
 创建一个 WIFI Direct 应用程序，包括发现连接点、请求连接、建立连接、发送数据，
 以及建立对该应用程序广播的 Intent 进行接收的 BroadcastReceiver
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG =
            "player_alexander";

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    public WiFiDirectBroadcastReceiver() {
        super();
    }

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager,
                                       WifiP2pManager.Channel channel) {
        super();
        this.manager = manager;
        this.channel = channel;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION");
            // 检测 WIFI 功能是否被打开
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct is enabled
                // Wifi Direct mode is enabled activity.setlsWifiP2pEnabled (true) ;
                Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_STATE_ENABLED");
            } else {
                // Wi-Fi Direct is not enabled
                Log.e(TAG, "onReceive() WifiP2pManager.WIFI_P2P_STATE_DISABLED");
            }
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION");
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // 获取当前可用连接点的列表
            Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION");
            if (MainActivity.activity != null) {
                MainActivity.activity.requestPeers();
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // 建立或者断开连接
            Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION");
            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            NetworkInfo.State state = networkInfo.getState();
            Log.i(TAG, "onReceive() state: " + state);
            if (networkInfo.isConnected()) {
                // we are connected with the other device,
                // request connection info to find group owner IP
                if (MainActivity.activity != null) {
                    MainActivity.activity.requestConnectionlnfo();
                }
            } else {
                // It's a disconnect
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // 当前设备的 WIFI 状态发生变化
            Log.i(TAG, "onReceive() WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            if (MainActivity.activity != null) {
                MainActivity.activity.updateThisDevice(
                        (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
            }
        }
    }
}
