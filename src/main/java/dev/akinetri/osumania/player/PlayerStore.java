package dev.akinetri.osumania.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.akinetri.osumania.beatmap.Beatmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Profile {
        public String name = "";
        public int scrollMs;
        public int offsetMs = 0;
        public boolean hitSounds = true;
        public int uiScale = dev.akinetri.osumania.ui.Canvas.DEFAULT_SCALE;
        public Map<String, Best> bests = new HashMap<>();
    }

    public record Best(long score, double accuracy, int maxCombo, String rank, boolean fullCombo) {
    }

    private final Path file;
    private final int defaultScrollMs;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public PlayerStore(Path file, int defaultScrollMs) {
        this.file = file;
        this.defaultScrollMs = defaultScrollMs;
        if (Files.exists(file)) {
            try {
                Map<UUID, Profile> loaded = GSON.fromJson(Files.readString(file),
                        new TypeToken<Map<UUID, Profile>>() {
                        }.getType());
                if (loaded != null) {
                    loaded.values().forEach(p -> {
                        if (p.bests == null) p.bests = new HashMap<>();
                    });
                    profiles.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.warn("Не удалось прочитать {}: {}", file, e.toString());
            }
        }
    }

    public Profile get(UUID uuid, String name) {
        Profile profile = profiles.computeIfAbsent(uuid, u -> {
            Profile p = new Profile();
            p.scrollMs = defaultScrollMs;
            return p;
        });
        profile.name = name;
        return profile;
    }

    public static String mapKey(Beatmap map) {
        return map.displayName() + " |" + map.keys() + "K|" + map.notes().size();
    }

    public boolean submit(UUID uuid, String name, Beatmap map, Best best) {
        Profile profile = get(uuid, name);
        String key = mapKey(map);
        Best old = profile.bests.get(key);
        if (old != null && old.score() >= best.score()) return false;
        profile.bests.put(key, best);
        save();
        return true;
    }

    public synchronized void save() {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(profiles));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить {}: {}", file, e.toString());
        }
    }
}
