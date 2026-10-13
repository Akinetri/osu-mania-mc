package dev.akinetri.osumania.beatmap;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Beatmap(
        String title,
        String artist,
        String version,
        String creator,
        int keys,
        double overallDifficulty,
        double bpm,
        List<HitObject> notes,
        @Nullable String soundEvent,
        List<BackingSound> backing,
        @Nullable String previewSound,
        @Nullable String background
) {
    public static final int MAX_KEYS = 7;

    public int lastTime() {
        int last = 0;
        for (HitObject note : notes) last = Math.max(last, note.endTimeOrTime());
        return last;
    }

    public int firstTime() {
        return notes.isEmpty() ? 0 : notes.getFirst().time();
    }

    public long holdCount() {
        return notes.stream().filter(HitObject::isHold).count();
    }

    public String displayName() {
        return artist + " - " + title + " [" + version + "]";
    }

    public record HitObject(int column, int time, int endTime) implements Comparable<HitObject> {
        public boolean isHold() {
            return endTime > time;
        }

        public int endTimeOrTime() {
            return isHold() ? endTime : time;
        }

        @Override
        public int compareTo(HitObject o) {
            int c = Integer.compare(time, o.time);
            return c != 0 ? c : Integer.compare(column, o.column);
        }
    }

    public record BackingSound(int time, String sound, float pitch, float volume) {
    }
}
