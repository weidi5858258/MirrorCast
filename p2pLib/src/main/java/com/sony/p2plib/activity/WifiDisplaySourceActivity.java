package com.sony.p2plib.activity;

import android.app.Activity;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;

import com.sony.p2plib.R;

public class WifiDisplaySourceActivity extends Activity {
    private MediaProjection mMediaProjection;
    private DisplayMetrics mDisplayMetrics;
    private String mIFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_display_source);

//        Intent intent = getIntent();
//        Intent projData = intent.getParcelableExtra(WfdConstants.PROJECTION_DATA);
//        MediaProjectionManager mpm = null;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            mpm = getSystemService(MediaProjectionManager.class);
//        }
//        mMediaProjection = mpm.getMediaProjection(Activity.RESULT_OK, projData);
//        String host = intent.getStringExtra(WfdConstants.SOURCE_HOST);
//        int port = intent.getIntExtra(WfdConstants.SOURCE_PORT, -1);
//        mIFace = host + ':' + port;
//        mDisplayMetrics = new DisplayMetrics();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(mDisplayMetrics);
//        }
    }

    public void onClick(View v) {
       // new RemoteDisplay(mMediaProjection, mDisplayMetrics, mIFace);
    }
}