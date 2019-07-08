package com.camera.myapplication;

import android.graphics.SurfaceTexture;
import android.view.Surface;

public class WindowSurface extends EglSurfaceBase {

    private Surface mSurface;
    private boolean bReleaseSurface;

    //将native的surface 与 EGL关联起来
    public WindowSurface(EglCore eglCore, Surface surface, boolean isReleaseSurface) {
        super(eglCore);
        createWindowSurface(surface);
        mSurface = surface;
        bReleaseSurface = isReleaseSurface;
    }
    //将SurfaceTexture 与 EGL关联起来
    protected WindowSurface(EglCore eglCore, SurfaceTexture surfaceTexture) {
        super(eglCore);
        createWindowSurface(surfaceTexture);
    }
    //释放当前EGL上下文 关联 的 surface
    public void release() {
        releaseEglSurface();
        if (mSurface != null
                && bReleaseSurface) {
            mSurface.release();
            mSurface = null;
        }
    }
    // That's All.
}
