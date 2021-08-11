package com.weidi.mirrorcast;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

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

    public boolean mIsHandling = false;
    private ServerSocket server;
    // 先做一路
    private Socket socket;
    // 读取客户端发送过来的数据
    private InputStream inputStream;
    // 不使用
    private OutputStream outputStream;

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

    // child thread
    public void sccept() {
        if (mIsHandling || server != null) {
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

    private void receiveData() {
        mIsHandling = true;
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
        while (mIsHandling) {
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

    public synchronized void close() {
        Log.i(TAG, "MediaServer close() start");
        mIsHandling = false;
        closeInputStream(inputStream);
        closeOutputStream(outputStream);
        closeSocket(socket);
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

    private void putData(int which_client, byte[] buffer) {
        //MyJni.getDefault().putData(which_client, buffer, buffer.length);
    }

}
