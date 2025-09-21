// engine/gl/Texture.java
package engine.gl;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;       // GL_TEXTURE0, glActiveTexture
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*; // memAlloc/memFree

public class Texture {
    private final int id;
    public int id() { return id; }
    private Texture(int id) { this.id = id; }

    public static Texture load(String resourcePath) {
        // Read the file into a byte[]
        byte[] bytes = readResourceBytes(resourcePath);
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("Texture resource not found or empty: " + resourcePath);
        }

        // Create GL texture + defaults
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        STBImage.stbi_set_flip_vertically_on_load(true);

        // Use stack ONLY for small ints; allocate the big byte buffer off-heap.
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer data = null;
            ByteBuffer image = null;
            try {
                data = memAlloc(bytes.length);
                data.put(bytes).flip();

                // Let STBI decode; ask it to keep the source channels (0) or force RGBA (4)
                image = STBImage.stbi_load_from_memory(data, w, h, comp, 0);
                if (image == null) {
                    throw new RuntimeException("Failed to load texture " + resourcePath + " : " +
                            STBImage.stbi_failure_reason());
                }

                int width = w.get(0);
                int height = h.get(0);
                int channels = comp.get(0);
                int format = (channels == 4) ? GL_RGBA : GL_RGB;

                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format,
                        GL_UNSIGNED_BYTE, image);
                glGenerateMipmap(GL_TEXTURE_2D);
            } finally {
                if (image != null) STBImage.stbi_image_free(image);
                if (data != null)  memFree(data);
            }
        }

        glBindTexture(GL_TEXTURE_2D, 0);
        return new Texture(tex);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void delete() { glDeleteTextures(id); }

    // ---- helpers ----
    private static byte[] readResourceBytes(String resourcePath) {
        try (InputStream is = Texture.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) return readAll(is);
        } catch (Exception ignored) {}
        try (InputStream is = Texture.class.getResourceAsStream("/" + resourcePath)) {
            if (is != null) return readAll(is);
        } catch (Exception ignored) {}
        return null;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[8 * 1024];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }
}
