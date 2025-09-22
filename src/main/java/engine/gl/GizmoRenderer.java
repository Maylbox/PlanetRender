package engine.gl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class GizmoRenderer {
    private final int vao, vbo;
    private final Shader shader;

    public GizmoRenderer(Shader shader) {
        this.shader = shader;
        vao = glGenVertexArrays(); vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 2L*3L*Float.BYTES, GL_STREAM_DRAW);
        glVertexAttribPointer(0,3,GL_FLOAT,false,3*Float.BYTES,0L);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    public void draw(float[] proj,float[] view,
                     float cx,float cy,float cz,
                     float dirX,float dirY,float dirZ,
                     float lineLen) {

        float len=(float)Math.sqrt(dirX*dirX+dirY*dirY+dirZ*dirZ);
        dirX/=len; dirY/=len; dirZ/=len;

        float[] pts = new float[]{
                cx,cy,cz,
                cx+dirX*lineLen, cy+dirY*lineLen, cz+dirZ*lineLen
        };

        java.nio.FloatBuffer fb = java.nio.ByteBuffer
                .allocateDirect(pts.length*Float.BYTES)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(pts).flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, fb);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);

        glBindVertexArray(vao);
        shader.use();
        glUniformMatrix4fv(glGetUniformLocation(shader.id(),"uProj"), false, proj);
        glUniformMatrix4fv(glGetUniformLocation(shader.id(),"uView"), false, view);
        glUniform3f(glGetUniformLocation(shader.id(),"uColor"), 1.0f,0.9f,0.2f);

        glLineWidth(3f);
        glDrawArrays(GL_LINES,0,2);
        glBindVertexArray(0);
    }

    public void delete(){ glDeleteBuffers(vbo); glDeleteVertexArrays(vao); }
}
