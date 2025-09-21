package engine.config;

public class PlanetConfig {
    public float[] center;          // [x,y,z]
    public float   baseRadius;      // sphere radius before scaling
    public float[] size;            // [sx,sy,sz] scale

    public float   minMarginPct;    // e.g., 0.15
    public float   maxDistanceMult; // e.g., 12

    public float   spinDegPerSec;   // e.g., 45
    public int     spinSignFree;    // +1 or -1
    public int     spinSignOrbit;   // +1 or -1

    // sane defaults in case fields are missing
    public void applyDefaultsIfNeeded() {
        if (center == null || center.length != 3) center = new float[]{0,0,0};
        if (size   == null || size.length   != 3) size   = new float[]{1,1,1};
        if (baseRadius <= 0) baseRadius = 1.0f;

        if (minMarginPct <= 0)   minMarginPct = 0.15f;
        if (maxDistanceMult <= 0) maxDistanceMult = 12f;

        if (spinDegPerSec == 0) spinDegPerSec = 45f;
        if (spinSignFree == 0)  spinSignFree = -1;
        if (spinSignOrbit == 0) spinSignOrbit = +1;
    }
}
