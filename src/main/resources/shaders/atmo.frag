#version 330 core
in vec3 vWorldPos;
out vec4 fragColor;

// World-space inputs
uniform vec3  uCamPos;
uniform vec3  uCenter;
uniform vec3  uLightDir;       // direction TOWARD the light (normalize if needed)

// Radii
uniform float uPlanetRadius;
uniform float uShellRadius;

// Config knobs (from JSON)
uniform vec3  uAtmoColor      = vec3(0.45, 0.7, 1.0);
uniform float uAtmoIntensity  = 0.4;   // overall opacity
uniform float uFalloffPower   = 3.0;   // density falloff exponent

// ---- helpers ----
bool raySphere(vec3 ro, vec3 rd, float R, out float t0, out float t1) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - R*R;
    float h = b*b - c;
    if (h < 0.0) return false;
    h = sqrt(h);
    t0 = -b - h;
    t1 = -b + h;
    return true;
}

void main() {
    // Planet-centered frame
    vec3 ro = uCamPos   - uCenter;        // ray origin (planet-centered)
    vec3 pw = vWorldPos - uCenter;        // this shell point (planet-centered)
    vec3 rd = normalize(pw - ro);         // view ray dir

    // Intersect shell; trim against planet so we only shade the visible shell arc
    float tEnter, tExit;
    if (!raySphere(ro, rd, uShellRadius, tEnter, tExit)) discard;
    float t0 = max(tEnter, 0.0);
    float t1 = tExit;

    float p0, p1;
    if (raySphere(ro, rd, uPlanetRadius, p0, p1) && p0 > 0.0) {
        t1 = min(t1, p0);
    }
    if (t1 <= t0) discard;

    // Midpoint in the shell, used for a cheap “where am I in the atmosphere” probe
    float tMid   = 0.5 * (t0 + t1);
    vec3  posMid = ro + rd * tMid;
    float rMid   = length(posMid);

    // Density 0 at shell top → 1 at surface, then shaped by power
    float shellH = max(uShellRadius - uPlanetRadius, 1e-5);
    float v      = 1.0 - clamp((rMid - uPlanetRadius) / shellH, 0.0, 1.0);
    float dens   = pow(v, max(uFalloffPower, 1e-3));

    // Dayside factor (no color on the nightside)
    vec3  nMid   = (rMid > 0.0) ? posMid / rMid : vec3(0.0);
    float sun    = clamp(dot(nMid, normalize(uLightDir)), 0.0, 1.0);

    // Opacity: density * dayside * intensity (straight alpha)
    float alpha = clamp(dens * sun * uAtmoIntensity, 0.0, 1.0);
    if (alpha < 0.003) discard;

    fragColor = vec4(uAtmoColor, alpha);
}
