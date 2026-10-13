package dev.akinetri.osumania.beatmap;

import dev.akinetri.osumania.Config;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SongLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongLibrary.class);

    public record AudioTrack(String id, Path ogg, @Nullable Path preview) {
        public String soundEvent() {
            return "song." + id;
        }

        public String previewEvent() {
            return "preview." + id;
        }
    }

    public record Background(String id, Path png) {
        public String sprite() {
            return "bg/" + id;
        }
    }

    private static final int PREVIEW_SECONDS = 25;
    private static final int BG_WIDTH = 480, BG_HEIGHT = 270;

    private final List<Beatmap> maps;
    private final List<AudioTrack> tracks;
    private final List<Background> backgrounds;

    private SongLibrary(List<Beatmap> maps, List<AudioTrack> tracks, List<Background> backgrounds) {
        this.maps = List.copyOf(maps);
        this.tracks = List.copyOf(tracks);
        this.backgrounds = List.copyOf(backgrounds);
    }

    public List<Background> backgrounds() {
        return backgrounds;
    }

    public List<Beatmap> maps() {
        return maps;
    }

    public List<AudioTrack> tracks() {
        return tracks;
    }

    public static SongLibrary load(Path songsDir, Path cacheDir, Config config) throws IOException {
        Files.createDirectories(songsDir);
        Path extractedDir = cacheDir.resolve("extracted");
        Path audioDir = cacheDir.resolve("audio");
        Path bgDir = cacheDir.resolve("bg");
        Files.createDirectories(extractedDir);
        Files.createDirectories(audioDir);
        Files.createDirectories(bgDir);

        List<Path> folders = new ArrayList<>();
        try (Stream<Path> entries = Files.list(songsDir)) {
            for (Path entry : entries.sorted().toList()) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    folders.add(entry);
                } else if (name.toLowerCase().endsWith(".osz")) {
                    Path target = extractedDir.resolve(name.substring(0, name.length() - 4));
                    try {
                        extractIfNeeded(entry, target);
                        folders.add(target);
                    } catch (IOException e) {
                        LOGGER.warn("Не удалось распаковать {}: {}", name, e.toString());
                    }
                }
            }
        }

        List<Beatmap> maps = new ArrayList<>();
        Map<Path, AudioTrack> trackBySource = new LinkedHashMap<>();
        Map<Path, Optional<Background>> bgBySource = new LinkedHashMap<>();
        maps.add(DemoMap.create());

        List<Beatmap> loaded = new ArrayList<>();
        for (Path folder : folders) {
            List<Path> osuFiles;
            try (Stream<Path> walk = Files.walk(folder, 3)) {
                osuFiles = walk.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".osu")).sorted().toList();
            }
            for (Path osuFile : osuFiles) {
                OsuParser.ParsedMap parsed;
                try {
                    parsed = OsuParser.parse(Files.readAllLines(osuFile, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOGGER.warn("Ошибка чтения {}: {}", osuFile, e.toString());
                    continue;
                }
                if (parsed.mode() != 3) continue;
                if (parsed.keys() < 1 || parsed.keys() > Beatmap.MAX_KEYS) {
                    LOGGER.warn("Пропуск {} [{}]: {}K не поддерживается (максимум {}K)",
                            parsed.title(), parsed.version(), parsed.keys(), Beatmap.MAX_KEYS);
                    continue;
                }
                if (parsed.notes().isEmpty()) continue;

                String soundEvent = null;
                String previewEvent = null;
                Path audioSource = osuFile.getParent().resolve(parsed.audioFilename()).normalize();
                if (!parsed.audioFilename().isBlank() && Files.isRegularFile(audioSource)) {
                    AudioTrack track = trackBySource.get(audioSource);
                    if (track == null) {
                        Path ogg = convertAudio(audioSource, audioDir, config.ffmpegPath);
                        if (ogg != null) {
                            int previewMs = parsed.previewTime() >= 0 ? parsed.previewTime()
                                    : (int) (lastNoteTime(parsed.notes()) * 0.4);
                            Path preview = makePreview(ogg, previewMs, audioDir, config.ffmpegPath);
                            track = new AudioTrack("s" + (trackBySource.size() + 1), ogg, preview);
                            trackBySource.put(audioSource, track);
                        }
                    }
                    if (track != null) {
                        soundEvent = track.soundEvent();
                        if (track.preview() != null) previewEvent = track.previewEvent();
                    }
                } else {
                    LOGGER.warn("Аудио {} не найдено для {}", parsed.audioFilename(), osuFile);
                }
                String background = null;
                if (!parsed.backgroundFilename().isBlank()) {
                    Path bgSource = osuFile.getParent().resolve(parsed.backgroundFilename()).normalize();
                    Optional<Background> bg = bgBySource.computeIfAbsent(bgSource, src ->
                            Optional.ofNullable(convertBackground(src, bgDir, "b" + (bgBySource.size() + 1))));
                    if (bg.isPresent()) background = bg.get().sprite();
                }
                loaded.add(parsed.toBeatmap(soundEvent, previewEvent, background));
            }
        }
        loaded.sort(Comparator.comparing(Beatmap::artist, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Beatmap::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Beatmap::keys)
                .thenComparingDouble(Beatmap::overallDifficulty)
                .thenComparingInt(b -> b.notes().size()));
        maps.addAll(loaded);

        List<Background> backgrounds = bgBySource.values().stream().flatMap(Optional::stream).toList();
        LOGGER.info("Загружено карт: {} (аудиодорожек: {}, фонов: {})", maps.size(), trackBySource.size(), backgrounds.size());
        return new SongLibrary(maps, new ArrayList<>(trackBySource.values()), backgrounds);
    }

    private static int lastNoteTime(List<Beatmap.HitObject> notes) {
        int last = 0;
        for (Beatmap.HitObject n : notes) last = Math.max(last, n.endTimeOrTime());
        return last;
    }

    private static @Nullable Path makePreview(Path ogg, int previewMs, Path audioDir, String ffmpeg) {
        String name = ogg.getFileName().toString().replace(".ogg", "") + "-p" + previewMs + ".ogg";
        Path target = audioDir.resolve(name);
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) return target;
            Path tmp = audioDir.resolve(name + ".tmp.ogg");
            Process process = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", previewMs / 1000.0), "-t", Integer.toString(PREVIEW_SECONDS),
                    "-i", ogg.toString(), "-vn",
                    "-af", "afade=t=in:d=0.6,afade=t=out:st=" + (PREVIEW_SECONDS - 2) + ":d=2",
                    "-c:a", "libvorbis", "-q:a", "3", tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
                process.destroyForcibly();
                Files.deleteIfExists(tmp);
                return null;
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static @Nullable Background convertBackground(Path source, Path bgDir, String id) {
        if (!Files.isRegularFile(source)) return null;
        try {
            Path target = bgDir.resolve(sha1(source.toAbsolutePath() + "|" + Files.size(source) + "|"
                    + Files.getLastModifiedTime(source).toMillis()) + ".png");
            if (!Files.isRegularFile(target)) {
                BufferedImage image = ImageIO.read(source.toFile());
                if (image == null) return null;
                double scale = Math.max(BG_WIDTH / (double) image.getWidth(), BG_HEIGHT / (double) image.getHeight());
                int w = (int) Math.ceil(image.getWidth() * scale), h = (int) Math.ceil(image.getHeight() * scale);
                BufferedImage out = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(image, (BG_WIDTH - w) / 2, (BG_HEIGHT - h) / 2, w, h, null);
                g.dispose();
                ImageIO.write(out, "png", target.toFile());
            }
            return new Background(id, target);
        } catch (Exception e) {
            LOGGER.warn("Не удалось прочитать фон {}: {}", source.getFileName(), e.toString());
            return null;
        }
    }

    private static void extractIfNeeded(Path osz, Path target) throws IOException {
        Path marker = target.resolve(".extracted");
        long stamp = Files.getLastModifiedTime(osz).toMillis() ^ Files.size(osz);
        if (Files.exists(marker) && Files.readString(marker).equals(Long.toString(stamp))) return;

        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(osz))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.writeString(marker, Long.toString(stamp));
    }

    private static Path convertAudio(Path source, Path audioDir, String ffmpeg) {
        try {
            String hash = sha1(source.toAbsolutePath() + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis());
            Path target = audioDir.resolve(hash + ".ogg");
            if (Files.isRegularFile(target) && Files.size(target) > 0) return target;

            Path tmp = audioDir.resolve(hash + ".tmp.ogg");
            LOGGER.info("Конвертация аудио {} ...", source.getFileName());
            Process process = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error", "-i", source.toString(),
                    "-vn", "-map_metadata", "-1", "-ac", "2", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "4",
                    tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(5, TimeUnit.MINUTES) || process.exitValue() != 0) {
                process.destroyForcibly();
                Files.deleteIfExists(tmp);
                LOGGER.warn("ffmpeg не смог сконвертировать {}: {}", source, output.strip());
                return fallbackOgg(source, target);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            LOGGER.warn("ffmpeg недоступен ({}), аудио {} пропущено", e.getMessage(), source.getFileName());
            try {
                return fallbackOgg(source, audioDir.resolve(sha1(source.toString()) + ".ogg"));
            } catch (IOException ignored) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Path fallbackOgg(Path source, Path target) throws IOException {
        if (!source.getFileName().toString().toLowerCase().endsWith(".ogg")) return null;
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
