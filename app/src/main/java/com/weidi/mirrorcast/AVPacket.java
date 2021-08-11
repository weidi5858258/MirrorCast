package com.weidi.mirrorcast;

import android.media.MediaCodec;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Created by alexander on 2020/1/17.
 */
public class AVPacket implements Parcelable {

    public int serial = 0;
    public byte[] data = null;
    public int size = 0;
    public long presentationTimeUs = 0;// pts
    public long dts = 0;
    public long pos = 0;
    public long duration = 0;
    public MediaCodec.CryptoInfo cryptoInfo = null;
    public int flags = 0;

    public AVPacket(int size) {
        data = new byte[size];
        this.size = size;
    }

    public AVPacket(int size, long presentationTimeUs) {
        data = new byte[size];
        this.size = size;
        this.presentationTimeUs = presentationTimeUs;
    }

    public void clear() {
        //Arrays.fill(data, (byte) 0);
        data = null;
        cryptoInfo = null;
    }

    protected AVPacket(Parcel in) {
        data = in.createByteArray();
        size = in.readInt();
        presentationTimeUs = in.readLong();
        flags = in.readInt();
    }

    public static final Creator<AVPacket> CREATOR = new Creator<AVPacket>() {
        @Override
        public AVPacket createFromParcel(Parcel in) {
            return new AVPacket(in);
        }

        @Override
        public AVPacket[] newArray(int size) {
            return new AVPacket[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(data);
        dest.writeInt(size);
        dest.writeLong(presentationTimeUs);
        dest.writeInt(flags);
    }


}
