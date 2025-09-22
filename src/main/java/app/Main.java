package app;

import engine.config.PlanetConfig;
import engine.gl.*;
import engine.scene.Camera;
import engine.scene.Mesh;
import engine.scene.Planet;
import engine.util.CameraController;
import engine.util.DebugMenu;
import engine.util.DebugMenuController;
import engine.util.Resources;

import static org.lwjgl.glfw.GLFW.*;   // for SHIFT keys
import static org.lwjgl.opengl.GL11.*;

public class Main {
    public static void main(String[] args){ new Main().run(); }

    private void run() {
        // Window, camera, controller
        GLWindow win = new GLWindow(1280, 720, "PlanetRender");
        Camera cam = new Camera();
        CameraController ctrl = new CameraController();

        // ---- Load config ----
        PlanetConfig cfg = loadPlanetConfig();
        normalizeDir(cfg.lighting.direction);

        // Convert cfg.atmosphere -> renderer settings
        AtmosphereRenderer.Settings atmoSettings = toAtmoSettings(cfg.atmosphere);

        // Uniform scale for planet + atmo (keep sphere math happy)
        float UNIFORM_S = Math.max(cfg.size[0], Math.max(cfg.size[1], cfg.size[2]));

        // Mesh + shaders
        Mesh sphere = Mesh.uvSphere(64, 128, cfg.baseRadius);
        Shader planetShader = new Shader(Resources.text("shaders/basic.vert"),
                Resources.text("shaders/basic.frag"));
        Shader atmoShader   = (cfg.atmosphere != null && cfg.atmosphere.enabled)
                ? new Shader(Resources.text("shaders/atmo.vert"),
                Resources.text("shaders/atmo.frag"))
                : null;
        Shader gizmoShader  = new Shader(Resources.text("shaders/gizmo.vert"),
                Resources.text("shaders/gizmo.frag"));

        // Renderer orchestrates solid planet, gizmo, and atmosphere, using live cfg for lighting
        Renderer renderer = new Renderer(
                planetShader,
                atmoShader,
                gizmoShader,
                sphere,
                atmoSettings,
                cfg.lighting
        );

        // Build scene planet
        Planet planet = new Planet(
                cfg.center[0], cfg.center[1], cfg.center[2],
                cfg.baseRadius, UNIFORM_S,
                cfg.spinDegPerSec, cfg.spinSignFree, cfg.spinSignOrbit,
                sphere,
                loadAlbedo(cfg.albedo)
        );

        // Debug menu (edits cfg.lighting + atmoSettings live)
        DebugMenu menu = new DebugMenu(cfg.lighting, atmoSettings);
        DebugMenuController menuCtrl = new DebugMenuController();

        // Aim camera at planet to start
        aimFreeCameraAt(cam, planet.cx, planet.cy, planet.cz);

        // Loop state
        float angle = 0f;
        long last = System.nanoTime();

        // Main loop
        while (win.isOpen()) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            float minDist = planet.worldRadius() * (1f + cfg.minMarginPct);
            float maxDist = Math.max(minDist * 1.1f, planet.worldRadius() * cfg.maxDistanceMult);

            // Input + camera
            ctrl.update(win, cam, dt, minDist, maxDist, planet.cx, planet.cy, planet.cz);

            // Debug menu input (use current key states on win.keys)
            boolean shiftHeld = win.keys[GLFW_KEY_LEFT_SHIFT] || win.keys[GLFW_KEY_RIGHT_SHIFT];
            menuCtrl.update(win, menu, shiftHeld, dt);

            enforceDistanceFromSphere(cam, planet, minDist, maxDist);

            int spinSign = cam.followTarget ? planet.spinSignOrbit : planet.spinSignFree;
            angle += planet.spinDegPerSec * dt * spinSign;

            // Frame
            glViewport(0, 0, win.width(), win.height());
            glClearColor(0.06f, 0.07f, 0.09f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float[] proj = cam.projMatrix(win.width(), win.height());
            float[] view = cam.viewMatrix();

            // Lighting comes from cfg.lighting (held by renderer)
            renderer.drawPlanet(planet, proj, view, angle, win.width(), win.height(), cam, cfg.lighting.direction);

            // Overlay last
            menu.render(win.width(), win.height());

            win.swap();
            win.poll();
        }

        // Cleanup
        renderer.delete();
        planetShader.delete();
        if (atmoShader != null) atmoShader.delete();
        gizmoShader.delete();
        sphere.delete();
        if (planet.albedo != null) planet.albedo.delete();
        win.destroy();
    }

    // ---------------- helpers kept local (tiny & generic) ----------------

    private PlanetConfig loadPlanetConfig() {
        try {
            String json = Resources.text("data/planet.json");
            PlanetConfig cfg = new com.google.gson.Gson().fromJson(json, PlanetConfig.class);
            if (cfg == null) throw new RuntimeException("Empty planet config");
            cfg.applyDefaultsIfNeeded();
            return cfg;
        } catch (Exception e) {
            System.err.println("planet.json not found/invalid; using defaults. " + e);
            PlanetConfig cfg = new PlanetConfig();
            cfg.center = new float[]{0,0,0};
            cfg.baseRadius = 1f;
            cfg.size = new float[]{1,1,1};
            cfg.minMarginPct = 0.15f;
            cfg.maxDistanceMult = 12f;
            cfg.spinDegPerSec = 45f;
            cfg.spinSignFree  = -1;
            cfg.spinSignOrbit = +1;
            if (cfg.atmosphere == null) cfg.atmosphere = new PlanetConfig.Atmosphere();
            cfg.applyDefaultsIfNeeded();
            return cfg;
        }
    }

    private static AtmosphereRenderer.Settings toAtmoSettings(PlanetConfig.Atmosphere a){
        AtmosphereRenderer.Settings s = new AtmosphereRenderer.Settings();
        s.enabled = (a != null) && a.enabled;
        if (a != null) {
            s.thicknessPct = a.thicknessPct;
            s.intensity    = a.intensity;
            s.color        = new float[]{ a.color[0], a.color[1], a.color[2] };
        }
        return s;
    }

    private Texture loadAlbedo(String path) {
        if (path == null || path.isBlank()) return null;
        try { return Texture.load(path); }
        catch (Exception e) {
            System.err.println("Could not load albedo: " + e.getMessage());
            return null;
        }
    }

    private void aimFreeCameraAt(Camera c, float tx, float ty, float tz) {
        float vx = tx - c.x, vy = ty - c.y, vz = tz - c.z;
        float len = (float)Math.sqrt(vx*vx + vy*vy + vz*vz);
        if (len < 1e-6f) { c.pitchDeg = 0f; return; }
        vx /= len; vy /= len; vz /= len;
        c.yawDeg   = (float)Math.toDegrees(Math.atan2(vz, vx));
        c.pitchDeg = (float)Math.toDegrees(Math.asin(vy));
        if (c.pitchDeg < -89f) c.pitchDeg = -89f;
        if (c.pitchDeg >  89f) c.pitchDeg =  89f;
    }

    // Keep the camera outside the planet shell
    private void enforceDistanceFromSphere(Camera c, Planet p, float minDist, float maxDist) {
        float dx = c.x - p.cx, dy = c.y - p.cy, dz = c.z - p.cz;
        float dist = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist <= 1e-6f) { c.x = p.cx; c.y = p.cy; c.z = p.cz + minDist; return; }
        float inv = 1f / dist;
        float dirX = dx * inv, dirY = dy * inv, dirZ = dz * inv;
        if (dist < minDist) {
            c.x = p.cx + dirX * minDist;
            c.y = p.cy + dirY * minDist;
            c.z = p.cz + dirZ * minDist;
        } else if (dist > maxDist) {
            c.x = p.cx + dirX * maxDist;
            c.y = p.cy + dirY * maxDist;
            c.z = p.cz + dirZ * maxDist;
        }
    }

    private static void normalizeDir(float[] d){
        float L = (float)Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);
        if (L < 1e-6f){ d[0]=1; d[1]=0; d[2]=0; return; }
        d[0]/=L; d[1]/=L; d[2]/=L;
    }
}
