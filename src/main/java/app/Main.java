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
    private boolean prevO = false, prevT = false;
    private float orbitAzimuthDeg = -90f, orbitElevationDeg = 0f, orbitRadius = 3f;

    private static final float ORBIT_YAW_SPEED_DEG = 90f;
    private static final float ORBIT_ELEV_SPEED_DEG = 60f;
    private static final float ORBIT_ZOOM_SPEED   = 3f;
    private static final float MOUSE_ORBIT_SENS   = 0.12f;

    public static void main(String[] args) { new Main().run(); }

    private void run() {
        Camera cam = new Camera();

        // ---- Load config ----
        engine.config.PlanetConfig cfg;
        try {
            String json = Resources.text("data/planet.json");
            cfg = new com.google.gson.Gson().fromJson(json, engine.config.PlanetConfig.class);
            if (cfg == null) throw new RuntimeException("Empty planet config");
            cfg.applyDefaultsIfNeeded();
        } catch (Exception e) {
            System.err.println("planet.json not found/invalid; using defaults. " + e);
            cfg = new engine.config.PlanetConfig();
            cfg.center = new float[]{0,0,0};
            cfg.baseRadius = 1f;
            cfg.size = new float[]{1,1,1};
            cfg.minMarginPct = 0.15f;
            cfg.maxDistanceMult = 12f;
            cfg.spinDegPerSec = 45f;
            cfg.spinSignFree  = -1;
            cfg.spinSignOrbit = +1;
        }

        final float PCX = cfg.center[0], PCY = cfg.center[1], PCZ = cfg.center[2];
        final float SX = cfg.size[0],   SY = cfg.size[1],   SZ = cfg.size[2];
        float radius = effectivePlanetRadius(cfg.baseRadius, SX, SY, SZ);
        float minDist = radius + radius * cfg.minMarginPct;
        float maxDist = Math.max(minDist * 1.1f, radius * cfg.maxDistanceMult);

        aimFreeCameraAt(cam, PCX, PCY, PCZ);

        long last = System.nanoTime();
        float angle = 0f;

        GLWindow win = new GLWindow(1280, 720, "PlanetRender");
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);

        Mesh sphere = Mesh.uvSphere(64, 128, cfg.baseRadius);

        Shader planetShader = new Shader(
                Resources.text("shaders/basic.vert"),
                Resources.text("shaders/basic.frag")
        );
        Shader atmoShader = null;
        if (cfg.atmosphere.enabled) {
            atmoShader = new Shader(
                    Resources.text("shaders/atmo.vert"),
                    Resources.text("shaders/atmo.frag")
            );
        }

        engine.gl.Texture albedo = null;
        if (cfg.albedo != null && !cfg.albedo.isBlank()) {
            try { albedo = engine.gl.Texture.load(cfg.albedo); }
            catch (Exception e) { System.err.println("Could not load albedo: " + e.getMessage()); }
        }

        while (win.isOpen()) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            handleInput(win, cam, dt, minDist, maxDist, PCX, PCY, PCZ);
            enforceDistanceFromSphere(cam, PCX, PCY, PCZ, minDist, maxDist);

            int spinSign = cam.followTarget ? cfg.spinSignOrbit : cfg.spinSignFree;
            angle += cfg.spinDegPerSec * dt * spinSign;

            glViewport(0, 0, win.width(), win.height());
            glClearColor(0.06f, 0.07f, 0.09f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float[] proj  = cam.projMatrix(win.width(), win.height());
            float[] view  = cam.viewMatrix();
            float[] model = mul(mul(matTranslate(PCX, PCY, PCZ), matRotateY(angle)), matScale(SX, SY, SZ));

            // -------- Draw solid planet --------
            planetShader.use();
            setMat4(planetShader.id(), "uProj",  proj);
            setMat4(planetShader.id(), "uView",  view);
            setMat4(planetShader.id(), "uModel", model);
            setVec3(planetShader.id(), "uCamPos", cam.x, cam.y, cam.z);
            setVec3(planetShader.id(), "uLightDir", 0.3f, 0.6f, -0.7f);
            setVec3(planetShader.id(), "uLightColor", 1f, 1f, 1f);
            glUniform1f(glGetUniformLocation(planetShader.id(), "uLightIntensity"), 1.0f);

            if (albedo != null) {
                glUniform1i(glGetUniformLocation(planetShader.id(), "uUseTexture"), 1);
                glUniform1i(glGetUniformLocation(planetShader.id(), "uAlbedo"), 0);
                albedo.bind(0);
            } else {
                glUniform1i(glGetUniformLocation(planetShader.id(), "uUseTexture"), 0);
            }

            // Solid draw: back-face culling ON, depth write ON, blending OFF
            glDisable(GL_BLEND);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glEnable(GL_CULL_FACE);

            glFrontFace(GL_CCW);
            glCullFace(GL_BACK);

            sphere.draw();


            // -------- Draw atmosphere shell --------
            if (cfg.atmosphere.enabled && atmoShader != null) {
                float shellScale = 1.0f + Math.max(0, cfg.atmosphere.thicknessPct);
                float[] modelAtmo = mul(model, matUniformScale(shellScale));

                atmoShader.use();
                setMat4(atmoShader.id(), "uProj",  proj);
                setMat4(atmoShader.id(), "uView",  view);
                setMat4(atmoShader.id(), "uModel", modelAtmo);

                // ---- set ALL uniforms before any draw ----
                glUniform3f(glGetUniformLocation(atmoShader.id(),"uCamPos"), cam.x, cam.y, cam.z);
                glUniform3f(glGetUniformLocation(atmoShader.id(),"uLightDir"), 0.3f, 0.6f, -0.7f);

                glUniform1f(glGetUniformLocation(atmoShader.id(),"uPlanetRadius"), cfg.baseRadius);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uShellRadius"),  cfg.baseRadius * shellScale);

// you MUST set these (the frag uses them)
                float[] ac = cfg.atmosphere.color;
                glUniform3f(glGetUniformLocation(atmoShader.id(),"uAtmoColor"), ac[0], ac[1], ac[2]);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uAtmoIntensity"), cfg.atmosphere.intensity);

// optional tunables
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uHazeBoost"), 1.3f);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uRayleighSigma"), 0.8f);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uMieSigma"),      1.5f);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uAbsorption"),    0.05f);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uScaleHeightR"),  0.15f);
                glUniform1f(glGetUniformLocation(atmoShader.id(),"uScaleHeightM"),  0.06f);

// <<< IMPORTANT: enable blending and stop depth writes for the atmosphere >>>
                glEnable(GL_BLEND);
                glDepthMask(false);
                glEnable(GL_CULL_FACE);

// PASS 0 (rim: additive, backfaces)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE);
                glCullFace(GL_FRONT);
                glUniform1i(glGetUniformLocation(atmoShader.id(),"uPass"), 0);
                sphere.draw();

// PASS 1 (face haze: alpha, frontfaces)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glCullFace(GL_BACK);
                glUniform1i(glGetUniformLocation(atmoShader.id(),"uPass"), 1);
                sphere.draw();

// restore
                glDepthMask(true);
                glDisable(GL_BLEND);
                glCullFace(GL_BACK);

            }

            win.swap();
            win.poll();
        }

        sphere.delete();
        planetShader.delete();
        if (atmoShader != null) atmoShader.delete();
        if (albedo != null) albedo.delete();
        win.destroy();
    }

    // --- matrix helpers (unchanged) ---
    private static float[] matTranslate(float x, float y, float z) {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, x,y,z,1};
    }
    private static float[] matScale(float sx, float sy, float sz) {
        return new float[]{sx,0,0,0, 0,sy,0,0, 0,0,sz,0, 0,0,0,1};
    }
    private static float[] matUniformScale(float s) {
        return new float[]{s,0,0,0, 0,s,0,0, 0,0,s,0, 0,0,0,1};
    }
    private static float[] matRotateY(float deg) {
        double r = Math.toRadians(deg);
        float c = (float)Math.cos(r), s = (float)Math.sin(r);
        return new float[]{c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1};
    }
    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c=0;c<4;c++)
            for (int r0=0;r0<4;r0++)
                r[c*4+r0] = a[0*4+r0]*b[c*4+0] + a[1*4+r0]*b[c*4+1] + a[2*4+r0]*b[c*4+2] + a[3*4+r0]*b[c*4+3];
        return r;
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