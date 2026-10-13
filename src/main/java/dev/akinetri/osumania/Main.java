package dev.akinetri.osumania;

import dev.akinetri.osumania.beatmap.SongLibrary;
import dev.akinetri.osumania.command.Commands;
import dev.akinetri.osumania.game.GameManager;
import dev.akinetri.osumania.pack.ResourcePack;
import dev.akinetri.osumania.player.PlayerStore;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path workDir = Path.of("").toAbsolutePath();
        Config config = Config.load(workDir.resolve("config.json"));
        System.setProperty("minestom.tps", Integer.toString(Math.clamp(config.serverTps, 20, 100)));

        Path cacheDir = workDir.resolve("cache");
        SongLibrary library = SongLibrary.load(workDir.resolve("songs"), cacheDir, config);
        ResourcePack pack = ResourcePack.build(library, cacheDir.resolve("pack.zip"));
        pack.serve(config.packBindHost, config.packPort, config.packUrl());

        PlayerStore store = new PlayerStore(workDir.resolve("players.json"), config.defaultScrollMs);

        MinecraftServer server = MinecraftServer.init(config.onlineMode ? new Auth.Online() : new Auth.Offline());
        MinecraftServer.setBrandName("osu!mania-mc");

        GameManager manager = new GameManager(config, library, pack, store);
        manager.register(MinecraftServer.getGlobalEventHandler());
        Commands.register(MinecraftServer.getCommandManager(), manager);

        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            manager.stopAll();
            pack.stop();
        });

        server.start(config.host, config.port);
        LOGGER.info("osu!mania-mc запущен на {}:{} (Minecraft {}), карт: {}", config.host, config.port,
                MinecraftServer.VERSION_NAME, library.maps().size());
    }
}
