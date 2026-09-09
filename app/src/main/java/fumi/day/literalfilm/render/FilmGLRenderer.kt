package fumi.day.literalfilm.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import fumi.day.literalfilm.domain.model.FilmPreset
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

/** Drives the live preview [GLSurfaceView]. Preset switches only touch uniforms + reseed grain. */
class FilmGLRenderer : GLSurfaceView.Renderer {

    private var shader: FilmGradeShader? = null
    private var textureId = 0

    private var lastUploadedBitmap: Bitmap? = null
    private var pendingBitmap: Bitmap? = null
    private var preset: FilmPreset = FilmPreset.IDENTITY
    private var seed = 0f

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var bitmapWidth = 1
    private var bitmapHeight = 1

    @Synchronized
    fun setSourceBitmap(bitmap: Bitmap) {
        if (bitmap === lastUploadedBitmap) return
        lastUploadedBitmap = bitmap
        pendingBitmap = bitmap
    }

    @Synchronized
    fun setPreset(preset: FilmPreset?) {
        this.preset = preset ?: FilmPreset.IDENTITY
        this.seed = Random.nextFloat() * 1000f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        shader = FilmGradeShader()
        textureId = createTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    @Synchronized
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        pendingBitmap?.let { bitmap ->
            uploadTexture(bitmap)
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
            pendingBitmap = null
        }

        if (lastUploadedBitmap != null) {
            val (scaleX, scaleY) = computeAspectFitScale()
            shader?.draw(textureId, preset, seed, scaleX, scaleY)
        }
    }

    /** Letterbox scale so the quad shows the image's true aspect ratio instead of stretching to fill the viewport. */
    private fun computeAspectFitScale(): Pair<Float, Float> {
        val viewportAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        return if (imageAspect > viewportAspect) {
            1f to (viewportAspect / imageAspect)
        } else {
            (imageAspect / viewportAspect) to 1f
        }
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

    private fun uploadTexture(bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }
}
