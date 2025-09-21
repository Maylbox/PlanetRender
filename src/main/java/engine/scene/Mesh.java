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

    // In engine.scene.Mesh
    public static Mesh uvSphere(int stacks, int slices, float radius) {
        // stacks: latitudes (>=2), slices: longitudes (>=3)
        stacks = Math.max(2, stacks);
        slices = Math.max(3, slices);

        final int verts = (stacks + 1) * (slices + 1);
        final float[] interleaved = new float[verts * 6]; // pos(3) + normal(3)

        int vi = 0;
        for (int i = 0; i <= stacks; i++) {
            // phi in [-π/2 .. +π/2]  (south -> north)
            float v = (float)i / stacks;
            float phi = (float)(Math.PI * (v - 0.5f)); // -π/2 .. +π/2
            float cp = (float)Math.cos(phi);
            float sp = (float)Math.sin(phi);

            for (int j = 0; j <= slices; j++) {
                // theta in [0 .. 2π) around Y
                float u = (float)j / slices;
                float theta = (float)(u * Math.PI * 2.0);

                float ct = (float)Math.cos(theta);
                float st = (float)Math.sin(theta);

                // unit sphere normal
                float nx = cp * ct;
                float ny = sp;
                float nz = cp * st;

                // position = normal * radius
                float px = nx * radius;
                float py = ny * radius;
                float pz = nz * radius;

                // interleave: pos then normal
                interleaved[vi++] = px;
                interleaved[vi++] = py;
                interleaved[vi++] = pz;
                interleaved[vi++] = nx;
                interleaved[vi++] = ny;
                interleaved[vi++] = nz;
            }
        }

        // indices (two tris per quad), CCW winding
        final int stride = slices + 1;
        final int quads = stacks * slices;
        int[] idx = new int[quads * 6];
        int k = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int i0 = i * stride + j;
                int i1 = i0 + 1;
                int i2 = i0 + stride;
                int i3 = i2 + 1;

                // (i0, i2, i1) and (i1, i2, i3) => outward normals with default GL_CCW
                idx[k++] = i0; idx[k++] = i2; idx[k++] = i1;
                idx[k++] = i1; idx[k++] = i2; idx[k++] = i3;
            }
        }

        return new Mesh(interleaved, idx);
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
}
