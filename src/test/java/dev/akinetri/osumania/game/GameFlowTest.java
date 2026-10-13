package dev.akinetri.osumania.game;

import dev.akinetri.osumania.Config;
import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.beatmap.SongLibrary;
import dev.akinetri.osumania.pack.ResourcePack;
import dev.akinetri.osumania.player.PlayerStore;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
class GameFlowTest {
    private static final String MAP = """
            osu file format v14

            [General]
            AudioFilename: none.mp3
            Mode: 3

            [Metadata]
            Title:Flow Test
            Artist:Test
            Creator:junit
            Version:4K

            [Difficulty]
            CircleSize:4
            OverallDifficulty:5

            [TimingPoints]
            0,500,4,1,0,100,1,0

            [HitObjects]
            64,192,600,1,0,0:0:0:0:
            192,192,900,1,0,0:0:0:0:
            320,192,1200,1,0,0:0:0:0:
            448,192,1500,1,0,0:0:0:0:
            64,192,1800,128,0,2400:0:0:0:0:
            """;

    @Test
    void menuGameResults(Env env, @TempDir Path dir) throws Exception {
        Path songs = dir.resolve("songs/flow");
        Files.createDirectories(songs);
        Files.writeString(songs.resolve("map.osu"), MAP);

        Config config = new Config();
        SongLibrary library = SongLibrary.load(dir.resolve("songs"), dir.resolve("cache"), config);
        assertEquals(2, library.maps().size(), "демо-карта + тестовая");
        ResourcePack pack = ResourcePack.build(library, dir.resolve("cache/pack.zip"));
        PlayerStore store = new PlayerStore(dir.resolve("players.json"), 700);
        GameManager manager = new GameManager(config, library, pack, store);
        manager.register(env.process().eventHandler());

        Instance lobby = env.createFlatInstance();
        Player player = env.createPlayer(lobby, new Pos(0, 42, 0));
        assertTrue(env.tickWhile(() -> manager.screen(player) != GameManager.Screen.MENU, Duration.ofSeconds(5)),
                "меню должно открыться");
        Instance room = env.process().instance().getInstances().stream()
                .filter(i -> i != lobby).findFirst().orElseThrow();
        assertTrue(count(room, EntityType.ITEM_DISPLAY) >= 8, "меню нарисовано спрайтами");

        tap(env, player, InputKey.BACKWARD);
        tap(env, player, InputKey.JUMP);
        assertEquals(GameManager.Screen.PLAYING, manager.screen(player));
        GameSession session = manager.session(player);
        assertNotNull(session);
        Beatmap map = session.map();
        assertEquals("Flow Test", map.title());

        InputKey[] layout = InputKey.layout(map.keys());
        List<Beatmap.HitObject> notes = map.notes();
        Set<InputKey> held = EnumSet.noneOf(InputKey.class);
        double[] releaseAt = new double[map.keys()];
        int[] next = {0};
        double lag = config.clientTickCompensationMs + 25;
        assertTrue(env.tickWhile(() -> {
            if (manager.screen(player) != GameManager.Screen.PLAYING) return false;
            double t = session.currentChartTime();
            while (next[0] < notes.size() && t >= notes.get(next[0]).time() + lag) {
                Beatmap.HitObject note = notes.get(next[0]++);
                held.add(layout[note.column()]);
                releaseAt[note.column()] = note.isHold() ? note.endTime() + lag : t + 40;
                send(player, held);
            }
            for (int c = 0; c < layout.length; c++) {
                if (held.contains(layout[c]) && t >= releaseAt[c]) {
                    held.remove(layout[c]);
                    send(player, held);
                }
            }
            return true;
        }, Duration.ofSeconds(30)), "игра должна закончиться");

        assertEquals(GameManager.Screen.RESULTS, manager.screen(player));
        PlayerStore.Best best = store.get(player.getUuid(), player.getUsername()).bests.get(PlayerStore.mapKey(map));
        assertNotNull(best, "рекорд сохранён");
        assertTrue(best.fullCombo(), "все ноты пойманы: " + best);
        assertTrue(best.accuracy() > 0.9, "точность " + best.accuracy());

        env.tickWhile(() -> true, Duration.ofMillis(700));
        tap(env, player, InputKey.JUMP);
        assertEquals(GameManager.Screen.MENU, manager.screen(player));
    }

    private static long count(Instance instance, EntityType type) {
        return instance.getEntities().stream().filter(e -> e.getEntityType() == type).count();
    }

    private static void tap(Env env, Player player, InputKey key) {
        send(player, Set.of(key));
        env.tick();
        send(player, Set.of());
        env.tick();
    }

    private static void send(Player player, Set<InputKey> keys) {
        player.refreshInput(keys.contains(InputKey.FORWARD), keys.contains(InputKey.BACKWARD),
                keys.contains(InputKey.LEFT), keys.contains(InputKey.RIGHT), keys.contains(InputKey.JUMP),
                keys.contains(InputKey.SNEAK), keys.contains(InputKey.SPRINT));
    }
}
