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

    public void applyDefaultsIfNeeded() {
        if (center == null || center.length != 3) center = new float[]{0,0,0};
        if (size   == null || size.length   != 3) size   = new float[]{1,1,1};
        if (baseRadius <= 0) baseRadius = 1.0f;

        if (minMarginPct <= 0)   minMarginPct   = 0.15f;
        if (maxDistanceMult <= 0) maxDistanceMult = 12f;

        if (spinDegPerSec == 0) spinDegPerSec = 45f;
        if (spinSignFree == 0)  spinSignFree  = -1;
        if (spinSignOrbit == 0) spinSignOrbit = +1;

        if (albedo == null || albedo.isBlank()) albedo = "";

        if (atmosphere == null) atmosphere = new Atmosphere();
        if (atmosphere.color == null || atmosphere.color.length != 3) atmosphere.color = new float[]{0.45f, 0.7f, 1.0f};
        if (atmosphere.power <= 0)      atmosphere.power = 3.0f;
        if (atmosphere.intensity <= 0)  atmosphere.intensity = 1.0f;
        // modest default thickness
        if (atmosphere.thicknessPct <= 0) atmosphere.thicknessPct = 0.02f;
        // default disabled unless user opts in
        // (leave atmosphere.enabled as-is)
    }
}
