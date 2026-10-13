package dev.akinetri.osumania.game;

import java.util.EnumMap;
import java.util.Map;

public final class ScoreState {
    private final int totalJudgments;
    private final Map<Judgment, Integer> counts = new EnumMap<>(Judgment.class);
    private long scoreWeightSum;
    private long accWeightSum;
    private int judged;
    private int combo;
    private int maxCombo;
    private double errorSum;
    private int errorCount;

    public ScoreState(int totalJudgments) {
        this.totalJudgments = Math.max(1, totalJudgments);
        for (Judgment j : Judgment.values()) counts.put(j, 0);
    }

    public void apply(Judgment judgment) {
        counts.merge(judgment, 1, Integer::sum);
        scoreWeightSum += judgment.scoreWeight();
        accWeightSum += judgment.accWeight();
        judged++;
        if (judgment == Judgment.MISS) {
            combo = 0;
        } else {
            combo++;
            maxCombo = Math.max(maxCombo, combo);
        }
    }

    public void recordError(double offset) {
        errorSum += offset;
        errorCount++;
    }

    public long score() {
        return Math.round(1_000_000.0 * scoreWeightSum / (320.0 * totalJudgments));
    }

    public double accuracy() {
        return judged == 0 ? 1 : accWeightSum / (300.0 * judged);
    }

    public int combo() {
        return combo;
    }

    public int maxCombo() {
        return maxCombo;
    }

    public int count(Judgment judgment) {
        return counts.get(judgment);
    }

    public boolean fullCombo() {
        return counts.get(Judgment.MISS) == 0;
    }

    public double meanError() {
        return errorCount == 0 ? 0 : errorSum / errorCount;
    }

    public String rank() {
        double acc = accuracy();
        if (acc >= 1) return "SS";
        if (acc >= 0.95) return "S";
        if (acc >= 0.90) return "A";
        if (acc >= 0.80) return "B";
        if (acc >= 0.70) return "C";
        return "D";
    }
}
