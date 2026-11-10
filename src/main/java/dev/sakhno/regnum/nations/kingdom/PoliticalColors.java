package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;

/** Wire/persistent values are palette indexes, never arbitrary client RGB values. */
public final class PoliticalColors {
    private PoliticalColors() { }
    private static final int[] COLORS = {0xffb76758,0xff4c91b8,0xff6c9c69,0xffc2a45b,0xff9276b5,
            0xff58a69c,0xffc780a1,0xffba8653,0xff7887b9,0xff93a653};
    public static final int COUNT = COLORS.length;
    public static boolean valid(int index) { return index >= 0 && index < COUNT; }
    public static int fallback(UUID id) { return Math.floorMod(id.hashCode(),COUNT); }
    public static int rgb(int index) {
        if (!valid(index)) throw new IllegalArgumentException("Invalid political palette index");
        return COLORS[index];
    }
    public static int map(int index) { return subdued(rgb(index)); }
    public static int subdued(int color) {
        int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
        return 0xff000000 | ((r*2+48)/3)<<16 | ((g*2+48)/3)<<8 | (b*2+48)/3;
    }
}
