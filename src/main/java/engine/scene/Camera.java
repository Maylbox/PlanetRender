package engine.scene;

import static java.lang.Math.*;

public class Camera {
    public float orbitUpY = 1f;
    public float fovDeg = 60f, near = 0.1f, far = 1000f;
    public float x = 0, y = 0, z = 3;
    public float yawDeg = -90f, pitchDeg = 0f;   // we won't change pitch via mouse anymore
    public float moveSpeed = 5f;
    public float mouseSensitivity = 0.1f;

    // follow/lock-on mode: if true, view always looks at (targetX,Y,Z)
    public boolean followTarget = false;
    public float targetX = 0f, targetY = 0f, targetZ = 0f;

    public void setTarget(float cx, float cy, float cz) {
        this.targetX = cx; this.targetY = cy; this.targetZ = cz;
    }

    // returns column-major 4x4 as float[16]
    public float[] viewMatrix() {
        if (followTarget) {
            float upY = (y >= targetY) ? 1f : -1f;
            return lookAtStable(x, y, z, targetX, targetY, targetZ, 0f, orbitUpY, 0f);
        }

        // compute forward from yaw/pitch (pitch is static unless you change it elsewhere)
        double cy = cos(toRadians(yawDeg));
        double sy = sin(toRadians(yawDeg));
        double cp = cos(toRadians(pitchDeg));
        double sp = sin(toRadians(pitchDeg));

        float fx = (float) (cy * cp);
        float fy = (float) (sp);
        float fz = (float) (sy * cp);

        // right = normalize(cross(f, up))
        float rX = (float) (+fz);
        float rY = 0f;
        float rZ = (float) (-fx);
        float rl = (float) sqrt(rX * rX + rY * rY + rZ * rZ);
        rX /= rl; rY /= rl; rZ /= rl;

        // up' = normalize(cross(r, f))
        float uX = rY * fz - rZ * fy;
        float uY = rZ * fx - rX * fz;
        float uZ = rX * fy - rY * fx;
        float ul = (float) sqrt(uX * uX + uY * uY + uZ * uZ);
        uX /= ul; uY /= ul; uZ /= ul;

        // view matrix (lookAt) columns
        float[] m = new float[16];
        m[0] = rX;  m[4] = uX;  m[8]  = -fx; m[12] = 0;
        m[1] = rY;  m[5] = uY;  m[9]  = -fy; m[13] = 0;
        m[2] = rZ;  m[6] = uZ;  m[10] = -fz; m[14] = 0;
        m[3] = 0;   m[7] = 0;   m[11] = 0;   m[15] = 1;

        // translate by -pos
        float tx = -(rX * x + rY * y + rZ * z);
        float ty = -(uX * x + uY * y + uZ * z);
        float tz = -(-fx * x + -fy * y + -fz * z);
        m[12] = tx; m[13] = ty; m[14] = tz;

        return m;
    }

    public float[] projMatrix(int width, int height) {
        float aspect = (float) width / max(1, height);
        float f = (float) (1.0 / tan(toRadians(fovDeg) * 0.5));
        float nf = 1f / (near - far);

        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) * nf;
        m[11] = -1f;
        m[14] = (2f * far * near) * nf;
        m[15] = 0f;

        return m;
    }

    private static float[] lookAtStable(float eyeX,float eyeY,float eyeZ,
                                        float cx,float cy,float cz,
                                        float upX,float upY,float upZ) {
        // f = (center - eye)
        float fx = cx-eyeX, fy = cy-eyeY, fz = cz-eyeZ;
        float fl = (float)Math.sqrt(fx*fx+fy*fy+fz*fz); if (fl < 1e-6f) fl = 1e-6f;
        fx/=fl; fy/=fl; fz/=fl;

        // World up
        float ux = upX, uy = upY, uz = upZ;

        // r = normalize(cross(f, up)); if too small we’re at/near the pole -> pick a fallback up
        float rX = fy*uz - fz*uy;
        float rY = fz*ux - fx*uz;
        float rZ = fx*uy - fy*ux;
        float rl = (float)Math.sqrt(rX*rX+rY*rY+rZ*rZ);

        if (rl < 1e-4f) {
            // Choose an alternate up that isn’t parallel to f
            // Prefer world Z as fallback; if that’s still too close, use world X
            float auxX = 0f, auxY = 0f, auxZ = 1f;
            rX = fy*auxZ - fz*auxY;
            rY = fz*auxX - fx*auxZ;
            rZ = fx*auxY - fy*auxX;
            rl = (float)Math.sqrt(rX*rX+rY*rY+rZ*rZ);
            if (rl < 1e-4f) { // extreme case: try X
                auxX = 1f; auxY = 0f; auxZ = 0f;
                rX = fy*auxZ - fz*auxY;
                rY = fz*auxX - fx*auxZ;
                rZ = fx*auxY - fy*auxX;
                rl = (float)Math.sqrt(rX*rX+rY*rY+rZ*rZ);
                if (rl < 1e-6f) rl = 1e-6f;
            }
        }
        rX/=rl; rY/=rl; rZ/=rl;

        // u = cross(r, f) (guaranteed orthonormal and consistent roll)
        float uX = rY*fz - rZ*fy;
        float uY = rZ*fx - rX*fz;
        float uZ = rX*fy - rY*fx;

        // column-major matrix with r,u,-f
        float[] m = new float[16];
        m[0]= rX; m[4]= uX; m[8] = -fx; m[12]=0;
        m[1]= rY; m[5]= uY; m[9] = -fy; m[13]=0;
        m[2]= rZ; m[6]= uZ; m[10]= -fz; m[14]=0;
        m[3]= 0 ; m[7]= 0 ; m[11]=  0 ; m[15]=1;

        // translate by -eye
        float tx = -(rX*eyeX + rY*eyeY + rZ*eyeZ);
        float ty = -(uX*eyeX + uY*eyeY + uZ*eyeZ);
        float tz = -(-fx*eyeX + -fy*eyeY + -fz*eyeZ);
        m[12]=tx; m[13]=ty; m[14]=tz;
        return m;
    }
}
