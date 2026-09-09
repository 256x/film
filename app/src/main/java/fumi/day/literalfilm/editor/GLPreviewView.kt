package fumi.day.literalfilm.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fumi.day.literalfilm.domain.model.FilmPreset
import fumi.day.literalfilm.render.FilmGLRenderer

private class FilmGLSurfaceView(context: Context) : GLSurfaceView(context) {
    val filmRenderer = FilmGLRenderer()

    init {
        setEGLContextClientVersion(3)
        setRenderer(filmRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

@Composable
fun GLPreviewView(bitmap: Bitmap, preset: FilmPreset?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> FilmGLSurfaceView(context) },
        update = { view ->
            view.filmRenderer.setSourceBitmap(bitmap)
            view.filmRenderer.setPreset(preset)
            view.requestRender()
        },
    )
}
