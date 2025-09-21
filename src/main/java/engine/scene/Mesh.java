package engine.scene;

import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Mesh {
    private final int vao, vbo, ebo, vertexCount;

    public Mesh(float[] interleavedPosNormal, int[] indices) {
        vertexCount = indices.length;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        // VBO
        FloatBuffer vb = memAllocFloat(interleavedPosNormal.length);
        vb.put(interleavedPosNormal).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        memFree(vb);

        // EBO
        IntBuffer ib = memAllocInt(indices.length);
        ib.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        memFree(ib);

        int stride = (3+3) * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, (long)(3*Float.BYTES));
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    public void delete() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }

    // A unit cube with normals
    public static Mesh cube() {
        // 24 unique verts (pos + normal per face) to keep crisp face normals
        float[] v = {
                // pos               // normal
                // +X
                0.5f,-0.5f,-0.5f,   1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,   0.5f, 0.5f, 0.5f, 1,0,0,   0.5f,-0.5f, 0.5f, 1,0,0,
                // -X
                -0.5f,-0.5f, 0.5f,  -1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f,-0.5f,-1,0,0,  -0.5f,-0.5f,-0.5f,-1,0,0,
                // +Y
                -0.5f, 0.5f,-0.5f,   0,1,0,  -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,
                // -Y
                -0.5f,-0.5f, 0.5f,   0,-1,0, -0.5f,-0.5f,-0.5f,0,-1,0,  0.5f,-0.5f,-0.5f,0,-1,0,   0.5f,-0.5f, 0.5f,0,-1,0,
                // +Z
                -0.5f,-0.5f, 0.5f,   0,0,1,   0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,  -0.5f, 0.5f, 0.5f,0,0,1,
                // -Z
                0.5f,-0.5f,-0.5f,   0,0,-1, -0.5f,-0.5f,-0.5f,0,0,-1,  -0.5f, 0.5f,-0.5f,0,0,-1,  0.5f, 0.5f,-0.5f,0,0,-1
        };
        int[] idx = {
                0,1,2, 0,2,3,  4,5,6, 4,6,7,
                8,9,10, 8,10,11,  12,13,14, 12,14,15,
                16,17,18, 16,18,19,  20,21,22, 20,22,23
        };
        return new Mesh(v, idx);
    }
    public Mesh(float[] interleaved, int[] indices, int strideFloats) {
        vertexCount = indices.length;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        // VBO
        FloatBuffer vb = memAllocFloat(interleaved.length);
        vb.put(interleaved).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        memFree(vb);

        // EBO
        IntBuffer ib = memAllocInt(indices.length);
        ib.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        memFree(ib);

        int strideBytes = strideFloats * Float.BYTES;

        // layout(location=0) position
        glVertexAttribPointer(0, 3, GL_FLOAT, false, strideBytes, 0L);
        glEnableVertexAttribArray(0);

        // layout(location=1) normal (assumes starts at float3)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, strideBytes, (long)(3 * Float.BYTES));
        glEnableVertexAttribArray(1);

        if (strideFloats >= 8) {
            // layout(location=2) uv (assumes P(3) N(3) UV(2))
            glVertexAttribPointer(2, 2, GL_FLOAT, false, strideBytes, (long)(6 * Float.BYTES));
            glEnableVertexAttribArray(2);
        }

        glBindVertexArray(0);
    }

    // NEW: UV sphere with interleaved P(3), N(3), UV(2)
    public static Mesh uvSphere(int stacks, int slices, float radius) {
        stacks = Math.max(2, stacks);
        slices = Math.max(3, slices);

        int vertCount = (stacks + 1) * (slices + 1);
        int idxCount  = stacks * slices * 6;

        float[] v = new float[vertCount * 8]; // 3 pos + 3 norm + 2 uv
        int[]   idx = new int[idxCount];

        int vi = 0;
        for (int i = 0; i <= stacks; i++) {
            float vPct = (float)i / stacks;          // [0..1] top->bottom
            float phi  = (float)Math.PI * vPct;      // [0..PI]
            float y    = (float)Math.cos(phi);
            float r    = (float)Math.sin(phi);

            for (int j = 0; j <= slices; j++) {
                float uPct = (float)j / slices;      // [0..1] around Y
                float theta = (float)(uPct * Math.PI * 2.0); // [0..2PI]
                float x = r * (float)Math.cos(theta);
                float z = r * (float)Math.sin(theta);

                // pos
                v[vi++] = radius * x;
                v[vi++] = radius * y;
                v[vi++] = radius * z;

                // normal (unit)
                v[vi++] = x;
                v[vi++] = y;
                v[vi++] = z;

                // uv (u right->left to match typical east-west; flip if needed)
                v[vi++] = uPct;
                v[vi++] = vPct;
            }
        }

        int ii = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a =  i      * (slices + 1) + j;
                int b = (i + 1) * (slices + 1) + j;
                int c =  a + 1;
                int d =  b + 1;

                // two tris per quad
                idx[ii++] = a; idx[ii++] = b; idx[ii++] = c;
                idx[ii++] = c; idx[ii++] = b; idx[ii++] = d;
            }
        }

        return new Mesh(v, idx, 8);
    }
}
