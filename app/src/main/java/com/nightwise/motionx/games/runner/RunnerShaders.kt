package com.nightwise.motionx.games.runner

/** GLSL ES 3.00 shaders for Neon Dash. */
object RunnerShaders {

    const val LIT_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
uniform mat4 uModel;
out vec3 vN;
out vec3 vW;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vW = w.xyz;
    vN = mat3(uModel) * aNormal;
    gl_Position = uVP * w;
}
"""

    const val LIT_FS = """#version 300 es
precision mediump float;
in vec3 vN;
in vec3 vW;
uniform vec3 uColor;
uniform float uEmissive;
uniform float uAlpha;
uniform vec3 uCam;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform vec3 uLightDir;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vN);
    float diff = max(dot(n, -uLightDir), 0.0);
    float hemi = 0.5 + 0.5 * n.y;
    vec3 V = normalize(uCam - vW);
    float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
    // cool fill from the left, warm key from the right: reads well on the neon backdrop
    vec3 fill = vec3(0.35, 0.30, 0.75) * max(dot(n, normalize(vec3(-1.0, 0.4, 0.3))), 0.0);
    vec3 col = uColor * (0.22 * hemi + 0.70 * diff) + uColor * fill * 0.5 + mix(uColor, vec3(0.6, 0.9, 1.0), 0.5) * rim * 0.55;
    col = mix(col, uColor * 1.35, uEmissive);
    float d = length(vW - uCam);
    float fog = 1.0 - exp(-pow(d * uFogDensity, 2.0));
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, uAlpha * (1.0 - fog * 0.6));
}
"""

    const val GROUND_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
out vec3 vW;
void main() {
    vW = aPos;
    gl_Position = uVP * vec4(aPos, 1.0);
}
"""

    const val GROUND_FS = """#version 300 es
precision highp float;
in vec3 vW;
uniform float uScroll;
uniform vec3 uCam;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uLaneW;
uniform float uPulse;
out vec4 fragColor;

float gridLine(float v, float width) {
    float d = abs(fract(v - 0.5) - 0.5) / max(fwidth(v), 1e-4);
    return 1.0 - min(d / width, 1.0);
}
float glow(float d, float k) { return exp(-d * k); }

void main() {
    float x = vW.x;
    float z = vW.z - uScroll;
    float hw = uLaneW * 1.5;
    float ax = abs(x);
    vec3 col = vec3(0.020, 0.022, 0.055);

    // Synthwave grid outside the track
    float g = max(gridLine(x / 4.0, 1.2), gridLine(z / 4.0, 1.2));
    col += vec3(0.45, 0.15, 0.85) * g * 0.35 * step(hw + 0.6, ax);

    // Track surface
    float onTrack = 1.0 - smoothstep(hw - 0.02, hw + 0.02, ax);
    vec3 track = vec3(0.055, 0.050, 0.13);
    // subtle alternating tiles
    float tile = step(0.5, fract(z / 6.0));
    track += vec3(0.012, 0.010, 0.03) * tile;
    // cross lines
    track += vec3(0.10, 0.55, 0.85) * gridLine(z / 3.0, 1.0) * 0.18;
    col = mix(col, track, onTrack);

    // Lane dividers (cyan) and track edges (magenta), with soft glow
    float dDiv = abs(ax - uLaneW * 0.5);
    float dash = step(0.35, fract(z / 2.5));
    col += vec3(0.13, 0.88, 1.0) * (glow(dDiv, 22.0) * 0.9 * dash + glow(dDiv, 4.0) * 0.12) * onTrack;
    float dEdge = abs(ax - hw);
    col += vec3(1.0, 0.24, 0.65) * (glow(dEdge, 18.0) * 1.1 + glow(dEdge, 2.5) * (0.18 + 0.08 * uPulse));

    float d = length(vW - uCam);
    float fog = 1.0 - exp(-pow(d * uFogDensity, 2.0));
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, 1.0);
}
"""

    /** Fullscreen sky: gradient, synthwave sun with cut stripes, stars. */
    const val SKY_VS = """#version 300 es
out vec2 vUV;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUV = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.999, 1.0);
}
"""

    const val SKY_FS = """#version 300 es
precision highp float;
in vec2 vUV;
uniform float uHorizon;   // horizon height in uv (0..1)
uniform float uAspect;
uniform float uTime;
uniform vec3 uFogColor;
uniform float uSunX;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    float h = vUV.y - uHorizon;
    vec3 top = vec3(0.025, 0.02, 0.07);
    vec3 mid = vec3(0.20, 0.05, 0.30);
    vec3 col = mix(uFogColor, mid, smoothstep(0.0, 0.12, h));
    col = mix(col, top, smoothstep(0.10, 0.55, h));

    // stars
    vec2 sp = vec2(vUV.x * uAspect, vUV.y) * 90.0;
    vec2 cell = floor(sp);
    float s = hash(cell);
    float tw = 0.6 + 0.4 * sin(uTime * 2.0 + s * 40.0);
    float star = step(0.985, s) * smoothstep(0.12, 0.0, length(fract(sp) - 0.5)) * tw;
    col += vec3(0.8, 0.85, 1.0) * star * smoothstep(0.05, 0.3, h);

    // sun
    vec2 c = vec2((vUV.x - uSunX) * uAspect, h - 0.13);
    float r = length(c);
    float R = 0.24;
    vec3 sunCol = mix(vec3(1.0, 0.25, 0.55), vec3(1.0, 0.82, 0.30), smoothstep(-R, R, c.y));
    float stripes = 1.0;
    if (c.y < 0.04) {
        float band = fract((c.y - 0.04) * 26.0 + uTime * 0.25);
        float w = clamp(-(c.y - 0.04) / R, 0.0, 1.0) * 0.55;
        stripes = step(w, band);
    }
    float disk = smoothstep(R, R - 0.004, r) * stripes;
    col = mix(col, sunCol, disk * step(0.0, h));
    col += vec3(1.0, 0.3, 0.6) * exp(-max(r - R, 0.0) * 9.0) * 0.25 * step(0.0, h);

    // horizon glow line
    col += vec3(1.0, 0.25, 0.7) * exp(-abs(h) * 70.0) * 0.6;
    fragColor = vec4(col, 1.0);
}
"""
}
