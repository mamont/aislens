package info.hntr.aislens.opengl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

import timber.log.Timber

internal class EglHelper {

    private var eglContext: EGLContext? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglSurface: EGLSurface? = null
    var eglSurfaceTexture: SurfaceTexture? = null
        private set
    private val eglTextures = IntArray(1)

    fun createSurface(surfaceTexture: SurfaceTexture, isVideo: Boolean): SurfaceTexture {
        this.eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val unusedEglVersion = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, unusedEglVersion, 0, unusedEglVersion, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        //Prepare the context
        val eglContextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, //Version 3
                EGL14.EGL_NONE //Null
        )

        var eglConfig = createEGLConfig(3, isVideo)
        if (eglConfig != null) {
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, eglContextAttributes, 0)
            if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
                Timber.e("Failed to create EGL3 context")
                eglContext = EGL14.EGL_NO_CONTEXT
            }
        }

        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            eglContextAttributes[1] = 2 //Fall back to version 2
            eglConfig = createEGLConfig(2, isVideo)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, eglContextAttributes, 0)
        }

        // Confirm with query.
        val values = IntArray(1)
        EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        Timber.d("EGLContext created, client version %d", values[0])

        // Prepare the surface
        val surfaceAttributes = intArrayOf(EGL14.EGL_NONE //Null
        )
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttributes, 0)
        checkEGLError("eglCreateWindowSurface")
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }

        //Create eglTextures
        GLES20.glGenTextures(eglTextures.size, eglTextures, 0)
        GlUtil.checkGLError("Texture bind")
        eglSurfaceTexture = SurfaceTexture(eglTextures[0])

        return eglSurfaceTexture!!
    }

    fun destroySurface() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            Timber.d("Disposing EGL resources")
            var released: Boolean
            released = EGL14.eglTerminate(eglDisplay)
            Timber.d("eglTerminate: %b", released)
            released = EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            Timber.d("eglMakeCurrent NONE: %b", released)
            released = EGL14.eglDestroyContext(eglDisplay, eglContext)
            Timber.d("eglDestroyContext: %b", released)
            released = EGL14.eglReleaseThread()
            Timber.d("eglReleaseThread: %b", released)
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        eglSurfaceTexture = null
    }

    private fun createEGLConfig(version: Int, isVideo: Boolean): EGLConfig? {
        // The actual surface is generally RGBA, so omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        val renderType = if (version == 3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
        val attributeList = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                //EGL14.EGL_DEPTH_SIZE, 16, //We are not going to use depth buffers
                //EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderType, EGL14.EGL_NONE, 0, // placeholder for video, if set
                EGL14.EGL_NONE //Null terminated
        )
        if (isVideo) {
            //Custom flag to allow recording video from openGL texture
            attributeList[attributeList.size - 3] = 0x3142 //Magic
            attributeList[attributeList.size - 2] = 1
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attributeList, 0, configs, 0, configs.size, numConfigs, 0)) {
            Timber.e("unable to find RGB8888 %d EGLConfig", version)
            return null
        }
        return configs[0]
    }


    fun makeCurrent(): Boolean {
        val success = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        if (!success) {
            Timber.e("eglMakeCurrent failed")
        }
        return success
    }

    fun swapBuffers(): Boolean {
        val success = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        if (!success) {
            Timber.e("eglSwapBuffers failed")
        }
        return success
    }

    /**
     * Checks for EGL errors.  Throws an exception if an error has been raised.
     */
    private fun checkEGLError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }
}
