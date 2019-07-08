package com.camera.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {

    public static final String TAG = "ContinuousRecord";
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 30;
    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    SurfaceView sv;
    private FrameRect mFrameRect;

    private File outputFile;
    private volatile boolean mRequestRecord;
    private CameraRecordEncoder mRecordEncoder;
    private boolean recording;

    FAGAGFASGAGSGAG f;
    public void  setCallStopBack(FAGAGFASGAGSGAG f1){
        this.f = f1;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.continuous_record);
        mHandler = new MainHandler(this);
        mFrameRect = new FrameRect();
        mWaterSign = new WaterSignature();
        mFBOFrameRect = new FBOFrameRect();
        mRecordEncoder = new CameraRecordEncoder();
        sv = (SurfaceView) findViewById(R.id.continuousRecord_surfaceView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        ImageView btnRecord = (ImageView) findViewById(R.id.btn_record);
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
        outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "camera-test.mp4");
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
//        releaseCamera();
    }

    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private int mTextureId;
    private SurfaceTexture mCameraTexture;
    private MainHandler mHandler;
    private int mSignTexId;
    private WaterSignature mWaterSign;
    private FBOFrameRect mFBOFrameRect;
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated holder=" + surfaceHolder);
        // 准备好EGL环境，创建渲染介质mDisplaySurface
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEglCore, surfaceHolder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        mTextureId = GlUtil.createExternalTextureObject();
//           GlUtil.createWaterTextureId(GlUtil.createTextImage("我是水印", 40, "#fff000", "#00000000", 0););

        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
            }
        });

        mFrameRect.setShaderProgram(new FrameRectSProgram());
        mWaterSign.setShaderProgram(new WaterSignSProgram());
        mFBOFrameRect.setShaderProgram(new WaterSignSProgram());
        mSignTexId = TextureHelper.loadTexture(CameraActivity.this);

        try {
            Log.d(TAG, "starting camera preview");
            mCamera.setPreviewTexture(mCameraTexture);
            mCamera.startPreview();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        recording = mRecordEncoder.isRecording();
//        fbo = new FrameBuffer();
//        fbo.setup(VIDEO_HEIGHT, VIDEO_WIDTH);

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

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
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
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
        Log.d(TAG, "Camera config: " + previewFacts);

        // 设置预览view的比例，注意摄像头默认是横着的
//        FrameLayout layout = (FrameLayout) findViewById(R.id.continuousRecord_afl);
        AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.continuousRecord_afl);
        layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
        // Portrait

//        layout.setAspectRatio((double) ((cameraPreviewSize.height)+80) / cameraPreviewSize.width);
//        mCamera.setDisplayOrientation(90);
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    private final float[] mTmpMatrix = new float[16];
    private void drawFrame() {
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }
        Log.d(TAG, " MSG_FRAME_AVAILABLE");
        mDisplaySurface.makeCurrent();
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        mCameraTexture.updateTexImage(); // 获取预览帧

        mCameraTexture.getTransformMatrix(mTmpMatrix); // 获取预览帧的变换矩阵
        int viewWidth = sv.getWidth();
        int viewHeight = sv.getHeight();
        GLES20.glViewport(0, 0, viewWidth, viewHeight); //设置视口为整个surface大小
        mFrameRect.drawFrame(mTextureId, mTmpMatrix); // 画图
        GLES20.glViewport(0, 0, 100, 70);
        mWaterSign.drawFrame(mSignTexId);
        mDisplaySurface.swapBuffers();

        // 水印录制 状态设置
        if(mRequestRecord) {
            if(!recording) {
                mRecordEncoder.startRecording(new CameraRecordEncoder.EncoderConfig(
                        outputFile, VIDEO_HEIGHT, VIDEO_WIDTH, 1000000,
                        EGL14.eglGetCurrentContext(), CameraActivity.this));

//                mRecordEncoder.setTextureId(fbo.getTextureId());
                recording = mRecordEncoder.isRecording();
            }
             mRecordEncoder.setTextureId(mTextureId);
            mRecordEncoder.frameAvailable(mCameraTexture);
        } else {
            if(recording) {
                mRecordEncoder.stopRecording();
                recording = false;
            }
        }
    }

    private static class MainHandler extends Handler {
        private WeakReference<CameraActivity> mWeakActivity;
        public static final int MSG_FRAME_AVAILABLE = 1;


        MainHandler(CameraActivity activity) {
            mWeakActivity = new WeakReference<CameraActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE:
                    activity.drawFrame();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

}