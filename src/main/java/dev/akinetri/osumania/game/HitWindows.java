package dev.akinetri.osumania.game;

import org.jetbrains.annotations.Nullable;

public final class HitWindows {
    private static final double TAIL_MULTIPLIER = 1.5;

    private final double max, great, good, ok, meh, miss;

    public HitWindows(double od, int leniency) {
        this.max = 16 + leniency;
        this.great = 64 - 3 * od + leniency;
        this.good = 97 - 3 * od + leniency;
        this.ok = 127 - 3 * od + leniency;
        this.meh = 151 - 3 * od + leniency;
        this.miss = 188 - 3 * od + leniency;
    }

    public double missWindow() {
        return miss;
    }

    public double lateLimit() {
        return meh;
    }

    public double tailLateLimit() {
        return meh * TAIL_MULTIPLIER;
    }

    public @Nullable Judgment judgeHead(double offset) {
        if (offset < -miss) return null;
        return judge(Math.abs(offset), 1);
    }

    public Judgment judgeTail(double offset) {
        return judge(Math.abs(offset), TAIL_MULTIPLIER);
    }

    private Judgment judge(double abs, double k) {
        if (abs <= max * k) return Judgment.MAX;
        if (abs <= great * k) return Judgment.GREAT;
        if (abs <= good * k) return Judgment.GOOD;
        if (abs <= ok * k) return Judgment.OK;
        if (abs <= meh * k) return Judgment.MEH;
        return Judgment.MISS;
    }
}
