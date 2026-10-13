package dev.akinetri.osumania.game;

import dev.akinetri.osumania.beatmap.Beatmap.HitObject;
import dev.akinetri.osumania.pack.Sprites;
import dev.akinetri.osumania.ui.Canvas;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Playfield {
    static final double LINE_Y = 62.1;
    static final double HEIGHT = 8.5;
    static final double NOTE_H = 0.3;
    static final double TOP_MASK_H = 6;
    static final double BOTTOM_MASK_H = 4;
    static final double MASK_GAP = 0.12;
    static final int MOTION_DELAY_MS = 110;
    private static final double LIGHT_H = 3.2;

    private static final double Z_BG = 14;
    private static final double Z_LANES = 10.3;
    private static final double Z_LIGHT = 10.25;
    private static final double Z_BODY = 10.15;
    private static final double Z_HEAD = 10.1;
    private static final double Z_MASK = 10.0;
    private static final double Z_FRONT = 9.95;
    private static final double Z_FX = 9.92;
    private static final double Z_TEXT = 9.85;

    private static final String[] LANE_PATTERNS = {"", "C", "OO", "OCO", "OIIO", "OICIO", "OIOOIO", "OIOCOIO"};

    public static final class NoteVisual {
        final HitObject note;
        @Nullable Entity head;
        @Nullable Entity body;
        double bodyH;
        double removeAt = Double.MAX_VALUE;

        NoteVisual(HitObject note) {
            this.note = note;
        }
    }

    private record Effect(long due, Runnable action) {
    }

    private final Canvas canvas;
    private final int keys;
    private final double laneWidth;
    private final double noteWidth;
    private final double total;
    private final double speed;
    private final String[] lanes;
    private final Entity[] keySprites;
    private final Entity[] lights;
    private final Entity[] bursts;
    private final Entity judgment;
    private final Entity combo;
    private final Entity score;
    private final Entity accuracy;
    private final Entity progress;
    private final List<Effect> effects = new ArrayList<>();
    private int judgmentGeneration;

    public Playfield(Canvas canvas, int keys, int scrollMs, InputKey[] layout, Component title, @Nullable String background) {
        this.canvas = canvas;
        this.keys = keys;
        this.laneWidth = Math.min(1.0, 5.6 / keys);
        this.noteWidth = laneWidth * 0.94;
        this.total = keys * laneWidth;
        this.speed = HEIGHT / scrollMs;
        this.lanes = new String[keys];
        for (int i = 0; i < keys; i++) {
            lanes[i] = switch (LANE_PATTERNS[keys].charAt(i)) {
                case 'I' -> "inner";
                case 'C' -> "center";
                default -> "outer";
            };
        }

        canvas.sprite(background != null ? background : Sprites.DEFAULT_BG, 0, Canvas.EYE_Y, Z_BG, 44, 24.75, 4);

        double laneBottom = LINE_Y - MASK_GAP - 0.5;
        for (int i = 0; i < keys; i++) {
            canvas.sprite(Sprites.STAGE_LANE, laneU(i), (laneBottom + LINE_Y + HEIGHT) / 2, Z_LANES,
                    laneWidth, LINE_Y + HEIGHT - laneBottom);
        }
        for (int side = -1; side <= 1; side += 2) {
            canvas.sprite(Sprites.STAGE_BORDER, side * (total / 2 + 0.06), (laneBottom + LINE_Y + HEIGHT) / 2, Z_LANES - 0.02,
                    0.12, LINE_Y + HEIGHT - laneBottom);
        }

        lights = new Entity[keys];
        bursts = new Entity[keys];
        keySprites = new Entity[keys];
        double keySize = Math.min(laneWidth * 0.86, 0.9);
        for (int i = 0; i < keys; i++) {
            lights[i] = canvas.sprite(Sprites.light(lanes[i]), laneU(i), LINE_Y, Z_LIGHT, laneWidth, 0);
            bursts[i] = canvas.sprite(Sprites.hit(lanes[i]), laneU(i), LINE_Y, Z_FX, 0, 0);
            keySprites[i] = canvas.sprite(Sprites.key(lanes[i], false), laneU(i), LINE_Y - 0.75, Z_FRONT, keySize, keySize);
            canvas.text(Component.keybind(layout[i].keybind()), laneU(i), LINE_Y - 1.7, Z_TEXT, 1.2f);
        }

        double maskWidth = total + 0.8;
        canvas.sprite(Sprites.STAGE_TOP, 0, LINE_Y + HEIGHT + TOP_MASK_H / 2, Z_MASK, maskWidth, TOP_MASK_H);
        canvas.sprite(Sprites.STAGE_BOTTOM, 0, LINE_Y - MASK_GAP - BOTTOM_MASK_H / 2, Z_MASK, maskWidth, BOTTOM_MASK_H);
        canvas.sprite(Sprites.STAGE_LINE, 0, LINE_Y, Z_FRONT, total + 0.1, 0.2);

        canvas.sprite(Sprites.BAR_BG, 0, LINE_Y + HEIGHT + 0.25, Z_FRONT, total, 0.1);
        progress = canvas.sprite(Sprites.BAR_FILL, -total / 2, LINE_Y + HEIGHT + 0.25, Z_FX, 0, 0.1);
        canvas.text(title, 0, LINE_Y + HEIGHT + 0.6, Z_TEXT, 1.1f);

        judgment = canvas.sprite(Sprites.judgment("MAX"), 0, LINE_Y + 2.4, Z_TEXT, 0, 0);
        combo = canvas.text(Component.empty(), 0, LINE_Y + 3.1, Z_TEXT, 3f);
        score = canvas.text(Component.empty(), total / 2 + 2.6, LINE_Y + HEIGHT - 0.6, Z_TEXT, 2.4f);
        accuracy = canvas.text(Component.empty(), total / 2 + 2.6, LINE_Y + HEIGHT - 1.3, Z_TEXT, 1.8f);
    }

    public double spawnLeadMs() {
        return Math.min(400, (TOP_MASK_H - NOTE_H - 0.3) / speed);
    }

    private double laneU(int column) {
        return (column - (keys - 1) / 2.0) * laneWidth;
    }

    public NoteVisual spawn(HitObject note, double motionStart) {
        NoteVisual v = new NoteVisual(note);
        int column = note.column();
        double dy = (note.time() - motionStart) * speed;
        if (note.isHold()) {
            v.bodyH = (note.endTime() - note.time()) * speed;
            v.body = canvas.sprite(Sprites.body(lanes[column]), laneU(column), LINE_Y, Z_BODY,
                    noteWidth * 0.86, v.bodyH, 15, dy + v.bodyH / 2);
        }
        v.head = canvas.sprite(Sprites.head(lanes[column]), laneU(column), LINE_Y, Z_HEAD, noteWidth, NOTE_H, 15, dy);
        return v;
    }

    public void startMotion(NoteVisual v, double now) {
        double hidden = v.note.endTimeOrTime() + (MASK_GAP + NOTE_H + 0.3) / speed;
        int ticks = Math.max(1, (int) Math.ceil((hidden - now) / 50.0));
        double target = now + ticks * 50.0;
        v.removeAt = target + 50;
        double dy = (v.note.time() - target) * speed;
        if (v.head != null) Canvas.transform(v.head, 0, dy, noteWidth, NOTE_H, ticks);
        if (v.body != null) Canvas.transform(v.body, 0, dy + v.bodyH / 2, noteWidth * 0.86, v.bodyH, ticks);
    }

    public void hitHead(NoteVisual v) {
        canvas.remove(v.head);
        v.head = null;
        if (v.body == null) v.removeAt = 0;
    }

    public void finish(NoteVisual v) {
        canvas.remove(v.head);
        canvas.remove(v.body);
        v.head = null;
        v.body = null;
        v.removeAt = 0;
    }

    public void markMissed(NoteVisual v) {
        if (v.head != null) Canvas.setSprite(v.head, Sprites.HEAD_MISS);
        if (v.body != null) Canvas.setSprite(v.body, Sprites.BODY_MISS);
    }

    public void setPressed(int column, boolean pressed) {
        Canvas.setSprite(keySprites[column], Sprites.key(lanes[column], pressed));
        if (pressed) Canvas.transform(lights[column], 0, LIGHT_H / 2, laneWidth, LIGHT_H, 0);
        else Canvas.transform(lights[column], 0, 0, laneWidth, 0, 3);
    }

    public void burst(int column) {
        Entity e = bursts[column];
        double size = laneWidth * 1.6;
        Canvas.transform(e, 0, 0, size, size, 0);
        schedule(60, () -> Canvas.transform(e, 0, 0, 0, 0, 3));
    }

    public void showJudgment(Judgment j) {
        String id = Sprites.judgment(j.name());
        double h = 0.7, w = h * Sprites.aspect(id);
        int generation = ++judgmentGeneration;
        Canvas.setSprite(judgment, id);
        Canvas.transform(judgment, 0, 0, w * 1.25, h * 1.25, 0);
        schedule(60, () -> {
            if (generation == judgmentGeneration) Canvas.transform(judgment, 0, 0, w, h, 2);
        });
        schedule(500, () -> {
            if (generation == judgmentGeneration) Canvas.transform(judgment, 0, 0, w, 0, 2);
        });
    }

    public void startProgress(double durationMs) {
        int ticks = Math.max(1, (int) Math.round(durationMs / 50));
        Canvas.transform(progress, total / 2, 0, total, 0.1, ticks);
    }

    public void showCombo(int value) {
        Canvas.setText(combo, value >= 2 ? digits(value + "x") : Component.empty());
    }

    public void showScore(String scoreText, String accText) {
        Canvas.setText(score, digits(scoreText));
        Canvas.setText(accuracy, digits(accText));
    }

    public static Component digits(String text) {
        return Component.text(text).font(Sprites.DIGITS_FONT);
    }

    private void schedule(long delayMs, Runnable action) {
        effects.add(new Effect(System.nanoTime() + delayMs * 1_000_000, action));
    }

    public void tick() {
        if (effects.isEmpty()) return;
        long now = System.nanoTime();
        List<Effect> due = new ArrayList<>();
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            if (e.due() <= now) {
                due.add(e);
                it.remove();
            }
        }
        due.forEach(e -> e.action().run());
    }

    public void destroy() {
        effects.clear();
        canvas.clear();
    }
}
