package info.hntr.aislens

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import info.hntr.aislens.opengl.DefaultCameraRenderer
import info.hntr.aislens.opengl.EGLSurfaceTextureListener
import info.hntr.aislens.opengl.TextureViewGLWrapper

import kotlinx.android.synthetic.main.activity_camera.*

class CameraActivity : AppCompatActivity() {
    var cameraManager: CameraManager? = null
    var cameraDevice: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var surface: Surface? = null

    lateinit var textureView: TextureView
    var surfaceTexture: SurfaceTexture? = null

    val backgroundThread = HandlerThread("bg")
    lateinit var backgroundHandler: Handler

    lateinit var textureViewGLWrapper: TextureViewGLWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.texture_view) as TextureView
        val defaultCameraRenderer = DefaultCameraRenderer(this)

        textureViewGLWrapper = TextureViewGLWrapper(defaultCameraRenderer)

        textureViewGLWrapper.setListener(object : EGLSurfaceTextureListener {
            override fun onSurfaceTextureReady(texture: SurfaceTexture) {
                surfaceTexture = texture
                openCamera()
            }
        }, Handler(Looper.getMainLooper()))

        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                textureViewGLWrapper.onSurfaceTextureAvailable(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                textureViewGLWrapper.onSurfaceTextureSizeChanged(surface, width, height)
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                textureViewGLWrapper.onSurfaceTextureUpdated(surface)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                textureViewGLWrapper.onSurfaceTextureDestroyed(surface)
                return true
            }
        }

        RxPermissions(this)
            .request(Manifest.permission.CAMERA)
            .subscribe { granted ->
                if (!granted) {
                    finish()
                } else {
                    openCamera()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }


    private fun openCamera() {
        if (!textureView.isAvailable) return
        if (surfaceTexture == null) return
        if (cameraDevice != null) return

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager!!.openCamera("0", object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@CameraActivity.cameraDevice = cameraDevice
                this@CameraActivity.surface = Surface(surfaceTexture)
                surfaceTexture?.setDefaultBufferSize(textureView.width, textureView.height)
                val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                req.addTarget(surface)

                camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        req.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                        session.setRepeatingRequest(req.build(), null, null)
                        this@CameraActivity.session = session
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession?) {
                        error("onConfigure Failed")
                    }
                }, null)
            }

            override fun onDisconnected(camera: CameraDevice?) {
            }

            override fun onError(camera: CameraDevice?, error: Int) {
                error("camera open failed")
            }
        }, null)
    }

    private fun closeCamera() {
        session?.close()
        session = null
        cameraDevice?.close()
        cameraDevice = null
        surfaceTexture = null
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    /*
    external fun stringFromJNI(): String

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
    */

}
