package info.hntr.aislens.opengl

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Semaphore
import android.view.TextureView

import GLRenderer;

interface EGLSurfaceTextureListener {
    /**
     * Underlying EGL Context is ready.
     */
    fun onSurfaceTextureReady(surfaceTexture: SurfaceTexture)
}

class TextureViewGLWrapper : SurfaceTexture.OnFrameAvailableListener, TextureView.SurfaceTextureListener {

    inner class RenderThread : Thread() {
        val eglContextReadyLock = Semaphore(0)
        var handler: Handler? = null

        override fun run() {
            Looper.prepare()
            handler = Handler()
            configure()
            eglContextReadyLock.release()
            Looper.loop()
            dispose()
        }

        internal fun blockingHandler(): Handler {
            //Block until the EGL context is ready to accept messages
            eglContextReadyLock.acquireUninterruptibly()
            eglContextReadyLock.release()
            return this.handler!!
        }
    }

    private val eglHelper = EglHelper()

    private var renderThread: RenderThread? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var eglSurfaceTexture: SurfaceTexture? = null
    private var listener: EGLSurfaceTextureListener? = null
    private var listenerHandler: Handler? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    //Drawing
    private val renderer: GLRenderer

    constructor(renderer: GLRenderer) {
        this.renderer = renderer
    }

    /**
     * Configure the listener for the EglSurface creation and the handler used to receive the
     * callback.
     */
    fun setListener(listener: EGLSurfaceTextureListener, handler: Handler) {
        this.listener = listener
        this.listenerHandler = handler
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (renderThread != null) {
            throw IllegalStateException("Already have a context")
        }
        this.surfaceTexture = surface
        this.renderThread = RenderThread()
        this.renderThread!!.start()
        this.surfaceWidth = width
        this.surfaceHeight = height
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (renderThread == null) {
            throw IllegalStateException("Context not ready")
        }
        this.surfaceWidth = width
        this.surfaceHeight = height
        this.renderThread!!.blockingHandler().post(Runnable { renderer.onSurfaceChanged(eglSurfaceTexture!!, surfaceWidth, surfaceHeight) })
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (renderThread == null) return true

        renderThread!!.handler!!.post(Runnable {
            val looper = Looper.myLooper()
            looper?.quit()
        })
        renderThread = null
        return true //Unused
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        //Nothing to do
    }

    private fun configure() {
        //Configure the display
        eglSurfaceTexture = eglHelper.createSurface(surfaceTexture!!, false)
        renderer.onSurfaceCreated(eglSurfaceTexture!!, surfaceWidth, surfaceHeight)
        eglSurfaceTexture!!.setOnFrameAvailableListener(this, renderThread!!.handler)
        //At this point we should be ready to accept frames from the camera
        listenerHandler!!.post { listener!!.onSurfaceTextureReady(eglSurfaceTexture!!) }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        renderer.onFrameAvailable(eglSurfaceTexture!!)
        eglHelper.makeCurrent()
        eglHelper.swapBuffers()
    }

    private fun dispose() {
        renderer.onSurfaceDestroyed(eglSurfaceTexture!!)
        eglHelper.destroySurface()
    }
}