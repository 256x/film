package fumi.day.literalfilm.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLUtils
import fumi.day.literalfilm.domain.model.FilmPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Full-resolution, one-shot render used only for save/export. Reuses the exact same
 * [FilmGradeShader] as the live preview (via a headless EGL pbuffer instead of a
 * [android.opengl.GLSurfaceView]) so output always matches what was tapped on screen.
 *
 * Must be called from a single thread with no suspension in the middle — the EGL
 * context created here is thread-affine and is destroyed before this function returns.
 */
object OffscreenRenderer {

    fun render(sourceBitmap: Bitmap, preset: FilmPreset): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL" }

        try {
            val config = chooseConfig(display)

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

            try {
                val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
                val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
                check(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer surface" }

                try {
                    check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Unable to make EGL context current" }
                    return renderOnCurrentContext(sourceBitmap, preset, width, height)
                } finally {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(display, surface)
                }
            } finally {
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun chooseConfig(display: android.opengl.EGLDisplay): EGLConfig {
        val configAttribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "Unable to choose EGL config"
        }
        return configs[0] ?: error("No matching EGL config")
    }

    private fun renderOnCurrentContext(sourceBitmap: Bitmap, preset: FilmPreset, width: Int, height: Int): Bitmap {
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val textureId = createTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, sourceBitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        val shader = FilmGradeShader()
        val seed = Random.nextFloat() * 1000f
        // Full-res render always matches the source bitmap's own aspect ratio, no letterboxing needed.
        shader.draw(textureId, preset, seed, 1f, 1f)

        return readPixelsAsBitmap(width, height)
    }

    private fun createTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun readPixelsAsBitmap(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()

        val upsideDown = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        upsideDown.copyPixelsFromBuffer(buffer)

        // glReadPixels returns rows bottom-to-top; flip to standard top-to-bottom Bitmap row order.
        val flipped = Bitmap.createBitmap(upsideDown, 0, 0, width, height, Matrix().apply { postScale(1f, -1f) }, true)
        if (flipped !== upsideDown) upsideDown.recycle()
        return flipped
    }
}
