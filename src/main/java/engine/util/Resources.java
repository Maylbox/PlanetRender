package engine.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class Resources {
    private Resources() {}

    public static String text(String pathOnClasspath) {
        InputStream in = Resources.class.getClassLoader().getResourceAsStream(pathOnClasspath);
        if (in == null) throw new RuntimeException("Resource not found: " + pathOnClasspath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + pathOnClasspath, e);
        }
    }
}
