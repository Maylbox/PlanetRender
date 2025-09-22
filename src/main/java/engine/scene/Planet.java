package engine.scene;

import engine.gl.Texture;

public class Planet {
    public final float cx, cy, cz;
    public final float baseRadius;
    public final float uniformScale;      // = max(SX,SY,SZ)
    public final float spinDegPerSec;
    public final int spinSignFree, spinSignOrbit;
    public final Mesh mesh;
    public final Texture albedo; // nullable

    public Planet(float cx,float cy,float cz,
                  float baseRadius,float uniformScale,
                  float spinDegPerSec,int spinFree,int spinOrbit,
                  Mesh mesh, Texture albedo) {

        this.cx=cx; this.cy=cy; this.cz=cz;
        this.baseRadius=baseRadius;
        this.uniformScale=uniformScale;
        this.spinDegPerSec=spinDegPerSec;
        this.spinSignFree=spinFree;
        this.spinSignOrbit=spinOrbit;
        this.mesh=mesh;
        this.albedo=albedo;
    }

    public float worldRadius(){ return baseRadius * uniformScale; }
}
