package com.camera.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;


public class CameraServiceView implements  SurfaceHolder.Callback, View.OnClickListener,FAGAGFASGAGSGAG{
    private static final String TAG = CameraService.class.getSimpleName();
    private Context mContext;
    private RelativeLayout rlContentView;
    private SurfaceView mSurFaceView;
    private Button btnBack;
    private PowerManager.WakeLock cpuWakeLock; // cpu保持工作电源锁
    protected static WindowManager mWindowManager;
    private static CameraServiceView mCameraServiceView;
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private int mTextureId;
    private SurfaceTexture mCameraTexture;
    private CameraServiceView.MainHandler mHandler;
    private int mSignTexId;
    private WaterSignature mWaterSign;
    private FBOFrameRect mFBOFrameRect;

    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 30;
    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    private FrameRect mFrameRect;

    private File outputFile;
    private volatile boolean mRequestRecord = false;
    private CameraRecordEncoder mRecordEncoder;
    private boolean recording;

    public static CameraServiceView getInstance(){
        synchronized (CameraServiceView.class){
            if (mCameraServiceView == null){
                mCameraServiceView = new CameraServiceView();
            }
        }
        return mCameraServiceView;
    }
    private void CameraServiceView(){

    }
    private void CameraServiceView(Context mContext){

    }
    public void  onDestory(Context mContext){
        mContext.unregisterReceiver(customeIntentListener);
    }
    public void  initBitch(Context mContext){
        this.mContext = mContext;
        MainActivity.setFAFAFA(this);
        mHandler = new MainHandler(this);
        mFrameRect = new FrameRect();
        mWaterSign = new WaterSignature();
        mFBOFrameRect = new FBOFrameRect();
        mRecordEncoder = new CameraRecordEncoder();
        onRegisterReceiver();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        // cpu锁
        cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zfyRecord.cpuWakeLock");
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        // |WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

        rlContentView = (RelativeLayout) View.inflate(mContext,
                R.layout.continuous_record_service, null);
        btnBack = rlContentView.findViewById(
                R.id.btn_back);
        btnBack.setOnClickListener(this);
        ImageView btnRecord = (ImageView) rlContentView.findViewById(R.id.btn_record);
        btnRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_UP:
                        mRequestRecord = false;
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mRequestRecord = true;
                        break;
                }
                return false;
            }
        });

        mSurFaceView = (SurfaceView) rlContentView.findViewById(R.id.continuousRecord_surfaceView);
        SurfaceHolder sh = mSurFaceView.getHolder();
        sh.addCallback(this);

        layoutParams.width = 1;
        layoutParams.height = 1;

        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        // 1x1小窗口时候隐藏黑色遮布
        mWindowManager.addView(rlContentView, layoutParams);
        outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "camera-test.mp4");
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
        fullScreenView();
    }



    public void fullScreenView() {
        if (rlContentView != null) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rlContentView
                    .getLayoutParams();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            mWindowManager.updateViewLayout(rlContentView, lp);
        }
    }
    private void exitScreenView(boolean isNaviHide) {
        if (rlContentView != null) {
            int wdith = isNaviHide ? WindowManager.LayoutParams.MATCH_PARENT
                    : 1;
            int height = isNaviHide ? WindowManager.LayoutParams.MATCH_PARENT
                    : 1;
            int left = 0;
            int top = 0;
            WindowManager.LayoutParams lp = (android.view.WindowManager.LayoutParams) rlContentView
                    .getLayoutParams();
            lp.width = wdith;
            lp.height = height;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            // 1x1小窗口时候隐藏黑色遮布
            mWindowManager.updateViewLayout(rlContentView, lp);
        }
    }
    FrameBuffer fbo;
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), true);
        mDisplaySurface.makeCurrent();
        mTextureId = GlUtil.createExternalTextureObject();
        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
                drawFrame();
            }
        });
        mFrameRect.setShaderProgram(new FrameRectSProgram());
        mWaterSign.setShaderProgram(new WaterSignSProgram());
        mFBOFrameRect.setShaderProgram(new WaterSignSProgram());
        fbo = new FrameBuffer();
        fbo.setup(VIDEO_HEIGHT, VIDEO_WIDTH);
        mSignTexId = TextureHelper.loadTexture(mContext);
        try {
            mCamera.setPreviewTexture(mCameraTexture);
            mCamera.startPreview();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        recording = mRecordEncoder.isRecording();
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_back:
                    exitScreenView(false);
                break;
                default:
                    break;
        }
    }

    @Override
    public void stop() {
        fullScreenView();
    }

    private static class MainHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE = 1;
        CameraServiceView view;
        public MainHandler(CameraServiceView view){
            this.view = view;
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE:
                    view.drawFrame();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    public static final float[] mTmpMatrix = new float[16];
    private void drawFrame() {
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }
        Log.d(TAG, " MSG_FRAME_AVAILABLE");
        mDisplaySurface.makeCurrent();

//        fbo.begin();
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        mCameraTexture.updateTexImage(); // 获取预览帧
        mCameraTexture.getTransformMatrix(mTmpMatrix); // 获取预览帧的变换矩阵
        int viewWidth = mSurFaceView.getWidth();
        int viewHeight = mSurFaceView.getHeight();
        Log.e("-------","viewWidth:"+viewWidth+"     viewHeight"+viewHeight);
        GLES20.glViewport(0, 0, viewWidth, viewHeight); //设置视口为整个surface大小
        mFrameRect.drawFrame(mTextureId, mTmpMatrix); // 画图
        GLES20.glViewport(0, 0, 300 , 100);
        mWaterSign.drawFrame(mSignTexId);
//        fbo.end();
//        GLES20.glViewport(0, 0, viewWidth, viewWidth);
//        mFBOFrameRect.drawFrame(fbo.getTextureId());
        mDisplaySurface.swapBuffers();

        // 水印录制 状态设置
        if(mRequestRecord) {
            if(!recording) {
                mRecordEncoder.startRecording(new CameraRecordEncoder.EncoderConfig(
                        outputFile, VIDEO_HEIGHT, VIDEO_WIDTH, 1000000,
                        EGL14.eglGetCurrentContext(), mContext));

//                mRecordEncoder.setTextureId(fbo.getTextureId());
                recording = mRecordEncoder.isRecording();
            }
//            mRecordEncoder.setTextureId(fbo.getTextureId());
            mRecordEncoder.setTextureId(mTextureId);
            mRecordEncoder.frameAvailable(mCameraTexture);
        } else {
            if(recording) {
                mRecordEncoder.stopRecording();
                recording = false;
            }
        }
    }

    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();
        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);
        // Give the camera a hint that we're recording video.
        // This can have a big impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.e(TAG, "Camera config: " + previewFacts);

        // 设置预览view的比例，注意摄像头默认是横着的
        FrameLayout layout = (FrameLayout) rlContentView.findViewById(R.id.continuousRecord_afl);
//        AspectFrameLayout layout = (AspectFrameLayout) rlContentView.findViewById(R.id.continuousRecord_afl);
//        layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
        // Portrait

//        layout.setAspectRatio((double) ((cameraPreviewSize.height)) / cameraPreviewSize.width);
//        mCamera.setDisplayOrientation(90);
//        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
//            @Override
//            public void onPreviewFrame(byte[] data, Camera camera) {
//                Log.e("TAG","onPreviewFrameonPreviewFrameonPreviewFrame");
//            }
//        });
    }

    private void onRegisterReceiver() {
        // 注册sdcard监听器

        IntentFilter customeFilter = new IntentFilter();

        customeFilter.addAction("zzx_action_record");
        mContext.registerReceiver(customeIntentListener, customeFilter);
    }
    private BroadcastReceiver customeIntentListener = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle bundle = intent.getExtras();
            Log.e(TAG, "fzyaction:" + action);
            if ("zzx_action_record".equals(action)) {

                if(mRequestRecord){
                    mRequestRecord = false;
                    Toast.makeText(mContext,"不開錄了  兄弟",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(mContext,"開錄了  兄弟",Toast.LENGTH_SHORT).show();
                    mRequestRecord = true;
                }
            }
        }
    };
}
