package com.shamil.launcher.ui.livebg

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush

/*
 * The sky layer: cloud drift, mist, light rays, aurora and the storm's lightning light, all in one GPU program.
 * A "shader" is a tiny program the graphics chip runs once for every pixel; Android 13 (API 33) lets apps write
 * their own in AGSL (Android's shader language, very close to the mockup's WebGL GLSL). This is the mockup's
 * fragment shader FS, ported line by line. Changes from GLSL: float2/float3 instead of vec2/vec3, main() gets
 * the pixel position and returns the colour, no global variables (the aspect ratio is passed along), and early
 * exits for rows an effect can't reach (clouds stay in the top 2/3, the aurora in the top half), to save work.
 * The engine draws it into a half-size layer that is then stretched over the screen (see LiveWorld.drawSky).
 */
private const val SKY_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform float2 uSh;
uniform float uCloud;
uniform float uCover;
uniform float uMist;
uniform float uAur;
uniform float uRay;
uniform float uFlash;
uniform float3 uCL;
uniform float3 uCD;
uniform float3 uGlow;
uniform float3 uTone;
uniform float3 uMistC;
uniform float3 uFlashC;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}

float fbm(float2 p0) {
    float v = 0.0;
    float a = 0.5;
    float2 p = p0;
    float2x2 m = float2x2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = m * p;
        a *= 0.5;
    }
    return v;
}

// One cloud layer: density in x, the lit edge in y.
float2 cloudL(float2 uv, float asp, float sc, float sp, float seed, float cover) {
    float2 p = float2(uv.x * asp * sc - uTime * sp, uv.y * sc * 1.9) + seed;
    float2 q = float2(fbm(p * 0.6 + float2(0.0, uTime * 0.01)), fbm(p * 0.6 + float2(5.2, 1.3)));
    float2 pw = p + 1.6 * q;
    float d = fbm(pw);
    float th = mix(0.70, 0.38, cover);
    float dens = smoothstep(th, th + 0.22, d);
    float d2 = fbm(pw + float2(0.0, 0.16));
    float rim = clamp((d - d2) * 5.0, 0.0, 1.0);
    return float2(dens, rim);
}

half4 main(float2 fragCoord) {
    float asp = uRes.x / uRes.y;
    // Android's y already runs downward, like the mockup's flipped uv.
    float2 uv = fragCoord / uRes;
    float3 col = float3(0.0);
    float a = 0.0;
    float yMask = 1.0 - smoothstep(0.30, 0.64, uv.y);
    float dn = 0.0;
    float dfar = 0.0;
    if (uCloud > 0.003 && uv.y < 0.66) {
        float2 f = cloudL(uv - uSh * 0.02, asp, 1.5, 0.010, 3.1, uCover);
        float2 n = cloudL(uv - uSh * 0.05, asp, 2.6, 0.022, 11.7, uCover);
        float3 baseF = mix(uCD, uCL, 0.35);
        float3 litF = mix(uCL, uGlow, 0.45);
        float3 cF = mix(baseF, litF, f.y) * float3(1.0, 0.97, 1.02);
        float3 baseN = mix(uCD, uCL, 0.14);
        float3 litN = mix(uCL, uGlow, 0.6);
        float3 cN = mix(baseN, litN, n.y);
        float a1 = f.x * yMask * 0.55 * uCloud;
        float a2 = n.x * yMask * 0.78 * uCloud;
        col = cF * a1;
        a = a1;
        col = cN * a2 + col * (1.0 - a2);
        a = a2 + a * (1.0 - a2);
        dfar = f.x * yMask * uCloud;
        dn = n.x * yMask * uCloud;
    }
    if (uFlash > 0.003) {
        col += uFlashC * uFlash * (dn * 0.95 + dfar * 0.55) * 1.1;
        col += uFlashC * uFlash * 0.14 * (1.0 - uv.y * 0.8);
        a = max(a, uFlash * 0.1);
    }
    if (uMist > 0.003) {
        float wide = clamp(uMist - 1.0, 0.0, 1.0);
        float m1 = min(uMist, 1.0);
        float lo = mix(0.50, 0.20, wide);
        float hi = mix(0.70, 0.98, wide);
        if (uv.y > lo - 0.01 && uv.y < hi + 0.11) {
            float band = smoothstep(lo, lo + 0.14, uv.y) * (1.0 - smoothstep(hi - 0.1, hi + 0.1, uv.y));
            float n1 = fbm(float2(uv.x * asp * 2.2 - uTime * 0.012, uv.y * 7.0) + 3.0);
            float n2 = fbm(float2(uv.x * asp * 3.6 + uTime * 0.02, uv.y * 11.0) + 9.0);
            float m = band * smoothstep(0.22, 0.82, n1 * 0.62 + n2 * 0.5) * m1 * (1.05 + 0.2 * wide);
            col = uMistC * m + col * (1.0 - m);
            a = m + a * (1.0 - m);
        }
    }
    if (uRay > 0.003) {
        float2 src = float2(0.42, 0.56) - uSh * 0.03;
        float2 d = float2((uv.x - src.x) * asp, uv.y - src.y);
        float r = length(d) + 0.0001;
        float ang = atan(d.x, -d.y);
        float s1 = noise(float2(ang * 7.0, uTime * 0.05 + 2.0));
        float s2 = noise(float2(ang * 19.0 + 4.0, -uTime * 0.07));
        float rays = smoothstep(0.36, 0.86, s1 * 0.66 + s2 * 0.46);
        float breath = 0.72 + 0.28 * sin(uTime * 0.4 + ang * 3.0);
        float up = smoothstep(-0.05, 0.45, -d.y / r);
        float fall = exp(-r * 1.55) * smoothstep(0.0, 0.14, r);
        float v = rays * fall * up * breath;
        col += uGlow * (v * 0.62 + exp(-r * 5.0) * up * 0.16) * uRay;
    }
    if (uAur > 0.003 && uv.y < 0.5) {
        float x = uv.x * asp * 2.4 + uTime * 0.03;
        float3 ac = float3(0.0);
        for (int k = 0; k < 2; k++) {
            float fk = float(k);
            float wob = sin(x * 1.7 + uTime * 0.18 + fk * 2.0) * 0.05 + sin(x * 4.3 - uTime * 0.11 + fk) * 0.022;
            float base = 0.27 + fk * 0.08 + wob;
            float h = base - uv.y;
            float streak = noise(float2(uv.x * asp * 34.0 + wob * 9.0 + fk * 7.0, uTime * 0.12)) * 0.6
                + noise(float2(uv.x * asp * 80.0, uTime * 0.25 + fk)) * 0.4;
            float prof = smoothstep(0.0, 0.03, h) * exp(-h * 7.5);
            float3 c = mix(float3(0.25, 1.0, 0.62), float3(0.62, 0.34, 1.0), smoothstep(0.0, 0.34, h));
            c = mix(c, uTone, 0.22);
            ac += c * prof * (0.35 + 0.9 * streak) * (1.0 - fk * 0.4);
        }
        col += ac * uAur * 0.85;
    }
    col = min(col, float3(1.0));
    a = clamp(max(a, max(col.r, max(col.g, col.b))), 0.0, 1.0);
    return half4(half3(col), half(a));
}
"""

/**
 * The sky shader and the brush that paints with it. [create] returns null below Android 13, or if the phone's
 * graphics driver refuses the program (then the sky effects simply don't show; nothing crashes).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SkyShader private constructor(val shader: RuntimeShader) {
    val brush = ShaderBrush(shader)

    fun uniforms(
        w: Float, h: Float, time: Float, shX: Float, shY: Float,
        cloud: Float, cover: Float, mist: Float, aurora: Float, rays: Float, flash: Float,
        colours: FloatArray,
    ) {
        shader.setFloatUniform("uRes", w, h)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uSh", shX, shY)
        shader.setFloatUniform("uCloud", cloud)
        shader.setFloatUniform("uCover", cover)
        shader.setFloatUniform("uMist", mist)
        shader.setFloatUniform("uAur", aurora)
        shader.setFloatUniform("uRay", rays)
        shader.setFloatUniform("uFlash", flash)
        // colours: CL, CD, Glow, Tone, MistC, FlashC, three floats each (filled by the engine, no allocation).
        shader.setFloatUniform("uCL", colours[0], colours[1], colours[2])
        shader.setFloatUniform("uCD", colours[3], colours[4], colours[5])
        shader.setFloatUniform("uGlow", colours[6], colours[7], colours[8])
        shader.setFloatUniform("uTone", colours[9], colours[10], colours[11])
        shader.setFloatUniform("uMistC", colours[12], colours[13], colours[14])
        shader.setFloatUniform("uFlashC", colours[15], colours[16], colours[17])
    }

    companion object {
        fun create(): SkyShader? = try {
            SkyShader(RuntimeShader(SKY_AGSL))
        } catch (e: Exception) {
            Log.w("Seelie", "Living background: the sky shader didn't compile, sky effects are off", e)
            null
        }
    }
}

/** True when this phone can draw the sky effects (cloud drift, mist, light rays, aurora). */
internal val skyEffectsSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
