package fumi.day.literalfilm.render

import android.opengl.GLES30
import fumi.day.literalfilm.domain.model.FilmPreset

private const val VERTEX_SRC =
    """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform vec2 uScale;
out vec2 vTexCoord;
void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition * uScale, 0.0, 1.0);
}
"""

// Single generic pass shared by all 9 presets: tone curve -> selective hue shift
// (skin-protected) -> saturation -> color cast -> halation glow -> grain -> vignette
// -> optional monochrome desaturation. Halation is approximated cheaply by sampling a
// blurred mip of the *source* texture rather than running a separate blur pass.
private const val FRAGMENT_SRC =
    """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSourceTex;
uniform float uBrightness;
uniform float uShadowLift;
uniform float uHighlightRolloff;
uniform float uContrast;
uniform float uGamma;
uniform float uShadowHueShiftDegrees;
uniform float uShadowHueShiftStrength;
uniform float uHighlightHueShiftDegrees;
uniform float uHighlightHueShiftStrength;
uniform float uSkinProtectHueCenter;
uniform float uSkinProtectHueWidth;
uniform float uSkinProtectStrength;
uniform float uSaturation;
uniform vec3 uColorCast;
uniform float uColorCastStrength;
uniform float uSharpness;
uniform float uStructure;
uniform float uGrainIntensity;
uniform float uGrainSize;
uniform float uGrainLuminanceDependence;
uniform float uSeed;
uniform float uHalationIntensity;
uniform float uHalationThreshold;
uniform vec3 uHalationColor;
uniform float uVignetteIntensity;
uniform float uVignetteSoftness;
uniform float uMonochrome;

float luminance(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Smooth value noise (bilinear-interpolated hash lattice) — reads as organic clumpy
// grain instead of a hard grid of flat-colored cells, at the same coarse spatial scale.
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    vec3 c = texture(uSourceTex, vTexCoord).rgb;

    // brightness: a uniform multiplicative exposure gain (stops-like, exp2). Scaling
    // every channel by the same factor leaves hue/saturation ratios untouched, unlike
    // the tone-curve steps below which need explicit luminance-preserving handling.
    c *= exp2(uBrightness);

    // tone curve: gamma, shadow lift, highlight rolloff, contrast around midpoint.
    // Highlight rolloff is applied to luminance only and the RGB ratio rescaled to
    // match — doing it per-channel (naive Reinhard) desaturates saturated highlights,
    // e.g. a vivid sunset sky goes gray instead of just dimming.
    c = pow(max(c, 0.0), vec3(1.0 / uGamma));
    // Only true near-black shadows get lifted (fades out by luma 0.3) — the old 0..0.5
    // linear falloff reached into midtones and, since it adds equally to R/G/B, washed
    // large chunks of the frame (e.g. asphalt) toward flat gray/white. The 0.5 damping
    // keeps the max swing tasteful — undamped, full lift drove near-blacks all the way
    // to white.
    float shadowLiftWeight = 1.0 - smoothstep(0.0, 0.3, luminance(c));
    c = c + uShadowLift * 0.5 * shadowLiftWeight * (1.0 - c);
    // Crushing shadows (negative lift) can push c below 0 — left unclamped, that negative
    // value corrupts every luminance-based step downstream (rolloff's division, the S-curve
    // rescale, rgb2hsv, which all assume non-negative input) and reads as random blown-out
    // colors, not a darker image.
    c = max(c, 0.0);

    // Highlight rolloff, bidirectional in -1..1: positive expands/brightens highlights
    // (inverse-Reinhard, pushes bright tones toward clipping — slide right = brighter
    // matches how exposure-ish sliders read intuitively), negative compresses/darkens
    // them (Reinhard-style, tames overexposure), 0 is identity either way.
    float preRolloffLuma = luminance(c);
    float rolloffAmount = clamp(uHighlightRolloff, -1.0, 1.0);
    float rolledLuma;
    if (rolloffAmount <= 0.0) {
        rolledLuma = mix(preRolloffLuma, preRolloffLuma / (1.0 + preRolloffLuma), -rolloffAmount);
    } else {
        float expanded = min(preRolloffLuma / max(1.0 - preRolloffLuma, 0.1), 1.0);
        rolledLuma = mix(preRolloffLuma, expanded, rolloffAmount);
    }
    c *= rolledLuma / max(preRolloffLuma, 0.0001);

    // S-curve contrast, applied to luminance only and rescaled back (not a per-channel
    // linear stretch, which desaturates). uContrast is bidirectional in -1..1: positive
    // blends toward a smoothstep S-shape (more contrast), negative blends toward flat
    // gray (less contrast/fade), 0 is identity either way.
    float preSLuma = luminance(c);
    float contrastAmount = clamp(uContrast, -1.0, 1.0);
    float sShapedLuma;
    if (contrastAmount >= 0.0) {
        sShapedLuma = preSLuma + (smoothstep(0.0, 1.0, preSLuma) - preSLuma) * contrastAmount;
    } else {
        sShapedLuma = mix(preSLuma, 0.5, -contrastAmount);
    }
    c *= sShapedLuma / max(preSLuma, 0.0001);
    c = clamp(c, 0.0, 1.0);

    // selective hue shift, protected around a configurable skin-tone hue band
    vec3 hsv = rgb2hsv(c);
    float hueDeg = hsv.x * 360.0;
    float delta = abs(mod(hueDeg - uSkinProtectHueCenter + 180.0, 360.0) - 180.0);
    float skinMask = 1.0 - smoothstep(0.0, max(uSkinProtectHueWidth, 0.001), delta) * uSkinProtectStrength;
    float luma = luminance(c);
    float shadowWeight = smoothstep(0.6, 0.0, luma);
    float highlightWeight = smoothstep(0.4, 1.0, luma);
    hsv.x =
        fract(
            hsv.x
                + (uShadowHueShiftDegrees / 360.0) * uShadowHueShiftStrength * shadowWeight * skinMask
                + (uHighlightHueShiftDegrees / 360.0) * uHighlightHueShiftStrength * highlightWeight * skinMask
        );
    c = hsv2rgb(hsv);

    // saturation + color cast
    c = mix(vec3(luminance(c)), c, uSaturation);
    c = mix(c, c * uColorCast, uColorCastStrength);

    // sharpness/structure: unsharp-mask style local contrast, detail extracted from the
    // *source* texture (a blurred version of the already-graded `c` isn't available in a
    // single pass) and added back onto the graded result. Sharpness = fine detail (small
    // blur radius), Structure = broader local contrast (larger blur radius).
    vec3 rawSample = texture(uSourceTex, vTexCoord).rgb;
    vec3 fineDetail = rawSample - textureLod(uSourceTex, vTexCoord, 1.2).rgb;
    vec3 coarseDetail = rawSample - textureLod(uSourceTex, vTexCoord, 3.0).rgb;
    c += fineDetail * uSharpness + coarseDetail * uStructure;

    // halation: bright-area glow sampled from a blurred mip of the source.
    // Real halation only blooms around near-clipped highlights (sun glints, bright
    // lamps) — a narrow smoothstep band plus a capped blend keeps a broad bright sky
    // from screen-blending itself to solid white.
    vec3 blurredSource = textureLod(uSourceTex, vTexCoord, 4.0).rgb;
    float glowMask = smoothstep(uHalationThreshold, min(uHalationThreshold + 0.08, 1.0), luminance(blurredSource));
    vec3 glow = uHalationColor * glowMask * uHalationIntensity * 0.6;
    c = 1.0 - (1.0 - c) * (1.0 - glow);

    // grain: smooth value noise at a coarse spatial scale — reads as organic clumpy
    // speckle. Per-pixel hash noise averages into a flat haze on any downscale/
    // compression pass; a hard-edged grid of flat cells reads as artificial blocks.
    // Two octaves (base + a finer half-scale layer) avoid a too-uniform blob size.
    float grainCellPx = max(uGrainSize * 1.5, 1.0);
    vec2 grainCoord = gl_FragCoord.xy / grainCellPx + vec2(uSeed);
    float n1 = valueNoise(grainCoord);
    float n2 = valueNoise(grainCoord * 2.3 + 17.0);
    float n = ((n1 * 0.7 + n2 * 0.3) - 0.5) * 2.0;
    float grainWeight = mix(1.0, 1.0 - luma, uGrainLuminanceDependence);
    c += n * uGrainIntensity * 0.35 * grainWeight;

    // vignette
    float dist = length(vTexCoord - 0.5) * 1.4142;
    float vignette = 1.0 - smoothstep(1.0 - uVignetteSoftness, 1.0, dist) * uVignetteIntensity;
    c *= vignette;

    // monochrome: red-weighted panchromatic-ish desaturation, applied last
    if (uMonochrome > 0.5) {
        float pan = dot(clamp(c, 0.0, 1.0), vec3(0.4, 0.4, 0.2));
        c = vec3(pan);
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

class FilmGradeShader {

    private val program = ShaderProgram(VERTEX_SRC, FRAGMENT_SRC)
    private val quad = GLQuad()

    fun draw(textureId: Int, preset: FilmPreset, seed: Float, scaleX: Float, scaleY: Float) {
        program.use()

        GLES30.glUniform2f(program.uniformLocation("uScale"), scaleX, scaleY)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(program.uniformLocation("uSourceTex"), 0)

        GLES30.glUniform1f(program.uniformLocation("uBrightness"), preset.brightness)
        GLES30.glUniform1f(program.uniformLocation("uShadowLift"), preset.shadowLift)
        GLES30.glUniform1f(program.uniformLocation("uHighlightRolloff"), preset.highlightRolloff)
        GLES30.glUniform1f(program.uniformLocation("uContrast"), preset.contrast)
        GLES30.glUniform1f(program.uniformLocation("uGamma"), preset.gamma)
        GLES30.glUniform1f(program.uniformLocation("uShadowHueShiftDegrees"), preset.shadowHueShiftDegrees)
        GLES30.glUniform1f(program.uniformLocation("uShadowHueShiftStrength"), preset.shadowHueShiftStrength)
        GLES30.glUniform1f(program.uniformLocation("uHighlightHueShiftDegrees"), preset.highlightHueShiftDegrees)
        GLES30.glUniform1f(program.uniformLocation("uHighlightHueShiftStrength"), preset.highlightHueShiftStrength)
        GLES30.glUniform1f(program.uniformLocation("uSkinProtectHueCenter"), 30f)
        GLES30.glUniform1f(program.uniformLocation("uSkinProtectHueWidth"), 25f)
        GLES30.glUniform1f(program.uniformLocation("uSkinProtectStrength"), preset.skinProtectStrength)
        GLES30.glUniform1f(program.uniformLocation("uSaturation"), preset.saturation)
        GLES30.glUniform3f(
            program.uniformLocation("uColorCast"),
            preset.colorCast[0],
            preset.colorCast[1],
            preset.colorCast[2],
        )
        GLES30.glUniform1f(program.uniformLocation("uColorCastStrength"), preset.colorCastStrength)
        GLES30.glUniform1f(program.uniformLocation("uSharpness"), preset.sharpness)
        GLES30.glUniform1f(program.uniformLocation("uStructure"), preset.structure)
        GLES30.glUniform1f(program.uniformLocation("uGrainIntensity"), preset.grainIntensity)
        GLES30.glUniform1f(program.uniformLocation("uGrainSize"), preset.grainSize)
        GLES30.glUniform1f(program.uniformLocation("uGrainLuminanceDependence"), preset.grainLuminanceDependence)
        GLES30.glUniform1f(program.uniformLocation("uSeed"), seed)
        GLES30.glUniform1f(program.uniformLocation("uHalationIntensity"), preset.halationIntensity)
        GLES30.glUniform1f(program.uniformLocation("uHalationThreshold"), preset.halationThreshold)
        GLES30.glUniform3f(
            program.uniformLocation("uHalationColor"),
            preset.halationColor[0],
            preset.halationColor[1],
            preset.halationColor[2],
        )
        GLES30.glUniform1f(program.uniformLocation("uVignetteIntensity"), preset.vignetteIntensity)
        GLES30.glUniform1f(program.uniformLocation("uVignetteSoftness"), preset.vignetteSoftness)
        GLES30.glUniform1f(program.uniformLocation("uMonochrome"), if (preset.monochrome) 1f else 0f)

        quad.draw()
    }
}
