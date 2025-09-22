package engine.gl;

import engine.config.PlanetConfig;
import engine.scene.Camera;
import engine.scene.Mesh;
import engine.scene.Planet;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class Renderer {
    private final Shader planetShader;
    private final Shader atmoShader;
    private final GizmoRenderer gizmo;
    private final AtmosphereRenderer atmoRenderer;
    private final AtmosphereRenderer.Settings atmoSettings;
    private final float[] lightDir;
    private final float[] lightColor;
    private final float   lightIntensity;
    private final PlanetConfig.Lighting lightingCfg;

    public Renderer(Shader planetShader, Shader atmoShader, Shader gizmoShader,
                    Mesh sphere,
                    AtmosphereRenderer.Settings atmoSettings,
                    PlanetConfig.Lighting lightingCfg) {
        this.planetShader = planetShader;
        this.atmoShader   = atmoShader;
        this.gizmo        = new GizmoRenderer(gizmoShader);
        this.atmoRenderer = (atmoShader != null) ? new AtmosphereRenderer(atmoShader, sphere) : null;
        this.atmoSettings = atmoSettings;

        this.lightDir       = lightingCfg.direction.clone();
        this.lightColor     = lightingCfg.color.clone();
        this.lightIntensity = lightingCfg.intensity;
        this.lightingCfg  = lightingCfg;
    }


    public void drawPlanet(Planet p, float[] proj, float[] view,
                           float angleDeg, int width, int height,
                           Camera cam, float[] lightDir) {

        float[] model = mul(mul(matTranslate(p.cx,p.cy,p.cz), matRotateY(angleDeg)),
                matUniformScale(p.uniformScale));

        // Solid planet
        planetShader.use();
        setMat4(planetShader.id(),"uProj", proj);
        setMat4(planetShader.id(),"uView", view);
        setMat4(planetShader.id(),"uModel",model);
        glUniform3f(glGetUniformLocation(planetShader.id(),"uCamPos"), cam.x,cam.y,cam.z);
        glUniform3f(glGetUniformLocation(planetShader.id(),"uLightDir"),
                lightingCfg.direction[0], lightingCfg.direction[1], lightingCfg.direction[2]);
        glUniform3f(glGetUniformLocation(planetShader.id(),"uLightColor"),
                lightingCfg.color[0], lightingCfg.color[1], lightingCfg.color[2]);
        glUniform1f(glGetUniformLocation(planetShader.id(),"uLightIntensity"),
                lightingCfg.intensity);


        int locUseTex = glGetUniformLocation(planetShader.id(), "uUseTexture");
        int locSampler = glGetUniformLocation(planetShader.id(), "uAlbedo");
        if (p.albedo != null && locUseTex >= 0 && locSampler >= 0) {
            glUniform1i(locUseTex, 1);
            glUniform1i(locSampler, 0);   // sampler uses texture unit 0
            p.albedo.bind(0);             // glActiveTexture(GL_TEXTURE0); glBindTexture(...)
        } else if (locUseTex >= 0) {
            glUniform1i(locUseTex, 0);
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glFrontFace(GL_CCW);
        glCullFace(GL_BACK);
        p.mesh.draw();

        // Gizmo (optional)
        float lineLen = p.worldRadius() * 1.3f;
        gizmo.draw(proj, view, p.cx,p.cy,p.cz, lightDir[0],lightDir[1],lightDir[2], lineLen);

        // Atmosphere from config
        if (atmoRenderer != null && atmoSettings != null && atmoSettings.enabled) {
            atmoRenderer.draw(p, proj, view, model, cam.x, cam.y, cam.z,
                    lightingCfg.direction[0], lightingCfg.direction[1], lightingCfg.direction[2],
                    atmoSettings);
        }
    }


    public void delete(){ gizmo.delete(); }

    // helpers copied from your Main
    private static float[] matTranslate(float x,float y,float z){ return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, x,y,z,1}; }
    private static float[] matRotateY(float deg){ double r=Math.toRadians(deg); float c=(float)Math.cos(r), s=(float)Math.sin(r);
        return new float[]{c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1}; }
    private static float[] matUniformScale(float s){ return new float[]{s,0,0,0, 0,s,0,0, 0,0,s,0, 0,0,0,1}; }
    private static float[] mul(float[] a,float[] b){ float[] r=new float[16];
        for(int c=0;c<4;c++) for(int r0=0;r0<4;r0++)
            r[c*4+r0]=a[0*4+r0]*b[c*4+0]+a[1*4+r0]*b[c*4+1]+a[2*4+r0]*b[c*4+2]+a[3*4+r0]*b[c*4+3];
        return r;
    }
    private static void setMat4(int p,String n,float[] m){ glUniformMatrix4fv(glGetUniformLocation(p,n), false, m); }
}
