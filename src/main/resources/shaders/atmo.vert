#version 330 core
layout(location=0) in vec3 aPos;
uniform mat4 uModel, uView, uProj;

out vec3 vWorldPos;
out vec3 vWorldNormal;

void main(){
    vec4 w = uModel * vec4(aPos,1.0);
    vWorldPos = w.xyz;

    // assumes near-uniform scale on the shell; good enough for the atmo rim
    vWorldNormal = normalize(mat3(uModel) * aPos);

    gl_Position = uProj * uView * w;
}
