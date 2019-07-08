package com.camera.myapplication;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class CameraService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CameraServiceView.getInstance().initBitch(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CameraServiceView.getInstance().onDestory(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }
}
