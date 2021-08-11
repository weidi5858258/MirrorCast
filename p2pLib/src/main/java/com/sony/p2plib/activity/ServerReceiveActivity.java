package com.sony.p2plib.activity;


import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.sony.baselib.DLog;
import com.sony.p2plib.R;
import com.sony.p2plib.p2phelper.WifiP2PHelper;
import com.sony.p2plib.p2phelper.WifiP2PListener;
import com.sony.p2plib.sockethelper.ServerSocketHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

public class ServerReceiveActivity extends BaseActivity {

    private TextView showContent;
    private EditText input;
    private Button btnSend;
    private ConstraintLayout root;

    private final String TAG = "ServerReceiveActivity";

    public static final int WFD_SOURCE = 0;
    public static final int PRIMARY_SINK = 1;
    public static final int SECONDARY_SINK = 2;
    public static final int SOURCE_OR_PRIMARY_SINK = 3;

    private ServerSocketHelper serverSocketHelper;
//    private final Handler mHandler = new Handler();
//    RetryStartWfdSinkRunnable mRetryStartWfdSinkRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_receive);
        root = findViewById(R.id.root);

        showContent = findViewById(R.id.show_content);
        input = findViewById(R.id.input);
        btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString();
//                serverSocketHelper.send(text + RandomColor.INSTANCE.makeRandomColor());
//                input.setText("");
            }
        });

//        serverSocketHelper = new ServerSocketHelper(new SocketBase.OnReceiveListener() {
//            @Override
//            public void onReceived(String text) {
//                showContent.setText(text);
//                root.setBackgroundColor(RandomColor.INSTANCE.parseRandomColorInt(text));
//            }
//        });

        WifiP2PHelper.getInstance(ServerReceiveActivity.this).createGroup();
    }

    @Override
    protected void onDestroy() {
        WifiP2PHelper.getInstance(ServerReceiveActivity.this).removeGroup();
//        if (serverSocketHelper != null) {
//            serverSocketHelper.clear();
//        }
        super.onDestroy();
    }

    @Override
    protected WifiP2PListener getListener() {
        return mWifiP2PListener;
    }

    private WifiP2PListener mWifiP2PListener = new WifiP2PListener() {
        @Override
        public void onDiscoverPeers(boolean isSuccess) {
            toast(isSuccess ? "扫描设备成功" : "扫描设备失败");
        }

        @Override
        public void onWifiP2pEnabled(boolean isEnabled) {
            DLog.INSTANCE.logV(isEnabled ? "wifi p2p 可用" : "wifi p2p 不可用");
        }

        @Override
        public void onCreateGroup(boolean isSuccess) {
            toast(isSuccess ? "创建群组成功" : "创建群组失败");
            DLog.INSTANCE.logV(isSuccess ? "创建群组成功" : "创建群组失败");
        }

        @Override
        public void onRemoveGroup(boolean isSuccess) {
            toast(isSuccess ? "移除群组成功" : "移除群组失败");
        }

        @Override
        public void onConnectCallChanged(boolean isConnected) {
            String msg = isConnected ? "调用连接成功" : "调用连接失败";
            toast(msg);
            DLog.INSTANCE.logV(msg);
        }

        @Override
        public void onDisConnectCallChanged(boolean isDisConnected) {
            String msg = isDisConnected ? "断开连接成功" : "断开连接失败";
            DLog.INSTANCE.logV(msg);
            toast(msg);
        }

        @Override
        public void onSelfDeviceAvailable(@NonNull WifiP2pDevice wifiP2pDevice) {
            String msg = "当前设备信息 " + wifiP2pDevice.deviceAddress;

//            toast(msg);
            DLog.INSTANCE.logV(msg);
        }

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            if (wifiP2pInfo != null && !TextUtils.isEmpty(wifiP2pInfo.groupOwnerAddress.getHostAddress())) {
                if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                    DLog.INSTANCE.logV("isGroupOwner");
                } else if (wifiP2pInfo.groupFormed) {
                    DLog.INSTANCE.logV("ispeer");
                }
                String ip = wifiP2pInfo.groupOwnerAddress.getHostAddress();
                Toast.makeText(ServerReceiveActivity.this, "连接成功 - " + ip, Toast.LENGTH_SHORT).show();
                DLog.INSTANCE.logV("连接成功 - " + ip);


            } else {
                Toast.makeText(ServerReceiveActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                DLog.INSTANCE.logV("连接失败");
            }
        }
//36:46:ec:fd:7e:30

        public int getControlPort(WifiP2pDevice device) {
            try {
                Class<?> c = Class.forName("android.net.wifi.p2p.WifiP2pDevice");
                //Class<?> c = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo");
                Field field = c.getDeclaredField("wfdInfo");
                WifiP2pWfdInfo wfdInfo = (WifiP2pWfdInfo) field.get(device);

               Field mCtrlPort = wfdInfo.getClass().getDeclaredField("mCtrlPort");
               mCtrlPort.setAccessible(true);
               Log.i("aaa","aaa:"+mCtrlPort.get(wfdInfo));


                Method set = c.getDeclaredMethod("getControlPort", WifiP2pWfdInfo.class);
                set.setAccessible(true);
                return (int) set.invoke(wfdInfo);
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        }

//        public int getDevicePort(WifiP2pDevice device) {
//            int port = 123;
//            try {
//                Field field = device.getClass().getDeclaredField("wfdInfo");//Field("wfdInfo")
//                field.setAccessible(true);
//
//                if (field == null) {
//                    DLog.INSTANCE.logV("field==null");
//                    return port;
//                }
//                WifiP2pWfdInfo wfdInfo = (WifiP2pWfdInfo) field.get(device);
//
//                if (wfdInfo != null) {
//                    port = wfdInfo.getControlPort();
//                    if (port == 0) {
//                        port = 123;
//                        DLog.INSTANCE.logV("set port to 123 default value");
//                    }
//                }
//            } catch (Exception e) {
//                DLog.INSTANCE.logV("sgetMessage：" + e.getMessage());
//                e.printStackTrace();
//            }
//            return port;
//        }

        @Override
        public void onPeersAvailable(@NonNull Collection<WifiP2pDevice> wifiP2pDeviceList) {
            toast("发现设备数量 " + wifiP2pDeviceList.size());
            DLog.INSTANCE.logV("发现设备数量 " + wifiP2pDeviceList.size());

//            for (WifiP2pDevice device : wifiP2pDeviceList) {
//                boolean isconnected = (WifiP2pDevice.CONNECTED == device.status);
//                if (isconnected) {
//                    String macadress = device.deviceAddress;
//                    DLog.INSTANCE.logV("已连接设备mac地址： " + macadress);
//                    //int port = getDevicePort(device);
//                    int port = getControlPort(device);
//                    DLog.INSTANCE.logV("已连接设备Port： " + port);
//                }
//            }


//            if(wifiP2pDeviceList.size()>0){
//                        Intent intent = new Intent(ServerReceiveActivity.this, WifiDisplaySourceActivity.class);
//        //intent.putExtra(WfdConstants.PROJECTION_DATA, mProjectionData);
////        intent.putExtra(WfdConstants.SOURCE_HOST, sourceIp);
////        intent.putExtra(WfdConstants.SOURCE_PORT, sourcePort);
//        startActivity(intent);
//            }
        }

        @Override
        public void onGroupInfoAvailable(WifiP2pGroup group) {


        }

    };

//    private class RetryStartWfdSinkRunnable implements Runnable {
//        private final WifiP2pGroup mGroup;
//        private final String mGroupOwnerIp;
//        private int mRetryCount;
//
//        public RetryStartWfdSinkRunnable(WifiP2pGroup group, String groupOwnerIp) {
//            mGroup = group;
//            mGroupOwnerIp = groupOwnerIp;
//        }
//
//        public int getDeviceType() {
//            try {
//                Class<?> c = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo");
//                Method set = c.getMethod("getDeviceType");
//                return (int) set.invoke(c);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return 0;
//            }
//        }
//
//        public int getControlPort() {
//            try {
//                Class<?> c = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo");
//                Method set = c.getMethod("getControlPort");
//                return (int) set.invoke(c);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return 0;
//            }
//        }
//
//        public boolean isWfdEnabled() {
//            try {
//                Class<?> c = Class.forName("android.net.wifi.p2p.WifiP2pWfdInfo");
//                Method set = c.getMethod("isWfdEnabled");
//                return (boolean) set.invoke(c);
//            } catch (Exception e) {
//                e.printStackTrace();
//                Log.d(TAG, "111111111=isWfdEnabled");
//                return false;
//            }
//        }
//
//        @Override
//        public void run() {
//            String sourceMac = "00:00:00:00:00:00";
//            String sourceIp = null; // should be "192.168.49.*"
//            int controlPort = 7236;
//
//            Collection<WifiP2pDevice> p2pDevs = mGroup.getClientList();
//            Log.d(TAG, "onConnectionChanged source1 isWfdEnabled=" + p2pDevs.size());
//
//            for (WifiP2pDevice dev : p2pDevs) {
//                Log.d(TAG, "onConnectionChanged source2 isWfdEnabled=" + isWfdEnabled());
//                if (isWfdEnabled()) {
//                    int type = getDeviceType();
//                    Log.d(TAG, "onConnectionChanged source3 type=" + type);
//                    if (type == WFD_SOURCE
//                            || type == SOURCE_OR_PRIMARY_SINK) {
//                        sourceMac = dev.deviceAddress;
//                        Log.d(TAG, "onConnectionChanged source4 mac=" + sourceMac);
//                        controlPort = getControlPort();
//                        Log.d(TAG, "onConnectionChanged source5 port=" + controlPort);
//                        break;
//                    }
//                } else {
//                    continue;
//                }
//            }
//
//            if (mGroup.isGroupOwner()) {
//                Log.d(TAG, "This device is the p2p group owner");
//                sourceIp = mGroupOwnerIp;
//            } else {
//                Log.d(TAG, "This device is a p2p group client");
//                sourceIp = NetUtils.getP2pIpAddress();
//            }
//            Log.d(TAG, "onConnectionChanged source IP=" + sourceIp);
//            startWifiDisplaySourceActivity(sourceIp, controlPort);
//            mRetryStartWfdSinkRunnable = null;
//        }
//    }

    private void startWifiDisplaySourceActivity(String sourceIp, int sourcePort) {
        Log.d(TAG, "startWifiDisplaySourceActivity " + sourceIp + ':' + sourcePort);
//        Intent intent = new Intent(this, WifiDisplaySourceActivity.class);
//        //intent.putExtra(WfdConstants.PROJECTION_DATA, mProjectionData);
//        intent.putExtra(WfdConstants.SOURCE_HOST, sourceIp);
//        intent.putExtra(WfdConstants.SOURCE_PORT, sourcePort);
//        startActivity(intent);
//        finish();
    }
}
