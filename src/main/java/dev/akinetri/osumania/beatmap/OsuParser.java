package dev.akinetri.osumania.beatmap;

import java.util.ArrayList;
import java.util.List;

public final class OsuParser {
    public record ParsedMap(
            int mode,
            String audioFilename,
            String title,
            String artist,
            String version,
            String creator,
            int keys,
            double overallDifficulty,
            double bpm,
            List<Beatmap.HitObject> notes,
            int previewTime,
            String backgroundFilename
    ) {
        public Beatmap toBeatmap(String soundEvent, String previewSound, String background) {
            return new Beatmap(title, artist, version, creator, keys, overallDifficulty, bpm,
                    notes, soundEvent, List.of(), previewSound, background);
        }
    }

    private OsuParser() {
    }

    public static ParsedMap parse(List<String> lines) {
        String section = "";
        int mode = 0;
        String audio = "";
        int previewTime = -1;
        String background = "";
        String title = "Unknown", titleUnicode = null, artist = "Unknown", artistUnicode = null;
        String version = "Normal", creator = "";
        double circleSize = 4, od = 5;
        double beatLength = -1;
        List<String> objectLines = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            switch (section) {
                case "General", "Metadata", "Difficulty" -> {
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).strip();
                    String value = line.substring(colon + 1).strip();
                    switch (key) {
                        case "Mode" -> mode = parseInt(value, 0);
                        case "AudioFilename" -> audio = value;
                        case "PreviewTime" -> previewTime = parseInt(value, -1);
                        case "Title" -> title = value;
                        case "TitleUnicode" -> titleUnicode = value;
                        case "Artist" -> artist = value;
                        case "ArtistUnicode" -> artistUnicode = value;
                        case "Version" -> version = value;
                        case "Creator" -> creator = value;
                        case "CircleSize" -> circleSize = parseDouble(value, 4);
                        case "OverallDifficulty" -> od = parseDouble(value, 5);
                        default -> {
                        }
                    }
                }
                case "TimingPoints" -> {
                    String[] p = line.split(",");
                    if (beatLength < 0 && p.length >= 2) {
                        double bl = parseDouble(p[1], -1);
                        boolean uninherited = p.length < 7 || p[6].strip().equals("1");
                        if (uninherited && bl > 0) beatLength = bl;
                    }
                }
                case "Events" -> {
                    String[] p = line.split(",");
                    if (background.isEmpty() && p.length >= 3 && (p[0].equals("0") || p[0].equals("Background"))) {
                        background = p[2].strip().replace("\"", "");
                    }
                }
                case "HitObjects" -> objectLines.add(line);
                default -> {
                }
            }
        }

        int keys = (int) Math.round(circleSize);
        List<Beatmap.HitObject> notes = new ArrayList<>(objectLines.size());
        if (keys >= 1) {
            for (String line : objectLines) {
                String[] p = line.split(",");
                if (p.length < 4) continue;
                int x = parseInt(p[0], 0);
                int time = parseInt(p[2], 0);
                int type = parseInt(p[3], 0);
                int column = Math.clamp((int) Math.floor(x * keys / 512.0), 0, keys - 1);
                int endTime = -1;
                if ((type & 128) != 0 && p.length >= 6) {
                    String extra = p[5];
                    int colon = extra.indexOf(':');
                    endTime = parseInt(colon >= 0 ? extra.substring(0, colon) : extra, -1);
                }
                notes.add(new Beatmap.HitObject(column, time, endTime));
            }
        }
        notes.sort(null);

        double bpm = beatLength > 0 ? 60000.0 / beatLength : 0;
        String finalTitle = titleUnicode != null && !titleUnicode.isBlank() ? titleUnicode : title;
        String finalArtist = artistUnicode != null && !artistUnicode.isBlank() ? artistUnicode : artist;
        return new ParsedMap(mode, audio, finalTitle, finalArtist, version, creator, keys, od, bpm, notes,
                previewTime, background);
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.strip()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
