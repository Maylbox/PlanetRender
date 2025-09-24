#version 330 core
in vec3 vWorldPos;
out vec4 fragColor;

uniform vec3  uCamPos;
uniform vec3  uCenter;
uniform float uPlanetRadius;
uniform float uTime;

// Layer arrays
uniform int   uLayerCount;
uniform float uLayerScale[4];       // shell scale per layer (radius multiplier) -- still driven by CPU
uniform float uLayerOpacity[4];     // 0..1
uniform float uLayerRotDegPS[4];    // extra spin around Y (deg/sec)
uniform vec2  uLayerScrollUV[4];    // XY drift per second (used as 3D wind XZ below)
uniform vec3  uLayerColor[4];       // tint
uniform float uLayerCoverage[4];    // threshold
uniform float uLayerNoiseScale[4];  // noise scale
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform float uLightIntensity;

// --- 3D value noise + FBM (renamed to avoid GLSL noise* names) ---
float hash3D(vec3 p){ return fract(sin(dot(p, vec3(127.1,311.7,74.7))) * 43758.5453); }

float valueNoise3D(vec3 p){
    vec3 i = floor(p), f = fract(p);
    float n000 = hash3D(i + vec3(0,0,0));
    float n100 = hash3D(i + vec3(1,0,0));
    float n010 = hash3D(i + vec3(0,1,0));
    float n110 = hash3D(i + vec3(1,1,0));
    float n001 = hash3D(i + vec3(0,0,1));
    float n101 = hash3D(i + vec3(1,0,1));
    float n011 = hash3D(i + vec3(0,1,1));
    float n111 = hash3D(i + vec3(1,1,1));
    vec3 u = f*f*(3.0-2.0*f);
    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm3D(vec3 p){
    float v = 0.0, a = 0.5;
    for (int i=0; i<5; ++i){
        v += a * valueNoise3D(p);
        p *= 2.02; a *= 0.5;
    }
    return v;
}

vec3 computeCloudLight(vec3 n, vec3 L, vec3 lightColor, float lightIntensity, out float nightAlpha)
{
    // ndl in [0..1]
    float ndl = clamp(dot(n, L), 0.0, 1.0);

    // Stronger day-side curve (smaller exponent => brighter highlights)
    float dayCurve = pow(ndl, 0.45);

    // Lower ambient, subtle rim
    float ambient = 0.05;
    float rim     = 0.08 * pow(1.0 - ndl, 3.0);

    float shade = mix(ambient, 1.0, dayCurve) + rim;

    // Night side alpha reduction (0.12 at night → 1.0 in full sun)
    nightAlpha = mix(0.12, 1.0, ndl);

    return lightColor * (lightIntensity * shade);
}

void main(){
    vec3 color = vec3(0.0);
    float alpha = 0.0;

    vec3 cw = normalize(vWorldPos - uCenter);
    vec3 n  = cw;
    vec3  L = normalize(uLightDir);
    float nightAlpha;
    vec3  light = computeCloudLight(n, L, uLightColor, uLightIntensity, nightAlpha);

    for (int i = 0; i < uLayerCount; ++i){
        // animate: spin around Y + drift (wind) in XZ
        float rot = radians(uLayerRotDegPS[i]) * uTime;
        mat2 R = mat2(cos(rot), -sin(rot),
        sin(rot),  cos(rot));

        // rotate longitude (xz), keep latitude (y)
        vec2 xz = R * cw.xz;

        // drift as 3D wind: use scrollUV.x->X, scrollUV.y->Z; Y drift left 0
        vec3 p3 = vec3(xz.x, cw.y, xz.y);
        p3 += vec3(uLayerScrollUV[i].x, 0.0, uLayerScrollUV[i].y) * uTime;

        float scale = max(0.1, uLayerNoiseScale[i]);
        float cval = fbm3D(p3 * scale);

        // soft threshold and opacity
        float cover = clamp(uLayerCoverage[i], 0.0, 1.0);
        float m = smoothstep(cover - 0.08, cover + 0.08, cval);

        float a   = m * clamp(uLayerOpacity[i], 0.0, 1.0) * nightAlpha;
        vec3  col = uLayerColor[i] * light;

        // over-composite
        color = mix(color, col, a * (1.0 - alpha));
        alpha = alpha + a * (1.0 - alpha);
    }

    if (alpha < 0.003) discard;
    fragColor = vec4(color, alpha);   // straight alpha
}
