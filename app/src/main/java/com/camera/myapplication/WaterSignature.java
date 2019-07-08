package com.camera.myapplication;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static com.camera.myapplication.FrameRect.SIZE_OF_FLOAT;

/**
 * Created by zzr on 2018/5/22.
 */

public class WaterSignature {

    /**
     * 一个“完整”的正方形，从两维延伸到-1到1。
     * 当 模型/视图/投影矩阵是都为单位矩阵的时候，这将完全覆盖视口。
     * 纹理坐标相对于矩形是y反的。
     * (This seems to work out right with external textures from SurfaceTexture.)
     */
//    private static final float FULL_RECTANGLE_COORDS[] = {
//            -1.0f, -1.0f,   // 0 bottom left
//            1.0f, -1.0f,   // 1 bottom right
//            -1.0f,  1.0f,   // 2 top left
//            1.0f,  1.0f,   // 3 top right
//    };
//    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
//            0.0f, 1.0f,     //0 bottom left     //0.0f, 0.0f, // 0 bottom left
//            1.0f, 1.0f,     //1 bottom right    //1.0f, 0.0f, // 1 bottom right
//            0.0f, 0.0f,     //2 top left        //0.0f, 1.0f, // 2 top left
//            1.0f, 0.0f,     //3 top right       //1.0f, 1.0f, // 3 top right
//    };

    private static final float FULL_RECTANGLE_COORDS[] = {
            0, 0,   // 0 bottom left
            -0.5f, 0.5f,   // 1 bottom right
            0.5f,  0.5f,   // 2 top left
            0.5f,  -0.5f,   // 3 top right
            -0.5f, -0.5f,   // 1 bottom right
            -0.5f,  0.5f,   // 2 top left
    };
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.5f,  0.5f,
            0f,  0f,
            1f,  0f,
            1f,  1f,
            0f,  1f,
            0f,  0f,
    };


    private FloatBuffer mVertexArray;
    private FloatBuffer mTexCoordArray;
    private int mCoordsPerVertex;
    private int mCoordsPerTexture;
    private int mVertexCount;
    private int mVertexStride;
    private int mTexCoordStride;

    public float[] mProjectionMatrix = new float[16];// 投影矩阵
    public float[] mViewMatrix = new float[16]; // 摄像机位置朝向9参数矩阵
    public float[] mModelMatrix = new float[16];// 模型变换矩阵
    public float[] mMVPMatrix = new float[16];// 获取具体物体的总变换矩阵
    private float[] getFinalMatrix() {
        long time = SystemClock.uptimeMillis() % 4000L;
        float angle = 0.090f * ((int) time);
//        Matrix.setRotateM(mModelMatrix, 0, 90f, 0, 0, -1f);//矩陣旋轉 但是是在原有的圖像区域旋转  会变形(結合)

        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        return mMVPMatrix;
    }

    public WaterSignature() {
        mVertexArray = createFloatBuffer(FULL_RECTANGLE_COORDS);
        mTexCoordArray = createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);
        mCoordsPerVertex = 2;
        mCoordsPerTexture = 2;
        mVertexCount = FULL_RECTANGLE_COORDS.length / mCoordsPerVertex; // 4
        mTexCoordStride = 2 * SIZE_OF_FLOAT;
        mVertexStride = 2 * SIZE_OF_FLOAT;
        Matrix.setIdentityM(mProjectionMatrix, 0);
        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.setIdentityM(mMVPMatrix, 0);
    }

    private FloatBuffer createFloatBuffer(float[] coords) {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZE_OF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }

    private WaterSignSProgram mProgram;

    public void setShaderProgram(WaterSignSProgram mProgram) {
        this.mProgram = mProgram;
    }

    public void drawFrame(int mTextureId) {
        GLES20.glUseProgram(mProgram.getShaderProgramId());
        // 设置纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);
        GLES20.glUniform1i(mProgram.sTextureLoc, 0);
        GlUtil.checkGlError("GL_TEXTURE_2D sTexture");
        // 设置 model / view / projection 矩阵
        GLES20.glUniformMatrix4fv(mProgram.uMVPMatrixLoc, 1, false, getFinalMatrix(), 0);
        GlUtil.checkGlError("glUniformMatrix4fv uMVPMatrixLoc");
        // 使用简单的VAO 设置顶点坐标数据
        GLES20.glEnableVertexAttribArray(mProgram.aPositionLoc);
        GLES20.glVertexAttribPointer(mProgram.aPositionLoc, mCoordsPerVertex,
                GLES20.GL_FLOAT, false, mVertexStride, mVertexArray);
        GlUtil.checkGlError("VAO aPositionLoc");
        // 使用简单的VAO 设置纹理坐标数据
        GLES20.glEnableVertexAttribArray(mProgram.aTextureCoordLoc);
        GLES20.glVertexAttribPointer(mProgram.aTextureCoordLoc, mCoordsPerTexture,
                GLES20.GL_FLOAT, false, mTexCoordStride, mTexCoordArray);

        GlUtil.checkGlError("VAO aTextureCoordLoc");
        // GL_TRIANGLE_STRIP三角形带，这就为啥只需要指出4个坐标点，就能画出两个三角形了。
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mVertexCount);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, mVertexCount);
        // Done -- 解绑~
        GLES20.glDisableVertexAttribArray(mProgram.aPositionLoc);
        GLES20.glDisableVertexAttribArray(mProgram.aTextureCoordLoc);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glUseProgram(0);
    }
}
