package engine.gl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GLWindow {
    private long handle;
    private int width, height;
    private boolean mouseCaptured = false;
    private double lastMX, lastMY;
    public double deltaX, deltaY;  // mouse deltas per frame
    public boolean[] keys = new boolean[512];

    public GLWindow(int w, int h, String title) {
        width = w; height = h;
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        // macOS forward-compat
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Failed to create window");
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // vsync
        GL.createCapabilities();

        glfwSetFramebufferSizeCallback(handle, (win, w2, h2) -> {
            width = w2; height = h2;
            glViewport(0, 0, width, height);
        });

        glfwSetKeyCallback(handle, (win, key, sc, action, mods) -> {
            if (key >= 0 && key < keys.length) {
                keys[key] = action != GLFW_RELEASE;
            }
            // toggle mouse capture with ESC
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) setMouseCaptured(!mouseCaptured);
        });

        glfwSetCursorPosCallback(handle, (win, mx, my) -> {
            if (mouseCaptured) {
                deltaX += mx - lastMX;
                deltaY += my - lastMY;
            }
            lastMX = mx; lastMY = my;
        });

        setMouseCaptured(true);
        glEnable(GL_DEPTH_TEST);
    }

    public void setMouseCaptured(boolean capture) {
        mouseCaptured = capture;
        glfwSetInputMode(handle, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        deltaX = deltaY = 0.0;
    }

    public boolean isOpen() { return !glfwWindowShouldClose(handle); }
    public void poll() { glfwPollEvents(); }
    public void swap() { glfwSwapBuffers(handle); }
    public void close() { glfwSetWindowShouldClose(handle, true); }
    public void destroy() { glfwDestroyWindow(handle); glfwTerminate(); }

    public int width() { return width; }
    public int height() { return height; }
    public long handle() { return handle; }
}
