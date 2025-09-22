#version 330 core
in vec3 vNormal;
in vec3 vWorldPos;
in vec2 vUV;

uniform vec3 uCamPos;
uniform vec3 uLightDir;     // **planet → sun**, already normalized or normalize here
uniform vec3 uLightColor;
uniform float uLightIntensity;

uniform bool uUseTexture;
uniform sampler2D uAlbedo;

out vec4 FragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 L = normalize(uLightDir);        // <-- no minus
    float NdotL = max(dot(N, L), 0.0);

    vec3 baseColor = uUseTexture ? texture(uAlbedo, vUV).rgb
                                 : vec3(0.7, 0.75, 0.8);

    vec3 diffuse = baseColor * uLightColor * (uLightIntensity * NdotL);
    vec3 ambient = baseColor * 0.2;

    FragColor = vec4(ambient + diffuse, 1.0);
}
