package dev.akinetri.osumania.game;

import net.kyori.adventure.text.format.TextColor;

public enum Judgment {
    MAX("PERFECT", TextColor.color(0x66E0FF), 320, 300),
    GREAT("GREAT", TextColor.color(0xFFD84A), 300, 300),
    GOOD("GOOD", TextColor.color(0x6CE06C), 200, 200),
    OK("OK", TextColor.color(0x5A9BFF), 100, 100),
    MEH("MEH", TextColor.color(0xB0B0B0), 50, 50),
    MISS("MISS", TextColor.color(0xFF4A4A), 0, 0);

    private final String label;
    private final TextColor color;
    private final int scoreWeight;
    private final int accWeight;

    Judgment(String label, TextColor color, int scoreWeight, int accWeight) {
        this.label = label;
        this.color = color;
        this.scoreWeight = scoreWeight;
        this.accWeight = accWeight;
    }

    public String label() {
        return label;
    }

    public TextColor color() {
        return color;
    }

    public int scoreWeight() {
        return scoreWeight;
    }

    public int accWeight() {
        return accWeight;
    }
}
