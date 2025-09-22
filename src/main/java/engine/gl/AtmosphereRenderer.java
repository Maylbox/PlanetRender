package engine.gl;

import engine.scene.Mesh;
import engine.scene.Planet;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class AtmosphereRenderer {
    private final Shader shader;
    private final Mesh sphere;

    public static class Settings {
        public boolean enabled;
        public float thicknessPct = 0.02f;
        public float intensity = 1.0f;
        public float[] color = {0.45f,0.7f,1.0f};
    }

    public AtmosphereRenderer(Shader shader, Mesh sphere) {
        this.shader = shader;
        this.sphere = sphere;
    }

    public void draw(Planet p, float[] proj, float[] view, float[] modelBase,
                     float camX, float camY, float camZ,
                     float lightX, float lightY, float lightZ,
                     Settings s) {
        if (!s.enabled) return;

        // --- build shell transform ---
        float shellScale = 1.0f + Math.max(0f, s.thicknessPct);
        float[] modelAtmo = mul(modelBase, matUniformScale(shellScale));

        // --- set shader + matrices ---
        shader.use();
        setMat4(shader.id(), "uProj",  proj);
        setMat4(shader.id(), "uView",  view);
        setMat4(shader.id(), "uModel", modelAtmo);

        // uniforms
        glUniform3f(glGetUniformLocation(shader.id(),"uCamPos"),   camX, camY, camZ);
        glUniform3f(glGetUniformLocation(shader.id(),"uLightDir"), lightX, lightY, lightZ);
        glUniform3f(glGetUniformLocation(shader.id(),"uAtmoColor"),
                s.color[0], s.color[1], s.color[2]);
        glUniform1f(glGetUniformLocation(shader.id(),"uAtmoIntensity"), s.intensity);

        float planetR_W = p.worldRadius();
        float shellR_W  = planetR_W * shellScale;
        glUniform1f(glGetUniformLocation(shader.id(),"uPlanetRadius"), planetR_W);
        glUniform1f(glGetUniformLocation(shader.id(),"uShellRadius"),  shellR_W);
        glUniform3f(glGetUniformLocation(shader.id(),"uCenter"), p.cx, p.cy, p.cz);

        // --- inside/outside test in world space ---
        float dx = camX - p.cx, dy = camY - p.cy, dz = camZ - p.cz;
        float camDist = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        boolean inside = camDist < shellR_W - 1e-4f;

        // --- blend & depth state for the atmo shell ---
        glEnable(GL_BLEND);
        // Use STRAIGHT alpha blending to match shader output:
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glDepthMask(false);
        glEnable(GL_CULL_FACE);
        glCullFace(inside ? GL_FRONT : GL_BACK);

        // Optional: be a bit more lenient around the limb
        glDepthFunc(GL_LEQUAL);

        // draw
        sphere.draw();

        // restore state
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }


    // ---- tiny matrix helpers (copy from your Main) ----
    private static float[] matUniformScale(float s){ return new float[]{s,0,0,0, 0,s,0,0, 0,0,s,0, 0,0,0,1}; }
    private static float[] mul(float[] a,float[] b){ float[] r=new float[16];
        for (int c=0;c<4;c++) for (int r0=0;r0<4;r0++)
            r[c*4+r0]=a[0*4+r0]*b[c*4+0]+a[1*4+r0]*b[c*4+1]+a[2*4+r0]*b[c*4+2]+a[3*4+r0]*b[c*4+3];
        return r;
    }
    private static void setMat4(int prog,String name,float[] m){
        glUniformMatrix4fv(glGetUniformLocation(prog,name), false, m);
    }
}
