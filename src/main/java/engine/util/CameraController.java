package engine.util;

import engine.gl.GLWindow;
import engine.scene.Camera;

import static org.lwjgl.glfw.GLFW.*;

public class CameraController {
    public float orbitAzimuthDeg=-90f, orbitElevationDeg=0f, orbitRadius=3f;
    private boolean prevO=false, prevT=false;

    private static final float ORBIT_YAW_SPEED_DEG=90f;
    private static final float ORBIT_ELEV_SPEED_DEG=60f;
    private static final float ORBIT_ZOOM_SPEED=3f;
    private static final float MOUSE_ORBIT_SENS=0.12f;

    public void update(GLWindow w, Camera c, float dt,
                       float minDist, float maxDist,
                       float cx, float cy, float cz) {

        boolean oNow=w.keys[GLFW_KEY_O], tNow=w.keys[GLFW_KEY_T];
        if (oNow && !prevO){
            c.followTarget=!c.followTarget;
            c.setTarget(cx,cy,cz);
            if (c.followTarget){
                // choose a stable up sign to avoid instant roll
                c.orbitUpY = (c.y >= cy) ? 1f : -1f;

                float dx=c.x-cx, dy=c.y-cy, dz=c.z-cz;
                float dist=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if (dist<1e-6f) dist=minDist;
                orbitRadius=dist;

                float distXZ=(float)Math.sqrt(dx*dx+dz*dz);
                float azDeg=(float)Math.toDegrees(Math.atan2(dz,dx));
                float elDeg=(float)Math.toDegrees(Math.atan2(dy, Math.max(1e-6f, distXZ)));
                orbitAzimuthDeg = wrap180(azDeg);
                orbitElevationDeg = clamp(elDeg,-85f,85f);
            } else {
                aimFreeCameraAt(c,cx,cy,cz);
            }

            w.deltaX=w.deltaY=0;
        }
        prevO=oNow; prevT=tNow;

        if (c.followTarget){
            orbitAzimuthDeg -= (float)(w.deltaX * MOUSE_ORBIT_SENS);
            w.deltaX=w.deltaY=0;
            if (w.keys[GLFW_KEY_A]) orbitAzimuthDeg -= ORBIT_YAW_SPEED_DEG*dt;
            if (w.keys[GLFW_KEY_D]) orbitAzimuthDeg += ORBIT_YAW_SPEED_DEG*dt;
            if (w.keys[GLFW_KEY_SPACE]) orbitElevationDeg += ORBIT_ELEV_SPEED_DEG*dt;
            if (w.keys[GLFW_KEY_LEFT_CONTROL]||w.keys[GLFW_KEY_C]) orbitElevationDeg -= ORBIT_ELEV_SPEED_DEG*dt;
            orbitElevationDeg = clamp(orbitElevationDeg,-89f,89f);
            if (w.keys[GLFW_KEY_W]) orbitRadius -= ORBIT_ZOOM_SPEED*dt;
            if (w.keys[GLFW_KEY_S]) orbitRadius += ORBIT_ZOOM_SPEED*dt;
            orbitRadius = clamp(orbitRadius, minDist, maxDist);

            double ay=Math.toRadians(orbitAzimuthDeg);
            double el=Math.toRadians(orbitElevationDeg);
            float cxr=(float)Math.cos(ay), sxr=(float)Math.sin(ay);
            float cp=(float)Math.cos(el), sp=(float)Math.sin(el);
            c.x = cx + orbitRadius*cxr*cp;
            c.y = cy + orbitRadius*sp;
            c.z = cz + orbitRadius*sxr*cp;
            c.setTarget(cx,cy,cz);
        } else {
            c.yawDeg -= (float)(w.deltaX*c.mouseSensitivity);
            w.deltaX=w.deltaY=0;
            float speed=c.moveSpeed*dt;
            boolean forward=w.keys[GLFW_KEY_W], back=w.keys[GLFW_KEY_S];
            boolean left=w.keys[GLFW_KEY_A], right=w.keys[GLFW_KEY_D];
            boolean up=w.keys[GLFW_KEY_SPACE];
            boolean down=w.keys[GLFW_KEY_LEFT_CONTROL]||w.keys[GLFW_KEY_C];

            double yaw=Math.toRadians(c.yawDeg);
            float fx=(float)Math.cos(yaw), fz=(float)Math.sin(yaw);
            float rx=+fz, rz=-fx;

            if (forward){ c.x+=fx*speed; c.z+=fz*speed; }
            if (back)   { c.x-=fx*speed; c.z-=fz*speed; }
            if (right)  { c.x+=rx*speed; c.z+=rz*speed; }
            if (left)   { c.x-=rx*speed; c.z-=rz*speed; }
            if (up)     { c.y+=speed; }
            if (down)   { c.y-=speed; }
        }
    }

    private static float wrap180(float a){ if (a<-180f) a+=360f; if (a>180f) a-=360f; return a; }
    private static float clamp(float v,float lo,float hi){ return v<lo?lo:(v>hi?hi:v); }
    private static void aimFreeCameraAt(Camera c,float tx,float ty,float tz){ /* same as yours */ }
    private static float computeFreeUpY(Camera c){ /* same as yours */ return 1f; }
}
