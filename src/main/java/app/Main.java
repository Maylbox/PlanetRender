package app;

import engine.gl.GLWindow;
import engine.gl.Shader;
import engine.scene.Camera;
import engine.scene.Mesh;
import engine.util.Resources;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class Main {

    // Follow/orbit state
    private boolean prevO = false;   // edge detection for 'O'
    private boolean prevT = false;   // edge detection for 'T'
    private float orbitAzimuthDeg = -90f;    // yaw around planet
    private float orbitElevationDeg = 0f;    // pitch around planet
    private float orbitRadius = 3f;

    // Orbit speeds
    private static final float ORBIT_YAW_SPEED_DEG = 90f;     // A/D deg per second
    private static final float ORBIT_ELEV_SPEED_DEG = 60f;    // Space/Ctrl deg per second
    private static final float ORBIT_ZOOM_SPEED = 3f;         // W/S units per second
    private static final float MOUSE_ORBIT_SENS = 0.12f;      // mouse X deg per pixel

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        Camera cam = new Camera();
        // ---- Load JSON into PlanetConfig POJO ----
        engine.config.PlanetConfig cfg;
        try {
            String json = Resources.text("data/planet.json");
            cfg = new com.google.gson.Gson().fromJson(json, engine.config.PlanetConfig.class);
            if (cfg == null) throw new RuntimeException("Empty planet config");
            cfg.applyDefaultsIfNeeded();
        } catch (Exception e) {
            System.err.println("planet.json not found/invalid; using defaults. " + e);
            cfg = new engine.config.PlanetConfig();
            cfg.center = new float[]{0f,0f,0f};
            cfg.baseRadius = 1f;
            cfg.size = new float[]{1f,1f,1f};
            cfg.minMarginPct = 0.15f;
            cfg.maxDistanceMult = 12f;
            cfg.spinDegPerSec = 45f;
            cfg.spinSignFree = -1;
            cfg.spinSignOrbit = +1;
        }

        // Unpack config
        final float PCX = cfg.center[0], PCY = cfg.center[1], PCZ = cfg.center[2];
        final float SX = cfg.size[0], SY = cfg.size[1], SZ = cfg.size[2];

        // Derived constraints
        float radius = effectivePlanetRadius(cfg.baseRadius, SX, SY, SZ);
        float minDist = radius + marginFromPct(radius, cfg.minMarginPct);
        float maxDist = Math.max(minDist * 1.1f, radius * cfg.maxDistanceMult);

        // Aim free camera at configured center
        aimFreeCameraAt(cam, PCX, PCY, PCZ);

        long last = System.nanoTime();
        float angle = 0f;

        GLWindow win = new GLWindow(1280, 720, "PlanetRender - Bootstrap");
        Mesh sphere = Mesh.uvSphere(64, 128, cfg.baseRadius /* or cfg.baseRadius */);


        String vsrc = Resources.text("shaders/basic.vert");
        String fsrc = Resources.text("shaders/basic.frag");
        Shader shader = new Shader(vsrc, fsrc);



        while (win.isOpen()) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            // Pass center from config into input handler
            handleInput(win, cam, dt, minDist, maxDist, PCX, PCY, PCZ);

            // Enforce distances using configured center
            enforceDistanceFromSphere(cam, PCX, PCY, PCZ, minDist, maxDist);

            // Spin sign per mode from config
            int spinSign = cam.followTarget ? cfg.spinSignOrbit : cfg.spinSignFree;
            angle += cfg.spinDegPerSec * dt * spinSign;

            glViewport(0, 0, win.width(), win.height());
            glClearColor(0.06f, 0.07f, 0.09f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.use();

            // matrices
            float[] proj = cam.projMatrix(win.width(), win.height());
            float[] view = cam.viewMatrix();
            float[] model = matRotateY(angle);

            setMat4(shader.id(), "uProj", proj);
            setMat4(shader.id(), "uView", view);
            setMat4(shader.id(), "uModel", model);

            // lighting
            setVec3(shader.id(), "uCamPos", cam.x, cam.y, cam.z);
            setVec3(shader.id(), "uLightDir", 0.3f, 0.6f, -0.7f);
            setVec3(shader.id(), "uLightColor", 1f, 1f, 1f);
            glUniform1f(glGetUniformLocation(shader.id(), "uLightIntensity"), 1.0f);

            sphere.draw();

            win.swap();
            win.poll();
        }

        sphere.delete();
        shader.delete();
        win.destroy();
    }

    private void handleInput(GLWindow w, Camera c, float dt, float minDist, float maxDist,
                             float PCX, float PCY, float PCZ) {
        // --- toggle follow with 'O' ---
        boolean oNow = w.keys[GLFW_KEY_O];
        boolean tNow = w.keys[GLFW_KEY_T];

        // Debug logs
        if (oNow && !prevO) logCam("O pressed (toggle follow)", c);
        if (tNow && !prevT) logCam("T pressed (telemetry)", c);

        // Toggle follow on O press (edge)
        if (oNow && !prevO) {
            c.followTarget = !c.followTarget;
            c.setTarget(PCX, PCY, PCZ);

            if (c.followTarget) {
                // Latch orbit "up" based on current free-cam up (no flip on toggle)
                float freeUpY = computeFreeUpY(c);  // + if camera up ~ +Y, - if ~ -Y
                c.orbitUpY = (freeUpY >= 0f) ? 1f : -1f;

                // Enter orbit: derive spherical from current position (no position change)
                float dx = c.x - PCX, dy = c.y - PCY, dz = c.z - PCZ;
                float dist = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist < 1e-6f) dist = minDist;

                orbitRadius = dist;

                // Stable spherical: azimuth from +X around Y, elevation from XZ-plane
                float distXZ = (float)Math.sqrt(dx*dx + dz*dz);
                float azDeg  = (float)Math.toDegrees(Math.atan2(dz, dx));
                float elDeg  = (float)Math.toDegrees(Math.atan2(dy, Math.max(1e-6f, distXZ)));

                // Normalize / clamp
                if (azDeg < -180f) azDeg += 360f;
                if (azDeg >  180f) azDeg -= 360f;
                elDeg = clamp(elDeg, -85f, 85f);

                orbitAzimuthDeg   = azDeg;
                orbitElevationDeg = elDeg;
            } else {
                // Exit orbit: keep position, make free camera face planet
                aimFreeCameraAt(c, PCX, PCY, PCZ);
            }

            // prevent snap from stale deltas
            w.deltaX = w.deltaY = 0;
        }

        prevO = oNow;
        prevT = tNow;

        if (c.followTarget) {
            // ----- FOLLOW/ORBIT MODE -----
            // Mouse X rotates azimuth (no pitch from mouse)
            orbitAzimuthDeg -= (float)(w.deltaX * MOUSE_ORBIT_SENS);
            w.deltaX = w.deltaY = 0;

            // Keyboard orbit controls
            boolean left  = w.keys[GLFW_KEY_A];
            boolean right = w.keys[GLFW_KEY_D];
            boolean up    = w.keys[GLFW_KEY_SPACE];
            boolean down  = w.keys[GLFW_KEY_LEFT_CONTROL] || w.keys[GLFW_KEY_C];
            boolean zoomIn  = w.keys[GLFW_KEY_W];
            boolean zoomOut = w.keys[GLFW_KEY_S];

            if (left)  orbitAzimuthDeg -= ORBIT_YAW_SPEED_DEG * dt;
            if (right) orbitAzimuthDeg += ORBIT_YAW_SPEED_DEG * dt;

            if (up)    orbitElevationDeg += ORBIT_ELEV_SPEED_DEG * dt;
            if (down)  orbitElevationDeg -= ORBIT_ELEV_SPEED_DEG * dt;
            orbitElevationDeg = clamp(orbitElevationDeg, -89f, 89f);

            if (zoomIn)  orbitRadius -= ORBIT_ZOOM_SPEED * dt;
            if (zoomOut) orbitRadius += ORBIT_ZOOM_SPEED * dt;
            orbitRadius = clamp(orbitRadius, minDist, maxDist);

            // Recompute camera position from spherical
            double ay = Math.toRadians(orbitAzimuthDeg);
            double el = Math.toRadians(orbitElevationDeg);
            float cx = (float)Math.cos(ay), sx = (float)Math.sin(ay);
            float cp = (float)Math.cos(el), sp = (float)Math.sin(el);

            c.x = PCX + orbitRadius * cx * cp;
            c.y = PCY + orbitRadius * sp;
            c.z = PCZ + orbitRadius * sx * cp;

            // Keep looking at target in viewMatrix()
            c.setTarget(PCX, PCY, PCZ);

        } else {
            // ----- FREE-FLY MODE -----
            // mouse yaw only (invert if you like)
            c.yawDeg -= (float)(w.deltaX * c.mouseSensitivity);
            w.deltaX = w.deltaY = 0;

            float speed = c.moveSpeed * dt;
            boolean forward = w.keys[GLFW_KEY_W];
            boolean back    = w.keys[GLFW_KEY_S];
            boolean left    = w.keys[GLFW_KEY_A];
            boolean right   = w.keys[GLFW_KEY_D];
            boolean up      = w.keys[GLFW_KEY_SPACE];
            boolean down    = w.keys[GLFW_KEY_LEFT_CONTROL] || w.keys[GLFW_KEY_C];

            // derive forward/right from yaw (ignore pitch for strafing)
            double yaw = Math.toRadians(c.yawDeg);
            float fx = (float)Math.cos(yaw);
            float fz = (float)Math.sin(yaw);
            float rx = +fz, rz = -fx;

            if (forward) { c.x += fx*speed; c.z += fz*speed; }
            if (back)    { c.x -= fx*speed; c.z -= fz*speed; }
            if (right)   { c.x += rx*speed; c.z += rz*speed; }
            if (left)    { c.x -= rx*speed; c.z -= rz*speed; }
            if (up)      { c.y += speed; }
            if (down)    { c.y -= speed; }
        }
    }

    private static float computeFreeUpY(Camera c) {
        double cy = Math.cos(Math.toRadians(c.yawDeg));
        double sy = Math.sin(Math.toRadians(c.yawDeg));
        double cp = Math.cos(Math.toRadians(c.pitchDeg));
        double sp = Math.sin(Math.toRadians(c.pitchDeg));

        float fx = (float)(cy * cp);
        float fy = (float)(sp);
        float fz = (float)(sy * cp);

        // right = normalize(cross(f, worldUp))
        float rX = +fz, rY = 0f, rZ = -fx;
        float rl = (float)Math.sqrt(rX*rX + rZ*rZ);
        if (rl < 1e-6f) rl = 1e-6f;
        rX /= rl; rZ /= rl;

        // up' = normalize(cross(r, f)) -> we only need Y
        float uY = rZ * fx - rX * fz;
        return uY;
    }

    private static void aimFreeCameraAt(Camera c, float tx, float ty, float tz) {
        float vx = tx - c.x, vy = ty - c.y, vz = tz - c.z;
        float len = (float)Math.sqrt(vx*vx + vy*vy + vz*vz);
        if (len < 1e-6f) { c.pitchDeg = 0f; return; }
        vx /= len; vy /= len; vz /= len;
        c.yawDeg   = (float)Math.toDegrees(Math.atan2(vz, vx));
        c.pitchDeg = (float)Math.toDegrees(Math.asin(vy));
        c.pitchDeg = clamp(c.pitchDeg, -89f, 89f);
    }

    private static float clamp(float v, float lo, float hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }

    // Enforce camera distance between [minDist, maxDist] from a sphere center
    private static void enforceDistanceFromSphere(Camera c,
                                                  float cx, float cy, float cz,
                                                  float minDist, float maxDist) {
        float dx = c.x - cx, dy = c.y - cy, dz = c.z - cz;
        float dist = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (dist <= 1e-6f) {
            c.x = cx; c.y = cy; c.z = cz + minDist;
            return;
        }

        float dirX = dx / dist, dirY = dy / dist, dirZ = dz / dist;

        if (dist < minDist) {
            c.x = cx + dirX * minDist;
            c.y = cy + dirY * minDist;
            c.z = cz + dirZ * minDist;
        } else if (dist > maxDist) {
            c.x = cx + dirX * maxDist;
            c.y = cy + dirY * maxDist;
            c.z = cz + dirZ * maxDist;
        }
    }

    // Use the maximum axis scale to keep a conservative "effective radius"
    private static float effectivePlanetRadius(float baseRadius, float sx, float sy, float sz) {
        float s = Math.max(sx, Math.max(sy, sz));
        return baseRadius * s;
    }

    private static float marginFromPct(float radius, float pct) {
        return radius * pct;
    }

    // --- tiny math/uniform helpers (column-major uploads) ---
    private static float[] matRotateY(float deg) {
        double r = Math.toRadians(deg);
        float c = (float)Math.cos(r), s = (float)Math.sin(r);
        return new float[]{
                c,0,-s,0,
                0,1, 0,0,
                s,0, c,0,
                0,0, 0,1
        };
    }

    private static void setMat4(int prog, String name, float[] m) {
        int loc = glGetUniformLocation(prog, name);
        glUniformMatrix4fv(loc, false, m); // already column-major
    }

    private static void setVec3(int prog, String name, float x, float y, float z) {
        glUniform3f(glGetUniformLocation(prog, name), x, y, z);
    }

    //debugger
    private void logCam(String source, Camera c) {
        System.out.printf(
                "[%s] follow=%s  pos=(%.3f, %.3f, %.3f)  yaw=%.2f  pitch=%.2f%n",
                source, c.followTarget, c.x, c.y, c.z, c.yawDeg, c.pitchDeg
        );
    }
}
