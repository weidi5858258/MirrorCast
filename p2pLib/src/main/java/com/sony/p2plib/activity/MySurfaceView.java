package com.sony.p2plib.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.RequiresApi;

import java.io.IOException;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mSurfaceHolder;
    private Canvas mCanvas;
    private Paint paint;
    private MediaPlayer mediaPlayer;
    private int lastpoint=0;
    public MySurfaceView(Context context) {
        this(context, null, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setKeepScreenOn(true);
        setZOrderOnTop(true);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        System.out.println("=========surfaceCreated========");
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                play();
            }
        }).start();
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        System.out.println("=========surfaceChanged========");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        System.out.println("=========surfaceDestroyed========");
            stop();

    }


    protected void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            lastpoint = mediaPlayer.getCurrentPosition();//保存当前播放点
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void play() {
//        String path = "/sdcard/DCIM/Camera/VID_20190110_102218.mp4";
//        File file = new File(path);
//        if (!file.exists()) {
//            return;
//        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 设置播放的视频源
            mediaPlayer.setDataSource("intel_rtp://" +"192.168.49.1" + ":" + 7236);
//            mediaPlayer.setDataSource(file.getAbsolutePath());
//            String uriPath = Environment.getExternalStorageDirectory().getPath() + "/30.mp4";//获取视频路径
//            Uri uri = Uri.parse(uriPath);//将路径转换成uri
            mediaPlayer.reset();
            //mediaPlayer.setDataSource(getContext(), uri);
            // 设置显示视频的SurfaceHolder
            mediaPlayer.setDisplay(getHolder());
            Log.e("lastpoint",""+lastpoint);
//            if(lastpoint!=0){
//                mediaPlayer.prepare();
//                mediaPlayer.seekTo(lastpoint,3);
//                mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                    @Override
//                    public void onSeekComplete(MediaPlayer mp) {
//                        mp.start();
//                    }
//                });
//            }else
                {
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mediaPlayer.start();
                    }
                });
            }
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    replay();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    play();
                    return false;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void replay() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
        } else {
            play();
        }
    }

    protected void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.start();
        }
    }
    protected void vote(float num1,float num2) {
        if (mediaPlayer != null) {
            Log.e("vote",""+num1+num2);
            mediaPlayer.setVolume(num1,num2);
        }
    }
    protected void set(String sourceIp, int sourcePort) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDataSource("intel_rtp://" + sourceIp + ":" + sourcePort);
                replay();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    protected void clearSeek() {
          lastpoint=0;
    }
    //            else if ("openvote".equals(intent.getStringExtra("isdis"))) {
//                Toast.makeText(SurfaceActivity.this, "打开声音", Toast.LENGTH_LONG).show();
//             my_surview.vote(1.0f,1.0f);
//            }else if ("closevote".equals(intent.getStringExtra("isdis"))) {
//                Toast.makeText(SurfaceActivity.this, "关闭声音", Toast.LENGTH_LONG).show();
//                my_surview.vote(0.0f,0.0f);
//            }
}
