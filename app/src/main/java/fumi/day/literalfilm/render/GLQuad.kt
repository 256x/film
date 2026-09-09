package fumi.day.literalfilm.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val POSITION_ATTR = 0
private const val TEX_COORD_ATTR = 1
private const val STRIDE_BYTES = 4 * 4

/** Fullscreen textured quad shared by the preview and offscreen render passes. */
class GLQuad {

    // x, y, u, v — v=0 at the top of the source bitmap (matches GLUtils.texImage2D row order).
    private val vertices =
        floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

    private val buffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

    fun draw() {
        buffer.position(0)
        GLES30.glVertexAttribPointer(POSITION_ATTR, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, buffer)
        GLES30.glEnableVertexAttribArray(POSITION_ATTR)

        buffer.position(2)
        GLES30.glVertexAttribPointer(TEX_COORD_ATTR, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, buffer)
        GLES30.glEnableVertexAttribArray(TEX_COORD_ATTR)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(POSITION_ATTR)
        GLES30.glDisableVertexAttribArray(TEX_COORD_ATTR)
    }
}
