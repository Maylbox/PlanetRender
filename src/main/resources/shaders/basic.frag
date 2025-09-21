#version 330 core
in vec3 vNormal;
in vec3 vWorldPos;

out vec4 FragColor;

uniform vec3 uCamPos;
uniform vec3 uLightDir;   // directional light (world)
uniform vec3 uLightColor;
uniform float uLightIntensity;

void main() {
    vec3 N = normalize(vNormal);
    vec3 L = normalize(-uLightDir);
    float ndotl = max(dot(N, L), 0.0);

    // simple Blinn-Phong-ish
    vec3 base = vec3(0.2, 0.6, 1.0); // placeholder color; will swap to texture later
    vec3 diffuse = base * (uLightColor * uLightIntensity * ndotl);
    vec3 ambient = base * 0.15;

    FragColor = vec4(ambient + diffuse, 1.0);
}
