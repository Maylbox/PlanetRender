#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec3 aNormal;
layout (location=2) in vec2 aUV;

uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uModel;

out vec3 vNormal;
out vec3 vWorldPos;
out vec2 vUV;

void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorldPos = world.xyz;

    // normal transform (assuming uModel is rotation only; if scaled, use inverse-transpose)
    vNormal = mat3(uModel) * aNormal;

    vUV = aUV;
    gl_Position = uProj * uView * world;
}
