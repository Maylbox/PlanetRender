#version 330 core
in vec3 vWorldPos;                // from vert: shell position in world
uniform vec3 uCamPos;
uniform vec3 uLightDir;           // normalized, pointing *from* surface *toward* the light
uniform mat4 uModel;              // shell model (for radius if needed)

uniform vec3 uAtmoColor = vec3(0.5,0.7,1.0);  // tint for Rayleigh-ish
uniform float uPlanetRadius = 1.0;            // base planet radius (model space)
uniform float uShellRadius  = 1.05;           // planetRadius * (1 + thicknessPct)
uniform float uRayleighSigma = 0.5;           // tweak
uniform float uMieSigma      = 1.2;           // tweak (scattering)
uniform float uAbsorption    = 0.1;           // simple absorption term
uniform float uScaleHeightR  = 0.25;          // Rayleigh scale height (in planet radii)
uniform float uScaleHeightM  = 0.10;          // Mie scale height

// Cornette–Shanks phase (better backscatter than HG, converges to Rayleigh)
float phaseCornetteShanks(float mu, float g) {
    float g2 = g*g;
    return (3.0*(1.0 - g2)*(1.0 + mu*mu)) / (2.0*(2.0 + g2)*pow(1.0 + g2 - 2.0*g*mu, 1.5));
}
// Rayleigh phase
float phaseRayleigh(float mu) {
    return 3.0/(16.0*3.14159265) * (1.0 + mu*mu);
}

// Signed distance from a ray to a sphere, return entry/exit t; bool hit
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

out vec4 fragColor;

void main() {
    // World-space ray from camera through this pixel
    vec3 ro = uCamPos;
    vec3 rd = normalize(vWorldPos - uCamPos);

    // Intersect with outer shell & inner planet
    float tEnter, tExit;
    if (!raySphere(ro, rd, uShellRadius, tEnter, tExit)) discard; // outside sky dome entirely

    // Start marching where the ray enters the shell (if camera already *inside* shell, clamp)
    float t0 = max(tEnter, 0.0);
    float t1 = tExit;

    // If the planet blocks the view, clamp the exit to planet entry
    float p0, p1;
    if (raySphere(ro, rd, uPlanetRadius, p0, p1)) {
        // If the planet is in front along the ray, stop at its front intersection
        if (p0 > 0.0) t1 = min(t1, p0);
    }

    // Short ray-march for optical depth & in-scatter
    const int STEPS = 12; // small but enough for smoothness
    float dt = (t1 - t0) / float(STEPS);
    if (dt <= 0.0) { discard; } // nothing visible (behind the planet)

    vec3 L = normalize(uLightDir);
    float g = 0.82;  // Mie anisotropy (0.75..0.9 typical)
    float muView = dot(rd, L);

    float pR = phaseRayleigh(muView);           // Rayleigh phase
    float pM = phaseCornetteShanks(muView, g);  // Mie phase

    vec3 inscatter = vec3(0.0);
    float opticalDepth = 0.0;

    for (int i = 0; i < STEPS; ++i) {
        float t = t0 + (float(i) + 0.5) * dt;
        vec3 pos = ro + rd * t;
        float h = length(pos) - uPlanetRadius;  // altitude above ground (planet radii units)

        // Exponential densities vs altitude
        float rhoR = exp(-h / uScaleHeightR);
        float rhoM = exp(-h / uScaleHeightM);

        // Local extinction & scatter
        float sigma_t = uRayleighSigma*rhoR + (uMieSigma + uAbsorption)*rhoM;
        float sigma_s = uRayleighSigma*rhoR + uMieSigma*rhoM;

        opticalDepth += sigma_t * dt;

        // Simple single-scatter source term from sun (Beer through a straight segment to sun)
        // For a big visual improvement, approximate sun transmittance along light dir with a few steps:
        float sunTrans = exp(-sigma_t * 0.8); // cheap, tweak 0.5..1.5 or do a tiny 3-step march toward the sun

        // Spectral-ish tint: push Rayleigh bluish, Mie towards uAtmoColor
        vec3 rayleighCol = vec3(0.45, 0.6, 1.0) * pR;
        vec3 mieCol      = uAtmoColor * pM;

        inscatter += sunTrans * (rayleighCol + mieCol) * sigma_s * dt;
    }

    // Beer–Lambert for view transmittance
    float T = exp(-opticalDepth);

    // Energy-consistent premultiplied output
    vec3 rgb = inscatter;           // already integrated (units are arbitrary here)
    float alpha = 1.0 - T;          // how much of the background the atmosphere replaces
    fragColor = vec4(rgb * alpha, alpha);
}
