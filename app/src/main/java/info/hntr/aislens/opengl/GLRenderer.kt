import android.graphics.SurfaceTexture

/**
 * Renderer for the TextureView.
 */
interface GLRenderer {

    /**
     * Initialize the shader.
     */
    fun onSurfaceCreated(eglSurfaceTexture: SurfaceTexture, surfaceWidth: Int, surfaceHeight: Int)

    /**
     * Surface resized.
     */
    fun onSurfaceChanged(eglSurfaceTexture: SurfaceTexture, surfaceWidth: Int, surfaceHeight: Int)

    /**
     * Remove allocated resources.
     */
    fun onSurfaceDestroyed(eglSurfaceTexture: SurfaceTexture)

    /**
     * A frame from the camera is ready to be displayed.
     * <pre>
     * GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
     * surfaceTexture.updateTexImage();
    </pre> *
     * Call [SurfaceTexture.updateTexImage] when [android.opengl.GLES20.GL_TEXTURE0]
     * is active to bind the camera output to the `samplerExternalOES` in the shader.
     */
    fun onFrameAvailable(eglSurfaceTexture: SurfaceTexture)
}
