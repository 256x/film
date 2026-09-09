package fumi.day.literalfilm.domain.model

import kotlinx.serialization.Serializable

/**
 * A film-emulation look, user-authored via the slider editor and persisted as-is.
 * All fields feed uniforms of the single shared fragment shader (see
 * render/FilmGradeShader.kt) — no preset gets its own shader.
 */
@Serializable
data class FilmPreset(
    val id: String,
    val displayName: String,
    val brightness: Float = 0f,
    val shadowLift: Float,
    val highlightRolloff: Float,
    val contrast: Float,
    val gamma: Float,
    val shadowHueShiftDegrees: Float,
    val shadowHueShiftStrength: Float,
    val highlightHueShiftDegrees: Float,
    val highlightHueShiftStrength: Float,
    val skinProtectStrength: Float,
    val saturation: Float,
    val colorCast: FloatArray,
    val colorCastStrength: Float,
    val sharpness: Float = 0f,
    val structure: Float = 0f,
    val grainIntensity: Float,
    val grainSize: Float,
    val grainLuminanceDependence: Float,
    val halationIntensity: Float,
    val halationThreshold: Float,
    val halationColor: FloatArray,
    val vignetteIntensity: Float,
    val vignetteSoftness: Float,
    val monochrome: Boolean,
) {
    companion object {
        val IDENTITY =
            FilmPreset(
                id = "identity",
                displayName = "無加工",
                shadowLift = 0f,
                highlightRolloff = 0f,
                contrast = 0f,
                gamma = 1f,
                shadowHueShiftDegrees = 0f,
                shadowHueShiftStrength = 0f,
                highlightHueShiftDegrees = 0f,
                highlightHueShiftStrength = 0f,
                skinProtectStrength = 0f,
                saturation = 1f,
                colorCast = floatArrayOf(1f, 1f, 1f),
                colorCastStrength = 0f,
                grainIntensity = 0f,
                grainSize = 1f,
                grainLuminanceDependence = 0f,
                halationIntensity = 0f,
                halationThreshold = 1f,
                halationColor = floatArrayOf(1f, 1f, 1f),
                vignetteIntensity = 0f,
                vignetteSoftness = 0f,
                monochrome = false,
            )

        /** Base for the manual editor when nothing is selected — same "hidden field" defaults the old built-in presets used, so sliders don't produce degenerate results (e.g. a hard-edged vignette). */
        val EDITOR_DEFAULT =
            IDENTITY.copy(grainSize = 1.2f, grainLuminanceDependence = 0.5f, vignetteSoftness = 0.55f)
    }
}
