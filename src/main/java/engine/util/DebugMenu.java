package engine.util;

import engine.config.PlanetConfig;
import engine.gl.AtmosphereRenderer;
import engine.gl.Shader;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static java.lang.Math.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class DebugMenu {
    private final PlanetConfig.Lighting lighting;
    private final AtmosphereRenderer.Settings atmo;

    private boolean visible = true;
    private int selected = 0;

    // ---- we edit angles now ----
    private float azDeg; // 0..360 around Y (0=+X, 90=+Z)
    private float elDeg; // -90..+90 (0=equator, +90=north pole)

    // ---- panel + text rendering (modern GL) ----
    private final int vao;
    private final int vbo;         // shared dynamic VBO for both panel + text
    private final Shader textShader;
    private final int uViewportLoc;
    private final int uColorLoc;

    private static final String VS = """
        #version 330 core
        layout(location=0) in vec2 aPos;     // pixel coords
        uniform vec2 uViewport;              // (width, height)
        void main(){
            float x =  (aPos.x / uViewport.x) * 2.0 - 1.0;
            float y =  1.0 - (aPos.y / uViewport.y) * 2.0; // top-left origin
            gl_Position = vec4(x, y, 0.0, 1.0);
        }
    """;

    private static final String FS = """
        #version 330 core
        uniform vec3 uColor;
        out vec4 o;
        void main(){ o = vec4(uColor, 1.0); }
    """;

    private static final String[] ITEMS = new String[]{
            "Light Azimuth (deg)","Light Elevation (deg)",
            "Light Color R","Light Color G","Light Color B",
            "Light Intensity",
            "Atmo Enabled",
            "Atmo Color R","Atmo Color G","Atmo Color B",
            "Atmo ThicknessPct","Atmo Intensity",
    };

    // ---- type-in edit mode ----
    private boolean editMode = false;
    private final StringBuilder editBuf = new StringBuilder();

    public DebugMenu(PlanetConfig.Lighting lighting, AtmosphereRenderer.Settings atmo) {
        this.lighting = lighting;
        this.atmo     = atmo;

        normalize(lighting.direction);
        clamp01(lighting.color);
        clamp01(atmo.color);
        atmo.thicknessPct = Math.max(0f, atmo.thicknessPct);
        atmo.intensity    = Math.max(0f, atmo.intensity);

        // initialize angles from current direction
        dirToAngles(lighting.direction, outAzEl);
        this.azDeg = outAzEl[0];
        this.elDeg = outAzEl[1];

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 128 * 1024, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        glBindVertexArray(0);

        textShader = new Shader(VS, FS);
        uViewportLoc = glGetUniformLocation(textShader.id(), "uViewport");
        uColorLoc    = glGetUniformLocation(textShader.id(), "uColor");
    }

    public void delete() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        textShader.delete();
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; }

    public void moveSelection(int delta) {
        selected = (selected + delta);
        if (selected < 0) selected = ITEMS.length - 1;
        if (selected >= ITEMS.length) selected = 0;
        // leaving edit mode when switching rows feels more natural
        editMode = false;
        editBuf.setLength(0);
    }

    /** Called on discrete step (tap arrows). Uses step sizing (Shift/Ctrl handled by controller). */
    public void nudgeStep(float delta) { applyDelta(delta); }

    /** Called every frame while a key is held (hold-to-change). */
    public void nudgeHeld(float deltaPerSecond, float dt) {
        if (dt <= 0) return;
        applyDelta(deltaPerSecond * dt);
    }

    /** Toggle atmosphere on the current toggle row (we’ll bind this to 'H'). */
    public void toggleAtmo() {
        atmo.enabled = !atmo.enabled;
    }

    public void renormalizeLightDir() {
        // Re-derive angles from the current vector (if user typed crazy stuff elsewhere)
        normalize(lighting.direction);
        dirToAngles(lighting.direction, outAzEl);
        azDeg = outAzEl[0];
        elDeg = outAzEl[1];
    }

    // ---------- Edit mode (type-in value) ----------
    public boolean isEditing() { return editMode; }

    public void beginEdit() {
        if (!isEditable(selected)) return;
        editMode = true;
        editBuf.setLength(0);
    }

    public void editBackspace() {
        if (!editMode || editBuf.length() == 0) return;
        editBuf.deleteCharAt(editBuf.length() - 1);
    }

    public void editAppendChar(char c) {
        if (!editMode) return;
        if ((c >= '0' && c <= '9') || c == '.' || c == '-' ) {
            // simple guard: only one '-' at start, one '.'
            if (c == '-') {
                if (editBuf.length() == 0) editBuf.append(c);
            } else if (c == '.') {
                if (editBuf.indexOf(".") < 0) editBuf.append(c);
            } else {
                editBuf.append(c);
            }
        }
    }

    public void editCommit() {
        if (!editMode) return;
        if (editBuf.length() == 0) { editMode = false; return; }
        try {
            float v = Float.parseFloat(editBuf.toString());
            setValueForSelected(v);
        } catch (NumberFormatException ignored) {}
        editMode = false;
        editBuf.setLength(0);
    }

    public void editCancel() {
        editMode = false;
        editBuf.setLength(0);
    }

    // ---------- Render ----------
    public void render(int viewportWidth, int viewportHeight) {
        if (!visible) return;

        // Panel geometry
        float px = 10, py = 10;
        float pw = Math.max(520, viewportWidth * 0.36f);
        float ph = 300;

        // Build text
        String txt = buildText();

        // Generate STB verts (quads)
        int maxVerts = Math.max(2048, txt.length() * 64);
        ByteBuffer quadBuf = BufferUtils.createByteBuffer(maxVerts * 16);
        int quads = STBEasyFont.stb_easy_font_print(px + 10, py + 12, txt, null, quadBuf);
        FloatBuffer quadFloats = quadBuf.asFloatBuffer();

        // Convert to triangles
        int triVerts = quads * 6;
        FloatBuffer tri = BufferUtils.createFloatBuffer(triVerts * 2);
        for (int q = 0; q < quads; q++) {
            int base = q * 4;
            putXY(tri, quadFloats, base + 0);
            putXY(tri, quadFloats, base + 1);
            putXY(tri, quadFloats, base + 2);
            putXY(tri, quadFloats, base + 2);
            putXY(tri, quadFloats, base + 3);
            putXY(tri, quadFloats, base + 0);
        }
        tri.flip();

        // Draw
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        textShader.use();
        glUniform2f(uViewportLoc, (float)viewportWidth, (float)viewportHeight);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Panel (semi-transparent backdrop)
        drawPanel(px, py, pw, ph, 0f, 0f, 0f, 0.55f);

        // Text (white)
        glUniform3f(uColorLoc, 1f, 1f, 1f);
        glBufferData(GL_ARRAY_BUFFER, tri, GL_STREAM_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);
        glDrawArrays(GL_TRIANGLES, 0, triVerts);

        glBindVertexArray(0);
        glUseProgram(0);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    // ---------- internals ----------
    private void drawPanel(float x, float y, float w, float h, float r, float g, float b, float a) {
        // 2 triangles = 6 verts
        FloatBuffer rect = BufferUtils.createFloatBuffer(6 * 2);
        rect.put(x).put(y);
        rect.put(x + w).put(y);
        rect.put(x + w).put(y + h);
        rect.put(x + w).put(y + h);
        rect.put(x).put(y + h);
        rect.put(x).put(y);
        rect.flip();

        // simple color via uColor (we want alpha too, so tint text pass uses blending)
        glUniform3f(uColorLoc, r, g, b);
        glBufferData(GL_ARRAY_BUFFER, rect, GL_STREAM_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        // draw dark panel
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // overlay a subtle top highlight (optional)
        float hi = h * 0.33f;
        FloatBuffer rect2 = BufferUtils.createFloatBuffer(6 * 2);
        rect2.put(x).put(y);
        rect2.put(x + w).put(y);
        rect2.put(x + w).put(y + hi);
        rect2.put(x + w).put(y + hi);
        rect2.put(x).put(y + hi);
        rect2.put(x).put(y);
        rect2.flip();
        glUniform3f(uColorLoc, r + 0.10f, g + 0.10f, b + 0.10f);
        glBufferData(GL_ARRAY_BUFFER, rect2, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private static void putXY(FloatBuffer dst, FloatBuffer src, int vertIndex) {
        int off = vertIndex * 4; // STB packs 4 floats per vertex; we only read x,y
        dst.put(src.get(off));
        dst.put(src.get(off + 1));
    }

    private String buildText() {
        StringBuilder sb = new StringBuilder();
        sb.append("F1: Toggle  |  Up/Down: Select  |  Left/Right: Change  |  Shift/Ctrl: big/tiny  |  H: Toggle Atmo  |  R: Sync Angles\n");
        sb.append("Enter: type value  |  digits/-/. to edit  |  Backspace  |  Enter=commit  Esc=cancel\n\n");

        // angles
        append(sb, 0,  "Light Azimuth (deg)", azDeg);
        append(sb, 1,  "Light Elevation (deg)", elDeg);

        // readback (normalized vector)
        float[] d = lighting.direction.clone();
        normalize(d);
        sb.append(String.format("  Light Dir Readback   : (%.3f, %.3f, %.3f)%n%n", d[0], d[1], d[2]));

        // rest
        append(sb, 2,  "Light Color R", lighting.color[0]);
        append(sb, 3,  "Light Color G", lighting.color[1]);
        append(sb, 4,  "Light Color B", lighting.color[2]);
        append(sb, 5,  "Light Intensity", lighting.intensity);
        append(sb, 6,  "Atmo Enabled", atmo.enabled ? 1f : 0f);
        append(sb, 7,  "Atmo Color R", atmo.color[0]);
        append(sb, 8,  "Atmo Color G", atmo.color[1]);
        append(sb, 9,  "Atmo Color B", atmo.color[2]);
        append(sb, 10, "Atmo ThicknessPct", atmo.thicknessPct);
        append(sb, 11, "Atmo Intensity", atmo.intensity);

        if (editMode) {
            sb.append("\n> Type value: ").append(editBuf).append("_");
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, int idx, String label, float value) {
        sb.append(idx == selected ? "> " : "  ");
        sb.append(String.format("%-22s : %.4f%n", label, value));
    }

    private boolean isEditable(int idx) {
        // all except the toggle row (6, Atmo Enabled)
        return idx != 6;
    }

    private void setValueForSelected(float v) {
        switch (selected) {
            case 0 -> { // azimuth
                azDeg = wrap360(v);
                applyAnglesToDir();
            }
            case 1 -> { // elevation
                elDeg = clamp(v, -90f, 90f);
                applyAnglesToDir();
            }

            case 2 -> lighting.color[0] = clamp01f(v);
            case 3 -> lighting.color[1] = clamp01f(v);
            case 4 -> lighting.color[2] = clamp01f(v);

            case 5 -> lighting.intensity = Math.max(0f, v);

            case 7  -> atmo.color[0] = clamp01f(v);
            case 8  -> atmo.color[1] = clamp01f(v);
            case 9  -> atmo.color[2] = clamp01f(v);

            case 10 -> atmo.thicknessPct = Math.max(0f, v);
            case 11 -> atmo.intensity    = Math.max(0f, v);
        }
    }

    private void applyDelta(float delta) {
        switch (selected) {
            case 0 -> { // azimuth
                azDeg = wrap360(azDeg + delta);
                applyAnglesToDir();
            }
            case 1 -> { // elevation
                elDeg = clamp(elDeg + delta, -90f, 90f);
                applyAnglesToDir();
            }

            case 2 -> lighting.color[0] = clamp01f(lighting.color[0] + delta);
            case 3 -> lighting.color[1] = clamp01f(lighting.color[1] + delta);
            case 4 -> lighting.color[2] = clamp01f(lighting.color[2] + delta);

            case 5 -> lighting.intensity = Math.max(0f, lighting.intensity + delta);

            case 7  -> atmo.color[0] = clamp01f(atmo.color[0] + delta);
            case 8  -> atmo.color[1] = clamp01f(atmo.color[1] + delta);
            case 9  -> atmo.color[2] = clamp01f(atmo.color[2] + delta);

            case 10 -> atmo.thicknessPct = Math.max(0f, atmo.thicknessPct + delta);
            case 11 -> atmo.intensity    = Math.max(0f, atmo.intensity    + delta);
        }
    }

    // helpers
    private static void normalize(float[] v) {
        float len = (float)sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if (len < 1e-6f) { v[0]=1; v[1]=0; v[2]=0; return; }
        v[0] /= len; v[1] /= len; v[2] /= len;
    }
    private static void clamp01(float[] c) { c[0]=clamp01f(c[0]); c[1]=clamp01f(c[1]); c[2]=clamp01f(c[2]); }
    private static float clamp01f(float x){ return (x<0f?0f:(x>1f?1f:x)); }
    private static float clamp(float v, float lo, float hi){ return v < lo ? lo : (v > hi ? hi : v); }
    private static float wrap360(float a){ a = a % 360f; if (a < 0f) a += 360f; return a; }

    // convert current angles -> lighting.direction (unit)
    private void applyAnglesToDir(){
        double az = toRadians(azDeg);
        double el = toRadians(elDeg);
        float x = (float)(cos(el) * cos(az));
        float y = (float)(sin(el));
        float z = (float)(cos(el) * sin(az));
        lighting.direction[0] = x;
        lighting.direction[1] = y;
        lighting.direction[2] = z;
    }

    // convert direction -> angles, returns [azDeg, elDeg]
    private final float[] outAzEl = new float[2];
    private static void dirToAngles(float[] d, float[] outAzEl){
        float[] v = d.clone();
        float len = (float)sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if (len < 1e-6f) { v[0]=1; v[1]=0; v[2]=0; len=1f; }
        v[0]/=len; v[1]/=len; v[2]/=len;

        double el = asin(v[1]);                 // [-pi/2, +pi/2]
        double az = atan2(v[2], v[0]);          // [-pi, +pi]
        float azDeg = (float)toDegrees(az);
        if (azDeg < 0f) azDeg += 360f;
        outAzEl[0] = azDeg;
        outAzEl[1] = (float)toDegrees(el);
    }
}
