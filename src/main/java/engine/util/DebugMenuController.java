package engine.util;

import engine.gl.GLWindow;

import static org.lwjgl.glfw.GLFW.*;

public class DebugMenuController {
    private boolean prevF1=false, prevUp=false, prevDown=false, prevLeft=false, prevRight=false;
    private boolean prevH=false, prevR=false, prevEnter=false, prevEsc=false, prevBack=false;

    // continuous: we use held booleans + dt
    public void update(GLWindow win, DebugMenu menu, boolean shiftHeld, float dt) {
        boolean f1    = win.keys[GLFW_KEY_F1];
        boolean up    = win.keys[GLFW_KEY_UP];
        boolean down  = win.keys[GLFW_KEY_DOWN];
        boolean left  = win.keys[GLFW_KEY_LEFT];
        boolean right = win.keys[GLFW_KEY_RIGHT];

        boolean ctrl  = win.keys[GLFW_KEY_LEFT_CONTROL] || win.keys[GLFW_KEY_RIGHT_CONTROL];
        boolean H     = win.keys[GLFW_KEY_H];  // atmo toggle
        boolean R     = win.keys[GLFW_KEY_R];
        boolean Enter = win.keys[GLFW_KEY_ENTER] || win.keys[GLFW_KEY_KP_ENTER];
        boolean Esc   = win.keys[GLFW_KEY_ESCAPE];
        boolean Back  = win.keys[GLFW_KEY_BACKSPACE];

        // Toggle visibility
        if (edge(f1, prevF1)) menu.setVisible(!menu.isVisible());
        if (!menu.isVisible()) { store(f1,up,down,left,right,H,R,Enter,Esc,Back); return; }

        // Selection
        if (!menu.isEditing()) {
            if (edge(up, prevUp))   menu.moveSelection(-1);
            if (edge(down, prevDown)) menu.moveSelection(+1);
        }

        // Discrete steps on tap
        float baseStep = 0.02f;
        if (shiftHeld) baseStep = 0.10f;   // bigger
        if (ctrl)      baseStep = 0.005f;  // tiny

        if (!menu.isEditing()) {
            if (edge(left, prevLeft))  menu.nudgeStep(-baseStep);
            if (edge(right, prevRight)) menu.nudgeStep(+baseStep);

            // Continuous hold (smooth)
            float heldRate = baseStep * 6.0f; // per second
            if (left)  menu.nudgeHeld(-heldRate, dt);
            if (right) menu.nudgeHeld(+heldRate, dt);

            // Atmo toggle (H)
            if (edge(H, prevH)) menu.toggleAtmo();

            // Normalize lighting dir
            if (edge(R, prevR)) menu.renormalizeLightDir();

            // Enter -> begin edit
            if (edge(Enter, prevEnter)) menu.beginEdit();
        } else {
            // Editing: text input with digits, dot, minus
            // Append digits
            appendIfPressed(win, menu, GLFW_KEY_0);
            appendIfPressed(win, menu, GLFW_KEY_1);
            appendIfPressed(win, menu, GLFW_KEY_2);
            appendIfPressed(win, menu, GLFW_KEY_3);
            appendIfPressed(win, menu, GLFW_KEY_4);
            appendIfPressed(win, menu, GLFW_KEY_5);
            appendIfPressed(win, menu, GLFW_KEY_6);
            appendIfPressed(win, menu, GLFW_KEY_7);
            appendIfPressed(win, menu, GLFW_KEY_8);
            appendIfPressed(win, menu, GLFW_KEY_9);
            appendIfPressed(win, menu, GLFW_KEY_KP_0);
            appendIfPressed(win, menu, GLFW_KEY_KP_1);
            appendIfPressed(win, menu, GLFW_KEY_KP_2);
            appendIfPressed(win, menu, GLFW_KEY_KP_3);
            appendIfPressed(win, menu, GLFW_KEY_KP_4);
            appendIfPressed(win, menu, GLFW_KEY_KP_5);
            appendIfPressed(win, menu, GLFW_KEY_KP_6);
            appendIfPressed(win, menu, GLFW_KEY_KP_7);
            appendIfPressed(win, menu, GLFW_KEY_KP_8);
            appendIfPressed(win, menu, GLFW_KEY_KP_9);

            // dot & minus
            if (edge(win.keys[GLFW_KEY_PERIOD], false) || edge(win.keys[GLFW_KEY_KP_DECIMAL], false)) menu.editAppendChar('.');
            if (edge(win.keys[GLFW_KEY_MINUS],  false)) menu.editAppendChar('-');

            if (edge(Back, prevBack))   menu.editBackspace();
            if (edge(Enter, prevEnter)) menu.editCommit();
            if (edge(Esc,   prevEsc))   menu.editCancel();
        }

        store(f1,up,down,left,right,H,R,Enter,Esc,Back);
    }

    private static boolean edge(boolean now, boolean prev){ return now && !prev; }

    private void store(boolean f1,boolean up,boolean down,boolean left,boolean right,
                       boolean h,boolean r,boolean enter,boolean esc,boolean back){
        prevF1=f1; prevUp=up; prevDown=down; prevLeft=left; prevRight=right;
        prevH=h; prevR=r; prevEnter=enter; prevEsc=esc; prevBack=back;
    }

    private void appendIfPressed(GLWindow win, DebugMenu menu, int key){
        if (edge(win.keys[key], false)) {
            char c = keyToChar(key);
            if (c != 0) menu.editAppendChar(c);
        }
    }

    private char keyToChar(int key){
        // number row
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) return (char)('0' + (key - GLFW_KEY_0));
        // numpad
        if (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9) return (char)('0' + (key - GLFW_KEY_KP_0));
        return 0;
    }
}
