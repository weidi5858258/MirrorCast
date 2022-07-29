package com.weidi.mirrorcast;

import android.media.MediaCodec;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.weidi.mirrorcast.MediaClient.LIMIT;
import static com.weidi.mirrorcast.MediaClient.LIMIT_DATA;
import static com.weidi.mirrorcast.MediaClient.OFFSET;
import static com.weidi.mirrorcast.MyJni.DO_SOMETHING_CODE_Client_set_info;

/***
 2020/08/03
 */
public class MediaServer {

    private static final String TAG =
            "player_alexander";

    private volatile static MediaServer sMediaServer;
    public static final int PORT = 8890;// 8890
    private ConcurrentHashMap<Socket, InputStream> map =
            new ConcurrentHashMap<Socket, InputStream>();
    private Iterator<Map.Entry<Socket, InputStream>> iterator;

    public boolean isHandling = false;

    // TCP
    private ServerSocket server;
    // 先做一路
    private Socket socket;
    // 读取客户端发送过来的数据
    private InputStream inputStream;
    // 不使用
    private OutputStream outputStream;

    // UDP
    private DatagramSocket datagramSocket;
    private byte[] frame;
    private DatagramPacket packet;
    private MyJni myJni;

    private byte[] sps_pps_portrait = null;
    private byte[] sps_pps_landscape = null;

    public interface OnClientListener {
        int CLIENT_TYPE_CONFIG = 1;
        int CLIENT_TYPE_CONNECT = 2;
        int CLIENT_TYPE_DISCONNECT = 3;

        void onClient(int which_client, int type);
    }

    private OnClientListener mOnClientListener;

    public void setOnClientListener(OnClientListener listener) {
        mOnClientListener = listener;
    }

    private MediaServer() {

    }

    public static MediaServer getInstance() {
        if (sMediaServer == null) {
            synchronized (MediaServer.class) {
                if (sMediaServer == null) {
                    sMediaServer = new MediaServer();
                }
            }
        }
        return sMediaServer;
    }

    // TCP child thread
    public void sccept() {
        if (isHandling || server != null) {
            Log.e(TAG, "MediaServer sccept() return");
            return;
        }

        try {
            Log.i(TAG, "MediaServer new ServerSocket() start");
            // 第一种方式
            // server = new ServerSocket(PORT);
            // 第二种方式
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            Log.i(TAG, "MediaServer new ServerSocket() end");
        } catch (Exception e) {
            // java.net.BindException: bind failed: EADDRINUSE (Address already in use)
            // Caused by: android.system.ErrnoException:
            e.printStackTrace();
            Log.e(TAG, "MediaServer new ServerSocket failure");
            close();
            return;
        }

        while (true) {
            try {
                Log.i(TAG, "MediaServer sccept() start");
                socket = server.accept();
                Log.i(TAG, "MediaServer sccept() end");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaServer sccept() failure");
                close();
                continue;
            }
            try {
                inputStream = socket.getInputStream();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaServer getInputStream() failure");
                close();
                continue;
            }
            try {
                outputStream = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaServer getOutputStream() failure");
                close();
                continue;
            }

            // create thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveData();
                }
            }).start();
        }

        /*while (mIsHandling) {
            try {
                Log.i(TAG, "MediaServer sccept() start");
                socket = server.accept();
                inputStream = socket.getInputStream();
                Log.i(TAG, "MediaServer sccept() end");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaServer sccept() failure");
                close();
                return;
            }
            clearMap();
            map.put(socket, inputStream);
        }*/

        /*clearMap();
        Socket socket = null;
        InputStream inputStream = null;
        while (mIsHandling) {
            try {
                Log.i(TAG, "MediaServer sccept() start");
                socket = server.accept();
                Log.i(TAG, "MediaServer sccept() end");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaServer sccept() failure");
                break;
            }
            try {
                inputStream = socket.getInputStream();
            } catch (Exception e) {
                e.printStackTrace();
                closeSocket(socket);
                socket = null;
                inputStream = null;
                Log.e(TAG, "MediaServer getOutputStream() failure");
                continue;
            }

            if (!map.contains(socket)) {
                map.put(socket, inputStream);
                iterator = map.entrySet().iterator();
            }
        }
        clearMap();*/
    }

    // UDP
    public void start() {
        if (isHandling || datagramSocket != null) {
            Log.e(TAG, "MediaServer start() return");
            return;
        }

        isHandling = true;
        receiveDataForUDP();
    }

    private void receiveData() {
        isHandling = true;
        byte[] frame = null;

        // 先读取配置参数
        frame = read(inputStream, 4);
        if (frame != null) {
            // 前4个字节是保存 编码后的数据的个数
            int need_read_size = bytesToInt(frame);
            // 编码后的数据
            frame = read(inputStream, need_read_size);
            if (frame != null) {
                putData(1, frame);
                if (mOnClientListener != null) {
                    mOnClientListener.onClient(1, OnClientListener.CLIENT_TYPE_CONFIG);
                }
            }
        }

        if (mOnClientListener != null) {
            mOnClientListener.onClient(1, OnClientListener.CLIENT_TYPE_CONNECT);
        }

        Log.i(TAG, "MediaServer receiveData() start");
        while (isHandling) {
            //long start = SystemClock.uptimeMillis();
            frame = read(inputStream, 4);
            if (frame == null) {
                break;
            }
            // 前4个字节是保存 编码后的数据的个数
            int need_read_size = bytesToInt(frame);
            // 编码后的数据
            frame = read(inputStream, need_read_size);
            if (frame == null) {
                break;
            }

            putData(1, frame);
            /*long end = SystemClock.uptimeMillis();
            Log.i(TAG, "MediaServer receiveData() (end - start): " + (end - start));
            if ((end - start) >= 300) {
                Log.e(TAG, "MediaServer receiveData() (end - start): " + (end - start));
            }*/
        }
        Log.i(TAG, "MediaServer receiveData() end");

        if (mOnClientListener != null) {
            mOnClientListener.onClient(1, OnClientListener.CLIENT_TYPE_DISCONNECT);
        }

        close();
    }

    private void receiveDataForUDP() {
        try {
            // String ip = MainActivity.getIPAddress();
            String ip = "172.18.108.80";
            InetAddress inetAddress = InetAddress.getByName(ip);
            datagramSocket = new DatagramSocket(5858, inetAddress);
            /*LIMIT = BUFFER_FLAG_NOT_KEY_FRAME;
            LIMIT_DATA = LIMIT - OFFSET;*/
            frame = new byte[LIMIT];
            packet = new DatagramPacket(frame, 0, LIMIT);
            myJni = MyJni.getDefault();
        } catch (Exception e) {
            Log.e(TAG, "MediaServer receiveDataForUDP() return for new DatagramSocket");
            e.printStackTrace();
            close();
            return;
        }

        byte[] data = null; // 1024个byte长度的数据
        byte[] tempData = null;
        int length = 0;     // 实际数据长度

        // 客户端基本信息
        try {
            Arrays.fill(frame, (byte) 0);
            datagramSocket.receive(packet);
            data = packet.getData();
            length = bytesToInt(data);
            String params = new String(data, OFFSET, length);
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + packet.getLength());
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + length);
            // ARS-AL00@@@@@video/avc@@@@@1080@@@@@2244@@@@@1
            Log.i(TAG, "MediaServer receiveDataForUDP() params:\n" + params);
            Phone.callThread(MyJni.class.getName(),
                    DO_SOMETHING_CODE_Client_set_info, new Object[]{params});
        } catch (IOException e) {
            Log.e(TAG, "MediaServer receiveDataForUDP() return for new DatagramSocket");
            e.printStackTrace();
            close();
            return;
        }

        // SPS PPS
        try {
            Arrays.fill(frame, (byte) 0);
            datagramSocket.receive(packet);
            data = packet.getData();
            length = bytesToInt(data);
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + packet.getLength());
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + length);
            if (data[4] == 1) {
                // 竖屏
                sps_pps_portrait = new byte[length];
                System.arraycopy(data, OFFSET, sps_pps_portrait, 0, length);
                Log.i(TAG, "MediaServer receiveDataForUDP() 1 data:\n" +
                        Arrays.toString(sps_pps_portrait));
                myJni.putData(1, sps_pps_portrait, length);
            } else {
                // 横屏
                sps_pps_landscape = new byte[length];
                System.arraycopy(data, OFFSET, sps_pps_landscape, 0, length);
                Log.i(TAG, "MediaServer receiveDataForUDP() 2 data:\n" +
                        Arrays.toString(sps_pps_landscape));
                myJni.putData(1, sps_pps_landscape, length);
            }

            Arrays.fill(frame, (byte) 0);
            datagramSocket.receive(packet);
            data = packet.getData();
            length = bytesToInt(data);
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + packet.getLength());
            Log.i(TAG, "MediaServer receiveDataForUDP() length: " + length);
            if (data[4] == 1) {
                // 竖屏
                sps_pps_portrait = new byte[length];
                System.arraycopy(data, OFFSET, sps_pps_portrait, 0, length);
                Log.i(TAG, "MediaServer receiveDataForUDP() 1 data:\n" +
                        Arrays.toString(sps_pps_portrait));
                myJni.putData(1, sps_pps_portrait, length);
            } else {
                // 横屏
                sps_pps_landscape = new byte[length];
                System.arraycopy(data, OFFSET, sps_pps_landscape, 0, length);
                Log.i(TAG, "MediaServer receiveDataForUDP() 2 data:\n" +
                        Arrays.toString(sps_pps_landscape));
                myJni.putData(1, sps_pps_landscape, length);
            }
        } catch (IOException e) {
            Log.e(TAG, "MediaServer receiveDataForUDP() return for new DatagramSocket");
            e.printStackTrace();
            close();
            return;
        }

        /*if (mOnClientListener != null) {
            mOnClientListener.onClient(1, OnClientListener.CLIENT_TYPE_CONNECT);
        }*/

        int count = 0;
        int tempLength = 0; // 表示某帧的数据大小
        int temp1 = 0;
        int temp2 = 0;
        int sendDataCount = 0;
        ArrayList<Integer> list = new ArrayList<>();
        long totalLength = 0;
        boolean needToAbandon = false; // 需要抛弃
        Log.i(TAG, "MediaServer receiveDataForUDP() start");
        while (isHandling) {
            if (datagramSocket == null) {
                Log.e(TAG, "MediaServer receiveDataForUDP() break for datagramSocket is null");
                break;
            }

            try {
                Arrays.fill(frame, (byte) 0);
                datagramSocket.receive(packet);
                // LIMIT个byte的数据(LIMIT = 4 + 1/2 + flags + count + 实际数据 + 可能多余的数据)
                data = packet.getData();
                // 数据实际长度
                length = bytesToInt(data);
                // 数量比较大时,需要分段发送,表示第几次发送
                sendDataCount = bytesToInt2(data);

                /*Log.i(TAG, "MediaServer receiveDataForUDP()=======================");
                Log.i(TAG, "MediaServer receiveDataForUDP() data[5]: " + data[5]);
                Log.i(TAG, "MediaServer receiveDataForUDP()  length: " + length);
                Log.i(TAG, "MediaServer receiveDataForUDP() data[6]: " + data[6]);*/

                /*if (data[5] == 1) {
                    if (!list.contains(length)) {
                        Log.d(TAG, "MediaServer receiveDataForUDP()     length: " + length);
                        list.add(length);
                        totalLength += length;
                        int len = (int) (totalLength / list.size());
                        Log.d(TAG, "MediaServer receiveDataForUDP()        len: " + len);
                    }
                }*/

                if (needToAbandon) {
                    if (data[5] == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        needToAbandon = false;
                    } else {
                        continue;
                    }
                }

                if (length <= LIMIT_DATA) {
                    // 完整帧
                    tempData = new byte[length + 2];
                    System.arraycopy(data, OFFSET, tempData, 0, length);
                    tempData[length] = data[4];
                    tempData[length + 1] = data[5];
                    // 送入队列
                    myJni.putData(1, tempData, tempData.length);
                    count = 0;
                    tempLength = 0;
                    temp1 = 0;
                    temp2 = 0;
                    continue;
                }

                if (sendDataCount == 1) {
                    // 当前帧数据量比较大,需要分段读取,"1"表示第一次读的数据
                    tempData = new byte[length + 2];
                    temp1 = length / LIMIT_DATA;
                    temp2 = length % LIMIT_DATA;
                    if (temp2 != 0) {
                        temp1 += 1; // 需要传递的次数
                    }
                    tempLength = length;
                    count = 1;
                    System.arraycopy(data, OFFSET,
                            tempData, (count - 1) * LIMIT_DATA, LIMIT_DATA);
                    continue;
                }

                if (tempLength != 0) {
                    if (tempLength == length) {
                        count++;
                        // 如果读取到的次数(data[6])跟这里累加的次数(count)相等,并且数据大小也是相等的
                        // 那么说明数据包没有丢失,现在读取到的数据都是属于某一帧分段后的数据.
                        if (count < temp1) {
                            System.arraycopy(data, OFFSET,
                                    tempData, (count - 1) * LIMIT_DATA, LIMIT_DATA);
                        } else {
                            // 最后一次读取的数据
                            int len = tempLength - (count - 1) * LIMIT_DATA;
                            System.arraycopy(data, OFFSET,
                                    tempData, (count - 1) * LIMIT_DATA, len);
                            tempData[length] = data[4];
                            tempData[length + 1] = data[5];
                            // 送入队列
                            myJni.putData(1, tempData, tempData.length);
                            count = 0;
                            tempLength = 0;
                            temp1 = 0;
                            temp2 = 0;
                        }
                    } else {
                        Log.i(TAG, "MediaServer receiveDataForUDP()-----------------------");
                        Log.i(TAG, "MediaServer receiveDataForUDP()    data[5]: " + data[5]);
                        Log.i(TAG, "MediaServer receiveDataForUDP() tempLength: " + tempLength);
                        Log.i(TAG, "MediaServer receiveDataForUDP()     length: " + length);
                        Log.i(TAG, "MediaServer receiveDataForUDP()      temp1: " + temp1);
                        Log.i(TAG, "MediaServer receiveDataForUDP()      count: " + count);
                        Log.i(TAG, "MediaServer receiveDataForUDP()  dataCount: " + sendDataCount);
                        if (data[5] != MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            // 非关键帧有丢失,那么与之在同一个GOP中的其他非关键帧也要抛弃掉
                            needToAbandon = true;
                            myJni.drainFrame();
                        }
                    }
                }

                // 可能遇到丢失数据了
                // "1"是关键帧
                //Log.e(TAG, "MediaServer receiveDataForUDP() 可能丢失数据了,是否是关键帧: " + data[5]);
                // tempData = null;
                // count = 0;
                // tempLength = 0;
                // temp1 = 0;
                // temp2 = 0;
            } catch (Exception e) {
                Log.e(TAG, "MediaServer receiveDataForUDP() return for Exception");
                e.printStackTrace();
                close();
                return;
            }

            //Log.i(TAG, "MediaServer receiveDataForUDP() size: " + need_read_size);
            //Log.i(TAG, "MediaServer receiveDataForUDP() data:\n" + Arrays.toString(frame));
            // putData(1, frame);
        }
        Log.i(TAG, "MediaServer receiveDataForUDP() end");

        /*if (mOnClientListener != null) {
            mOnClientListener.onClient(1, OnClientListener.CLIENT_TYPE_DISCONNECT);
        }*/

        close();
    }

    public synchronized void close() {
        Log.i(TAG, "MediaServer close() start");
        isHandling = false;
        closeInputStream(inputStream);
        closeOutputStream(outputStream);
        closeSocket(socket);
        if (datagramSocket != null) {
            if (datagramSocket.isBound() || datagramSocket.isConnected()) {
                datagramSocket.disconnect();
            }
            if (!datagramSocket.isClosed()) {
                datagramSocket.close();
            }
            datagramSocket = null;
        }
        //closeServer(server);
        //server = null;
        socket = null;
        inputStream = null;
        outputStream = null;
        clearMap();
        Log.i(TAG, "MediaServer close() end");
    }

    private synchronized void updateMap() {
        iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Socket, InputStream> entry = iterator.next();
            Socket socket = entry.getKey();
            InputStream inputStream = entry.getValue();
            if (socket == null
                    || inputStream == null
                    || !socket.isConnected()
                    || socket.isClosed()) {
                Log.i(TAG, "MediaServer updateMap() remove socket");
                iterator.remove();
            }
        }
        iterator = map.entrySet().iterator();
    }

    private synchronized void clearMap() {
        if (map.isEmpty()) {
            return;
        }

        iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Socket, InputStream> entry = iterator.next();
            Socket socket = entry.getKey();
            InputStream inputStream = entry.getValue();

            closeSocket(socket);
            closeInputStream(inputStream);
            socket = null;
            inputStream = null;

            iterator.remove();
        }
        map.clear();
    }

    private void closeServer(ServerSocket server) {
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            server = null;
        }
    }

    private void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                if (!socket.isInputShutdown() && !socket.isClosed()) {
                    socket.shutdownInput();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (!socket.isOutputShutdown() && !socket.isClosed()) {
                    socket.shutdownOutput();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
        }
    }

    private void closeInputStream(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
    }

    private void closeOutputStream(OutputStream outputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
    }

    private byte[] buffer = new byte[1920 * 1080];

    private byte[] read(InputStream is, int want_to_read_length) {
        int read_length = -1;
        int total_read_length = 0;
        byte[] buff = new byte[want_to_read_length];
        while (total_read_length < want_to_read_length) {
            try {
                read_length = is.read(buffer, 0, want_to_read_length - total_read_length);
                if (read_length != -1) {
                    System.arraycopy(buffer, 0, buff, total_read_length, read_length);
                    total_read_length += read_length;
                    continue;
                }
                buff = null;
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                buff = null;
                return null;
            }
        }
        return buff;
    }

    private byte[] readForUDP(int want_to_read_length) {
        int read_length = -1;
        int total_read_length = 0;
        byte[] buff = new byte[want_to_read_length];
        DatagramPacket packet = null;
        while (total_read_length < want_to_read_length) {
            try {
                packet = new DatagramPacket(buffer, 0, want_to_read_length - total_read_length);
                if (datagramSocket != null) {
                    datagramSocket.receive(packet);
                }
                read_length = packet.getLength();
                if (read_length > 0) {
                    System.arraycopy(packet.getData(), 0, buff, total_read_length, read_length);
                    total_read_length += read_length;
                    continue;
                }
                buff = null;
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                buff = null;
                return null;
            }
        }
        return buff;
    }

    /***
     * byte数组中取int数值，本方法适用于(低位在前，高位在后)的顺序，和intToBytes()配套使用
     *
     * @param src byte数组
     * @return int数值
     */
    private static int bytesToInt(byte[] src) {
        int value;
        value = (int) ((src[0] & 0xFF)
                | ((src[1] & 0xFF) << 8)
                | ((src[2] & 0xFF) << 16)
                | ((src[3] & 0xFF) << 24));
        return value;
    }

    private static int bytesToInt2(byte[] src) {
        int value;
        value = (int) ((src[6] & 0xFF)
                | ((src[7] & 0xFF) << 8));
        return value;
    }

    private void putData(int which_client, byte[] buffer) {
        //MyJni.getDefault().putDataToJava(which_client, buffer, buffer.length);
    }

}
