#version 330 core
in vec3 vNormal;
in vec3 vWorldPos;
in vec2 vUV;

uniform vec3 uCamPos;
uniform vec3 uLightDir;     // should be normalized
uniform vec3 uLightColor;
uniform float uLightIntensity;

uniform bool uUseTexture;
uniform sampler2D uAlbedo;

out vec4 FragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 L = normalize(-uLightDir); // treat uLightDir as direction towards the light
    float NdotL = max(dot(N, L), 0.0);

    vec3 baseColor = vec3(0.7, 0.75, 0.8);
    if (uUseTexture) {
        baseColor = texture(uAlbedo, vUV).rgb;
    }

    vec3 diffuse = baseColor * uLightColor * NdotL * uLightIntensity;

    // simple ambient
    vec3 ambient = baseColor * 0.2;

    FragColor = vec4(ambient + diffuse, 1.0);
}
