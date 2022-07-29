package com.weidi.mirrorcast;

import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.text.TextUtils;
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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

import static com.weidi.mirrorcast.Constants.MAINACTIVITY_ON_RESUME;
import static com.weidi.mirrorcast.MediaServer.bytesToInt;

public class MediaClient {

    private static final String TAG =
            "player_alexander";

    private volatile static MediaClient sMediaClient;

    private String ip = "192.168.49.1"; // "192.168.49.1"
    private int port = 5858;
    private boolean isConnected = false;

    // TCP
    private Socket socket;
    // 不使用
    private InputStream inputStream;
    // 向服务器发送数据
    private OutputStream outputStream;

    // UDP
    // private static final int LIMIT = 65507;
    // private static final int LIMIT_DATA = LIMIT - 7;

    public static final int BUFFER_FLAG_KEY_FRAME = 3076 * 10;
    public static final int BUFFER_FLAG_NOT_KEY_FRAME = 1024 * 10;
    public static final int LIMIT = 1280 * 10; // 15360 12800 10240
    public static final int OFFSET = 8;
    public static final int LIMIT_DATA = LIMIT - OFFSET;
    private DatagramSocket datagramSocket;
    private InetAddress inetAddress;
    private byte[] frame;
    private DatagramPacket packet;
    // Test
    private ServerSocket server;
    private HashMap<Integer, byte[]> dataMap;
    private Object lock = new Object();

    public AudioTrack getmAudioTrack() {
        return mAudioTrack;
    }

    private MediaClient() {
    }

    public static MediaClient getInstance() {
        if (sMediaClient == null) {
            synchronized (MediaClient.class) {
                if (sMediaClient == null) {
                    sMediaClient = new MediaClient();
                }
            }
        }
        return sMediaClient;
    }

    public void setIpAndPort(String ipAddr, int port) {
        this.ip = ipAddr;
        this.port = port;
    }

    public boolean connect() {
        if (TextUtils.isEmpty(ip)) {
            Log.e(TAG, "MediaClient ip is empty");
            return false;
        }

        Log.i(TAG, "MediaClient connect()");
        if (MyJni.USE_TCP) {
            try {
                isConnected = false;
                socket = new Socket(ip, MediaServer.PORT);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                isConnected = true;
                Log.i(TAG, "MediaClient connect() tcp success ip: " + ip + " port: " + port);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "MediaClient connect() failure");
                e.printStackTrace();
                close();
            }
        } else {
            try {
                isConnected = false;
                datagramSocket = new DatagramSocket(0);
                datagramSocket.setSoTimeout(10000);
                inetAddress = InetAddress.getByName(ip);
                /*LIMIT = BUFFER_FLAG_NOT_KEY_FRAME;
                LIMIT_DATA = LIMIT - OFFSET;*/
                frame = new byte[LIMIT];
                packet = new DatagramPacket(frame, LIMIT, inetAddress, port);
                dataMap = new HashMap<>();
                isConnected = true;
                Log.i(TAG, "MediaClient connect() udp success ip: " + ip + " port: " + port);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
        }

        return false;
    }

    public synchronized void sendData(byte[] data, int offsetInBytes, int sizeInBytes) {
        //  type:
        //  "8"表示字符串
        // "10"表示竖屏sps_pps
        // "20"表示横屏sps_pps
        // "30"表示完整帧(data小于65507,不需要拆分)
        // "43"表示分2次发送,现在是第1次发送数据(40+2+1), "44"表示分2次发送,现在是第2次发送数据(40+2+2)
        // "54"(50+3+1), "55"(50+3+2), "56"(50+3+3)
        // "65"(60+4+1), "66"(60+4+2), "67"(60+4+3), "68"(60+4+4)

        //   1/2: "1"表示竖屏, "2"表示横屏

        // flags: 判断是否是关键帧
        // 0                                   非视频帧
        // MediaCodec.BUFFER_FLAG_CODEC_CONFIG 配置帧
        // MediaCodec.BUFFER_FLAG_KEY_FRAME    关键帧

        // 为什么要把"1/2","flags"和"type"排在后面呢?
        // 原因是服务端接收到数据后,把数据送到MediaCodec解码时的offset在有些设备上只能是0或者1,因此排在了后面
        // TCP: 4 + sizeInBytes + 1/2 + flags        [sizeInBytes+6]
        // UDP: 4 + sizeInBytes + type + 1/2 + flags [sizeInBytes+7]

        if (MyJni.USE_TCP) {
            if (outputStream != null) {
                try {
                    outputStream.write(data, offsetInBytes, sizeInBytes);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() TCP failure");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                }
            }
            return;
        }

        if (!isConnected || datagramSocket == null) {
            Log.e(TAG, "MediaClient sendData() UDP failure for datagramSocket is null");
            return;
        }

        // 最大传65507字节
        DatagramPacket packet = null;
        if (sizeInBytes <= LIMIT) {
            if (data[sizeInBytes - 3] == 0) {
                data[sizeInBytes - 3] = 30;
            }
            packet = new DatagramPacket(data, sizeInBytes, inetAddress, port);
            try {
                datagramSocket.send(packet);
            } catch (IOException e) {
                Log.e(TAG, "MediaClient sendData() UDP failure");
                e.printStackTrace();
                close();
                Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
            }
            return;
        }

        // 实际数据大小
        int actualDataLength = sizeInBytes - 7;
        int temp1 = actualDataLength / LIMIT_DATA;
        int temp2 = actualDataLength % LIMIT_DATA;
        if (temp2 != 0) {
            temp1 += 1;
        }
        switch (temp1) {
            case 2: {
                Log.e(TAG, "MediaClient sendData() UDP 2 sizeInBytes: " + sizeInBytes);
                byte[] tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 43;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 43");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                int length = actualDataLength - LIMIT_DATA;
                tempData = new byte[length + 7];
                int2Bytes(tempData, length + 3);
                System.arraycopy(data, LIMIT_DATA + 4, tempData, 4, length);
                tempData[LIMIT_DATA + 4] = 44;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, length + 7, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 44");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }
                break;
            }
            case 3: {
                Log.e(TAG, "MediaClient sendData() UDP 3 sizeInBytes: " + sizeInBytes);
                byte[] tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 54;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 54");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, LIMIT_DATA + 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 55;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 55");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                int length = actualDataLength - 2 * LIMIT_DATA;
                tempData = new byte[length + 7];
                int2Bytes(tempData, length + 3);
                System.arraycopy(data, 2 * LIMIT_DATA + 4, tempData, 4, length);
                tempData[LIMIT_DATA + 4] = 56;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, length + 7, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 56");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }
                break;
            }
            case 4: {
                Log.e(TAG, "MediaClient sendData() UDP 4 sizeInBytes: " + sizeInBytes);
                byte[] tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 65;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 65");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, LIMIT_DATA + 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 66;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 66");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                tempData = new byte[LIMIT];
                int2Bytes(tempData, LIMIT_DATA + 3);
                System.arraycopy(data, 2 * LIMIT_DATA + 4, tempData, 4, LIMIT_DATA);
                tempData[LIMIT_DATA + 4] = 67;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, LIMIT, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 67");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }

                int length = actualDataLength - 3 * LIMIT_DATA;
                tempData = new byte[length + 7];
                int2Bytes(tempData, length + 3);
                System.arraycopy(data, 3 * LIMIT_DATA + 4, tempData, 4, length);
                tempData[LIMIT_DATA + 4] = 68;
                tempData[LIMIT_DATA + 5] = data[sizeInBytes - 3];
                tempData[LIMIT_DATA + 6] = data[sizeInBytes - 2];
                packet = new DatagramPacket(tempData, length + 7, inetAddress, port);
                try {
                    datagramSocket.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() UDP failure 68");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                    return;
                }
                break;
            }
            default:
                Log.e(TAG, "MediaClient sendData() UDP sizeInBytes: " + sizeInBytes);
                break;
        }
    }

    public synchronized void sendDataForUDP_Str(String info, int orientation) {
        if (!isConnected || datagramSocket == null) {
            Log.e(TAG, "MediaClient sendStrData() UDP failure for datagramSocket is null");
            return;
        }
        Arrays.fill(frame, (byte) 0);
        byte[] data = info.getBytes();
        int length = data.length;
        int2Bytes(frame, length);
        frame[4] = (byte) ((orientation == Configuration.ORIENTATION_PORTRAIT) ? 1 : 2);
        frame[5] = 10;  // 非视频帧
        int2Bytes2(frame, 60010); // 字符串
        System.arraycopy(data, 0, frame, OFFSET, length);
        Log.i(TAG, "sendDataForUDP_Str() frame:\n" + Arrays.toString(frame));
        try {
            datagramSocket.send(packet);
        } catch (IOException e) {
            Log.e(TAG, "MediaClient sendStrData() TCP failure");
            e.printStackTrace();
            close();
            Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
        }
    }

    public synchronized void sendDataForUDP_Sps_Pps(byte[] data, boolean isPortrait) {
        if (!isConnected || datagramSocket == null) {
            Log.e(TAG, "MediaClient sendStrData() UDP failure for datagramSocket is null");
            return;
        }
        Arrays.fill(frame, (byte) 0);
        int length = data.length;
        int2Bytes(frame, length);
        frame[4] = (byte) (isPortrait ? 1 : 2);
        frame[5] = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        int2Bytes2(frame, isPortrait ? 60020 : 60030);
        System.arraycopy(data, 0, frame, OFFSET, length);
        Log.i(TAG, "sendDataForUDP_Sps_Pps() frame:\n" + Arrays.toString(frame));
        try {
            datagramSocket.send(packet);
        } catch (IOException e) {
            Log.e(TAG, "MediaClient sendStrData() TCP failure");
            e.printStackTrace();
            close();
            Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
        }
    }

    public synchronized void sendDataForUDP(byte[] data,
                                            int offsetInBytes,  // 偏移量(一般为0)
                                            int sizeInBytes,    // 实际数据大小
                                            boolean isPortrait, //
                                            int flags) {
        //   1/2: "1"表示竖屏, "2"表示横屏

        // flags: 判断是否是关键帧
        // 20                                  下一帧是关键帧
        // 10                                  非视频帧(字符串)
        // 0                                   非关键帧
        // MediaCodec.BUFFER_FLAG_CODEC_CONFIG 配置帧
        // MediaCodec.BUFFER_FLAG_KEY_FRAME    关键帧

        //  count:
        // "60010"表示字符串
        // "60020"表示竖屏sps_pps
        // "60030"表示横屏sps_pps
        // "60040"表示完整帧(data小于[1024-8],不需要拆分)
        // "1" ~ "n"表示这是第几次数据(如有10byte要发送,每次发送3byte,那么需要发送4次,分别是第1次,第2次,第3次,第4次)

        // TCP: 4 + sizeInBytes + 1/2 + flags         [sizeInBytes+6]
        // UDP: 4 + 1/2 + flags + count + sizeInBytes [sizeInBytes+8]

        if (MyJni.USE_TCP) {
            if (outputStream != null) {
                try {
                    outputStream.write(data, offsetInBytes, sizeInBytes);
                } catch (IOException e) {
                    Log.e(TAG, "MediaClient sendData() TCP failure");
                    e.printStackTrace();
                    close();
                    Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                }
            }
            return;
        }

        if (!isConnected || datagramSocket == null) {
            Log.e(TAG, "MediaClient sendData() UDP failure for datagramSocket is null");
            return;
        }

        // 把要发送的数据先保存起来,接收端如果接收失败了,可以再次重新发送
        if (!dataMap.containsKey(sizeInBytes)) {
            dataMap.put(sizeInBytes, data);
        }

        // 最大传65507字节
        if (sizeInBytes <= LIMIT_DATA) {
            Arrays.fill(frame, (byte) 0);
            int2Bytes(frame, sizeInBytes);
            frame[4] = (byte) (isPortrait ? 1 : 2);
            frame[5] = (byte) flags;
            int2Bytes2(frame, 60040);
            System.arraycopy(data, 0, frame, OFFSET, sizeInBytes);
            try {
                datagramSocket.send(packet);
                /*if (flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    if (!next()) {
                        sendDataForUDP(data, offsetInBytes, sizeInBytes, isPortrait, flags);
                    }
                }*/
            } catch (IOException e) {
                Log.e(TAG, "MediaClient sendData() UDP failure");
                e.printStackTrace();
                close();
                Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
            }
            return;
        }

        int count = 0;
        int temp1 = sizeInBytes / LIMIT_DATA;
        int temp2 = sizeInBytes % LIMIT_DATA;
        if (temp2 != 0) {
            temp1 += 1; // 需要发送temp1次之后,才能把data这个数据发送完成
        }
        for (int i = 0; i < temp1; i++) {
            count++;
            Arrays.fill(frame, (byte) 0);
            int2Bytes(frame, sizeInBytes);
            frame[4] = (byte) (isPortrait ? 1 : 2);
            frame[5] = (byte) flags;
            int2Bytes2(frame, count);
            if (count < temp1) {
                System.arraycopy(data, i * LIMIT_DATA, frame, OFFSET, LIMIT_DATA);
            } else {
                int len = sizeInBytes - (count - 1) * LIMIT_DATA;
                System.arraycopy(data, i * LIMIT_DATA, frame, OFFSET, len);
            }
            try {
                datagramSocket.send(packet);
                /*if (count >= temp1 && flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    // 发送最后一帧时,并且是关键帧,那么请等待一下,等到对方返回一些信息后才决定下一步怎么走
                    if (!next()) {
                        sendDataForUDP(data, offsetInBytes, sizeInBytes, isPortrait, flags);
                    }
                }*/
            } catch (IOException e) {
                Log.e(TAG, "MediaClient sendData() UDP failure");
                e.printStackTrace();
                close();
                Phone.call(MainActivity.class.getName(), MAINACTIVITY_ON_RESUME, null);
                return;
            }
        }
    }

    public void startServerSocket() {
        try {
            Log.i(TAG, "MediaClient startServerSocket() start");
            // 第一种方式
            // server = new ServerSocket(PORT);
            // 第二种方式
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(MediaServer.PORT));
            Log.i(TAG, "MediaClient startServerSocket() end");
        } catch (Exception e) {
            // java.net.BindException: bind failed: EADDRINUSE (Address already in use)
            // Caused by: android.system.ErrnoException:
            Log.e(TAG, "MediaClient new ServerSocket failure");
            e.printStackTrace();
            close();
            return;
        }
        try {
            Log.i(TAG, "MediaClient sccept() start");
            socket = server.accept();
            Log.i(TAG, "MediaClient sccept() end");
        } catch (Exception e) {
            Log.e(TAG, "MediaClient sccept() failure");
            e.printStackTrace();
            close();
            return;
        }
        try {
            inputStream = socket.getInputStream();
        } catch (Exception e) {
            Log.e(TAG, "MediaClient getInputStream() failure");
            e.printStackTrace();
            close();
        }

        /*int dataLength = 0;
        int isSuccessful = 0; // 0表示成功,否则表示失败
        int isPortrait = 0;   // 1表示竖屏,2表示横屏
        while (isConnected) {
            readForTest(inputStream, 6);
            dataLength = bytesToInt(bufferForTest);
            isSuccessful = bufferForTest[4];
            isPortrait = bufferForTest[5];
            if (isSuccessful == 0) {
                // 接收成功,那么删除相应的数据
                synchronized (lock) {
                    if (dataMap.containsKey(dataLength)) {
                        byte[] data = dataMap.get(dataLength);
                        data = null;
                        dataMap.remove(dataLength);
                    }
                }
            } else {
                // 接收失败,那么重新发送
                synchronized (lock) {
                    if (dataMap.containsKey(dataLength)) {
                        byte[] data = dataMap.get(dataLength);
                        // send
                        sendDataForUDP(data, 0, dataLength,
                                (isPortrait == 1) ? true : false, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    }
                }
            }
        }*/
    }

    public synchronized void close() {
        Log.i(TAG, "MediaClient close() start");
        isConnected = false;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
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
        if (datagramSocket != null) {
            try {
                if (datagramSocket.isBound() || datagramSocket.isConnected()) {
                    datagramSocket.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (!datagramSocket.isClosed()) {
                    datagramSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            datagramSocket = null;
        }
        inetAddress = null;

        MediaUtils.releaseMediaCodec(mVideoMC);
        MediaUtils.releaseMediaCodec(mAudioMC);
        MediaUtils.releaseAudioTrack(mAudioTrack);
        mVideoMC = null;
        mAudioMC = null;
        mAudioTrack = null;

        mSps = null;
        mPps = null;
        mUploadPpsSps = true;
        mIsHeaderWrite = false;
        mIsKeyFrameWrite = false;
        Log.i(TAG, "MediaClient close() end");
    }

    private byte[] buffer = new byte[6];
    private byte[] bufferForTest = new byte[6];

    private boolean next() {
        if (inputStream == null) {
            return true;
        }

        Log.i(TAG, "MediaClient next() UDP 1");
        readForTest(inputStream, 6);
        int dataLength = bytesToInt(bufferForTest);
        int isSuccessful = bufferForTest[4];
        int isPortrait = bufferForTest[5];
        Log.i(TAG, "MediaClient next() UDP 2 isSuccessful: " + isSuccessful);
        if (isSuccessful == 0) {
            // 接收成功,那么删除相应的数据
            return true;
        } else {
            // 接收失败,那么重新发送
            return false;
        }
    }

    private void readForTest(InputStream is, int want_to_read_length) {
        int read_length = -1;
        int total_read_length = 0;
        while (total_read_length < want_to_read_length) {
            try {
                read_length = is.read(buffer, 0, want_to_read_length - total_read_length);
                if (read_length != -1) {
                    System.arraycopy(buffer, 0, bufferForTest, total_read_length, read_length);
                    total_read_length += read_length;
                    continue;
                }
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        return;
    }

    public static void int2Bytes(byte[] frame, int length) {
        frame[0] = (byte) length;
        frame[1] = (byte) (length >> 8);
        frame[2] = (byte) (length >> 16);
        frame[3] = (byte) (length >> 24);
    }

    public static void int2Bytes2(byte[] frame, int length) {
        frame[6] = (byte) length;
        frame[7] = (byte) (length >> 8);
    }

    ////////////////////////////////////////////////////////////////////////////////

    // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int NonIDR = 1;
    // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
    public final static int IDR = 5;
    // Supplemental enhancement information (SEI) sei_rbsp( )
    public final static int SEI = 6;
    // Sequence parameter set seq_parameter_set_rbsp( )
    public final static int SPS = 7;
    // Picture parameter set pic_parameter_set_rbsp( )
    public final static int PPS = 8;
    // Access unit delimiter access_unit_delimiter_rbsp( )
    public final static int AccessUnitDelimiter = 9;

    //public static final int HEADER = 0;
    //public static final int METADATA = 1;
    public static final int FIRST_VIDEO = 2;
    public static final int KEY_FRAME = 5;
    public static final int INTER_FRAME = 6;

    private static final byte[] HEADER = {0x00, 0x00, 0x00, 0x01};

    private byte[] mSps = null;
    private byte[] mPps = null;
    private boolean mUploadPpsSps = true;
    private boolean mIsHeaderWrite = false;
    private boolean mIsKeyFrameWrite = false;

    private MediaCodec mVideoMC;
    private MediaCodec mAudioMC;
    private MediaFormat mVideoMF;
    private MediaFormat mAudioMF;
    private AudioTrack mAudioTrack;

    public void playVideo() {
        Log.i(TAG, "MediaClient playVideo() start");
        if (!isConnected) {
            Log.e(TAG, "MediaClient playVideo() isn't connected");
            return;
        }

        mVideoMF = MediaUtils.getVideoDecoderMediaFormat(720, 1280);
        mVideoMC = MediaUtils.getVideoDecoderMediaCodec(mVideoMF);
        if (mVideoMC == null) {
            Log.e(TAG, "MediaClient playVideo() mVideoMC is null");
            return;
        }

        final int VIDEO_FRAME_MAX_LENGTH = mVideoMF.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        final byte[] data = new byte[VIDEO_FRAME_MAX_LENGTH];
        int readCount = 0;
        while (isConnected) {
            Arrays.fill(data, (byte) 0);
            try {
                readCount = inputStream.read(data, 0, VIDEO_FRAME_MAX_LENGTH);
                Log.i(TAG, "MediaClient playVideo() readCount: " + readCount);
                if (readCount <= 0) {
                    Log.e(TAG, "MediaClient playVideo() readCount: " + readCount);
                    break;
                }

                long presentationTimeUs = System.nanoTime() / 1000;
                /*EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                        mCallback,
                        EDMediaCodec.TYPE.TYPE_VIDEO,
                        mVideoMC,
                        null,
                        data,
                        0,
                        readCount,
                        presentationTimeUs,
                        0,
                        false,
                        true);*/
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient playVideo() " + e.toString());
                break;
            }
        }// while(...) end
        Log.i(TAG, "MediaClient playVideo() end");
    }

    public void playAudio() {
        Log.i(TAG, "MediaClient playAudio() start");
        if (!isConnected) {
            Log.e(TAG, "MediaClient playAudio() isn't connected");
            return;
        }

        mAudioMF = MediaUtils.getAudioDecoderMediaFormat();
        //mAudioMF.setInteger(MediaFormat.KEY_IS_ADTS, 1);
        // Android MediaCodec解码AAC，AudioTrack播放PCM音频
        // https://blog.csdn.net/lavender1626/article/details/80431902
        byte[] csd0 = new byte[]{(byte) 0x12, (byte) 0x10};
        mAudioMF.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));
        /*mAudioMC = MediaUtils.getAudioDecoderMediaCodec(mAudioMF);
        if (mAudioMC == null) {
            Log.e(TAG, "MediaClient playAudio() mAudioMC is null");
            return;
        }*/

        // 创建AudioTrack
        // 1.
        int sampleRateInHz =
                mAudioMF.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // 2.
        int channelCount =
                mAudioMF.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        // 3.
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        Log.d(TAG, "MediaClient playAudio()" +
                " sampleRateInHz: " + sampleRateInHz +
                " channelCount: " + channelCount +
                " audioFormat: " + audioFormat);
        mAudioTrack = MediaUtils.createAudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateInHz, channelCount, audioFormat,
                AudioTrack.MODE_STREAM);
        if (mAudioTrack != null) {
            mAudioTrack.play();
        } else {
            Log.e(TAG, "MediaClient playAudio() mAudioTrack is null");
            return;
        }

        final int AUDIO_FRAME_MAX_LENGTH = mAudioMF.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        final byte[] data = new byte[AUDIO_FRAME_MAX_LENGTH];
        int readCount = 0;
        while (isConnected) {
            Arrays.fill(data, (byte) 0);
            try {
                readCount = inputStream.read(data, 0, AUDIO_FRAME_MAX_LENGTH);
                if (readCount <= 0) {
                    Log.e(TAG, "MediaClient playAudio() readCount: " + readCount);
                    break;
                }

                mAudioTrack.write(data, 0, readCount);

                /*long presentationTimeUs = System.nanoTime() / 1000;
                EDMediaCodec.feedInputBufferAndDrainOutputBuffer(
                        mCallback,
                        EDMediaCodec.TYPE.TYPE_AUDIO,
                        mAudioMC,
                        data,
                        0,
                        readCount,
                        presentationTimeUs,
                        false,
                        true);*/
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "MediaClient playAudio() " + e.toString());
                break;
            }
        }// while(...) end
        Log.i(TAG, "MediaClient playAudio() end");
    }

    /**
     * the search result for annexb.
     */
    private static class AnnexbSearch {
        public int startCode = 0;
        public boolean match = false;
    }

    public void analyseVideoDataonlyH264(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        boolean isKeyFrame = false;
        byte[] buffer = null;
        int writeIndex = 0;
        while (room.position() < roomInfo.offset + roomInfo.size) {
            // frame: 去掉[0 0 0 1]后剩下的数据
            byte[] frame = annexbDemux(room, roomInfo);
            if (frame == null) {
                Log.e(TAG, "annexb not match.");
                break;
            }
            // ignore the nalu type aud(9)
            if (isAccessUnitDelimiter(frame)) {
                continue;
            }
            // for sps
            if (mUploadPpsSps && isSps(frame)) {
                mSps = frame;
                continue;
            }
            // for pps
            if (mUploadPpsSps && isPps(frame)) {
                mPps = frame;
                continue;
            }
            if (!mIsKeyFrameWrite) {
                // 第一次关键帧发送后,就不需要再做了
                // for IDR frame
                if (isKeyFrame(frame)) {
                    isKeyFrame = true;
                } else {
                    isKeyFrame = false;
                }
            }
            if (buffer == null) {
                buffer = new byte[roomInfo.size + 4];
                System.arraycopy(int2Bytes(roomInfo.size), 0, buffer, writeIndex, 4);
                writeIndex += 4;
            }
            System.arraycopy(HEADER, 0, buffer, writeIndex, 4);
            writeIndex += 4;
            System.arraycopy(frame, 0, buffer, writeIndex, frame.length);
            writeIndex += frame.length;
        }
        if (mUploadPpsSps && mPps != null && mSps != null) {
            onSpsPps(mSps, mPps);
            mUploadPpsSps = false;
        }
        if (buffer == null) {
            return;
        }
        onVideo(buffer, isKeyFrame);
    }

    public void analyseVideoDataonlyH264_2(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        if (mUploadPpsSps) {
            handleSpsPps(room, roomInfo);
            mUploadPpsSps = false;
            return;
        }

        byte[] frame = null;
        switch (mark) {
            case MARK0:
            case MARK3:
            case MARK4: {
                Log.e(TAG, "I don't know how to do ^ $ ^");
                return;
            }
            case MARK1: {
                // 0 0 0 1 ...
                frame = new byte[roomInfo.size + 4];
                room.get(frame, 4, roomInfo.size);
                System.arraycopy(int2Bytes(roomInfo.size), 0, frame, 0, 4);
                break;
            }
            case MARK2: {
                //   0 0 1 ...
                frame = new byte[roomInfo.size + 5];
                room.get(frame, 5, roomInfo.size);
                System.arraycopy(int2Bytes(roomInfo.size + 1), 0, frame, 0, 4);
                break;
            }
            default:
                break;
        }

        if (!mIsKeyFrameWrite) {
            // 第一次关键帧发送后,就不需要再做了
            // for IDR frame
            if (frame != null) {
                byte bt = frame[8];
                boolean isKeyFrame = ((bt & 0x1f) == IDR);
                onVideo(frame, isKeyFrame);
                return;
            }
        }

        if (frame != null) {
            sendData(frame);
        }
    }

    private static final int MARK0 = 0;
    // 0 0 0 1
    private static final int MARK1 = 1;
    //   0 0 1
    private static final int MARK2 = 2;
    // ... 103 ... 104 ...
    private static final int MARK3 = 3;
    // ...  39 ... 40 ...
    private static final int MARK4 = 4;
    private int mark = MARK0;

    private void handleSpsPps(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        mSps = null;
        mPps = null;
        byte[] sps_pps = new byte[roomInfo.size];
        room.get(sps_pps, 0, roomInfo.size);
        room.position(roomInfo.offset);
        room.limit(roomInfo.offset + roomInfo.size);
        try {
            mark = MARK0;
            int index = -1;
            if (sps_pps[0] == 0
                    && sps_pps[1] == 0
                    && sps_pps[2] == 0
                    && sps_pps[3] == 1) {
                // region
                for (int i = 1; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 0
                            && sps_pps[i + 1] == 0
                            && sps_pps[i + 2] == 0
                            && sps_pps[i + 3] == 1) {
                        index = i;
                        mark = MARK1;
                        break;
                    }
                }
                // endregion
            } else if (sps_pps[0] == 0
                    && sps_pps[1] == 0
                    && sps_pps[2] == 1) {
                // region
                for (int i = 1; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 0
                            && sps_pps[i + 1] == 0
                            && sps_pps[i + 2] == 1) {
                        index = i;
                        mark = MARK2;
                        break;
                    }
                }
                // endregion
            }

            byte[] sps = null;
            byte[] pps = null;
            if (index != -1) {
                // region
                sps = new byte[index];
                pps = new byte[sps_pps.length - index];
                room.get(sps, 0, sps.length);
                room.get(pps, 0, pps.length);

                switch (mark) {
                    case MARK1: {
                        mSps = new byte[sps.length + 4];
                        System.arraycopy(int2Bytes(sps.length), 0, mSps, 0, 4);
                        System.arraycopy(sps, 0, mSps, 4, sps.length);

                        mPps = new byte[pps.length + 4];
                        System.arraycopy(int2Bytes(pps.length), 0, mPps, 0, 4);
                        System.arraycopy(pps, 0, mPps, 4, pps.length);
                        break;
                    }
                    case MARK2: {
                        mSps = new byte[sps.length + 5];
                        System.arraycopy(int2Bytes(sps.length + 1), 0, mSps, 0, 4);
                        System.arraycopy(sps, 0, mSps, 5, sps.length);

                        mPps = new byte[pps.length + 5];
                        System.arraycopy(int2Bytes(pps.length + 1), 0, mPps, 0, 4);
                        System.arraycopy(pps, 0, mPps, 5, pps.length);
                        break;
                    }
                    default:
                        break;
                }
                // endregion
            } else {
                // region
                // ... 103 ... 104 ...
                /***
                 sps_pps: [1, 66, 0, 30, -1, -31, 0, 26,// 多余数据
                 103, 66, -128, 30, -106, 82, 2, -32, -93, 96, 41,// sps
                 16, 0, 0, 3, 0, 16, 0, 0, 3, 3, 41, -38, 20, 42, 72,// sps
                 1, 0, 4,// 多余数据
                 104, -53, -115, 72]// pps

                 csd-0: [0, 0, 0, 1, 103, 66, -128, 30, -106, 82, 2, -32, -93, 96, 41,
                 16, 0, 0, 3, 0, 16, 0, 0, 3, 3, 41, -38, 20, 42, 72]
                 csd-1: [0, 0, 0, 1, 104, -53, -115, 72]
                 */
                mark = MARK3;
                int spsIndex = -1;
                int spsLength = 0;
                int ppsIndex = -1;
                int ppsLength = 0;
                for (int i = 0; i < sps_pps.length; i++) {
                    if (sps_pps[i] == 103) {
                        // 0x67 = 103
                        if (spsIndex == -1) {
                            spsIndex = i;
                            spsLength = sps_pps[i - 1];
                            if (spsLength <= 0) {
                                spsIndex = -1;
                            }
                        }
                    } else if (sps_pps[i] == 104) {
                        // 103后面可能有2个或多个104
                        // 0x68 = 104
                        ppsIndex = i;
                        ppsLength = sps_pps[i - 1];
                    }
                }

                // ... 39 ... 40 ...
                /***
                 sps_pps: [1, 100, 0, 31, -1, -31, 0, 16,// 多余数据
                 39, 100, 0, 31, -84, 86, -64, -120, 30, 123, -26, -96, 32, 32, 32, 64,// sps
                 1, 0, 4,// 多余数据
                 40, -18, 60, -80,// pps
                 -3, -8, -8, 0]// 多余数据

                 csd-0: [0, 0, 0, 1, 39, 100, 0, 31, -84, 86, -64, -120,
                 30, 123, -26, -96, 32, 32, 32, 64]
                 csd-1: [0, 0, 0, 1, 40, -18, 60, -80]
                 */
                if (spsIndex == -1 || ppsIndex == -1) {
                    mark = MARK4;
                    spsIndex = -1;
                    spsLength = 0;
                    ppsIndex = -1;
                    ppsLength = 0;
                    for (int i = 0; i < sps_pps.length; i++) {
                        if (sps_pps[i] == 39) {
                            if (spsIndex == -1) {
                                spsIndex = i;
                                spsLength = sps_pps[i - 1];
                                if (spsLength <= 0) {
                                    spsIndex = -1;
                                }
                            }
                        } else if (sps_pps[i] == 40) {
                            ppsIndex = i;
                            ppsLength = sps_pps[i - 1];
                        }
                    }
                }
                if (spsIndex != -1 && ppsIndex != -1) {
                    byte[] tempSpsPps = new byte[sps_pps.length];
                    room.get(tempSpsPps, 0, sps_pps.length);
                    sps = new byte[spsLength + 4];
                    pps = new byte[ppsLength + 4];
                    // 0x00, 0x00, 0x00, 0x01
                    sps[0] = pps[0] = 0;
                    sps[1] = pps[1] = 0;
                    sps[2] = pps[2] = 0;
                    sps[3] = pps[3] = 1;
                    System.arraycopy(tempSpsPps, spsIndex, sps, 4, spsLength);
                    System.arraycopy(tempSpsPps, ppsIndex, pps, 4, ppsLength);

                    mSps = new byte[sps.length + 4];
                    System.arraycopy(int2Bytes(sps.length), 0, mSps, 0, 4);
                    System.arraycopy(sps, 0, mSps, 4, sps.length);

                    mPps = new byte[pps.length + 4];
                    System.arraycopy(int2Bytes(pps.length), 0, mPps, 0, 4);
                    System.arraycopy(pps, 0, mPps, 4, pps.length);
                } else {
                    // 实在找不到sps和pps的数据了
                    mark = MARK0;
                }
                // endregion
            }

            if (mSps != null && mPps != null) {
                Log.i(TAG, "handleSpsPps() video \n  csd-0: " +
                        Arrays.toString(mSps));
                Log.i(TAG, "handleSpsPps() video \n  csd-1: " +
                        Arrays.toString(mPps));
                sendData(mSps);
                sendData(mPps);
                mIsHeaderWrite = true;
            } else {
                Log.e(TAG, "handleSpsPps() sps/pps is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleSpsPps() Exception: \n" + e);
        }
    }

    /**
     * 从硬编出来的数据取出一帧nal
     *
     * @param room
     * @param roomInfo
     * @return
     */
    private byte[] annexbDemux(ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        AnnexbSearch annexbSearch = new AnnexbSearch();
        avcStartWithAnnexb(annexbSearch, room, roomInfo);

        if (!annexbSearch.match || annexbSearch.startCode < 3) {
            return null;
        }

        for (int i = 0; i < annexbSearch.startCode; i++) {
            room.get();
        }

        ByteBuffer frameBuffer = room.slice();
        int pos = room.position();
        while (room.position() < roomInfo.offset + roomInfo.size) {
            avcStartWithAnnexb(annexbSearch, room, roomInfo);
            if (annexbSearch.match) {
                break;
            }
            room.get();
        }

        int size = room.position() - pos;
        byte[] frameBytes = new byte[size];
        frameBuffer.get(frameBytes);
        return frameBytes;
    }


    /**
     * 从硬编出来的byteBuffer中查找nal
     *
     * @param as
     * @param room
     * @param roomInfo
     */
    private void avcStartWithAnnexb(
            AnnexbSearch as, ByteBuffer room, MediaCodec.BufferInfo roomInfo) {
        as.match = false;
        as.startCode = 0;
        int pos = room.position();
        while (pos < roomInfo.offset + roomInfo.size - 3) {
            // not match.
            if (room.get(pos) != 0x00 || room.get(pos + 1) != 0x00) {
                break;
            }

            // match N[00] 00 00 01, where N>=0
            if (room.get(pos + 2) == 0x01) {
                as.match = true;
                as.startCode = pos + 3 - room.position();
                break;
            }
            pos++;
        }
    }

    private boolean isSps(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == SPS;
    }

    private boolean isPps(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == PPS;
    }

    private boolean isKeyFrame(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == IDR;
    }

    private static boolean isAccessUnitDelimiter(byte[] frame) {
        if (frame.length < 1) {
            return false;
        }
        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // [7 : SPS] [8 : PPS] [5 : I Frame] [1 : P Frame]
        int nal_unit_type = (frame[0] & 0x1f);
        return nal_unit_type == AccessUnitDelimiter;
    }

    private void onSpsPps(byte[] sps, byte[] pps) {
        mSps = new byte[sps.length + 8];
        System.arraycopy(int2Bytes(sps.length + 4), 0, mSps, 0, 4);
        System.arraycopy(HEADER, 0, mSps, 4, 4);
        System.arraycopy(sps, 0, mSps, 8, sps.length);
        sendData(mSps);

        mPps = new byte[pps.length + 8];
        System.arraycopy(int2Bytes(pps.length + 4), 0, mPps, 0, 4);
        System.arraycopy(HEADER, 0, mPps, 4, 4);
        System.arraycopy(pps, 0, mPps, 8, pps.length);
        sendData(mPps);

        /*ByteBuffer byteBuffer0 = ByteBuffer.allocate(sps.length + 4);
        byteBuffer0.put(HEADER);
        byteBuffer0.put(sps);
        mSps = new byte[sps.length + 4];
        System.arraycopy(byteBuffer0.array(), 0, mSps, 0, sps.length + 4);
        onPacket(mSps, FIRST_VIDEO);

        ByteBuffer byteBuffer1 = ByteBuffer.allocate(pps.length + 4);
        byteBuffer1.put(HEADER);
        byteBuffer1.put(pps);
        mPps = new byte[pps.length + 4];
        System.arraycopy(byteBuffer1.array(), 0, mPps, 0, pps.length + 4);
        onPacket(mPps, FIRST_VIDEO);*/

        mIsHeaderWrite = true;
    }

    private void onVideo(byte[] video, boolean isKeyFrame) {
        if (!mIsHeaderWrite) {
            return;
        }
        //int packetType = INTER_FRAME;
        if (isKeyFrame) {
            mIsKeyFrameWrite = true;
            //packetType = KEY_FRAME;
        }
        //确保第一帧是关键帧，避免一开始出现灰色模糊界面
        if (!mIsKeyFrameWrite) {
            return;
        }
        /*ByteBuffer room;
        if (isKeyFrame) {
            room = ByteBuffer.allocate(video.length);
            room.put(video);
        } else {
            room = ByteBuffer.allocate(video.length);
            room.put(video);
        }
        onPacket(room.array(), packetType);*/
        sendData(video);
        //onPacket(video, packetType);
    }

    private void onPacket(byte[] data, int type) {
        sendData(data);

        /*Frame<Chunk> frame = null;
        Video video = new Video();
        video.setData(data);
        if (type == FIRST_VIDEO) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_CONFIGURATION);
        } else if (type == KEY_FRAME) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_KEY_FRAME);
        } else if (type == INTER_FRAME) {
            frame = new Frame(video, type, Frame.FRAME_TYPE_INTER_FRAME);
        }
        if (frame == null) {
            return;
        }
        if (frame.data instanceof Video) {
            sendData(((Video) frame.data).getData());
        }*/
    }

    /***
     第一次发送sps
     第二次发送pps
     第三次发送关键帧
     第四次发送...
     */
    private void sendData(byte[] buff) {
        try {
            //Encode encode = new Encode(buff);
            //outputStream.write(encode.buildSendContent());
            outputStream.write(buff);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "MediaClient sendData() fialure");
        }
    }

    /**
     * 将int转为长度为4的byte数组
     *
     * @param length
     * @return
     */
    private static byte[] int2Bytes(int length) {
        byte[] result = new byte[4];
        result[0] = (byte) length;
        result[1] = (byte) (length >> 8);
        result[2] = (byte) (length >> 16);
        result[3] = (byte) (length >> 24);
        return result;
    }

}
