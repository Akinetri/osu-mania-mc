package dev.akinetri.osumania.beatmap;

import dev.akinetri.osumania.beatmap.Beatmap.BackingSound;
import dev.akinetri.osumania.beatmap.Beatmap.HitObject;

import java.util.ArrayList;
import java.util.List;

public final class DemoMap {
    private static final int BPM = 150;
    private static final int BEAT = 60000 / BPM;
    private static final int BAR = BEAT * 4;
    private static final int STEP = BEAT / 4;
    private static final int START = BAR;

    private static final int[][] CHORDS = {
            {3, 6, 10, 15},
            {11, 15, 18, 23},
            {6, 10, 13, 18},
            {1, 5, 8, 13},
    };
    private static final int[] BASS = {3, 11, 6, 1};

    private DemoMap() {
    }

    public static Beatmap create() {
        List<HitObject> notes = new ArrayList<>();

        int[] stairs = {0, 1, 2, 3, 3, 2, 1, 0};
        for (int bar = 0; bar < 4; bar++) {
            for (int beat = 0; beat < 4; beat++) {
                tap(notes, stairs[(bar * 4 + beat) % 8], at(bar, beat * 4));
            }
        }
        int[] eighths = {0, 2, 1, 3, 0, 2, 1, 3};
        for (int bar = 4; bar < 8; bar++) {
            for (int e = 0; e < 8; e++) {
                int t = at(bar, e * 2);
                if (e % 4 == 0) {
                    tap(notes, bar % 2 == 0 ? 0 : 1, t);
                    tap(notes, bar % 2 == 0 ? 3 : 2, t);
                } else {
                    tap(notes, eighths[e], t);
                }
            }
        }
        for (int bar = 8; bar < 12; bar++) {
            int holdCol = bar % 2 == 0 ? 0 : 3;
            hold(notes, holdCol, at(bar, 0), at(bar, 6));
            int[] taps = holdCol == 0 ? new int[]{2, 3, 1, 2} : new int[]{1, 0, 2, 1};
            for (int i = 0; i < 4; i++) tap(notes, taps[i], at(bar, i * 2));
            hold(notes, holdCol == 0 ? 2 : 1, at(bar, 8), at(bar, 14));
            tap(notes, holdCol, at(bar, 10));
            tap(notes, holdCol == 0 ? 1 : 2, at(bar, 12));
        }
        int[][] bursts = {{0, 1, 2, 3}, {3, 2, 1, 0}, {0, 2, 1, 3}, {3, 1, 2, 0}};
        for (int bar = 12; bar < 16; bar++) {
            for (int beat = 0; beat < 4; beat++) {
                int[] burst = bursts[(bar + beat) % 4];
                if (beat % 2 == 0) {
                    for (int i = 0; i < 4; i++) tap(notes, burst[i], at(bar, beat * 4 + i));
                } else {
                    tap(notes, burst[0], at(bar, beat * 4));
                    tap(notes, burst[3], at(bar, beat * 4 + 2));
                }
            }
        }
        int[][] chords = {{0, 3}, {1, 2}, {0, 1, 3}, {0, 2, 3}, {1, 3}, {0, 2}};
        for (int bar = 16; bar < 20; bar++) {
            for (int e = 0; e < 8; e++) {
                int t = at(bar, e * 2);
                if (e % 2 == 0) {
                    for (int c : chords[(bar * 4 + e / 2) % chords.length]) tap(notes, c, t);
                } else {
                    tap(notes, (bar + e) % 4, t);
                }
            }
        }
        for (int c = 0; c < 4; c++) hold(notes, c, at(20, 0), at(20, 12));

        notes.sort(null);
        List<BackingSound> backing = buildBacking(notes, 21);
        return new Beatmap("Minestom Groove", "osu!mania-mc", "Demo 4K", "server", 4, 6, BPM,
                List.copyOf(notes), null, backing, null, null);
    }

    private static List<BackingSound> buildBacking(List<HitObject> notes, int bars) {
        List<BackingSound> out = new ArrayList<>();
        for (int beat = 0; beat < 4; beat++) {
            out.add(new BackingSound(beat * BEAT, "block.note_block.hat", beat == 0 ? 1.4f : 1.0f, 0.8f));
        }
        for (int bar = 0; bar < bars; bar++) {
            int chord = bar % 4;
            out.add(new BackingSound(at(bar, 0), "block.note_block.bass", pitch(BASS[chord]), 0.9f));
            out.add(new BackingSound(at(bar, 8), "block.note_block.bass", pitch(BASS[chord]), 0.7f));
            for (int beat = 0; beat < 4; beat++) {
                int t = at(bar, beat * 4);
                out.add(new BackingSound(t, beat % 2 == 0 ? "block.note_block.basedrum" : "block.note_block.snare", 1f, 0.9f));
                out.add(new BackingSound(t + STEP * 2, "block.note_block.hat", 1.2f, 0.35f));
            }
        }
        for (HitObject n : notes) {
            int bar = Math.max(0, (n.time() - START) / BAR);
            int semitone = CHORDS[bar % 4][n.column()];
            out.add(new BackingSound(n.time(), n.isHold() ? "block.note_block.bell" : "block.note_block.harp",
                    pitch(semitone), 0.55f));
        }
        out.sort((a, b) -> Integer.compare(a.time(), b.time()));
        return List.copyOf(out);
    }

    private static int at(int bar, int sixteenth) {
        return START + bar * BAR + sixteenth * STEP;
    }

    private static void tap(List<HitObject> notes, int column, int time) {
        notes.add(new HitObject(column, time, -1));
    }

    private static void hold(List<HitObject> notes, int column, int time, int end) {
        notes.add(new HitObject(column, time, end));
    }

    private static float pitch(int semitone) {
        return (float) Math.pow(2, (semitone - 12) / 12.0);
    }
}
