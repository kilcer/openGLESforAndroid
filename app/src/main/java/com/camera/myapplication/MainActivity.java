package com.camera.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity{

    private static FAGAGFASGAGSGAG  FAFAFA;
    public static void setFAFAFA(FAGAGFASGAGSGAG afgsa){
        FAFAFA = afgsa;
    }
    ServiceConnection mySconnect = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

    }
    public void ok(View v){
        this.bindService(new Intent(this, CameraService.class), mySconnect,BIND_AUTO_CREATE);
    }
    public void gogogogo(View v){
        if (FAFAFA != null){
            FAFAFA.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mySconnect);
    }
}