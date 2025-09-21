// atmo.frag
#version 330 core
in vec3 vWorldPos;
in vec3 vNormal;

uniform vec3  uCamPos;
uniform vec3  uLightDir;
uniform vec3  uAtmoColor;
uniform float uAtmoIntensity;
uniform float uAtmoPower;
uniform int   uPass;           // 0 = rim glow (backfaces, additive), 1 = front haze (frontfaces, alpha)

out vec4 FragColor;

void main() {
    vec3 V = normalize(uCamPos - vWorldPos);
    vec3 N = normalize(vNormal);

    float sun = max(dot(N, normalize(-uLightDir)), 0.0);

    // Rim term: strongest at grazing angles
    float horizon = pow(1.0 - max(dot(N, V), 0.0), uAtmoPower);

    vec3 glow = uAtmoColor * (0.25 + 0.75 * sun) * uAtmoIntensity;

    float a;
    if (uPass == 0) {
        // Backface rim: emissive-ish, used with additive blending
        a = horizon;                    // drives intensity in SRC_ALPHA, ONE
    } else {
        // Front disk haze: subtle, used with SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        // baseline + rim boost so it shows across the disk
        float base = 0.06;              // overall disk veil
        float rimBoost = 0.35 * horizon;
        a = clamp(base + rimBoost, 0.0, 1.0);
        // Optionally tone down color over disk a bit
        glow *= 0.6;
    }

    FragColor = vec4(glow, a);
}
