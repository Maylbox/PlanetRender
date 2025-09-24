package engine.gl;

import engine.config.PlanetConfig;
import engine.scene.Mesh;
import engine.scene.Planet;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class CloudRenderer {
    private final Shader shader;
    private final Mesh sphere;
    private final int MAX_LAYERS = 4;

    public CloudRenderer(Shader shader, Mesh sphere) {
        this.shader = shader;
        this.sphere = sphere;
    }

    public void draw(Planet p, float[] proj, float[] view, float[] modelBase,
                     float camX, float camY, float camZ,
                     PlanetConfig.Clouds cfgClouds, float timeSec,
                     float lx, float ly, float lz,
                     float lcr, float lcg, float lcb,
                     float lintensity) {
        if (cfgClouds == null || !cfgClouds.enabled) return;
        int layerCount = Math.min(cfgClouds.layers.length, MAX_LAYERS);
        if (layerCount <= 0) return;

        // Common uniforms
        shader.use();
        setMat4(shader.id(),"uProj", proj);
        setMat4(shader.id(),"uView", view);

        glUniform3f(glGetUniformLocation(shader.id(),"uCamPos"), camX, camY, camZ);
        glUniform3f(glGetUniformLocation(shader.id(),"uCenter"), p.cx, p.cy, p.cz);
        glUniform1f(glGetUniformLocation(shader.id(),"uPlanetRadius"), p.worldRadius());
        glUniform1f(glGetUniformLocation(shader.id(),"uTime"), timeSec);
        glUniform3f(glGetUniformLocation(shader.id(), "uLightDir"), lx, ly, lz);
        glUniform3f(glGetUniformLocation(shader.id(), "uLightColor"), lcr, lcg, lcb);
        glUniform1f(glGetUniformLocation(shader.id(), "uLightIntensity"), lintensity);

        // Fill arrays
        float[] scales   = new float[MAX_LAYERS];
        float[] opacity  = new float[MAX_LAYERS];
        float[] rotDegPS = new float[MAX_LAYERS];
        float[] scrollUV = new float[MAX_LAYERS*2];
        float[] color    = new float[MAX_LAYERS*3];
        float[] cover    = new float[MAX_LAYERS];
        float[] nscale   = new float[MAX_LAYERS];

        for (int i = 0; i < layerCount; i++) {
            var L = cfgClouds.layers[i];
            float shellScale = 1.0f + Math.max(0f, L.altitudePct);
            scales[i]   = shellScale;
            opacity[i]  = Math.max(0f, Math.min(1f, L.opacity));
            rotDegPS[i] = L.rotationDegPS;
            scrollUV[i*2+0] = L.scrollUV[0];
            scrollUV[i*2+1] = L.scrollUV[1];
            color[i*3+0] = L.color[0];
            color[i*3+1] = L.color[1];
            color[i*3+2] = L.color[2];
            cover[i]  = L.coverage;
            nscale[i] = L.noiseScale;

            // Optional: bind texture i to unit i (if you add texture support)
            // if (!L.texture.isBlank()) { /* load/bind Texture to unit i, set uHasTex[i]=1 */ }
        }

        // push arrays
        glUniform1i(glGetUniformLocation(shader.id(),"uLayerCount"), layerCount);
        glUniform1fv(glGetUniformLocation(shader.id(),"uLayerScale"), scales);
        glUniform1fv(glGetUniformLocation(shader.id(),"uLayerOpacity"), opacity);
        glUniform1fv(glGetUniformLocation(shader.id(),"uLayerRotDegPS"), rotDegPS);
        glUniform2fv(glGetUniformLocation(shader.id(),"uLayerScrollUV"), scrollUV);
        glUniform3fv(glGetUniformLocation(shader.id(),"uLayerColor"), color);
        glUniform1fv(glGetUniformLocation(shader.id(),"uLayerCoverage"), cover);
        glUniform1fv(glGetUniformLocation(shader.id(),"uLayerNoiseScale"), nscale);

        // Blending for straight alpha
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glEnable(GL_CULL_FACE);

        // Draw each layer as its own scaled shell (handles inside/outside)
        for (int i = 0; i < layerCount; i++) {
            float shellScale = scales[i];
            float[] model = mul(modelBase, matUniformScale(shellScale));

            // inside/outside
            float dx = camX - p.cx, dy = camY - p.cy, dz = camZ - p.cz;
            float camDist = (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            float shellR_W = p.worldRadius() * shellScale;
            boolean inside = camDist < shellR_W - 1e-4f;

            setMat4(shader.id(),"uModel", model);
            glCullFace(inside ? GL_FRONT : GL_BACK);
            sphere.draw();
        }

        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    // small helpers
    private static float[] matUniformScale(float s){ return new float[]{s,0,0,0, 0,s,0,0, 0,0,s,0, 0,0,0,1}; }
    private static float[] mul(float[] a,float[] b){ float[] r=new float[16];
        for (int c=0;c<4;c++) for(int r0=0;r0<4;r0++)
            r[c*4+r0]=a[0*4+r0]*b[c*4+0]+a[1*4+r0]*b[c*4+1]+a[2*4+r0]*b[c*4+2]+a[3*4+r0]*b[c*4+3];
        return r;
    }
    private static void setMat4(int p,String n,float[] m){ glUniformMatrix4fv(glGetUniformLocation(p,n), false, m); }
}
