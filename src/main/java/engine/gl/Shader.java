package engine.gl;

import org.lwjgl.opengl.GL20;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Shader {
    private final int program;

    public Shader(String vertexSource, String fragmentSource) {
        int vs = compile(GL_VERTEX_SHADER, vertexSource);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSource);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL20.GL_FALSE) {
            throw new RuntimeException("Program link error: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(id));
        }
        return id;
    }

    public void use() { glUseProgram(program); }
    public int id() { return program; }

    public void delete() { glDeleteProgram(program); }
}
