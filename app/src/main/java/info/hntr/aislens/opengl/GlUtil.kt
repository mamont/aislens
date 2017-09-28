package info.hntr.aislens.opengl

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

import timber.log.Timber

/**
 * Some OpenGL utility functions.
 */
object GlUtil {

    /** Identity matrix for general use.  Don't modify or life will get weird.  */
    val IDENTITY_MATRIX: FloatArray

    init {
        IDENTITY_MATRIX = FloatArray(16)
        Matrix.setIdentityM(IDENTITY_MATRIX, 0)
    }

    /**
     * Creates a new program from the supplied vertex and fragment shaders.

     * @return A handle to the program, or 0 on failure.
     */
    fun createProgram(context: Context, vertexAssetFile: String, fragmentAssetFile: String): Int {
        val vertexSource = getStringFromFileInAssets(context, vertexAssetFile)
        val fragmentSource = getStringFromFileInAssets(context, fragmentAssetFile)
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        Timber.d("vertexShader log: %s", GLES20.glGetShaderInfoLog(vertexShader))

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            return 0
        }
        Timber.d("fragmentShader log: %s", GLES20.glGetShaderInfoLog(fragmentShader))

        var program = GLES20.glCreateProgram()
        checkGLError("glCreateProgram")
        if (program == 0) {
            Timber.e("Could not create program from %s, %s", fragmentAssetFile, vertexAssetFile)
        } else {
            GLES20.glAttachShader(program, vertexShader)
            checkGLError("glAttachShader")
            GLES20.glAttachShader(program, fragmentShader)
            checkGLError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Timber.e("Could not link program: %s", GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    /**
     * Compiles the provided shader source.

     * @return A handle to the shader, or 0 on failure.
     */
    private fun compileShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGLError("glCreateShader type=" + shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Timber.e("Could not compile shader %d", shaderType)
            Timber.e(GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }


    private fun getStringFromFileInAssets(ctx: Context, filename: String): String {
        try {
            val stream = ctx.assets.open(filename)
            val reader = BufferedReader(InputStreamReader(stream))
            val builder = StringBuilder()

            for (line in reader.lines()) {
                builder.append(line).append("\n")
            }

            stream.close();
            return builder.toString()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    fun checkGLError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = op + ": glError 0x" + Integer.toHexString(error)
            Timber.e(msg)
            throw RuntimeException(msg)
        }
    }

}//No instances