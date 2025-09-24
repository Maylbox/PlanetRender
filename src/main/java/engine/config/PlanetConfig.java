package engine.config;

public class PlanetConfig {
    public float[] center;
    public float   baseRadius;
    public float[] size;

    public float   minMarginPct;
    public float   maxDistanceMult;

    public float   spinDegPerSec;
    public int     spinSignFree;
    public int     spinSignOrbit;

    public String  albedo;

    // NEW
    public Atmosphere atmosphere;

    public static class Atmosphere {
        public boolean enabled;
        public float   thicknessPct;  // shell thickness as % of planet radius, e.g. 0.02 => +2%
        public float   intensity;     // overall brightness multiplier
        public float   power;         // falloff exponent, 2..6 is nice
        public float[] color;         // [r,g,b] 0..1
    }

    public static class Clouds {
        public boolean enabled = false;

        // Per-layer settings
        public static class Layer {
            public float   altitudePct   = 0.015f;        // +% of planet radius above surface (e.g. 1.5%)
            public float   thicknessPct  = 0.002f;        // shell thickness – mostly visual, can stay small
            public float[] color         = {1f, 1f, 1f};  // tint (multiply)
            public float   opacity       = 0.6f;          // 0..1
            public float[] scrollUV      = {0.01f, 0.0f}; // movement in lat-long UV / sec
            public float   rotationDegPS = 0f;            // optional extra spin around Y / sec
            public float   coverage      = 0.5f;          // 0..1 density threshold (procedural path)
            public float   noiseScale    = 2.5f;          // procedural noise scale (procedural path)
            public String  texture       = "";            // optional albedo (rgba) for clouds; empty = procedural
            public float   storminess    = 0.0f;          // 0 calm … 1 violent (affects appearance/speed)
        }

        public Layer[] layers = new Layer[]{ new Layer() };
    }
    public Clouds clouds = new Clouds();

    public static class Lighting {
        public float[] direction = {1f, 0f, 0f};
        public float[] color     = {1f, 1f, 1f};
        public float   intensity = 2.0f;
    }

    public Lighting lighting = new Lighting();

    public void applyDefaultsIfNeeded() {
        if (center == null || center.length != 3) center = new float[]{0,0,0};
        if (size   == null || size.length   != 3) size   = new float[]{1,1,1};
        if (baseRadius <= 0) baseRadius = 1.0f;

        if (minMarginPct   <= 0)  minMarginPct   = 0.15f;
        if (maxDistanceMult <= 1) maxDistanceMult = 12f;

        if (spinDegPerSec == 0) spinDegPerSec = 45f;
        if (spinSignFree  == 0) spinSignFree  = -1;
        if (spinSignOrbit == 0) spinSignOrbit = +1;

        if (albedo == null) albedo = "";

        if (atmosphere == null) atmosphere = new Atmosphere();
        if (atmosphere.color == null || atmosphere.color.length != 3)
            atmosphere.color = new float[]{0.45f, 0.7f, 1.0f};

        // clamps/sanity
        atmosphere.color[0] = clamp01(atmosphere.color[0]);
        atmosphere.color[1] = clamp01(atmosphere.color[1]);
        atmosphere.color[2] = clamp01(atmosphere.color[2]);

        if (atmosphere.power <= 0)     atmosphere.power = 3.0f;
        if (atmosphere.intensity < 0)  atmosphere.intensity = 0.0f;
        if (atmosphere.thicknessPct < 0) atmosphere.thicknessPct = 0.02f;
        if (atmosphere.thicknessPct > 1.0f) atmosphere.thicknessPct = 1.0f; // optional cap

        if (clouds == null) clouds = new Clouds();
        // clamp/sanitize layers
        for (var L : clouds.layers) {
            if (L.color == null || L.color.length != 3) L.color = new float[]{1f,1f,1f};
            L.opacity      = Math.max(0f, Math.min(1f, L.opacity));
            L.coverage     = Math.max(0f, Math.min(1f, L.coverage));
            L.noiseScale   = Math.max(0.1f, L.noiseScale);
            L.altitudePct  = Math.max(0.001f, L.altitudePct);
            L.thicknessPct = Math.max(0.0f,   L.thicknessPct);
        }
    }

    private static float clamp01(float x){ return x < 0f ? 0f : (x > 1f ? 1f : x); }
}
